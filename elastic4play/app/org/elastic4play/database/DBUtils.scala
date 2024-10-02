package org.elastic4play.database

import play.api.libs.json._
import com.sksamuel.elastic4s.ElasticDsl.fieldSort
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.requests.searches.sort.{Sort, SortOrder}
import org.elastic4play.utils

object DBUtils {

  def sortDefinition(sortBy: Seq[String]): Seq[Sort] = {
    val byFieldList: Seq[(String, Sort)] = sortBy
      .map {
        case f if f.startsWith("+") => f.drop(1) -> fieldSort(f.drop(1)).order(SortOrder.ASC)
        case f if f.startsWith("-") => f.drop(1) -> fieldSort(f.drop(1)).order(SortOrder.DESC)
        case f if f.nonEmpty        => f         -> fieldSort(f)
      }
    // then remove duplicates
    // Same as : val fieldSortDefs = byFieldList.groupBy(_._1).map(_._2.head).values.toSeq
    utils
      .Collection
      .distinctBy(byFieldList)(_._1)
      .map(_._2) :+ fieldSort("_doc").order(SortOrder.DESC)
  }

  private def toJson(any: Any): JsValue =
    any match {
      case m: Map[_, _] => JsObject(m.toSeq.map { case (k, v) => k.toString -> toJson(v) })
      case s: String    => JsString(s)
      case l: Long      => JsNumber(l)
      case i: Int       => JsNumber(i)
      case d: Double    => JsNumber(d)
      case f: Float     => JsNumber(f)
      case b: Boolean   => JsBoolean(b)
      case null         => JsNull
      case s: Seq[_]    => JsArray(s.map(toJson))
    }

  /** Transform search hit into JsObject
    * This function parses hit source add _type, _routing, _parent, _id, _seqNo and _primaryTerm  attributes
    */
  def hit2json(hit: SearchHit): JsObject = {
    val id   = JsString(hit.id)
    val body = toJson(hit.sourceAsMap).as[JsObject]
    val (parent, model) = (body \ "relations" \ "parent").asOpt[JsString] match {
      case Some(p) => p      -> (body \ "relations" \ "name").as[JsString]
      case None    => JsNull -> (body \ "relations").as[JsString]
    }
    body - "relations" +
      ("_type"        -> model) +
      ("_routing"     -> hit.routing.fold(id)(JsString.apply)) +
      ("_parent"      -> parent) +
      ("_id"          -> id) +
      ("_seqNo"       -> JsNumber(hit.seqNo)) +
      ("_primaryTerm" -> JsNumber(hit.primaryTerm))
  }
}
