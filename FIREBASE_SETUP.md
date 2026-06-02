# Firebase Hosting Setup

This repository can deploy `vedenemo-ux` to Firebase Hosting through:

- Terraform infrastructure in `infra/gcp/firebase-hosting`
- GitHub Actions workflow `.github/workflows/deploy-ux.yml`
- Firebase Hosting config `vedenemo-ux/firebase.json`

The deploy workflow is intentionally skipped until the required GitHub repository
variables are set.

## 1. Local Tools

Install and authenticate these tools on the machine that will provision GCP:

- Google Cloud CLI: `gcloud`
- Terraform `>= 1.6.0`
- GitHub CLI: `gh`

Authenticate:

```bash
gcloud auth login --update-adc
gh auth login
```

`--update-adc` signs in to the Google Cloud CLI and writes Application Default
Credentials (ADC) for Terraform in the same flow.

Verify:

```bash
gcloud auth list
gcloud auth application-default print-access-token
terraform version
gh auth status
```

The ADC command should print an access token. If the normal browser flow does
not work in WSL, retry with:

```bash
gcloud auth login --update-adc --no-browser
```

This mode requires a second trusted machine with `gcloud` installed. Avoid the
older copy-pasted verification-code flow if
`gcloud auth application-default login` crashes with a `Scope has changed`
warning.

## 2. Create a GCP Project

Choose globally unique IDs:

```bash
export GCP_PROJECT_ID="vedenemo-ux-prod"
export FIREBASE_HOSTING_SITE="vedenemo-ux-prod"
export GITHUB_REPOSITORY_NAME="VedenemoDev/VedenemoMonorepo"
```

Create the project:

```bash
gcloud projects create "$GCP_PROJECT_ID" \
  --name="Vedenemo UX" \
  --set-as-default
```

Enable Service Usage first. Terraform uses this API to enable the rest:

```bash
gcloud services enable serviceusage.googleapis.com \
  --project "$GCP_PROJECT_ID"
```

If Google Cloud reports that billing is required for enabling services, link a
billing account in the Google Cloud console or with `gcloud billing`. Firebase
Hosting itself can still be kept on the Firebase Spark/free plan, but GCP project
policy may require a billing account before API enablement succeeds.

## 3. Apply Terraform

Terraform config is intentionally separate from the app build. It creates:

- required GCP/Firebase APIs
- Firebase project linkage
- Firebase Hosting site
- GitHub Actions deploy service account
- IAM roles needed by Firebase CLI deploys
- Workload Identity Federation for keyless GitHub Actions auth

Run:

```bash
cd infra/gcp/firebase-hosting

terraform init

terraform apply \
  -var="project_id=$GCP_PROJECT_ID" \
  -var="firebase_site_id=$FIREBASE_HOSTING_SITE" \
  -var="github_repository=$GITHUB_REPOSITORY_NAME"
```

After apply, capture outputs:

```bash
export GCP_WORKLOAD_IDENTITY_PROVIDER="$(terraform output -raw workload_identity_provider)"
export GCP_DEPLOY_SERVICE_ACCOUNT="$(terraform output -raw deploy_service_account_email)"
export FIREBASE_HOSTING_SITE="$(terraform output -raw firebase_site_id)"
```

Return to the repo root:

```bash
cd ../../..
```

## 4. Set GitHub Repository Variables

The deploy workflow reads repository variables, not secrets. No service account
JSON key is stored in GitHub.

From the repo root:

```bash
gh variable set GCP_PROJECT_ID --body "$GCP_PROJECT_ID"
gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER --body "$GCP_WORKLOAD_IDENTITY_PROVIDER"
gh variable set GCP_DEPLOY_SERVICE_ACCOUNT --body "$GCP_DEPLOY_SERVICE_ACCOUNT"
gh variable set FIREBASE_HOSTING_SITE --body "$FIREBASE_HOSTING_SITE"
```

Verify:

```bash
gh variable list
```

Manual alternative:

