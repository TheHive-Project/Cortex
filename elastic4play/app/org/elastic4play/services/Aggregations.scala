package org.elastic4play.services

import scala.util.Try
import play.api.libs.json._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.JacksonSupport
import com.sksamuel.elastic4s.requests.script.Script
import com.sksamuel.elastic4s.requests.searches.DateHistogramInterval
import com.sksamuel.elastic4s.requests.searches.aggs.{
  Aggregation,
  AvgAggregation,
  KeyedFiltersAggregation,
  MaxAggregation,
  MinAggregation,
  SumAggregation,
  TermsAggregation,
  TermsOrder
}
import com.sksamuel.elastic4s.requests.searches.aggs.responses.HasAggregations
import com.sksamuel.elastic4s.requests.searches.aggs.responses.bucket.Terms
import com.sksamuel.elastic4s.requests.searches.aggs.responses.metrics.TopHits
import org.elastic4play.BadRequestError
import org.elastic4play.database.DBUtils
import org.elastic4play.models.BaseModelDef

abstract class Agg(val aggregationName: String) {
  def apply(model: BaseModelDef): Seq[Aggregation]

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject
}

abstract class FieldAgg(val fieldName: String, aggregationName: String, query: Option[QueryDef]) extends Agg(aggregationName) {
  def script(s: String): Aggregation

  def field(f: String): Aggregation

  def getAggregation(fieldName: String, aggregations: HasAggregations, query: Option[QueryDef]): HasAggregations = {

    val agg = query match {
      case None => aggregations
      case _    => aggregations.filter(aggregationName)
    }

    if (fieldName.startsWith("computed")) agg
    else {
      fieldName.split("\\.").init.foldLeft(agg) { (a, _) =>
        a.nested(aggregationName)
      }
    }
  }

  def apply(model: BaseModelDef): Seq[Aggregation] = {
    val aggs = fieldName.split("\\.") match {
      case Array("computed", c) =>
        val s = model.computedMetrics.getOrElse(c, throw BadRequestError(s"Field $fieldName is unknown in ${model.modelName}"))
        Seq(script(s))
      case array =>
        if (array(0) != "" && !model.attributes.exists(_.attributeName == array(0))) {
          throw BadRequestError(s"Field $fieldName is unknown in ${model.modelName}")
        }
        // TODO check attribute type
        Seq(
          fieldName
            .split("\\.")
            .toSeq
            .init
            .inits
            .toSeq
            .init
            .foldLeft[Aggregation](field(fieldName)) { (agg, f) =>
              nestedAggregation(aggregationName, f.mkString(".")).subaggs(agg)
            }
        )
    }
    query match {
      case None    => aggs
      case Some(q) => Seq(filterAgg(aggregationName, q.query).subAggregations(aggs))
    }
  }
}

class SelectAvg(aggregationName: String, fieldName: String, query: Option[QueryDef]) extends FieldAgg(fieldName, aggregationName, query) {
  def script(s: String): Aggregation = AvgAggregation(aggregationName).script(Script(s).lang("painless"))

  def field(f: String): Aggregation = AvgAggregation(aggregationName).field(f)

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val avg   = getAggregation(fieldName, aggregations, query).avg(aggregationName)
    val value = Try(JsNumber(avg.value)).toOption.getOrElse(JsNumber(0))
    JsObject(Seq(avg.name -> value))
  }
}

class SelectMin(aggregationName: String, fieldName: String, query: Option[QueryDef]) extends FieldAgg(fieldName, aggregationName, query) {
  def script(s: String): Aggregation = MinAggregation(aggregationName).script(Script(s).lang("painless"))

  def field(f: String): Aggregation = MinAggregation(aggregationName).field(f)

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val min   = getAggregation(fieldName, aggregations, query).min(aggregationName)
    val value = min.value.fold(JsNumber(0))(m => JsNumber(m))
    JsObject(Seq(min.name -> value))
  }
}

class SelectMax(aggregationName: String, fieldName: String, query: Option[QueryDef]) extends FieldAgg(fieldName, aggregationName, query) {
  def script(s: String): Aggregation = MaxAggregation(aggregationName).script(Script(s).lang("painless"))

  def field(f: String): Aggregation = MaxAggregation(aggregationName).field(f)

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val max   = getAggregation(fieldName, aggregations, query).max(aggregationName)
    val value = max.value.fold(JsNumber(0))(m => JsNumber(m))
    JsObject(Seq(max.name -> value))
  }
}

class SelectSum(aggregationName: String, fieldName: String, query: Option[QueryDef]) extends FieldAgg(fieldName, aggregationName, query) {
  def script(s: String): Aggregation = SumAggregation(aggregationName).script(Script(s).lang("painless"))

  def field(f: String): Aggregation = SumAggregation(aggregationName).field(f)

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val sum   = getAggregation(fieldName, aggregations, query).sum(aggregationName)
    val value = JsNumber(sum.value)
    JsObject(Seq(sum.name -> value))
  }
}

class SelectCount(aggregationName: String, query: Option[QueryDef]) extends FieldAgg("", aggregationName, query) {
  def script(s: String): Aggregation = ???

  def field(f: String): Aggregation = filterAgg(aggregationName, matchAllQuery())

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val count = aggregations.filter(aggregationName)
    JsObject(Seq(count.name -> JsNumber(count.docCount)))
  }
}

