variable "job_name" {
  # If "", the pack name will be used
  description = "The name to use as the job name which overrides using the pack name"
  type        = string
  default     = "cortex-dev"
}

variable "datacenters" {
  description = "A list of datacenters in the region which are eligible for task placement"
  type        = list(string)
  default     = ["dc1"]
}

variable "count" {
  description = "The number of app instances to deploy"
  type        = number
  default     = 1
}

variable "analyzers" {
    description = "settings for analyzers"
    type = object({
        nb_instances = number
    })
    default = {
        nb_instances = 11
    }
}

variable "register_service" {
  description = "If you want to register a Nomad service for the job"
  type        = bool
  default     = true
}

variable "service_name" {
  description = "The service name for the cortex application"
  type        = string
  default     = "cortex-dev"
}

variable "cluster_name" {
  description = "The cluster name to be used by Cortex"
  type        = string
  default     = "cluster1"
}

variable "docker_image" {
  description = "Docker image to be used by deployment"
  type        = string
  default     = "ghcr.io/strangebee/cortex"
}

variable "docker_image_version" {
  description = "Docker image version to be used by deployment"
  type        = string
  default     = "latest"
}

variable "service_version" {
  description = "Version name used by services and URL"
  type        = string
  default     = "latest"
}