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
	"full": ${ARTIFACT}
}
EOF
