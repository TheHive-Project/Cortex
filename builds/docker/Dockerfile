FROM debian:13-slim
LABEL org.opencontainers.image.source=https://github.com/strangebee/cortex
LABEL org.opencontainers.image.description="Development version of Cortex image, supported by StrangeBee"
LABEL org.opencontainers.image.title="cortex"
WORKDIR /opt/cortex
ENV JAVA_HOME=/usr/lib/jvm/java-11-amazon-corretto
RUN apt update && apt upgrade -y && apt install -y curl gnupg && curl -fL https://apt.corretto.aws/corretto.key | gpg --dearmor -o /usr/share/keyrings/corretto.gpg && echo 'deb [signed-by=/usr/share/keyrings/corretto.gpg] https://apt.corretto.aws stable main' > /etc/apt/sources.list.d/corretto.list && apt update && apt install -y java-11-amazon-corretto-jdk && curl -fsSL https://download.docker.com/linux/debian/gpg -o /usr/share/keyrings/docker.asc && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list && apt update && apt install -y docker-ce docker-ce-cli containerd.io docker-ce-rootless-extras uidmap iproute2 fuse-overlayfs && groupadd -g 1001 cortex && useradd --system --uid 1001 --gid 1001 --groups docker cortex -d /opt/cortex && mkdir -m 777 /var/log/cortex && chmod 666 /etc/subuid /etc/subgid && rm -rf /var/lib/apt/lists/* && apt autoclean -y -q && apt autoremove -y -q
ADD --chown=root:root opt /opt
ADD --chown=cortex:cortex --chmod=755 etc /etc
VOLUME /var/lib/docker
RUN ["chmod", "+x", "/opt/cortex/bin/cortex", "/opt/cortex/entrypoint"]
EXPOSE 9001
ENTRYPOINT ["/opt/cortex/entrypoint"]
CMD []
