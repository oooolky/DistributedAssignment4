#!/usr/bin/env bash
# build-and-push.sh
#
# Builds the database-node Docker image and pushes it to the ECR repository
# created by Terraform.
#
# Usage:
#   ./scripts/build-and-push.sh
#
# Prerequisites:
#   - terraform apply has been run (ECR repository must exist)
#   - AWS CLI configured with permissions to push to ECR
#   - Docker daemon running
#   - Run from the project root directory

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=== Step 1: Get ECR repository URL from Terraform ==="
cd "${PROJECT_ROOT}/terraform"
ECR_URL=$(terraform output -raw ecr_repository_url)
AWS_REGION=$(echo "$ECR_URL" | grep -oP '[a-z]{2}-[a-z]+-[0-9]+' | head -1)
ECR_REGISTRY=$(echo "$ECR_URL" | cut -d'/' -f1)

echo "ECR URL    : $ECR_URL"
echo "AWS Region : $AWS_REGION"
cd "${PROJECT_ROOT}"

echo ""
echo "=== Step 2: Authenticate Docker with ECR ==="
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo ""
echo "=== Step 3: Build the Docker image ==="
# Build from the project root so the Dockerfile can access all modules.
docker build \
  --platform linux/amd64 \
  -t "cs6650/database-node:latest" \
  -f "${PROJECT_ROOT}/Dockerfile" \
  "${PROJECT_ROOT}"

echo ""
echo "=== Step 4: Tag and push to ECR ==="
docker tag "cs6650/database-node:latest" "${ECR_URL}:latest"
docker push "${ECR_URL}:latest"

echo ""
echo "✅ Image pushed: ${ECR_URL}:latest"
echo "You can now run: ./scripts/deploy.sh <mode> <ssh-key-path>"
