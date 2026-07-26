# Manual Phases

These are the human decisions and reviews that intentionally surround the
repo-managed scripts. The goal is to avoid clicking through the Google Cloud
Console for ordinary setup while still keeping cost, IAM, and project ownership
visible.

## Phase 1: Project And Billing

Manually choose or create the Google Cloud project that owns Vedenemo snapshot
storage.

Confirm:

- billing is enabled for the project;
- the project is appropriate for private Vedenemo development data;
- you have permission to enable services, create buckets, create service
  accounts, grant IAM roles, and configure budgets.

## Phase 2: Local Operator Authentication

Authenticate locally with `gcloud` before running scripts or Terraform:

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID
```

Use an account with enough administrative permission for setup. This is
operator authentication, not browser-console user authentication.

## Phase 3: Bootstrap APIs

Run `scripts/bootstrap-apis.sh` after filling in the project ID argument. This
keeps basic API enablement reproducible.

Some organization policies may still require manual approval outside this repo.
Document those exceptions in deployment notes rather than hiding them in local
state.

## Phase 4: Terraform Variables

Copy `terraform.tfvars.example` to a local `terraform.tfvars` file and fill in
real values. The real `.tfvars` file is ignored by Git.

Review especially:

- `project_id`;
- `bucket_name`;
- `bucket_location`;
- `snapshot_object_prefix`;
- `backend_service_account_id`;
- `storage_scope`;
- retention settings.

## Phase 5: Plan Review

Run:

```bash
terraform init
terraform plan
```

Before applying, manually review:

- the bucket is private;
- public access prevention is enabled;
- object versioning is off unless a later task changes that;
- lifecycle deletion rules match the intended retention policy;
- IAM grants are limited to the backend service account;
- resource names match the private development environment.

## Phase 6: Apply And Capture Outputs

Run `terraform apply` only after the plan is acceptable.

After apply, use Terraform outputs to configure `vedenemo-web-api`. Do not paste
service account keys or long-lived credentials into repository files.

## Phase 7: Budget And Organization Policies

Configure any budget alert, quota limit, or organization policy that is not
managed by Terraform in this module.

Budget alerts are part of the first implementation acceptance criteria. If they
remain manual, record where they were configured.

## Phase 8: Access Verification

Run `scripts/verify-snapshot-access.sh` with the chosen bucket and prefix. The
verification should prove that the backend identity can list, read, and write
only what the first slice expects.

If prefix-level IAM cannot be enforced cleanly for the chosen setup, keep the
bucket dedicated to Vedenemo snapshots and enforce the prefix in backend code.

