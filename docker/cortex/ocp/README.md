# OCP/ RHEL Cortex Image
Cortex docker image for Openshift platform 4.4 compitable on RHEL OS. It also includes HTTPS, probe changes.

 - prerequisite to clone cortex binary to /cortex folder. cortex-analyzers to be cloned in /cortex/Cortex-Analyzers directory.
 - application.conf parameters as well as entrypoint parameters are defined as env variable in k8 deployment. Secrets are also defined in k8 secret and loaded in application.conf
 - In init container of cortex is up and running. It requires store.sh which essentially added cert for truststore and keystore.
 - cortex : migrate endpoint bootstrap cortex : `/api/maintenance/migrate` and create super user and super user admin api key
 - once cortex is starts up and running. probe.sh will keep checking list of organization. which is liveness probe


