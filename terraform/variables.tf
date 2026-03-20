# ─────────────────────────────────────────────────────────────────────────────
# Input variables for the CS6650 Assignment 4 AWS infrastructure.
# Override defaults in a terraform.tfvars file (do NOT commit that file to git).
# ─────────────────────────────────────────────────────────────────────────────

variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "instance_type" {
  description = "EC2 instance type for all database and load-tester nodes."
  type        = string
  default     = "t3.micro"
}

variable "ssh_key_pair_name" {
  description = "Name of the existing EC2 key pair to use for SSH access to instances."
  type        = string
  # No default — this must be set in terraform.tfvars or via -var flag.
}

variable "database_image_uri" {
  description = <<-EOT
    Full URI of the Docker image for the database-node, e.g.:
      123456789012.dkr.ecr.us-east-1.amazonaws.com/cs6650/database-node:latest
    Build and push the image first, then set this variable.
  EOT
  type        = string
}

# ── Leader-Follower quorum settings (change to switch between LF configurations) ──

variable "write_quorum_size" {
  description = "W (write quorum). Use 5 for W=5/R=1, 1 for W=1/R=5, 3 for W=3/R=3."
  type        = number
  default     = 5
}

variable "read_quorum_size" {
  description = "R (read quorum). Use 1 for W=5/R=1, 5 for W=1/R=5, 3 for W=3/R=3."
  type        = number
  default     = 1
}

variable "database_mode" {
  description = "Replication mode: 'leader-follower' or 'leaderless'."
  type        = string
  default     = "leader-follower"

  validation {
    condition     = contains(["leader-follower", "leaderless"], var.database_mode)
    error_message = "database_mode must be 'leader-follower' or 'leaderless'."
  }
}
