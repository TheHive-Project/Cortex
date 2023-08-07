package org.thp.cortex.benchmark

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import java.util.UUID
import scala.concurrent.duration.DurationInt

class AnalyzerSimulation extends Simulation {

  val httpProtocal = http
    .baseUrl(Configuration.baseUrl)
    .authorizationHeader(s"Bearer ${Configuration.userApiKey}")

  val analyzer = Configuration.conf.getString("analyzer")

  val random = UUID.randomUUID()

  val scn = scenario("Analyzer")
    .exec(
      http("launch analyzer")
        .post(s"/api/analyzer/${analyzer}/run")
        .body(StringBody { session =>
          s"""{
             |  "dataType": "domain",
             |  "data": "${random}-${session.userId}",
             |  "pap": 2,
             |  "tlp": 2,
             |  "message": "",
             |  "parameters": {}
             |}""".stripMargin
        })
        .asJson
        .check(status.is(200))
        .check(bodyString.saveAs("job"))
        .check(jsonPath("$._id").saveAs("jobId"))
    )
    .exitHereIfFailed
    .exec(session => session.set("i", 0))
    .exec(
      doWhile(session => session("i").as[Int] < 10 && session("jobStatus").asOption[String].contains("Waiting")) {
        exec(
          http("get job status")
            .get("/api/job/#{jobId}/waitreport?atMost=1seconds")
            .check(jsonPath("$.status").saveAs("jobStatus"))
        ).exec(session => session.set("i", session("i").as[Int] + 1))
      }
    )

  setUp(
    scn.inject(constantConcurrentUsers(6).during(60.seconds))
  ).protocols(httpProtocal)
}
