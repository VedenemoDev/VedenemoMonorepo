#!/usr/bin/env bash
set -euo pipefail

# Verifies basic Cloud Storage access for the configured bucket and prefix.
#
# Manual phase before running:
# - terraform apply has created the bucket and backend service account;
# - local credentials are able to use the intended backend identity, or the
#   active gcloud account has equivalent access for this initial smoke check;
# - decide whether to add --impersonate-service-account to GCLOUD_EXTRA_ARGS.
#
# Usage:
#   ./scripts/verify-snapshot-access.sh YOUR_BUCKET_NAME snapshots/dev
#
# Optional:
#   GCLOUD_EXTRA_ARGS=(--impersonate-service-account SERVICE_ACCOUNT_EMAIL) ...
# Bash arrays cannot be exported portably, so edit this template or run the
# gcloud commands manually when impersonation is needed.

BUCKET_NAME="${1:-}"
SNAPSHOT_PREFIX="${2:-}"

if [[ -z "${BUCKET_NAME}" || -z "${SNAPSHOT_PREFIX}" ]]; then
  echo "usage: $0 YOUR_BUCKET_NAME snapshots/dev" >&2
  exit 2
fi

OBJECT_PATH="gs://${BUCKET_NAME}/${SNAPSHOT_PREFIX%/}/verification/verify-$(date +%Y%m%d%H%M%S).txt"
TEMP_FILE="$(mktemp)"

cleanup() {
  rm -f "${TEMP_FILE}"
  gcloud storage rm "${OBJECT_PATH}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

printf 'vedenemo snapshot access verification\n' > "${TEMP_FILE}"

gcloud storage cp "${TEMP_FILE}" "${OBJECT_PATH}"
gcloud storage ls "gs://${BUCKET_NAME}/${SNAPSHOT_PREFIX%/}/"
gcloud storage cat "${OBJECT_PATH}" >/dev/null

echo "Verified write/list/read access under gs://${BUCKET_NAME}/${SNAPSHOT_PREFIX%/}/."

