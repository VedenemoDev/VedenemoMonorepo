# Cloud Snapshot Setup Runbook

This runbook interleaves manual phases with repository-managed commands.

## 1. Manual: Choose Project

Choose the GCP project and confirm billing. Record the project ID in local
deployment notes.

## 2. Manual: Authenticate

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project YOUR_PROJECT_ID
gcloud auth application-default set-quota-project YOUR_PROJECT_ID
gcloud config set billing/quota_project YOUR_PROJECT_ID
```

## 3. Script: Enable APIs

```bash
cd infra/gcp/cloud-storage-snapshots
./scripts/bootstrap-apis.sh YOUR_PROJECT_ID
```

## 4. Manual: Fill Terraform Variables

```bash
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`. Do not commit it.

If you want to verify access as the backend service account from your local
machine, add your operator email to `impersonation_user_emails`. This grants
`roles/iam.serviceAccountTokenCreator` on only the backend service account.

## 5. Script: Plan Terraform

```bash
terraform init
terraform plan
```

## 6. Manual: Review Plan

Check bucket privacy, public access prevention, lifecycle rules, IAM bindings,
service account names, and cost-sensitive resources.

## 7. Script: Apply Terraform

```bash
terraform apply
```

## 8. Script: Print Backend Environment Template

```bash
./scripts/print-backend-env.sh
```

Copy the printed values into the backend deployment environment or local shell.
Keep local env files outside version control.

## 9. Manual: Configure Remaining Budget Or Policy Steps

If budget alerts or organization policies are not automated here, configure
them manually and record the location.

## 10. Script: Verify Snapshot Access

```bash
./scripts/verify-snapshot-access.sh YOUR_BUCKET_NAME YOUR_PREFIX
```

This verifies the currently active `gcloud` identity. To verify as the backend
service account after `impersonation_user_emails` has been applied, run:

```bash
gcloud storage ls gs://YOUR_BUCKET_NAME/YOUR_PREFIX/ \
  --billing-project=YOUR_PROJECT_ID \
  --impersonate-service-account=BACKEND_SERVICE_ACCOUNT_EMAIL
```

If impersonation succeeds but the command fails with
`serviceusage.services.use`, rerun `terraform plan` and `terraform apply` so
the backend service account receives `roles/serviceusage.serviceUsageConsumer`
on the project. If Terraform reports no changes and the binding exists, confirm
that the active `gcloud` quota project and `--billing-project` are set to the
snapshot project.

Runtime identity checks should still be repeated from the eventual backend
deployment target.
