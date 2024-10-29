package org.elastic4play.models

trait HiveEnumeration { self: Enumeration =>

  def getByName(name: String): Value =
    try {
      withName(name)
    } catch {
      case _: NoSuchElementException => //throw BadRequestError(
        sys.error(s"$name is invalid for $toString. Correct values are ${values.mkString(", ")}")
    }
}
