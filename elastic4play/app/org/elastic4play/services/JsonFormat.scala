package org.elastic4play.services

import scala.jdk.CollectionConverters._

import play.api.libs.json._
import play.api.{Configuration, Logger}

import com.typesafe.config.ConfigValueType._
import com.typesafe.config.{ConfigList, ConfigObject, ConfigValue}

import org.elastic4play.models.JsonFormat._
import org.elastic4play.services.QueryDSL._
import org.elastic4play.utils.Hash
import org.elastic4play.utils.JsonFormat.hashFormat

object JsonFormat {
  private[JsonFormat] lazy val logger = Logger(getClass)

  private val attachmentWrites: OWrites[Attachment] = OWrites[Attachment] { attachment =>
    Json.obj(
      "name"        -> attachment.name,
      "hashes"      -> attachment.hashes,
      "size"        -> attachment.size,
      "contentType" -> attachment.contentType,
      "id"          -> attachment.id
    )
  }

  private val attachmentReads: Reads[Attachment] = Reads { json =>
    for {
      name        <- (json \ "name").validate[String]
      hashes      <- (json \ "hashes").validate[Seq[Hash]]
      size        <- (json \ "size").validate[Long]
      contentType <- (json \ "contentType").validate[String]
      id          <- (json \ "id").validate[String]
    } yield Attachment(name, hashes, size, contentType, id)
  }

  implicit val attachmentFormat: OFormat[Attachment] = OFormat(attachmentReads, attachmentWrites)

  implicit val roleWrites: Writes[Role] = Writes[Role](role => JsString(role.name))

  implicit def configWrites: OWrites[Configuration] = OWrites[Configuration] { cfg =>
    JsObject(cfg.subKeys.map(key => key -> configValueWrites.writes(cfg.underlying.getValue(key))).toSeq)
  }

  implicit def configValueWrites: Writes[ConfigValue] = Writes[ConfigValue] {
    case v: ConfigObject             => configWrites.writes(Configuration(v.toConfig))
    case v: ConfigList               => JsArray(v.asScala.map(x => configValueWrites.writes(x)))
    case v if v.valueType == NUMBER  => JsNumber(BigDecimal(v.unwrapped.asInstanceOf[Number].toString))
    case v if v.valueType == BOOLEAN => JsBoolean(v.unwrapped.asInstanceOf[Boolean])
    case v if v.valueType == NULL    => JsNull
    case v if v.valueType == STRING  => JsString(v.unwrapped.asInstanceOf[String])
  }

  //def jsonGet[A](json: JsValue, name:  String)(implicit reads: Reads[A]) = (json \ name).as[A]

  object JsObj {

    def unapply(v: JsValue): Option[Seq[(String, JsValue)]] = v match {
      case JsObject(f) => Some(f.toSeq)
      case _           => None
    }
  }

  object JsObjOne {

    def unapply(v: JsValue): Option[(String, JsValue)] = v match {
      case JsObject(f) if f.size == 1 => f.toSeq.headOption
      case _                          => None
    }
  }

  object JsVal {

    def unapply(v: JsValue): Option[Any] = v match {
      case JsString(s)  => Some(s)
      case JsBoolean(b) => Some(b)
      case JsNumber(i)  => Some(i)
      case _            => None
    }
  }

  object JsRange {

    def unapply(v: JsValue): Option[(String, Any, Any)] =
      for {
        field  <- (v \ "_field").asOpt[String]
        jsFrom <- (v \ "_from").asOpt[JsValue]
        from   <- JsVal.unapply(jsFrom)
        jsTo   <- (v \ "_to").asOpt[JsValue]
        to     <- JsVal.unapply(jsTo)
      } yield (field, from, to)
  }

  object JsParent {

    def unapply(v: JsValue): Option[(String, QueryDef)] =
      for {
        t <- (v \ "_type").asOpt[String]
        q <- (v \ "_query").asOpt[QueryDef]
      } yield (t, q)
  }

  object JsParentId {

    def unapply(v: JsValue): Option[(String, String)] =
      for {
        t <- (v \ "_type").asOpt[String]
        i <- (v \ "_id").asOpt[String]
      } yield (t, i)
  }

  object JsField {

    def unapply(v: JsValue): Option[(String, Any)] =
      for {
        f          <- (v \ "_field").asOpt[String]
        maybeValue <- (v \ "_value").asOpt[JsValue]
        value      <- JsVal.unapply(maybeValue)
      } yield (f, value)
  }

  object JsFieldIn {

    def unapply(v: JsValue): Option[(String, Seq[String])] =
      for {
        f        <- (v \ "_field").asOpt[String]
        jsValues <- (v \ "_values").asOpt[Seq[JsValue]]
        values = jsValues.flatMap(JsVal.unapply)
      } yield f -> values.map(_.toString)
  }

  object JsAgg {

    def unapply(v: JsValue): Option[(String, Option[String], JsValue)] =
      for {
        agg <- (v \ "_agg").asOpt[String]
        aggName = (v \ "_name").asOpt[String]
      } yield (agg, aggName, v)
  }

  object JsAggFieldQuery {

    def unapply(v: JsValue): Option[(String, Option[QueryDef])] =
      for {
        field <- (v \ "_field").asOpt[String]
        query = (v \ "_query").asOpt[QueryDef]
      } yield (field, query)
  }

