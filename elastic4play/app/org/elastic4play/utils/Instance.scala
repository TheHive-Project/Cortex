package org.elastic4play.utils

import java.rmi.dgc.VMID
import java.util.concurrent.atomic.AtomicInteger

import play.api.mvc.RequestHeader

object Instance {
  val id: String                                   = (new VMID).toString
  private val counter                              = new AtomicInteger(0)
  def getRequestId(request: RequestHeader): String = s"$id:${request.id}"
  def getInternalId: String                        = s"$id::${counter.incrementAndGet}"
}
