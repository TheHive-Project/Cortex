package org.thp.cortex.models

import play.api.libs.json._

import org.elastic4play.models.JsonFormat.enumFormat
import org.elastic4play.services.Role

object JsonFormat {
  private val roleWrites: Writes[Role] = Writes((role: Role) => JsString(role.name.toLowerCase()))
  private val roleReads: Reads[Role] = Reads {
    case JsString(s) if Roles.isValid(s) => JsSuccess(Roles.withName(s).get)
    case _                               => JsError(Seq(JsPath -> Seq(JsonValidationError(s"error.expected.role(${Roles.roleNames}"))))
  }
  implicit val roleFormat: Format[Role]                   = Format[Role](roleReads, roleWrites)
  implicit val workerTypeFormat: Format[WorkerType.Value] = enumFormat(WorkerType)
}
