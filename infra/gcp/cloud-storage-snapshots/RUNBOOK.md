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

The verification command is a template. Adjust authentication impersonation or
runtime identity details after the backend deployment target is selected.

