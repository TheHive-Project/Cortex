job [[ template "job_name" . ]] {
  [[ template "region" . ]]
  datacenters = [[ var "datacenters" . | toStringList ]]
  type = "service"

  group "backend_services" {
    network {
      mode = "bridge"
      dns { servers = ["172.17.0.1"] }
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
      dns { servers = ["172.17.0.1"] }
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

    volume "jobs" {
      type = "host"
      read_only = false
      source = "cortex-dev-configs"
    }

    task "cortex" {
      driver = "docker"

      volume_mount {
        volume      = "jobs"
        destination = "/data/cortex-dev-configs/"
      }

      restart {
        attempts = 6
        delay    = "30s"
      }

      env {
        # Do not configure automatically with env variable
        # We'll pass the configuration file ourselves from a template
        no_config = 1
      }

      artifact {
          source      = "https://vault.service.infra.sb:8200/v1/pki/ca/pem"
          destination = "local/strangebee-sb-caroot.crt"
          mode        = "file"
      }

      config {
        [[ if eq true (var "from_docker_hub" . )]]
        image      = "thehiveproject/cortex:[[ (var "docker_image_version" .) ]]"
        [[ else ]]
        image      = "[[ var "docker_image" . ]][[ if hasPrefix "sha256" (var "docker_image_version" .) ]][[ "@" ]][[ else ]][[ ":" ]][[ end ]][[ (var "docker_image_version" .) ]]"
        [[ end ]]

        ports      = [ "http" ]
        entrypoint = [ "/bin/bash" ]
        args       = [
          "-c",
          <<-EOF
          apt update
          apt install -y ca-certificates-java
          /usr/sbin/update-ca-certificates
          keytool -importcert -alias strangebee -noprompt -file /local/strangebee-sb-caroot.crt -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit
          # You absolutely need to create the required subdirectories
          # otherwise it will fail silently !!!
          su cortex -c 'mkdir -p /data/cortex-dev-configs/[[ var "service_version" . ]]/jobs'
          /opt/cortex/entrypoint
          EOF
        ]
        volumes    = [
          "/var/run/docker.sock:/var/run/docker.sock",
          "local/application.conf:/etc/cortex/application.conf",
          "local/logback.xml:/etc/cortex/logback.xml",
          "local/analyzers.json:/etc/cortex/analyzers.json"
        ]
      }

      vault {
        policies = ["cortex-policy"]
      }

      template {
        destination = "local/application.conf"
        data        = <<-EOF
          # found frome here: https://github.com/StrangeBee/Cortex/blob/develop/conf/application.sample
          # new requirement !!!!
          docker {
            autoUpdate = true
            pullImageTimeout = 5000
          }
          play.server.http.port = {{ env "NOMAD_PORT_http" }}
          play.http.secret.key  = "[[ randAlphaNum 128 | sha256sum ]]"
          search {
            index = [[ list "cortex" (var "service_version" .) | join "-" | quote ]]
            uri   = "http://localhost:9200"
          }
          cache.job           = 10m
          job.directory       = "/data/cortex-dev-configs/[[ var "service_version" . ]]/jobs"
          job.dockerDirectory = "/data/cortex-dev-configs/[[ var "service_version" . ]]/jobs"

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
            "https://download.thehive-project.org/repository/download.thehive-project.org/analyzers-devel.json"
            "/etc/cortex/analyzers.json"
          ]
          responder.urls = [
            "https://download.thehive-project.org/responders.json"
          ]
        EOF
      }

      template {
        destination = "local/logback.xml"
        data        = <<-EOF
          <?xml version="1.0" encoding="UTF-8"?>
          <configuration debug=[[ var "cortex_debug" . | quote ]]>

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

              <logger name="play" level=[[ var "debug_level" . | quote ]] />
              <logger name="application" level=[[ var "debug_level" . | quote ]] />

              <logger name="com.gargoylesoftware.htmlunit.javascript" level="OFF"/>

              <root level=[[ var "debug_level" . | quote ]]>
                  <appender-ref ref="ASYNCSTDOUT"/>
              </root>

          </configuration>
        EOF
      }

    template {
          destination = "local/analyzers.json"
          data        = <<-EOF
          [
              [[ range $y := untilStep 1 11 1 ]]
              {
              "name": "testAnalyzer_[[ $y ]]",
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
              } [[ if not (eq $y 10) ]],[[ end ]]
              [[ end ]]
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
