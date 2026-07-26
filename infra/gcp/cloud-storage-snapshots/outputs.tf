output "project_id" {
  description = "Google Cloud project ID used for Vedenemo snapshot storage."
  value       = var.project_id
}

output "bucket_name" {
  description = "Cloud Storage bucket that stores Vedenemo .vdos snapshots."
  value       = google_storage_bucket.snapshots.name
}

output "snapshot_object_prefix" {
  description = "Object prefix used for Vedenemo snapshots."
  value       = local.normalized_snapshot_prefix
}

output "storage_scope" {
  description = "First fixed Vedenemo snapshot scope."
  value       = var.storage_scope
}

output "backend_service_account_email" {
  description = "Service account email for vedenemo-web-api snapshot access."
  value       = google_service_account.backend.email
}

output "backend_environment" {
  description = "Non-secret environment values for vedenemo-web-api."
  value = {
    VEDENEMO_SNAPSHOT_STORE = "gcs"
    VEDENEMO_GCS_PROJECT_ID = var.project_id
    VEDENEMO_GCS_BUCKET     = google_storage_bucket.snapshots.name
    VEDENEMO_GCS_PREFIX     = local.normalized_snapshot_prefix
    VEDENEMO_SNAPSHOT_SCOPE = var.storage_scope
  }
}

