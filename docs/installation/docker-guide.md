# Install TheHive using docker

This guide assume that you will use docker.

## How to use this image

Easiest way to start Cortex:
```
docker run certbdf/cortex
```

From version 1.1.0, we don't provide the all-in-one docker (an image which contained TheHive and Cortex). If you want to
run TheHive and Cortex in docker, follow the
[TheHive docker guide](https://github.com/CERT-BDF/TheHive/blob/master/docs/installation/docker-guide.md).

## Analyzers

Analyzers are embedded in docker image in /opt/Cortex-Analyzers/analyzers. If you want to update then, you should
install them outside docker and overwrite existing ones:
```
docker run --volume /path/to/analyzers:/opt/Cortex-Analyzers/analyzers:ro certbdf/cortex:latest  
```

Most analyzers require configuration. You can inject configuration file using volume argument:
```
docker run --volume /path/to/your/configuration:/etc/cortex/application.conf:ro certbdf/cortex:latest  
```

You should also publish HTTP service to make Cortex available. This is done by adding publish parameter:
```
docker run --publish 0.0.0.0:8080:9000 certbdf/cortex:latest  
```
This command exposes Cortex service on port 8080/tcp.

## Customize Cortex docker

By Default, Cortex docker add minimal configuration:
 - choose a random secret (play.crypto.secret)
 - configure analyzer path

This behavious can be disabled by adding `--no-config` to docker command line:
`docker run certbdf/cortex:latest --no-config`.
 
Docker image accepts more options:
 - --no-config            : do not try to configure Cortex (add secret and analyzers location)
 - --no-config-secret     : do not add random secret to configuration
 - --secret <secret>      : secret to secure sessions
 - --analyzer-path <path> : where analyzers are located


