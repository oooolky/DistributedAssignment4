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
# Data sources
# ─────────────────────────────────────────────────────────────────────────────

data "aws_vpc" "default" {
  default = true
}

# Exclude us-east-1e because t3.micro is not supported there.
data "aws_subnets" "supported" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "availabilityZone"
    values = ["us-east-1a", "us-east-1b", "us-east-1c", "us-east-1d", "us-east-1f"]
  }
}

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

# Learner Lab provides a pre-built instance profile — we cannot create IAM roles.
data "aws_iam_instance_profile" "lab" {
  name = "LabInstanceProfile"
}

# ─────────────────────────────────────────────────────────────────────────────
# ECR Repository
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_ecr_repository" "database_node" {
  name                 = "cs6650/database-node"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Security Group
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_security_group" "database_nodes" {
  name        = "cs6650-assignment4-nodes"
  description = "KV nodes: HTTP on 8080, SSH, and free inter-node traffic"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "HTTP from anywhere (load tester and ALB health checks)"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP on port 80 for ALB listener"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "SSH for debugging"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# User-data: install Docker and authenticate to ECR on first boot
# ─────────────────────────────────────────────────────────────────────────────

locals {
  base_user_data = <<-EOF
    #!/bin/bash
    set -e
    yum update -y
    yum install -y docker
    systemctl enable docker
    systemctl start docker

    # Wait for the instance role to be fully available before calling ECR
    sleep 5

    aws ecr get-login-password --region ${var.aws_region} \
      | docker login --username AWS --password-stdin \
        $(echo '${aws_ecr_repository.database_node.repository_url}' | cut -d'/' -f1)
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
  iam_instance_profile   = data.aws_iam_instance_profile.lab.name
  # Cycle through supported subnets only (us-east-1e excluded)
  subnet_id              = data.aws_subnets.supported.ids[count.index % length(data.aws_subnets.supported.ids)]

  user_data = local.base_user_data

  tags = {
    Name    = "cs6650-db-node-${count.index + 1}"
    Project = "CS6650-Assignment4"
  }
}

resource "aws_instance" "load_tester" {
  ami                    = data.aws_ami.amazon_linux_2023.id
  instance_type          = var.instance_type
  key_name               = var.ssh_key_pair_name
  vpc_security_group_ids = [aws_security_group.database_nodes.id]
  iam_instance_profile   = data.aws_iam_instance_profile.lab.name
  subnet_id              = data.aws_subnets.supported.ids[0]

  user_data = <<-EOF
    #!/bin/bash
    set -e
    yum update -y
    yum install -y java-17-amazon-corretto
  EOF

  tags = {
    Name    = "cs6650-load-tester"
    Project = "CS6650-Assignment4"
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# Application Load Balancer
# ─────────────────────────────────────────────────────────────────────────────

resource "aws_lb" "kv_alb" {
  name               = "cs6650-assignment4-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.database_nodes.id]
  subnets            = data.aws_subnets.supported.ids
}

resource "aws_lb_target_group" "kv_nodes" {
  name     = "cs6650-kv-nodes"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.default.id

  health_check {
    path                = "/kv?key=healthcheck"
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

resource "aws_lb_target_group_attachment" "database_nodes" {
  count            = 5
  target_group_arn = aws_lb_target_group.kv_nodes.arn
  target_id        = aws_instance.database_node[count.index].id
  port             = 8080
}
