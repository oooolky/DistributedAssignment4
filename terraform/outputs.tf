# ─────────────────────────────────────────────────────────────────────────────
# Outputs — all values needed to configure and deploy the KV cluster.
# Run `terraform output` after `terraform apply` to see these.
# The deploy.sh script reads these values automatically.
# ─────────────────────────────────────────────────────────────────────────────

output "ecr_repository_url" {
  description = "ECR repository URL. Tag and push your image here before deploying nodes."
  value       = aws_ecr_repository.database_node.repository_url
}

output "node1_public_ip" {
  description = "Public IP of database node 1 (Leader in LF mode; peer in Leaderless mode)."
  value       = aws_instance.database_node[0].public_ip
}

output "node2_public_ip" {
  description = "Public IP of database node 2."
  value       = aws_instance.database_node[1].public_ip
}

output "node3_public_ip" {
  description = "Public IP of database node 3."
  value       = aws_instance.database_node[2].public_ip
}

output "node4_public_ip" {
  description = "Public IP of database node 4."
  value       = aws_instance.database_node[3].public_ip
}

output "node5_public_ip" {
  description = "Public IP of database node 5."
  value       = aws_instance.database_node[4].public_ip
}

output "node_private_ips" {
  description = "Private IPs of all 5 database nodes (used for FOLLOWER_URLS / PEER_URLS)."
  value       = aws_instance.database_node[*].private_ip
}

output "load_tester_public_ip" {
  description = "Public IP of the load tester EC2 instance."
  value       = aws_instance.load_tester.public_ip
}

output "alb_dns_name" {
  description = "DNS name of the Application Load Balancer (use for Leaderless reads/writes)."
  value       = aws_lb.kv_alb.dns_name
}
