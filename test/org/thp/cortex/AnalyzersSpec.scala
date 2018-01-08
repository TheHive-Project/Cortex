package org.thp.cortex

import java.nio.file.Paths

import scala.concurrent.Future

import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.RequestHeader
import play.api.test._

import org.mockito.Matchers
import org.specs2.mock._
import org.thp.cortex.controllers.AnalyzerCtrl
import org.thp.cortex.models.{ Organization, Roles }
import org.thp.cortex.services.{ OrganizationSrv, UserSrv }

import org.elastic4play.services.AuthContext

class AnalyzersSpec extends PlaySpecification with Mockito {

  abstract class WithTheHiveApp(app: Application) extends WithApplication(app) with Injecting {

    def this(builder: GuiceApplicationBuilder ⇒ GuiceApplicationBuilder) {
      this({
        val UserSrv = mock[UserSrv].verbose
        val adminUser = mock[AuthContext].verbose
        adminUser.roles returns Seq(Roles.read, Roles.write, Roles.admin)
        adminUser.userId returns "admin"
        UserSrv.getFromId(Matchers.any[RequestHeader], Matchers.eq("admin")) returns Future.successful(adminUser)

        builder(GuiceApplicationBuilder().configure("analyzer.path" -> Seq(Paths.get("test/resources/analyzers").toAbsolutePath.toString))
          .configure("search.index" -> "TEST")
          .overrides(bind[UserSrv].toInstance(UserSrv)))
          .build()
      })
    }

    def this() = this((builder: GuiceApplicationBuilder) ⇒ builder)
  }

  "analyzer" should {

    "scan and read definitions" in new WithTheHiveApp {
      private val analyzerCtrl = inject[AnalyzerCtrl]
      private val result = analyzerCtrl.listDefinitions()(FakeRequest()
        .withSession("username" -> "admin"))

      status(result) must equalTo(OK)
      contentType(result) must beSome("application/json")
      contentAsJson(result)
        .as[Seq[JsValue]]
        .map(j ⇒ (j \ "id").as[String]) must contain(allOf("fakeAnalyzer_1_0"))
    }

    "be created from definition" in new WithTheHiveApp(builder ⇒ {
      val organizationSrv = mock[OrganizationSrv].verbose
      val fakeOrganization = mock[Organization]
      organizationSrv.get("fakeOrganization") returns Future.successful(fakeOrganization)
      builder.overrides(bind[OrganizationSrv].toInstance(organizationSrv))
    }) {

      private val analyzerCtrl = inject[AnalyzerCtrl]
      private val result = analyzerCtrl.create("fakeOrganization", "fakeAnalyzer_1_0")(FakeRequest()
        .withSession("username" -> "admin")
        .withHeaders("Content-Type" -> "application/json")
        .withJsonBody(Json.obj("name" -> "myFakeAnalyzerInstance")))

      //println(s"DEBUG: body=${contentAsString(result)}")
      status(result) must equalTo(CREATED)
      contentType(result) must beSome("application/json")
      //      contentAsJson(result) must_=== Json.obj(
      //        "" -> "")

    }
  }
}
