#!/usr/bin/env bash
# deploy.sh
#
# Reads Terraform outputs to discover EC2 IPs, then SSHes into each node to
# pull the Docker image and start the database-node container with the correct
# environment variables for the chosen configuration.
#
# Usage:
#   ./scripts/deploy.sh <mode> <ssh-key-path>
#
# Arguments:
#   mode          One of: lf-w5r1 | lf-w1r1 | lf-w3r3 | leaderless
#   ssh-key-path  Path to the .pem key file for SSH access
#
# Examples:
#   ./scripts/deploy.sh lf-w5r1    ~/.ssh/my-key.pem
#   ./scripts/deploy.sh leaderless ~/.ssh/my-key.pem
#
# Prerequisites:
#   - terraform apply has been run (outputs must be available)
#   - The Docker image has been built and pushed to ECR
#   - The ssh-key-path must have chmod 400

set -euo pipefail

# ─── Argument validation ────────────────────────────────────────────────────

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <mode> <ssh-key-path>"
  echo "  mode: lf-w5r1 | lf-w1r1 | lf-w3r3 | leaderless"
  exit 1
fi

DEPLOY_MODE="$1"
SSH_KEY_PATH="$2"

if [[ ! -f "$SSH_KEY_PATH" ]]; then
  echo "Error: SSH key not found at $SSH_KEY_PATH"
  exit 1
fi

VALID_MODES=("lf-w5r1" "lf-w1r1" "lf-w3r3" "leaderless")
if [[ ! " ${VALID_MODES[*]} " =~ " ${DEPLOY_MODE} " ]]; then
  echo "Error: unknown mode '$DEPLOY_MODE'. Must be one of: ${VALID_MODES[*]}"
  exit 1
fi

# ─── Read Terraform outputs ─────────────────────────────────────────────────

echo "Reading Terraform outputs..."
cd "$(dirname "$0")/../terraform"

NODE1_PUBLIC_IP=$(terraform output -raw node1_public_ip)
NODE2_PUBLIC_IP=$(terraform output -raw node2_public_ip)
NODE3_PUBLIC_IP=$(terraform output -raw node3_public_ip)
NODE4_PUBLIC_IP=$(terraform output -raw node4_public_ip)
NODE5_PUBLIC_IP=$(terraform output -raw node5_public_ip)
IMAGE_URI=$(terraform output -raw ecr_repository_url):latest
AWS_REGION=$(terraform output -raw ecr_repository_url | grep -oP '[a-z]{2}-[a-z]+-[0-9]+' | head -1)

# Private IPs are used for inter-node communication (FOLLOWER_URLS / PEER_URLS).
NODE1_PRIVATE_IP=$(terraform output -json node_private_ips | python3 -c "import sys,json; ips=json.load(sys.stdin); print(ips[0])")
NODE2_PRIVATE_IP=$(terraform output -json node_private_ips | python3 -c "import sys,json; ips=json.load(sys.stdin); print(ips[1])")
NODE3_PRIVATE_IP=$(terraform output -json node_private_ips | python3 -c "import sys,json; ips=json.load(sys.stdin); print(ips[2])")
NODE4_PRIVATE_IP=$(terraform output -json node_private_ips | python3 -c "import sys,json; ips=json.load(sys.stdin); print(ips[3])")
NODE5_PRIVATE_IP=$(terraform output -json node_private_ips | python3 -c "import sys,json; ips=json.load(sys.stdin); print(ips[4])")

cd ..

PUBLIC_IPS=("$NODE1_PUBLIC_IP" "$NODE2_PUBLIC_IP" "$NODE3_PUBLIC_IP" "$NODE4_PUBLIC_IP" "$NODE5_PUBLIC_IP")
PRIVATE_IPS=("$NODE1_PRIVATE_IP" "$NODE2_PRIVATE_IP" "$NODE3_PRIVATE_IP" "$NODE4_PRIVATE_IP" "$NODE5_PRIVATE_IP")

echo "Nodes (public / private):"
for i in "${!PUBLIC_IPS[@]}"; do
  echo "  Node $((i+1)): ${PUBLIC_IPS[$i]} / ${PRIVATE_IPS[$i]}"
done

# ─── Build environment variable strings per mode ───────────────────────────

