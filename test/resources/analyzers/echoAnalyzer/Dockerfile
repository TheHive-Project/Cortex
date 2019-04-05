FROM debian:latest

WORKDIR /analyzer
RUN apt update && apt install -y jq
COPY echoAnalyzer.sh echoAnalyzer/echoAnalyzer.sh
ENTRYPOINT ["echoAnalyzer/echoAnalyzer.sh"]