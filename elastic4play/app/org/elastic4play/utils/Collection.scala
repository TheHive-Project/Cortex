package org.elastic4play.utils

import scala.util.{Failure, Success, Try}

object Collection {

//  def distinctBy[A, B, Repr, That](xs: IterableOnce[A])(f: A => B)(implicit cbf: CanBuildFrom[Repr, A, That]): That = {
//    val builder = cbf(xs.repr)
//    val i       = xs.iterator
//    var set     = Set[B]()
//    while (i.hasNext) {
//      val o = i.next
//      val b = f(o)
//      if (!set(b)) {
//        set += b
//        builder += o
//      }
//    }
//    builder.result
//  }

//  def partitionTry[A, Repr, ThatA, ThatB](
//      xs: TraversableLike[Try[A], Repr]
//  )(implicit cbfa: CanBuildFrom[Repr, A, ThatA], cbfb: CanBuildFrom[Repr, Throwable, ThatB]): (ThatA, ThatB) = {
//    val aBuilder = cbfa()
//    val bBuilder = cbfb()
//    xs.foreach {
//      case Success(a) => aBuilder += a
//      case Failure(b) => bBuilder += b
//    }
//    (aBuilder.result(), bBuilder.result())
//  }

}