1. Open GitHub repository settings.
2. Go to `Secrets and variables` -> `Actions` -> `Variables`.
3. Add:
   - `GCP_PROJECT_ID`
   - `GCP_WORKLOAD_IDENTITY_PROVIDER`
   - `GCP_DEPLOY_SERVICE_ACCOUNT`
   - `FIREBASE_HOSTING_SITE`

## 5. Deploy

After the variables exist, deployment can happen in two ways.

Automatic deploy:

```bash
git push origin main
```

The workflow runs only when files under `vedenemo-ux/**` or
`.github/workflows/deploy-ux.yml` change.

Manual deploy:

```bash
gh workflow run deploy-ux.yml --ref main
gh run list --workflow "Deploy UX" --limit 3
```

Watch the latest run:

```bash
gh run watch
```

The workflow builds with:

```bash
cd vedenemo-ux
npm ci --registry=https://registry.npmjs.org/ --no-audit --no-fund
npm run build
```

Then it deploys:

```bash
npx --yes firebase-tools@latest target:apply hosting vedenemo-ux "$FIREBASE_HOSTING_SITE" --project "$GCP_PROJECT_ID"
npx --yes firebase-tools@latest deploy --only hosting:vedenemo-ux --project "$GCP_PROJECT_ID"
```

## 6. Codex CLI Execution Chain

Once the project IDs are chosen and local auth is available, Codex can run this
sequence from the repo root:

```bash
export GCP_PROJECT_ID="vedenemo-ux-prod"
export FIREBASE_HOSTING_SITE="vedenemo-ux-prod"
export GITHUB_REPOSITORY_NAME="VedenemoDev/VedenemoMonorepo"

gcloud projects create "$GCP_PROJECT_ID" --name="Vedenemo UX" --set-as-default
gcloud services enable serviceusage.googleapis.com --project "$GCP_PROJECT_ID"

cd infra/gcp/firebase-hosting
terraform init
terraform apply \
  -var="project_id=$GCP_PROJECT_ID" \
  -var="firebase_site_id=$FIREBASE_HOSTING_SITE" \
  -var="github_repository=$GITHUB_REPOSITORY_NAME"

export GCP_WORKLOAD_IDENTITY_PROVIDER="$(terraform output -raw workload_identity_provider)"
export GCP_DEPLOY_SERVICE_ACCOUNT="$(terraform output -raw deploy_service_account_email)"
export FIREBASE_HOSTING_SITE="$(terraform output -raw firebase_site_id)"
cd ../../..

gh variable set GCP_PROJECT_ID --body "$GCP_PROJECT_ID"
gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER --body "$GCP_WORKLOAD_IDENTITY_PROVIDER"
gh variable set GCP_DEPLOY_SERVICE_ACCOUNT --body "$GCP_DEPLOY_SERVICE_ACCOUNT"
gh variable set FIREBASE_HOSTING_SITE --body "$FIREBASE_HOSTING_SITE"

gh workflow run deploy-ux.yml --ref main
gh run list --workflow "Deploy UX" --limit 3
```

Commands that authenticate, create cloud resources, apply Terraform, or call
GitHub/GCP APIs may require explicit approval from the Codex CLI environment.

## 7. Troubleshooting

If `Deploy UX` is skipped, check that all four GitHub variables exist.

If authentication fails in GitHub Actions, compare:

```bash
terraform output -raw workload_identity_provider
terraform output -raw deploy_service_account_email
gh variable list
```

If Firebase deploy permissions fail, confirm the deploy service account has:

- `roles/firebasehosting.admin`
- `roles/firebase.viewer`
- `roles/serviceusage.apiKeysViewer`

If the frontend install fails, confirm `package-lock.json` uses
`https://registry.npmjs.org/` URLs and the workflow is using Node `20.19.5`.

## References

- Google Cloud project creation: `gcloud projects create`
- Firebase CLI deploy command: `firebase deploy`
- GitHub repository variables: `gh variable set`
- Google GitHub Actions authentication: `google-github-actions/auth`
