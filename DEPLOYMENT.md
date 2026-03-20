# CS6650 Assignment 4 — AWS Deployment Guide

## Prerequisites

- AWS CLI installed and configured (`aws configure`)
- Terraform ≥ 1.5 installed
- Docker with BuildKit support (for multi-platform builds)
- An EC2 key pair created in your target region (for SSH access)
- Java 17 + Maven installed locally (to build the load-tester JAR)

---

## Step 1 — Build the project

```bash
# From the project root
mvn clean package -DskipTests
```

This builds:
- `database-node/target/database-node-1.0-SNAPSHOT.jar` (fat JAR, runs in Docker)
- `load-tester/target/load-tester-1.0-SNAPSHOT.jar` (fat JAR, runs on load-tester EC2)

---

## Step 2 — Provision AWS infrastructure (first time only)

```bash
cd terraform

# Create terraform.tfvars with your values:
cat > terraform.tfvars <<EOF
aws_region         = "us-east-1"
instance_type      = "t3.micro"
ssh_key_pair_name  = "my-ec2-key"         # name in AWS Console, not local path
database_image_uri = "PLACEHOLDER"         # will be updated after ECR is created
write_quorum_size  = 5
read_quorum_size   = 1
database_mode      = "leader-follower"
EOF

# Phase 1: create only the ECR repository first
terraform apply -target=aws_ecr_repository.database_node \
                -target=aws_iam_role.ec2_ecr_pull \
                -target=aws_iam_role_policy_attachment.ecr_read_only \
                -target=aws_iam_instance_profile.ec2_ecr_pull

# Get the ECR URL
ECR_URL=$(terraform output -raw ecr_repository_url)
echo "ECR URL: $ECR_URL"

# Update terraform.tfvars with the real ECR URL
sed -i "s|PLACEHOLDER|${ECR_URL}:latest|" terraform.tfvars

# Phase 2: create everything else
terraform apply
```

> **Note:** The full `terraform apply` will create 5 database EC2 instances, 1 load-tester EC2,
> security groups, ALB, target group, and listener. It takes ~3 minutes.

---

## Step 3 — Build and push the Docker image

```bash
cd ..   # back to project root
chmod +x scripts/build-and-push.sh
./scripts/build-and-push.sh
```

---

## Step 4 — Deploy nodes for each configuration

Each load-test run requires a different configuration. Re-deploy between runs:

```bash
chmod +x scripts/deploy.sh

# Leader-Follower W=5/R=1
./scripts/deploy.sh lf-w5r1 ~/.ssh/my-ec2-key.pem

# Leader-Follower W=1/R=5
./scripts/deploy.sh lf-w1r1 ~/.ssh/my-ec2-key.pem

# Leader-Follower W=3/R=3
./scripts/deploy.sh lf-w3r3 ~/.ssh/my-ec2-key.pem

# Leaderless W=N/R=1
./scripts/deploy.sh leaderless ~/.ssh/my-ec2-key.pem
```

Wait ~10–15 seconds after each deploy for the containers to start before running tests.

---

## Step 5 — Run the load tests

```bash
chmod +x scripts/run-load-test.sh

# Runs all 4 read/write ratios (W=1%, 10%, 50%, 90%) for the given configuration.
# Results are downloaded to results/<mode>/*.csv
./scripts/run-load-test.sh lf-w5r1    ~/.ssh/my-ec2-key.pem 5000
./scripts/run-load-test.sh lf-w1r1    ~/.ssh/my-ec2-key.pem 5000
./scripts/run-load-test.sh lf-w3r3    ~/.ssh/my-ec2-key.pem 5000
./scripts/run-load-test.sh leaderless ~/.ssh/my-ec2-key.pem 5000
```

---

## Step 6 — Run unit tests (against local docker-compose)

```bash
# Build the Docker image locally first
docker build -t cs6650/database-node:latest .

# Test 1 and 2 (W=5/R=1 cluster)
docker compose up -d
mvn test -pl database-node -Dgroups="w5-setup"
docker compose down

# Test 3 (W=1/R=1 cluster — exposes inconsistency window)
docker compose -f docker-compose.yml -f docker-compose.w1r1.override.yml up -d
mvn test -pl database-node -Dgroups="w1-setup"
docker compose -f docker-compose.yml -f docker-compose.w1r1.override.yml down

# Leaderless test
docker compose -f docker-compose.leaderless.yml up -d
mvn test -pl database-node -Dgroups="leaderless"
docker compose -f docker-compose.leaderless.yml down
```

---

## Step 7 — Generate graphs for the report

```bash
pip3 install pandas matplotlib
python3 scripts/plot_results.py lf-w5r1
python3 scripts/plot_results.py lf-w1r1
python3 scripts/plot_results.py lf-w3r3
python3 scripts/plot_results.py leaderless
# Graphs saved to graphs/<mode>/*.png
```

---

## Step 8 — Tear down AWS resources when done

```bash
cd terraform
terraform destroy
```

> ⚠️ **Always destroy when finished for the day to avoid unexpected charges.**

---

## Configuration summary

| Configuration | W | R | Write URL | Read URLs |
|---------------|---|---|-----------|-----------|
| lf-w5r1 | 5 | 1 | Node 1 (Leader) | Any node |
| lf-w1r1 | 1 | 1 | Node 1 (Leader) | Any node |
| lf-w3r3 | 3 | 3 | Node 1 (Leader) | Any node |
| leaderless | 5 (N) | 1 | ALB | ALB |
