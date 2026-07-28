locals {
  required_services = toset([
    "cloudresourcemanager.googleapis.com",
    "iam.googleapis.com",
    "storage.googleapis.com",
  ])

  normalized_snapshot_prefix = trimsuffix(trimprefix(var.snapshot_object_prefix, "/"), "/")
}

resource "google_project_service" "required" {
  for_each = local.required_services

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_storage_bucket" "snapshots" {
  project       = var.project_id
  name          = var.bucket_name
  location      = var.bucket_location
  storage_class = var.bucket_storage_class
  labels        = var.labels

  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  versioning {
    enabled = false
  }

  dynamic "lifecycle_rule" {
    for_each = var.retention_days > 0 ? [var.retention_days] : []

    content {
      condition {
        age = lifecycle_rule.value
      }

      action {
        type = "Delete"
      }
    }
  }

  depends_on = [
    google_project_service.required,
  ]
}

resource "google_service_account" "backend" {
  project      = var.project_id
  account_id   = var.backend_service_account_id
  display_name = "Vedenemo snapshot backend"
  description  = "Used by vedenemo-web-api to read and write browser-console .vdos snapshots."

  depends_on = [
    google_project_service.required,
  ]
}

# First phase uses a dedicated private bucket and backend-mediated access.
# If a later deployment requires strict prefix-level IAM, validate Cloud Storage
# IAM Conditions for object and list operations before applying that change.
resource "google_storage_bucket_iam_member" "backend_object_user" {
  bucket = google_storage_bucket.snapshots.name
  role   = "roles/storage.objectUser"
  member = "serviceAccount:${google_service_account.backend.email}"
}

# Allows Google client tools/libraries using this project as the quota project
# to make API calls as the backend service account.
resource "google_project_iam_member" "backend_service_usage_consumer" {
  project = var.project_id
  role    = "roles/serviceusage.serviceUsageConsumer"
  member  = "serviceAccount:${google_service_account.backend.email}"
}

# Optional local verification support. Add only trusted operator emails here,
# because roles/iam.serviceAccountTokenCreator allows minting short-lived tokens
# as the backend service account.
resource "google_service_account_iam_member" "backend_impersonators" {
  for_each = var.impersonation_user_emails

  service_account_id = google_service_account.backend.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "user:${each.value}"
}