build_lf_env() {
  local node_index="$1"   # 0=leader, 1-4=follower
  local write_quorum="$2"
  local read_quorum="$3"

  if [[ $node_index -eq 0 ]]; then
    # Leader: include all 4 follower URLs using private IPs.
    local follower_urls="http://${PRIVATE_IPS[1]}:8080,http://${PRIVATE_IPS[2]}:8080,http://${PRIVATE_IPS[3]}:8080,http://${PRIVATE_IPS[4]}:8080"
    echo "-e MODE=leader-follower -e ROLE=leader -e NODE_ID=leader \
          -e WRITE_QUORUM_SIZE=${write_quorum} -e READ_QUORUM_SIZE=${read_quorum} \
          -e FOLLOWER_URLS=${follower_urls}"
  else
    echo "-e MODE=leader-follower -e ROLE=follower -e NODE_ID=follower-${node_index}"
  fi
}

build_leaderless_env() {
  local node_index="$1"   # 0-based

  # Build peer URL list: all nodes except self.
  local peer_urls=""
  for i in "${!PRIVATE_IPS[@]}"; do
    if [[ $i -ne $node_index ]]; then
      if [[ -n "$peer_urls" ]]; then peer_urls="${peer_urls},"; fi
      peer_urls="${peer_urls}http://${PRIVATE_IPS[$i]}:8080"
    fi
  done
  echo "-e MODE=leaderless -e ROLE=leaderless -e NODE_ID=node-$((node_index+1)) -e PEER_URLS=${peer_urls}"
}

# ─── Deploy function ─────────────────────────────────────────────────────────

deploy_node() {
  local public_ip="$1"
  local docker_env_flags="$2"
  local node_label="$3"

  echo ""
  echo "=== Deploying $node_label ($public_ip) ==="
  ssh -i "$SSH_KEY_PATH" \
      -o StrictHostKeyChecking=no \
      -o ConnectTimeout=15 \
      "ec2-user@${public_ip}" bash -s <<EOF
set -e
echo "--- Stopping any existing container ---"
docker stop kv-node 2>/dev/null || true
docker rm   kv-node 2>/dev/null || true

echo "--- Logging in to ECR ---"
aws ecr get-login-password --region ${AWS_REGION} \
  | docker login --username AWS --password-stdin \
    \$(echo '${IMAGE_URI}' | cut -d'/' -f1)

echo "--- Pulling image ---"
docker pull ${IMAGE_URI}

echo "--- Starting container ---"
docker run -d \
  --name kv-node \
  --restart unless-stopped \
  -p 8080:8080 \
  ${docker_env_flags} \
  ${IMAGE_URI}

echo "--- Container started ---"
docker ps --filter name=kv-node
EOF
  echo "✅ $node_label deployed"
}

# ─── Deploy all 5 nodes ─────────────────────────────────────────────────────

case "$DEPLOY_MODE" in
  lf-w5r1)
    echo "Deploying Leader-Follower W=5/R=1..."
    deploy_node "${PUBLIC_IPS[0]}" "$(build_lf_env 0 5 1)" "Leader (W=5/R=1)"
    for i in 1 2 3 4; do
      deploy_node "${PUBLIC_IPS[$i]}" "$(build_lf_env $i 5 1)" "Follower-$i"
    done
    ;;
  lf-w1r1)
    echo "Deploying Leader-Follower W=1/R=1..."
    deploy_node "${PUBLIC_IPS[0]}" "$(build_lf_env 0 1 1)" "Leader (W=1/R=1)"
    for i in 1 2 3 4; do
      deploy_node "${PUBLIC_IPS[$i]}" "$(build_lf_env $i 1 5)" "Follower-$i"
    done
    ;;
  lf-w3r3)
    echo "Deploying Leader-Follower W=3/R=3..."
    deploy_node "${PUBLIC_IPS[0]}" "$(build_lf_env 0 3 3)" "Leader (W=3/R=3)"
    for i in 1 2 3 4; do
      deploy_node "${PUBLIC_IPS[$i]}" "$(build_lf_env $i 3 3)" "Follower-$i"
    done
    ;;
  leaderless)
    echo "Deploying Leaderless W=N/R=1..."
    for i in 0 1 2 3 4; do
      deploy_node "${PUBLIC_IPS[$i]}" "$(build_leaderless_env $i)" "Node-$((i+1))"
    done
    ;;
esac

echo ""
echo "==================================================="
echo "✅ Deployment complete for mode: $DEPLOY_MODE"
echo ""
echo "Node 1 (Leader/Node1): http://${NODE1_PUBLIC_IP}:8080"
echo "Node 2:                http://${NODE2_PUBLIC_IP}:8080"
echo "Node 3:                http://${NODE3_PUBLIC_IP}:8080"
echo "Node 4:                http://${NODE4_PUBLIC_IP}:8080"
echo "Node 5:                http://${NODE5_PUBLIC_IP}:8080"
echo "==================================================="
