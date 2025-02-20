job [[ template "job_name" . ]] {
  [[ template "region" . ]]
  datacenters = [[ var "datacenters" . | toStringList ]]
  type = "service"

  group "backend_services" {
    network {
      mode = "bridge"
    }

    service {
      name = "elasticsearch-cortex-[[ var "service_version" . ]]"
      port = "9200"
      connect {
        sidecar_service {}
      }
    }

    task "elasticsearch" {

      driver = "docker"
      config {
        image   = "docker.elastic.co/elasticsearch/elasticsearch:7.17.4"
      }

      template {
        destination = "secrets/elasticsearch.env"
        env         = true
        data        = <<-EOF
          xpack.license.self_generated.type=basic
          discovery.type=single-node
          node.name=elasticsearch-cortex-dev-[[ var "service_version" . ]]
        EOF
      }      

      resources {
        cpu  = 1000
        memory = 5000
      }
    }

  }

  group "app" {
    count = [[ var "count" . ]]

    network {
      mode = "bridge"
      port "http" {}
    }

    [[ if var "register_service" . ]]
    service {
      name = "cortex-[[ var "service_version" . ]]"
      provider = "consul"
      port = "http"
      tags = [
        "traefik.enable=true",
        "traefik.http.routers.cortex-[[ var "service_version" . ]].entrypoints=http,https",
        "traefik.http.routers.cortex-[[ var "service_version" . ]].rule=Host(`cortex-[[ var "service_version" . ]].web.dev.sb`)"
      ]
    }
    [[ end ]]

    service {
      name = "thehive-backend-services"
      connect {
        sidecar_service {
          proxy {
            upstreams {
              destination_name = "elasticsearch-cortex-[[ var "service_version" . ]]"
              local_bind_port  = 9200
            }
          }
        }
      }
    }

    task "cortex" {
      driver = "docker"

      restart {
        attempts = 6
        delay    = "30s"
      }

      env {
        no_config = 1
        job_directory = "/data/cortex-[[ var "service_version" . ]]/jobs"
        docker_job_directory = "/data/cortex-[[ var "service_version" . ]]/jobs"
        start_docker = 1
      }  

      artifact {
          source      = "https://vault.service.infra.sb:8200/v1/pki/ca/pem"
          destination = "local/strangebee-sb-caroot.crt"
          mode        = "file"
      }

      config {
        image      = "[[ var "docker_image" . ]][[ if hasPrefix "sha256" (var "docker_image_version" .) ]][[ "@" ]][[ else ]][[ ":" ]][[ end ]][[ (var "docker_image_version" .) ]]"
        privileged = true
        ports      = [ "http" ]
        entrypoint = [ "/bin/bash" ]
        args       = [
          "-c",
          <<-EOF
          apt update
          apt install -y ca-certificates-java
          /usr/sbin/update-ca-certificates
          keytool -importcert -alias strangebee -noprompt -file /local/strangebee-sb-caroot.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
          /opt/cortex/entrypoint
          EOF
        ]
        volumes    = [
          "/var/run/docker.sock:/var/run/docker.sock",
          "local/application.conf:/etc/cortex/application.conf",
          "local/logback.xml:/etc/cortex/logback.xml",
          "local/analyzers.json:/etc/cortex/analyzers.json",
          "local/responders.json:/etc/cortex/responders.json"
        ]
      }

      vault {
        policies = ["cortex-policy"]
      }

      template {
        destination = "local/application.conf"
        data        = <<-EOF
          play.server.http.port = {{ env "NOMAD_PORT_http" }}
          play.http.secret.key  = "[[ randAlphaNum 128 | sha256sum ]]"
          search {
            index = cortex
            uri   = "http://localhost:9200"
          }
          cache.job           = 10 minutes
          job.directory       = "/data/cortex-[[ var "docker_image_version" . ]]/jobs"
          job.dockerDirectory = "/data/cortex-[[ var "docker_image_version" . ]]/jobs"

          {{ with secret "dev/data/microsoft/oauth2" }}
          auth {
            method {
              basic = true
            }
            provider = [local, oauth2]
            oauth2 {
              clientId         = "{{ .Data.data.clientId }}"
              clientSecret     = "{{ .Data.data.clientSecret }}"
              redirectUri      = "https://cortex-[[ var "service_version" . ]].web.dev.sb/api/ssoLogin"
              responseType     = "code"
              grantType        = "authorization_code"
              authorizationUrl = "{{ .Data.data.authorizationUrl }}"
              tokenUrl         = "{{ .Data.data.tokenUrl }}"
              userUrl          = "{{ .Data.data.userUrl }}"
              scope            = [User.Read]
            }

            sso {
              autoupdate          = true
              mapper              = simple
              attributes.login    = mail
              attributes.name     = displayName
              defaultRoles        = [read, analyze, orgadmin]
              defaultOrganization = strangebee
            }
          }
          {{ end }}

          analyzer.urls  = [
            "https://download.thehive-project.org/analyzers.json"
            "/etc/cortex/analyzers.json"
          ]
          responder.urls = [
            "https://download.thehive-project.org/responders.json"
            "/etc/cortex/responders.json"
          ]
        EOF
      }

      template {
        destination = "local/logback.xml"
        data        = <<-EOF
          <?xml version="1.0" encoding="UTF-8"?>
          <configuration debug="false">

              <conversionRule conversionWord="coloredLevel"
                              converterClass="play.api.libs.logback.ColoredLevel"/>

              <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                  <encoder>
                      <pattern>%date %coloredLevel %logger{15} - %message%n%xException{10}
                      </pattern>
                  </encoder>
              </appender>

              <appender name="ASYNCSTDOUT" class="ch.qos.logback.classic.AsyncAppender">
                  <appender-ref ref="STDOUT"/>
              </appender>

              <logger name="play" level="INFO"/>
              <logger name="application" level="INFO"/>

              <logger name="com.gargoylesoftware.htmlunit.javascript" level="OFF"/>

              <root level="INFO">
                  <appender-ref ref="ASYNCSTDOUT"/>
              </root>

          </configuration>
        EOF
      }

      template {
        destination = "local/analyzers.json"
        data        = <<-EOF
        [
          [[ $seq := until 11 ]]
          [[- range $idx := $seq ]]
            [[ `{
              "name": "testAnalyzer_REPLACEME",
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
            }` | replace "REPLACEME" ($idx | toString) ]][[ if eq false (eq $idx ($seq | last)) ]],[[ end ]]
        [[- end ]]
        ]
        EOF
      }

      template {
        destination = "local/responders.json"
        data        = <<-EOF
        [
          [[ $seq := until 11 ]]
          [[- range $idx := $seq ]]
            [[ `{
              "name": "testResponder_REPLACEME",
              "version": "1.0",
              "author": "TheHive-Project",
              "url": "https://github.com/thehive-project/thehive",
              "license": "AGPL-V3",
              "baseConfig": "testAnalyzer",
              "config": {},
              "description": "Fake analyzer used for functional tests",
              "dataTypeList": ["thehive:case", "thehive:alert", "thehive:case_task", "thehive:case_task_log", "thehive:case_artifact"],
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
            }`| replace "REPLACEME" ($idx | toString) ]][[ if eq false (eq $idx ($seq | last)) ]],[[ end ]]
        [[- end ]]
        ]
        EOF
      }
      resources {
        cpu  = 500
        memory = 1024
      }
    }
  }
}
