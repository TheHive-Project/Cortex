#!/usr/bin/env bash

ARTIFACT=$(cat)
DATA=$(jq .data <<< ${ARTIFACT})
DATATYPE=$(jq .dataType <<< ${ARTIFACT})

cat << EOF
{
	"success": true,
	"summary": {
		"taxonomies": [
			{ "namespace": "test", "predicate": "data", "value": "echo", "level": "info" }
		]
	},
	"artifacts": [
		{
			"data": ${DATA},
			"dataType": ${DATATYPE}
		}
	],
	"full": ${ARTIFACT},
	"operations": [
		{ "type": "AddTagToCase", "tag": "From Action Operation" },
		{ "type": "CreateTask", "title": "task created by action", "description": "yop !" }
	]
}
EOF
