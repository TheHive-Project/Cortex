package org.elastic4play

import play.api.libs.json.{JsObject, JsValue}

import org.elastic4play.controllers.InputValue

class ErrorWithObject(message: String, val obj: JsObject) extends Exception(message)

object ErrorWithObject {
  def unapply(ewo: ErrorWithObject): Option[(String, String, JsObject)] = Some((ewo.getClass.getSimpleName, ewo.getMessage, ewo.obj))
}

case class BadRequestError(message: String)                                           extends Exception(message)
case class CreateError(status: Option[String], message: String, attributes: JsObject) extends ErrorWithObject(message, attributes)
case class ConflictError(message: String, attributes: JsObject)                       extends ErrorWithObject(message, attributes)
case class NotFoundError(message: String)                                             extends Exception(message)
case class GetError(message: String)                                                  extends Exception(message)
case class UpdateError(status: Option[String], message: String, attributes: JsObject) extends ErrorWithObject(message, attributes)
case class InternalError(message: String)                                             extends Exception(message)
case class SearchError(message: String)                                               extends Exception(message)
case class AuthenticationError(message: String)                                       extends Exception(message)
case class AuthorizationError(message: String)                                        extends Exception(message)
case class MultiError(message: String, exceptions: Seq[Exception])
    extends Exception(message + exceptions.map(_.getMessage).mkString(" :\n\t- ", "\n\t- ", ""))
case object IndexNotFoundException extends Exception

case class AttributeCheckingError(tableName: String, errors: Seq[AttributeError] = Nil) extends Exception(errors.mkString("[", "][", "]")) {
  override def toString: String = errors.mkString("[", "][", "]")
}

sealed trait AttributeError extends Throwable {
  def withName(name: String): AttributeError
  val name: String
  override def getMessage: String = toString
}

case class InvalidFormatAttributeError(name: String, format: String, value: InputValue) extends AttributeError {
  override def toString                                  = s"Invalid format for $name: $value, expected $format"
  override def withName(newName: String): AttributeError = copy(name = newName)
}
case class UnknownAttributeError(name: String, value: JsValue) extends AttributeError {
  override def toString                                  = s"Unknown attribute $name: $value"
  override def withName(newName: String): AttributeError = copy(name = newName)
}
case class UpdateReadOnlyAttributeError(name: String) extends AttributeError {
  override def toString                                  = s"Attribute $name is read-only"
  override def withName(newName: String): AttributeError = copy(name = newName)
}
case class MissingAttributeError(name: String) extends AttributeError {
  override def toString                                  = s"Attribute $name is missing"
  override def withName(newName: String): AttributeError = copy(name = newName)
}
