terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
  required_version = ">= 1.5"
}

provider "aws" {
  region = var.aws_region
}

# ─────────────────────────────────────────────────────────────────────────────
# Data sources — use the default VPC and its subnets to keep the setup simple.
# ─────────────────────────────────────────────────────────────────────────────

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Amazon Linux 2023 (ARM64 image — switch to x86_64 below if needed).
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# ECR Repository — stores the database-node Docker image.
# Build and push before applying the EC2 resources.
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_ecr_repository" "database_node" {
  name                 = "cs6650/database-node"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# IAM — allows EC2 instances to pull images from ECR without static credentials.
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_iam_role" "ec2_ecr_pull" {
  name = "cs6650-assignment4-ec2-ecr-pull"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecr_read_only" {
  role       = aws_iam_role.ec2_ecr_pull.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_instance_profile" "ec2_ecr_pull" {
  name = "cs6650-assignment4-ec2-ecr-pull"
  role = aws_iam_role.ec2_ecr_pull.name
}

# ─────────────────────────────────────────────────────────────────────────────
# Security Groups
# ─────────────────────────────────────────────────────────────────────────────

# Allow HTTP traffic on port 8080 from anywhere, plus all traffic between
# nodes in the same security group (for inter-node replication).
resource "aws_security_group" "database_nodes" {
  name        = "cs6650-assignment4-database-nodes"
  description = "KV database nodes: allow HTTP from anywhere, free intra-cluster traffic"
  vpc_id      = data.aws_vpc.default.id

  # HTTP from the load tester and ALB health checks.
  ingress {
    description = "HTTP from anywhere (load tester and ALB)"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # SSH for debugging.
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # All outbound traffic allowed (required for ECR pulls and inter-node calls).
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# User-data script — installs Docker, pulls the image, and starts the container.
# The actual environment variables (FOLLOWER_URLS, PEER_URLS, etc.) are injected
# by the deploy.sh script AFTER Terraform outputs the IP addresses.
# ─────────────────────────────────────────────────────────────────────────────

locals {
  # Script that runs on first boot: install Docker and authenticate to ECR.
  base_user_data = <<-EOF
    #!/bin/bash
    set -e
    yum update -y
    yum install -y docker
    systemctl enable docker
    systemctl start docker

    # Log in to ECR so we can pull the private image.
    aws ecr get-login-password --region ${var.aws_region} \
      | docker login --username AWS --password-stdin \
        $(echo '${var.database_image_uri}' | cut -d'/' -f1)
  EOF
}

# ─────────────────────────────────────────────────────────────────────────────
# EC2 Instances — 5 database nodes + 1 load tester
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_instance" "database_node" {
  count = 5

  ami                    = data.aws_ami.amazon_linux_2023.id
  instance_type          = var.instance_type
  key_name               = var.ssh_key_pair_name
  vpc_security_group_ids = [aws_security_group.database_nodes.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2_ecr_pull.name
  subnet_id              = data.aws_subnets.default.ids[count.index % length(data.aws_subnets.default.ids)]

  user_data = local.base_user_data

  tags = {
    Name    = "cs6650-db-node-${count.index + 1}"
    Project = "CS6650-Assignment4"
    Role    = count.index == 0 ? "leader" : "follower-${count.index}"
  }
}

resource "aws_instance" "load_tester" {
  ami                    = data.aws_ami.amazon_linux_2023.id
  instance_type          = var.instance_type
  key_name               = var.ssh_key_pair_name
  vpc_security_group_ids = [aws_security_group.database_nodes.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2_ecr_pull.name
  subnet_id              = data.aws_subnets.default.ids[0]

  # The load tester only needs Java and the fat JAR — no Docker required.
  user_data = <<-EOF
    #!/bin/bash
    set -e
    yum update -y
    yum install -y java-17-amazon-corretto
  EOF

  tags = {
    Name    = "cs6650-load-tester"
    Project = "CS6650-Assignment4"
    Role    = "load-tester"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Application Load Balancer — used for the Leaderless configuration so that
# reads and writes are spread evenly across all 5 nodes.
# (Also deployed for Leader-Follower, but read traffic can be routed through it.)
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_lb" "kv_alb" {
  name               = "cs6650-assignment4-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.database_nodes.id]
  subnets            = data.aws_subnets.default.ids

  tags = {
    Project = "CS6650-Assignment4"
  }
}

resource "aws_lb_target_group" "kv_nodes" {
  name     = "cs6650-kv-nodes"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.default.id

  health_check {
    path                = "/kv?key=healthcheck"
    # 404 is acceptable here — it means the node is alive but the key doesn't exist.
    matcher             = "200,404"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.kv_alb.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.kv_nodes.arn
  }
}

# Register all 5 database nodes with the ALB target group.
resource "aws_lb_target_group_attachment" "database_nodes" {
  count            = 5
  target_group_arn = aws_lb_target_group.kv_nodes.arn
  target_id        = aws_instance.database_node[count.index].id
  port             = 8080
}
