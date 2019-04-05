#!/bin/bash

set -x

echo starting with parameters: $*
for JOB
do
	echo executing $JOB
	if [[ -d "${JOB}" ]]; then
		echo directory $JOB exists
		if [[ -r "${JOB}/input/input.json" ]]; then
			INPUT=$(cat ${JOB}/input/input.json)
		else
			INPUT="{}"
		fi
		echo input is $INPUT
		DATA=$(jq .data <<< ${INPUT})
		DATATYPE=$(jq .dataType <<< ${INPUT})

		echo building output
		mkdir -p "${JOB}/output"
		cat > "${JOB}/output/output.json" <<- EOF
		{
			"success": true,
			"summary": {
				"taxonomies": [
					{ "namespace": "test", "predicate": "data", "value": "echo", "level": "info" }
				]
			},
			"full": ${INPUT},
			"operations": [
				{ "type": "AddTagToCase", "tag": "From Action Operation" },
				{ "type": "CreateTask", "title": "task created by action", "description": "yop !" }
			]
		}
EOF
		echo output is:
		cat "${JOB}/output/output.json"
	fi
done
