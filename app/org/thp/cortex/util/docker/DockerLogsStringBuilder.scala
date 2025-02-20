package org.thp.cortex.util.docker

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame

class DockerLogsStringBuilder(var builder: StringBuilder) extends ResultCallback.Adapter[Frame] {
  override def onNext(item: Frame): Unit = {
    builder.append(new String(item.getPayload))
    super.onNext(item)
  }
}
