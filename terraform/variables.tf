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
  description = "Name of the EC2 key pair for SSH access (must exist in the target region)."
  type        = string
}

variable "database_mode" {
  description = "Replication mode: 'leader-follower' or 'leaderless'."
  type        = string
  default     = "leader-follower"
}

variable "write_quorum_size" {
  description = "W (write quorum). Use 5 for W=5/R=1, 1 for W=1/R=1, 3 for W=3/R=3."
  type        = number
  default     = 5
}

variable "read_quorum_size" {
  description = "R (read quorum). Use 1 for W=5/R=1, 1 for W=1/R=1, 3 for W=3/R=3."
  type        = number
  default     = 1
}
