#!/usr/bin/env bash

# fail should be called as a last resort to help the user to understand why the probe failed
function fail {
  timestamp=$(date --iso-8601=seconds)
  echo "{\"timestamp\": \"${timestamp}\", \"message\": \"Liveness probe failed\", "$1"}" | tee /proc/1/fd/2 2> /dev/null
  exit 1
}

READINESS_PROBE_TIMEOUT=${READINESS_PROBE_TIMEOUT:=60}
ENDPOINT="https://cortex:3000/api/organization"
status=$(curl -o /dev/null -w "%{http_code}" --max-time ${READINESS_PROBE_TIMEOUT} -XGET -s -k -H "Authorization: Bearer ${CORTEX_API_KEY}" $ENDPOINT)
curl_rc=$?

echo $STATUS

if [[ ${curl_rc} -ne 0 ]]; then
  fail "\"curl_rc\": \"${curl_rc}\""
fi

# ready if status code 200
if [[ ${status} == "200" ]]; then
  exit 0
else
  fail " \"status\": \"${status}\" "
fi
# end of cortex readiness and liveness check
