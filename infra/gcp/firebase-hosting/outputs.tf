output "firebase_site_id" {
  description = "Firebase Hosting site ID."
  value       = google_firebase_hosting_site.ux.site_id
}

output "deploy_service_account_email" {
  description = "Service account email to set as the GitHub Actions GCP_DEPLOY_SERVICE_ACCOUNT variable."
  value       = google_service_account.deploy.email
}

output "workload_identity_provider" {
  description = "Provider resource name to set as the GitHub Actions GCP_WORKLOAD_IDENTITY_PROVIDER variable."
  value       = google_iam_workload_identity_pool_provider.github.name
}
