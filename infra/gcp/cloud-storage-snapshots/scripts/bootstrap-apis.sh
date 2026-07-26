#!/usr/bin/env bash
set -euo pipefail

# Enables the Google Cloud APIs needed by the Terraform template in this
# directory.
#
# Manual phase before running:
# - choose the GCP project;
# - confirm billing is enabled;
# - authenticate locally with gcloud using an account allowed to enable
#   services and manage IAM/storage resources.
#
# Usage:
#   ./scripts/bootstrap-apis.sh YOUR_PROJECT_ID

PROJECT_ID="${1:-}"

if [[ -z "${PROJECT_ID}" ]]; then
  echo "usage: $0 YOUR_PROJECT_ID" >&2
  exit 2
fi

gcloud services enable \
  cloudresourcemanager.googleapis.com \
  iam.googleapis.com \
  storage.googleapis.com \
  --project "${PROJECT_ID}"

echo "Enabled required APIs for project ${PROJECT_ID}."

