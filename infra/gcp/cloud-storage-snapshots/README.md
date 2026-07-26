# Cloud Storage Snapshots Infrastructure

This directory is the planned home for Google Cloud Storage setup used by
browser-console `.vdos` snapshots.

The first implementation phase is intentionally private-development oriented:
private Tailscale/backend reachability is the access boundary, the browser never
receives Google Cloud credentials, and the backend stores snapshots through a
Vedenemo-specific snapshot store.

## Files

- `MANUAL-PHASES.md`: manual choices and review points that must happen between
  script/Terraform commands.
- `RUNBOOK.md`: ordered manual/scripted setup flow.
- `main.tf`, `variables.tf`, `outputs.tf`, `versions.tf`: Terraform template
  for a private snapshot bucket and backend service account.
- `terraform.tfvars.example`: copyable variable template. Do not commit real
  `.tfvars` files.
- `scripts/bootstrap-apis.sh`: enables required project APIs after manual
  project and billing selection.
- `scripts/print-backend-env.sh`: prints backend environment variable exports
  from Terraform outputs.
- `scripts/verify-snapshot-access.sh`: template verification for backend
  identity read/write/list access.

## First Decisions Already Chosen

- One global bucket namespace is acceptable for the first phase.
- Backend server clock is the first model last-modification timestamp source.
- Browser console `load` should prompt for a replacement model `azName` on
  duplicate import, matching terminal CLI behavior.
- Terminal CLI plain `save`, `snapshots`, and `load` stay local-filesystem
  commands.
- Browser console plain `save`, `snapshots`, and `load` use cloud snapshots.
- The storage abstraction should be Vedenemo-specific, not a generic artifact
  store.

## Safety Notes

- Do not commit service account keys, `.tfvars`, `.env`, `.terraform`, or
  Terraform state files.
- Prefer Application Default Credentials or runtime identity for backend access.
- Keep public access prevention enabled on the snapshot bucket.
- Treat Terraform output values as configuration, not secrets. Credentials
  belong in the deployment environment outside version control.

## Local Tooling

- `gcloud`
- Terraform `>= 1.6.0`
- `jq` for `scripts/print-backend-env.sh`
