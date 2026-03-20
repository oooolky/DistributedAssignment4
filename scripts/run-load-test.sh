#!/usr/bin/env bash
# run-load-test.sh
#
# Copies the load-tester fat JAR to the load tester EC2 instance and runs
# all four read/write ratios (1/99, 10/90, 50/50, 90/10) in sequence.
# Output CSVs are downloaded back to ./results/<mode>/.
#
# Usage:
#   ./scripts/run-load-test.sh <mode> <ssh-key-path> [total-requests]
#
# Arguments:
#   mode            One of: lf-w5r1 | lf-w1r1 | lf-w3r3 | leaderless
#   ssh-key-path    Path to the EC2 SSH .pem key
#   total-requests  Number of requests per run (default: 5000)
#
# Example:
#   ./scripts/run-load-test.sh lf-w5r1 ~/.ssh/my-key.pem 5000

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <mode> <ssh-key-path> [total-requests]"
  exit 1
fi

DEPLOY_MODE="$1"
SSH_KEY_PATH="$2"
TOTAL_REQUESTS="${3:-5000}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAR_PATH="${PROJECT_ROOT}/load-tester/target/load-tester-1.0-SNAPSHOT.jar"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Error: load-tester JAR not found at $JAR_PATH"
  echo "Build it first with: mvn clean package -pl common,load-tester -am -DskipTests"
  exit 1
fi

# ─── Read Terraform outputs ──────────────────────────────────────────────────
cd "${PROJECT_ROOT}/terraform"
LOAD_TESTER_IP=$(terraform output -raw load_tester_public_ip)
NODE1_IP=$(terraform output -raw node1_public_ip)
NODE2_IP=$(terraform output -raw node2_public_ip)
NODE3_IP=$(terraform output -raw node3_public_ip)
NODE4_IP=$(terraform output -raw node4_public_ip)
NODE5_IP=$(terraform output -raw node5_public_ip)
ALB_DNS=$(terraform output -raw alb_dns_name)
cd "${PROJECT_ROOT}"

# For Leader-Follower: writes go to Node 1 (leader), reads are distributed across all 5.
LEADER_WRITE_URL="http://${NODE1_IP}:8080"
ALL_NODE_READ_URLS="http://${NODE1_IP}:8080,http://${NODE2_IP}:8080,http://${NODE3_IP}:8080,http://${NODE4_IP}:8080,http://${NODE5_IP}:8080"
# For Leaderless: all traffic through the ALB.
ALB_URL="http://${ALB_DNS}"

case "$DEPLOY_MODE" in
  lf-w5r1|lf-w1r1|lf-w3r3)
    WRITE_URL="$LEADER_WRITE_URL"
    READ_URLS="$ALL_NODE_READ_URLS"
    ;;
  leaderless)
    WRITE_URL="$ALB_URL"
    READ_URLS="$ALB_URL"
    ;;
  *)
    echo "Unknown mode: $DEPLOY_MODE"
    exit 1
    ;;
esac

# ─── Copy JAR to load tester ─────────────────────────────────────────────────
echo "Copying load-tester JAR to ${LOAD_TESTER_IP}..."
scp -i "$SSH_KEY_PATH" -o StrictHostKeyChecking=no \
    "$JAR_PATH" "ec2-user@${LOAD_TESTER_IP}:~/load-tester.jar"

# ─── Run all 4 read/write ratios ─────────────────────────────────────────────
RESULTS_DIR="${PROJECT_ROOT}/results/${DEPLOY_MODE}"
mkdir -p "$RESULTS_DIR"

# Each entry: write_percentage
WRITE_PERCENTAGES=(1 10 50 90)

for write_pct in "${WRITE_PERCENTAGES[@]}"; do
  read_pct=$((100 - write_pct))
  echo ""
  echo "======================================================"
  echo "Running: ${DEPLOY_MODE} | W=${write_pct}% / R=${read_pct}%"
  echo "======================================================"

  # Run the load test on the remote machine and collect CSVs.
  ssh -i "$SSH_KEY_PATH" -o StrictHostKeyChecking=no \
      "ec2-user@${LOAD_TESTER_IP}" bash -s <<EOF
set -e
java -jar ~/load-tester.jar \
  "${WRITE_URL}" \
  "${READ_URLS}" \
  "${TOTAL_REQUESTS}" \
  "${write_pct}" \
  "200"
EOF

  # Download CSV results.
  echo "Downloading CSVs..."
  scp -i "$SSH_KEY_PATH" -o StrictHostKeyChecking=no \
      "ec2-user@${LOAD_TESTER_IP}:~/run_${write_pct}pctWrites_*.csv" \
      "${RESULTS_DIR}/" 2>/dev/null || echo "(no CSVs found for this run)"

  echo "✅ Run complete: W=${write_pct}%"
done

echo ""
echo "======================================================"
echo "✅ All runs complete. Results in: ${RESULTS_DIR}/"
ls -la "${RESULTS_DIR}/"
echo "======================================================"
echo ""
echo "To generate graphs, run:"
echo "  python3 scripts/plot_results.py ${DEPLOY_MODE}"