class SelectTop(aggregationName: String, size: Int, sortBy: Seq[String], query: Option[QueryDef] = None)
    extends FieldAgg("", aggregationName, query) {
  def script(s: String): Aggregation = ???

  def field(f: String): Aggregation = topHitsAgg(aggregationName).size(size).sortBy(DBUtils.sortDefinition(sortBy))

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val hits = aggregations.result[TopHits](aggregationName).hits.map { hit =>
      val id   = JsString(hit.id)
      val body = Json.parse(JacksonSupport.mapper.writeValueAsString(hit.source)).as[JsObject]
      val (parent, model) = (body \ "relations" \ "parent").asOpt[JsString] match {
        case Some(p) => p      -> (body \ "relations" \ "name").as[JsString]
        case None    => JsNull -> (body \ "relations").as[JsString]
      }
      body - "relations" +
        ("_type"   -> model) +
        ("_parent" -> parent) +
        ("_id"     -> id)

    }
    Json.obj("top" -> hits)
  }
}

class GroupByCategory(aggregationName: String, categories: Map[String, QueryDef], subAggs: Seq[Agg]) extends Agg(aggregationName) {

  def apply(model: BaseModelDef): Seq[KeyedFiltersAggregation] = {
    val filters         = categories.view.mapValues(_.query).toMap
    val subAggregations = subAggs.flatMap(_.apply(model))
    Seq(KeyedFiltersAggregation(aggregationName, filters).subAggregations(subAggregations))
  }

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val filters = aggregations.keyedFilters(aggregationName)
    JsObject {
      categories.keys.toSeq.map { cat =>
        val subAggResults = filters.aggResults(cat)
        cat -> subAggs
          .map(_.processResult(model, subAggResults))
          .reduceOption(_ ++ _)
          .getOrElse(JsObject.empty)
      }
    }
  }
}

class GroupByTime(aggregationName: String, fields: Seq[String], interval: String, subAggs: Seq[Agg]) extends Agg(aggregationName) {

  def apply(model: BaseModelDef): Seq[Aggregation] =
    fields.map { fieldName =>
      val dateHistoAgg = dateHistogramAgg(s"${aggregationName}_$fieldName", fieldName)
        .calendarInterval(DateHistogramInterval.fromString(interval))
        .subAggregations(subAggs.flatMap(_.apply(model)))
      fieldName
        .split("\\.")
        .toSeq
        .init
        .inits
        .toSeq
        .init
        .foldLeft[Aggregation](dateHistoAgg) { (agg, f) =>
          nestedAggregation(aggregationName, f.mkString(".")).subaggs(agg)
        }
    }

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val aggs = fields.map { fieldName =>
      val agg = fieldName.split("\\.").init.foldLeft(aggregations) { (a, _) =>
        a.nested(aggregationName)
      }

      val buckets = agg.histogram(s"${aggregationName}_$fieldName").buckets
      fieldName -> buckets.map { bucket =>
        val results = subAggs
          .map(_.processResult(model, bucket))
          .reduceOption(_ ++ _)
          .getOrElse(JsObject.empty)
        // date → obj(key{avg, min} → value)
        bucket.key -> results
      }.toMap
    }.toMap
    val keys = aggs.values.flatMap(_.keys).toSet
    JsObject {
      keys.map { date =>
        date -> JsObject(aggs.map {
          case (df, values) =>
            df -> values.getOrElse(date, JsObject.empty)
        })
      }.toMap
    }
  }
}

class GroupByField(aggregationName: String, fieldName: String, size: Option[Int], sortBy: Seq[String], subAggs: Seq[Agg])
    extends Agg(aggregationName) {
  private def setSize(agg: TermsAggregation): TermsAggregation =
    size.fold(agg)(s => agg.size(s))

  private def setOrder(agg: TermsAggregation): TermsAggregation = {
    val sortDefinition = sortBy
      .flatMap {
        case "_count" | "+_count"   => Seq(TermsOrder("_count", asc = true))
        case "-_count"              => Seq(TermsOrder("_count", asc = false))
        case "_term" | "+_term"     => Seq(TermsOrder("_key", asc = true))
        case "-_term"               => Seq(TermsOrder("_key", asc = false))
        case f if f.startsWith("+") => Seq(TermsOrder(f.drop(1), asc = true))
        case f if f.startsWith("-") => Seq(TermsOrder(f.drop(1), asc = false))
        case f if f.length() > 0    => Seq(TermsOrder(f, asc = true))
        case _                      => Nil
      }
    if (sortDefinition.nonEmpty)
      agg.order(sortDefinition)
    else
      agg
  }

  def apply(model: BaseModelDef): Seq[Aggregation] = {
    val agg = setSize(setOrder(termsAgg(s"${aggregationName}_$fieldName", fieldName).subAggregations(subAggs.flatMap(_.apply(model)))))
    Seq(
      fieldName
        .split("\\.")
        .toSeq
        .init
        .inits
        .toSeq
        .init
        .foldLeft[Aggregation](agg) { (agg, f) =>
          nestedAggregation(aggregationName, f.mkString(".")).subaggs(agg)
        }
    )
  }

  def processResult(model: BaseModelDef, aggregations: HasAggregations): JsObject = {
    val buckets = fieldName
      .split("\\.")
      .init
      .foldLeft(aggregations) { (a, _) =>
        a.nested(aggregationName)
      }
      .result[Terms](s"${aggregationName}_$fieldName")
      .buckets
    JsObject {
      buckets.map { bucket =>
        val results = subAggs
          .map(_.processResult(model, bucket))
          .reduceOption(_ ++ _)
          .getOrElse(JsObject.empty)
        bucket.key -> results
      }.toMap
    }
  }
}
