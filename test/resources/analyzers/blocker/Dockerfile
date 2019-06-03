FROM debian:latest

WORKDIR /analyzer
RUN apt update && apt install -y jq
COPY blocker.sh blocker/blocker.sh
ENTRYPOINT ["blocker/blocker.sh"]
