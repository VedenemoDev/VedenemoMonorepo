#!/usr/bin/env bash
set -euo pipefail

# Prints non-secret backend environment variables from Terraform outputs.
#
# Manual phase before running:
# - run terraform init/plan/apply in infra/gcp/cloud-storage-snapshots;
# - review that outputs match the intended private development environment.
#
# Usage:
#   ./scripts/print-backend-env.sh
#
# Do not commit generated .env files. Copy these values into the backend
# deployment environment or an ignored local env file.

terraform output -json backend_environment \
  | jq -r 'to_entries[] | "export \(.key)=\(.value|@sh)"'

echo "# Backend service account:"
terraform output -raw backend_service_account_email

