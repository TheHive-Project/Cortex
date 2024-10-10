package common

import scala.util.Random
import play.api.libs.json.{JsBoolean, JsNumber, JsObject, JsString, JsValue}

object Fabricator {
  def string(prefix: String = "", size: Int = 10): String = prefix + Random.alphanumeric.take(size).mkString
  def int: Int                                            = Random.nextInt()
  def boolean: Boolean                                    = Random.nextBoolean()
  def long: Long                                          = Random.nextLong()

  def jsValue: JsValue = int % 4 match {
    case 0 => JsNumber(long)
    case 1 => JsBoolean(boolean)
    case _ => JsString(string())
  }

  def jsObject(maxSize: Int = 10): JsObject = {
    val fields = Seq.fill(int % maxSize)(string() -> jsValue)
    JsObject(fields)
  }
}
