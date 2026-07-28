variable "project_id" {
  description = "Existing Google Cloud project ID that owns Vedenemo snapshot storage."
  type        = string
}

variable "bucket_name" {
  description = "Globally unique Cloud Storage bucket name for Vedenemo .vdos snapshots."
  type        = string
}

variable "bucket_location" {
  description = "Cloud Storage bucket location, for example europe-north1 or europe-west1."
  type        = string
}

variable "bucket_storage_class" {
  description = "Cloud Storage storage class for first-phase snapshots."
  type        = string
  default     = "STANDARD"
}

variable "snapshot_object_prefix" {
  description = "Object prefix under the bucket for Vedenemo snapshots, without leading or trailing slash."
  type        = string
  default     = "snapshots/dev"
}

variable "storage_scope" {
  description = "First fixed Vedenemo snapshot scope. This phase uses one global bucket namespace."
  type        = string
  default     = "dev"
}

variable "backend_service_account_id" {
  description = "Service account ID used by vedenemo-web-api for snapshot storage."
  type        = string
  default     = "vedenemo-snapshot-backend"
}

variable "impersonation_user_emails" {
  description = "Optional human/operator user emails allowed to impersonate the backend service account for local verification."
  type        = set(string)
  default     = []
}

variable "retention_days" {
  description = "Optional age-based object retention in days. Set to 0 to disable lifecycle deletion."
  type        = number
  default     = 0
}

variable "labels" {
  description = "Labels applied to snapshot infrastructure resources."
  type        = map(string)
  default = {
    app       = "vedenemo"
    component = "snapshots"
  }
}
