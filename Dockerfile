# syntax=docker/dockerfile:1
#
# Multi-stage image: compile Scala/Play + AngularJS frontend, then run on the official Cortex base layer.
#
# Build:
#   docker build -t cortex:local .
#
# Run (example; point Elasticsearch at your cluster):
#   docker run --rm -p 9001:9001 cortex:local
#
# Requires network access during build (Maven/Ivy/npm, Debian/Corretto/Docker apt repos).
#
# Runtime matches project/DockerSettings.scala / builds/docker/Dockerfile (Debian + Corretto 11 + Docker).
# To use the prebuilt base image instead (if you can pull it): replace the runtime FROM below with
#   FROM ghcr.io/strangebee/cortex-baselayer:rolling
# and remove the duplicate RUN that installs Java/Docker/user (keep COPY and chmod).

# -----------------------------------------------------------------------------
# Builder: JDK 11, sbt, Node (webpack), and bower (needed by www npm postinstall scripts)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:11-jdk-jammy AS builder

ENV LANG=C.UTF-8 \
    SBT_OPTS="-Xmx4096m -Xss2m"

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl git gnupg ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# sbt (version aligned with project/build.properties)
RUN curl -fsSL "https://github.com/sbt/sbt/releases/download/v1.11.7/sbt-1.11.7.tgz" \
    | tar xz -C /usr/local

ENV PATH="/usr/local/sbt/bin:${PATH}"

# Node.js 20 (for www: npm install + webpack via sbt)
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# css-spaces and other legacy deps invoke `bower` in postinstall
RUN npm install -g bower

WORKDIR /build

COPY . .

RUN sbt -batch stage

# -----------------------------------------------------------------------------
# Runtime: Debian + Amazon Corretto 11 + Docker CLI (same idea as builds/docker/Dockerfile)
# -----------------------------------------------------------------------------
FROM debian:13-slim

LABEL org.opencontainers.image.source="https://github.com/TheHive-Project/Cortex"
LABEL org.opencontainers.image.description="Cortex built from source"

WORKDIR /opt/cortex

ENV JAVA_HOME=/usr/lib/jvm/java-11-amazon-corretto

RUN apt-get update && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && curl -fL https://apt.corretto.aws/corretto.key | gpg --dearmor -o /usr/share/keyrings/corretto.gpg \
    && echo 'deb [signed-by=/usr/share/keyrings/corretto.gpg] https://apt.corretto.aws stable main' > /etc/apt/sources.list.d/corretto.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends java-11-amazon-corretto-jdk \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /usr/share/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce docker-ce-cli containerd.io docker-ce-rootless-extras uidmap iproute2 fuse-overlayfs \
    && groupadd -g 1001 cortex \
    && useradd --system --uid 1001 --gid 1001 --groups docker cortex -d /opt/cortex \
    && mkdir -m 777 /var/log/cortex \
    && chmod 666 /etc/subuid /etc/subgid \
    && rm -rf /var/lib/apt/lists/* \
    && apt-get clean -y -q \
    && apt-get autoremove -y -q

COPY --from=builder --chown=root:root /build/target/universal/stage/ /opt/cortex/

COPY --from=builder /build/package/docker/entrypoint /opt/cortex/entrypoint
COPY --from=builder /build/conf/application.sample /etc/cortex/application.conf
COPY --from=builder /build/package/logback.xml /etc/cortex/logback.xml

RUN chmod +x /opt/cortex/bin/cortex /opt/cortex/entrypoint \
    && chown -R cortex:cortex /etc/cortex

VOLUME /var/lib/docker

EXPOSE 9001

ENTRYPOINT ["/opt/cortex/entrypoint"]
CMD []
