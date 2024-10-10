package org.elastic4play.utils

import play.api.libs.json.{Format, JsString, Reads, Writes}

object JsonFormat {
  private val hashReads: Reads[Hash]    = Reads(json => json.validate[String].map(h => Hash(h)))
  private val hashWrites: Writes[Hash]  = Writes[Hash](h => JsString(h.toString))
  implicit val hashFormat: Format[Hash] = Format(hashReads, hashWrites)
}
