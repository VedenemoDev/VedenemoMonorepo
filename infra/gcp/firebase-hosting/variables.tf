variable "project_id" {
  description = "Existing Google Cloud project ID that will host the Firebase Hosting site."
  type        = string
}

variable "firebase_site_id" {
  description = "Firebase Hosting site ID. Must be globally unique across Firebase Hosting."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy, in owner/repo form."
  type        = string
}

variable "github_ref" {
  description = "Git ref allowed to deploy through Workload Identity Federation."
  type        = string
  default     = "refs/heads/main"
}

variable "deploy_service_account_id" {
  description = "Service account ID used by GitHub Actions for Firebase Hosting deploys."
  type        = string
  default     = "github-firebase-deploy"
}

variable "workload_identity_pool_id" {
  description = "Workload Identity Pool ID for GitHub Actions."
  type        = string
  default     = "github-actions"
}

variable "workload_identity_provider_id" {
  description = "Workload Identity Pool Provider ID for GitHub Actions."
  type        = string
  default     = "github"
}
