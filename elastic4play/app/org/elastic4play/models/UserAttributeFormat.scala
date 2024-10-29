package org.elastic4play.models

class UserAttributeFormat extends StringAttributeFormat {
  override val name: String = "user"
}

object UserAttributeFormat extends UserAttributeFormat
