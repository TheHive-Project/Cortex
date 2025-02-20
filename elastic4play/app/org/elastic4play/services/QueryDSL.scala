package org.elastic4play.services

import com.sksamuel.elastic4s.ElasticDsl.{
  boolQuery,
  existsQuery,
  hasChildQuery,
  hasParentQuery,
  idsQuery,
  matchAllQuery,
  matchQuery,
  nestedQuery,
  query,
  rangeQuery,
  termQuery,
  termsQuery,
  wildcardQuery
}
import com.sksamuel.elastic4s.requests.searches.ScoreMode
import com.sksamuel.elastic4s.requests.searches.queries.Query
import org.elastic4play.models.BaseEntity

object QueryDSL {

  def selectAvg(aggregationName: Option[String], field: String, query: Option[QueryDef]): SelectAvg =
    new SelectAvg(aggregationName.getOrElse(s"avg_$field"), field, query)
  def selectAvg(field: String): SelectAvg = selectAvg(None, field, None)

  def selectMin(aggregationName: Option[String], field: String, query: Option[QueryDef]): SelectMin =
    new SelectMin(aggregationName.getOrElse(s"min_$field"), field, query)
  def selectMin(field: String): SelectMin = selectMin(None, field, None)

  def selectMax(aggregationName: Option[String], field: String, query: Option[QueryDef]): SelectMax =
    new SelectMax(aggregationName.getOrElse(s"max_$field"), field, query)
  def selectMax(field: String): SelectMax = selectMax(None, field, None)

  def selectSum(aggregationName: Option[String], field: String, query: Option[QueryDef]): SelectSum =
    new SelectSum(aggregationName.getOrElse(s"sum_$field"), field, query)
  def selectSum(field: String): SelectSum                                                = selectSum(None, field, None)
  def selectCount(aggregationName: Option[String], query: Option[QueryDef]): SelectCount = new SelectCount(aggregationName.getOrElse("count"), query)
  val selectCount: SelectCount                                                           = selectCount(None, None)

  def selectTop(aggregationName: Option[String], size: Int, sortBy: Seq[String]): SelectTop =
    new SelectTop(aggregationName.getOrElse("top"), size, sortBy)
  def selectTop(size: Int, sortBy: Seq[String]): SelectTop = selectTop(None, size, sortBy)

  def groupByTime(aggregationName: Option[String], fields: Seq[String], interval: String, selectables: Agg*): GroupByTime =
    new GroupByTime(aggregationName.getOrElse("datehistogram"), fields, interval, selectables)
  def groupByTime(fields: Seq[String], interval: String, selectables: Agg*): GroupByTime = groupByTime(None, fields, interval, selectables: _*)

  def groupByField(aggregationName: Option[String], field: String, size: Int, sortBy: Seq[String], selectables: Agg*): GroupByField =
    new GroupByField(aggregationName.getOrElse("term"), field, Some(size), sortBy, selectables)

  def groupByField(aggregationName: Option[String], field: String, selectables: Agg*): GroupByField =
    new GroupByField(aggregationName.getOrElse("term"), field, None, Nil, selectables)
  def groupByField(field: String, selectables: Agg*): GroupByField = groupByField(None, field, selectables: _*)

  def groupByCaterogy(aggregationName: Option[String], categories: Map[String, QueryDef], selectables: Agg*) =
    new GroupByCategory(aggregationName.getOrElse("categories"), categories, selectables)

  private def nestedField(field: String, q: String => Query) = field match {
    case "_type" => q("relations")
    case _ =>
      field
        .split("\\.")
        .init
        .inits
        .toSeq
        .init
        .foldLeft(q(field)) {
          case (queryDef, subName) => nestedQuery(subName.mkString("."), queryDef).scoreMode(ScoreMode.None)
        }
  }

  implicit class SearchField(field: String) {
    private def convertValue(value: Any): Any = value match {
      case _: Enumeration#Value => value.toString
      case bd: BigDecimal       => bd.toDouble
      case _                    => value
    }
    def ~=(value: Any): QueryDef           = QueryDef(nestedField(field, termQuery(_, convertValue(value))))
    def ~=~(value: Any): QueryDef          = QueryDef(nestedField(field, wildcardQuery(_, convertValue(value))))
    def like(value: Any): QueryDef         = QueryDef(nestedField(field, matchQuery(_, convertValue(value))))
    def ~!=(value: Any): QueryDef          = not(QueryDef(nestedField(field, termQuery(_, convertValue(value)))))
    def ~<(value: Any): QueryDef           = QueryDef(nestedField(field, rangeQuery(_).lt(value.toString)))
    def ~>(value: Any): QueryDef           = QueryDef(nestedField(field, rangeQuery(_).gt(value.toString)))
    def ~<=(value: Any): QueryDef          = QueryDef(nestedField(field, rangeQuery(_).lte(value.toString)))
    def ~>=(value: Any): QueryDef          = QueryDef(nestedField(field, rangeQuery(_).gte(value.toString)))
    def ~<>(value: (Any, Any)): QueryDef   = QueryDef(nestedField(field, rangeQuery(_).gt(value._1.toString).lt(value._2.toString)))
    def ~=<>=(value: (Any, Any)): QueryDef = QueryDef(nestedField(field, rangeQuery(_).gte(value._1.toString).lte(value._2.toString)))
    def in(values: String*): QueryDef      = QueryDef(nestedField(field, termsQuery(_, values)))
  }

  def ofType(value: String): QueryDef                       = QueryDef(termQuery("relations", value))
  def withId(entityIds: String*): QueryDef                  = QueryDef(idsQuery(entityIds))
  def any: QueryDef                                         = QueryDef(matchAllQuery())
  def contains(field: String): QueryDef                     = QueryDef(nestedField(field, existsQuery))
  def or(queries: QueryDef*): QueryDef                      = or(queries)
  def or(queries: Iterable[QueryDef]): QueryDef             = QueryDef(boolQuery().should(queries.map(_.query)))
  def and(queries: QueryDef*): QueryDef                     = QueryDef(boolQuery().must(queries.map(_.query)))
  def and(queries: Iterable[QueryDef]): QueryDef            = QueryDef(boolQuery().must(queries.map(_.query)))
  def not(query: QueryDef): QueryDef                        = QueryDef(boolQuery().not(query.query))
  def child(childType: String, query: QueryDef): QueryDef   = QueryDef(hasChildQuery(childType, query.query, ScoreMode.None))
  def parent(parentType: String, query: QueryDef): QueryDef = QueryDef(hasParentQuery(parentType, query.query, score = false))
  def withParent(parent: BaseEntity): QueryDef              = withParent(parent.model.modelName, parent.id)

  def withParent(parentType: String, parentId: String): QueryDef =
    QueryDef(hasParentQuery(parentType, idsQuery(parentId), score = false))
  def string(queryString: String): QueryDef = QueryDef(query(queryString))
}
