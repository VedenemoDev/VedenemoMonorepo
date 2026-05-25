# Firebase Hosting Infrastructure

This Terraform module provisions Firebase Hosting for the Vedenemo Vite UX and
sets up keyless GitHub Actions deploys through Google Workload Identity
Federation.

## Prerequisites

- An existing Google Cloud project.
- Billing and Firebase availability appropriate for the chosen project.
- Local `gcloud` credentials with permission to enable services, configure
  Firebase, create IAM resources, and create service accounts.
- Terraform `>= 1.6.0`.

The Service Usage API may need to be enabled manually before Terraform can
enable other project services:

```bash
gcloud services enable serviceusage.googleapis.com --project YOUR_PROJECT_ID
```

## Apply

```bash
cd infra/gcp/firebase-hosting
terraform init
terraform apply \
  -var="project_id=YOUR_PROJECT_ID" \
  -var="firebase_site_id=YOUR_GLOBAL_FIREBASE_SITE_ID" \
  -var="github_repository=VedenemoDev/VedenemoMonorepo"
```

## Configure GitHub

After `terraform apply`, set these repository variables from the Terraform
outputs:

- `GCP_PROJECT_ID`: your Google Cloud project ID
- `GCP_WORKLOAD_IDENTITY_PROVIDER`: `workload_identity_provider`
- `GCP_DEPLOY_SERVICE_ACCOUNT`: `deploy_service_account_email`
- `FIREBASE_HOSTING_SITE`: `firebase_site_id`

The deploy workflow is intentionally skipped until all four variables exist.

The deploy service account is granted Firebase Hosting Admin, Firebase Viewer,
and API Keys Viewer. Firebase documents API Keys Viewer as required for Firebase
CLI deploys.
