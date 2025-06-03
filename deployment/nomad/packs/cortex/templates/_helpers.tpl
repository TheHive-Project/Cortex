[[- /*

# Template Helpers

This file contains Nomad pack template helpers. Any information outside of a
`define` template action is informational and is not rendered, allowing you
to write comments and implementation details about your helper functions here.
Some helper functions are included to get you started.

*/ -]]

[[- /*

## `job_name` helper

This helper demonstrates how to use a variable value or fall back to the pack's
metadata when that value is set to a default of "".

*/ -]]

[[- define "job_name" -]]
 [[ list ("cortex") (var "service_version" . ) | join "-" | quote ]]
[[- end -]]

[[- /*

## `region` helper

This helper demonstrates conditional element rendering. If your pack specifies
a variable named "region" and it's set, the region line will render otherwise
it won't.

*/ -]]

[[ define "region" -]]
[[- if var "region" . -]]
  region = "[[ var "region" . ]]"
[[- end -]]
[[- end -]]

[[- /*

## `constraints` helper

This helper creates Nomad constraint blocks from a value of type
  `list(object(attribute string, operator string, value string))`

*/ -]]

[[ define "constraints" -]]
[[ range $idx, $constraint := . ]]
  constraint {
    attribute = [[ $constraint.attribute | quote ]]
    [[ if $constraint.operator -]]
    operator  = [[ $constraint.operator | quote ]]
    [[ end -]]
    value     = [[ $constraint.value | quote ]]
  }
[[ end -]]
[[- end -]]

[[- /*

## `service` helper

This helper creates Nomad constraint blocks from a value of type

```
  list(
    object(
      service_name string, service_port_label string, service_provider string, service_tags list(string),
      upstreams list(object(name string, port number))
      check_type string, check_path string, check_interval string, check_timeout string
    )
  )
```

The template context should be set to the value of the object when calling the
template.

*/ -]]

[[ define "service" -]]
[[ $service := . ]]
      service {
        name = [[ $service.service_name | quote ]]
        port = [[ $service.service_port_label | quote ]]
        tags = [[ $service.service_tags | toStringList ]]
        provider = [[ $service.service_provider | quote ]]
        [[- if $service.upstreams ]]
        connect {
          sidecar_service {
            proxy {
              [[- range $upstream := $service.upstreams ]]
              upstreams {
                destination_name = [[ $upstream.name | quote ]]
                local_bind_port  = [[ $upstream.port ]]
              }
              [[- end ]]
            }
          }
        }
        [[- end ]]
        check {
          type     = [[ $service.check_type | quote ]]
          [[- if $service.check_path]]
          path     = [[ $service.check_path | quote ]]
          [[- end ]]
          interval = [[ $service.check_interval | quote ]]
          timeout  = [[ $service.check_timeout | quote ]]
        }
      }
[[- end ]]

[[- /*

## `env_vars` helper

This helper formats maps as key and quoted value pairs.

*/ -]]

[[ define "env_vars" -]]
        [[- range $idx, $var := . ]]
        [[ $var.key ]] = [[ $var.value | quote ]]
        [[- end ]]
[[- end ]]

[[- /*

## `resources` helper

This helper formats values of object(cpu number, memory number) as a `resources`
block

*/ -]]

[[ define "resources" -]]
[[- $resources := . ]]
      resources {
        cpu    = [[ $resources.cpu ]]
        memory = [[ $resources.memory ]]
      }
[[- end ]]

[[ define "analyzer" ]]
[[- $settings := . ]]
[[- range $idx := seq $settings.nb_instances | list ]]
  [[ printf `{
  "name": "testAnalyzer_$idx",
  "version": "1.0",
  "author": "TheHive-Project",
  "url": "https://github.com/thehive-project/thehive",
  "license": "AGPL-V3",
  "baseConfig": "testAnalyzer",
  "config": {},
  "description": "Fake analyzer used for functional tests",
  "dataTypeList": ["domain", "ip", "hash", "other"],
  "dockerImage": "tooom/test_analyzer",
  "configurationItems": [
    {
      "name": "artifacts",
      "description": "Artifacts to include to output report in JSON format (ex: {\"data\":\"8.8.8.8\",\"dataType\":\"ip\"})",
      "type": "string",
      "multi": true,
      "required": false
    },
    {
      "name": "summary",
      "description": "The value of the summary returned by the analyzer",
      "type": "string",
      "multi": false,
      "required": false
    },
    {
      "name": "delay",
      "description": "The delay, in seconds",
      "type": "number",
      "multi": false,
      "required": false
    },
    {
      "name": "errorMessage",
      "description": "If set, make the analyzer fails with this message",
      "type": "string",
      "multi": false,
      "required": false
    },
    {
      "name": "report",
      "description": "The report",
      "type": "string",
      "multi": false,
      "required": false
    },
    {
      "name": "operations",
      "description": "The operations",
      "type": "string",
      "multi": true,
      "required": false
    }
  ]
}`]]
[[- end ]]
[[- end ]]
