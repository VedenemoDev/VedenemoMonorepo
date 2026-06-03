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

## 2. Create and Prepare a GCP Project

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

For a new Google Cloud account, some steps must be completed in the browser.
The command line cannot accept every required Google/Firebase prompt.

Open the Google Cloud Console while signed in as the same account:

```text
https://console.cloud.google.com/
```

Complete these browser-only setup steps:

1. Accept Google Cloud Terms of Service if prompted.
2. Add or select billing information.
3. Link the billing account to `$GCP_PROJECT_ID`.

Enable Service Usage first. Terraform uses this API to enable the rest:

```bash
gcloud services enable serviceusage.googleapis.com \
  --project "$GCP_PROJECT_ID"
```

Set the active project and quota project for both `gcloud` and local ADC:

```bash
gcloud config set project "$GCP_PROJECT_ID"
gcloud config set billing/quota_project "$GCP_PROJECT_ID"
gcloud auth application-default set-quota-project "$GCP_PROJECT_ID"
export GOOGLE_CLOUD_QUOTA_PROJECT="$GCP_PROJECT_ID"
unset GOOGLE_APPLICATION_CREDENTIALS
```

`GOOGLE_APPLICATION_CREDENTIALS` should be unset here unless you intentionally
want Terraform to use a service account or external credential JSON file. If it
is set, it takes precedence over the local ADC file created by `gcloud auth
login --update-adc`.

Open the Firebase Console:

```text
https://console.firebase.google.com/
```

Complete the initial Firebase setup in the browser:

1. Click `Add project`.
2. Select the existing GCP project `$GCP_PROJECT_ID`.
3. Complete the wizard.
4. Disable or skip Google Analytics unless it is intentionally needed.

This one-time Firebase browser onboarding avoids a generic Terraform
`google_firebase_project.default` 403 error even when the caller already has
`roles/owner` and `roles/firebase.admin`.

## 3. Import Firebase Project and Apply Terraform

Terraform config is intentionally separate from the app build. It creates:

- required GCP/Firebase APIs
- Firebase Hosting site
- GitHub Actions deploy service account
- IAM roles needed by Firebase CLI deploys
- Workload Identity Federation for keyless GitHub Actions auth

The Firebase project itself was created through the browser in the previous
step, so import it into Terraform state before applying:

Run:

```bash
cd infra/gcp/firebase-hosting

terraform init

terraform import \
  -var="project_id=$GCP_PROJECT_ID" \
  -var="firebase_site_id=$FIREBASE_HOSTING_SITE" \
  -var="github_repository=$GITHUB_REPOSITORY_NAME" \
  google_firebase_project.default \
  "projects/$GCP_PROJECT_ID"

terraform apply \
  -var="project_id=$GCP_PROJECT_ID" \
  -var="firebase_site_id=$FIREBASE_HOSTING_SITE" \
  -var="github_repository=$GITHUB_REPOSITORY_NAME"
```

If Terraform says `google_firebase_project.default` is already managed, skip the
import and run `terraform apply`.

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

Once the project IDs are chosen and local auth is available, Codex can run the
command-line parts from the repo root:

```bash
export GCP_PROJECT_ID="vedenemo-ux-prod"
export FIREBASE_HOSTING_SITE="vedenemo-ux-prod"
export GITHUB_REPOSITORY_NAME="VedenemoDev/VedenemoMonorepo"

gcloud projects create "$GCP_PROJECT_ID" --name="Vedenemo UX" --set-as-default
gcloud services enable serviceusage.googleapis.com --project "$GCP_PROJECT_ID"
gcloud config set project "$GCP_PROJECT_ID"
gcloud config set billing/quota_project "$GCP_PROJECT_ID"
gcloud auth application-default set-quota-project "$GCP_PROJECT_ID"
export GOOGLE_CLOUD_QUOTA_PROJECT="$GCP_PROJECT_ID"
unset GOOGLE_APPLICATION_CREDENTIALS
```

Pause here and complete the browser-only setup:

1. Accept Google Cloud Terms of Service.
2. Add billing information.
3. Link billing to `$GCP_PROJECT_ID`.
4. Open Firebase Console.
5. Add Firebase to the existing project `$GCP_PROJECT_ID`.

After the browser setup is complete, continue:

```bash
cd /home/vedenemodev/github

cd infra/gcp/firebase-hosting
terraform init

terraform import \
  -var="project_id=$GCP_PROJECT_ID" \
  -var="firebase_site_id=$FIREBASE_HOSTING_SITE" \
  -var="github_repository=$GITHUB_REPOSITORY_NAME" \
  google_firebase_project.default \
  "projects/$GCP_PROJECT_ID"

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
The setup is not fully automatable from this environment because Google Cloud
Terms of Service, billing setup, and initial Firebase onboarding may require
browser interaction.

## 7. Troubleshooting

If `Deploy UX` is skipped, check that all four GitHub variables exist.

If Terraform fails with `requires a quota project`, run:

```bash
gcloud config set project "$GCP_PROJECT_ID"
gcloud config set billing/quota_project "$GCP_PROJECT_ID"
gcloud auth application-default set-quota-project "$GCP_PROJECT_ID"
export GOOGLE_CLOUD_QUOTA_PROJECT="$GCP_PROJECT_ID"
unset GOOGLE_APPLICATION_CREDENTIALS
```

Then retry `terraform apply`. Google classifies Firebase as a client-based API
for this authentication path, so local user ADC must specify a quota project.
The authenticated user must also have `serviceusage.services.use`, included in
`roles/serviceusage.serviceUsageConsumer`, on the quota project.

If Terraform fails creating `google_firebase_project.default` with a generic
`403: The caller does not have permission`, create/add the Firebase project once
through the Firebase Console and import it:

```bash
terraform import \
  -var="project_id=$GCP_PROJECT_ID" \
  -var="firebase_site_id=$FIREBASE_HOSTING_SITE" \
  -var="github_repository=$GITHUB_REPOSITORY_NAME" \
  google_firebase_project.default \
  "projects/$GCP_PROJECT_ID"
```

This is expected for some new accounts/projects because Firebase onboarding can
require browser-side prompts that the API error does not describe clearly.

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