  implicit val queryReads: Reads[QueryDef] = {
    Reads {
      case JsObjOne(("_and", JsArray(v)))                 => JsSuccess(and(v.map(_.as[QueryDef](queryReads)).toSeq: _*))
      case JsObjOne(("_or", JsArray(v)))                  => JsSuccess(or(v.map(_.as[QueryDef](queryReads)).toSeq: _*))
      case JsObjOne(("_contains", JsString(v)))           => JsSuccess(contains(v))
      case JsObjOne(("_not", v: JsObject))                => JsSuccess(not(v.as[QueryDef](queryReads)))
      case JsObjOne(("_any", _))                          => JsSuccess(any)
      case j: JsObject if j.fields.isEmpty                => JsSuccess(any)
      case JsObjOne(("_gt", JsObjOne(n, JsVal(v))))       => JsSuccess(n ~> v)
      case JsObjOne(("_gte", JsObjOne(n, JsVal(v))))      => JsSuccess(n ~>= v)
      case JsObjOne(("_lt", JsObjOne(n, JsVal(v))))       => JsSuccess(n ~< v)
      case JsObjOne(("_lte", JsObjOne(n, JsVal(v))))      => JsSuccess(n ~<= v)
      case JsObjOne(("_between", JsRange(n, f, t)))       => JsSuccess(n ~<> (f -> t))
      case JsObjOne(("_parent", JsParent(p, q)))          => JsSuccess(parent(p, q))
      case JsObjOne(("_parent", JsParentId(p, i)))        => JsSuccess(withParent(p, i))
      case JsObjOne(("_id", JsString(id)))                => JsSuccess(withId(id))
      case JsField(field, value)                          => JsSuccess(field ~= value)
      case JsObjOne(("_child", JsParent(p, q)))           => JsSuccess(child(p, q))
      case JsObjOne(("_string", JsString(s)))             => JsSuccess(string(s))
      case JsObjOne(("_in", JsFieldIn(f, v)))             => JsSuccess(f in (v: _*))
      case JsObjOne(("_type", JsString(v)))               => JsSuccess(ofType(v))
      case JsObjOne(("_like", JsField(field, value)))     => JsSuccess(field like value)
      case JsObjOne(("_wildcard", JsField(field, value))) => JsSuccess(field ~=~ value)
      case JsObjOne((n, JsVal(v))) =>
        if (n.startsWith("_")) logger.warn(s"""Potentially invalid search query : {"$n": "$v"}"""); JsSuccess(n ~= v)
      case other => JsError(s"Invalid query: unexpected $other")
    }
  }

  implicit val aggReads: Reads[Agg] = Reads {
    case JsAgg("avg", aggregationName, JsAggFieldQuery(field, query)) => JsSuccess(selectAvg(aggregationName, field, query))
    case JsAgg("min", aggregationName, JsAggFieldQuery(field, query)) => JsSuccess(selectMin(aggregationName, field, query))
    case JsAgg("max", aggregationName, JsAggFieldQuery(field, query)) => JsSuccess(selectMax(aggregationName, field, query))
    case JsAgg("sum", aggregationName, JsAggFieldQuery(field, query)) => JsSuccess(selectSum(aggregationName, field, query))
    case json @ JsAgg("count", aggregationName, _)                    => JsSuccess(selectCount(aggregationName, (json \ "_query").asOpt[QueryDef]))
    case json @ JsAgg("top", aggregationName, _) =>
      val size  = (json \ "_size").asOpt[Int].getOrElse(10)
      val order = (json \ "_order").asOpt[Seq[String]].getOrElse(Nil)
      JsSuccess(selectTop(aggregationName, size, order))
    case json @ JsAgg("time", aggregationName, _) =>
      val fields      = (json \ "_fields").as[Seq[String]]
      val interval    = (json \ "_interval").as[String]
      val selectables = (json \ "_select").as[Seq[Agg]]
      JsSuccess(groupByTime(aggregationName, fields, interval, selectables: _*))
    case json @ JsAgg("field", aggregationName, _) =>
      val field       = (json \ "_field").as[String]
      val size        = (json \ "_size").asOpt[Int].getOrElse(10)
      val order       = (json \ "_order").asOpt[Seq[String]].getOrElse(Nil)
      val selectables = (json \ "_select").as[Seq[Agg]]
      JsSuccess(groupByField(aggregationName, field, size, order, selectables: _*))
    case json @ JsAgg("category", aggregationName, _) =>
      val categories  = (json \ "_categories").as[Map[String, QueryDef]]
      val selectables = (json \ "_select").as[Seq[Agg]]
      JsSuccess(groupByCaterogy(aggregationName, categories, selectables: _*))
    case unexpected: JsValue => JsError(s"Unexpected JsValue $unexpected")
  }

  implicit val authContextWrites: OWrites[AuthContext] = OWrites[AuthContext] { authContext =>
    Json.obj("id" -> authContext.userId, "name" -> authContext.userName, "roles" -> authContext.roles)
  }

  implicit val auditableActionFormat: Format[AuditableAction.Type] = enumFormat(AuditableAction)

  implicit val AuditOperationWrites: OWrites[AuditOperation] = Json.writes[AuditOperation]
}
