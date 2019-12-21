package io.rebelapps.coop

import java.util.UUID

import io.rebelapps.coop.data.{Coop, DeferredValue}

package object execution {

  type RequestId = UUID

  sealed trait Result

  case class Return(value: Any) extends Result
  case class CreateFiber(coroutine: Coop[Any]) extends Result

  //todo refactor to suspension -> like Channel suspension
  sealed trait Termination extends Result

  case class ChannelCreation(size: Int, defVal: DeferredValue[SimpleChannel[Any]])         extends Termination
  case class ChannelRead(id: UUID, defVal: DeferredValue[Any])              extends Termination
  case class ChannelWrite(id: UUID, value: Any) extends Termination
  case class Suspended(requestId: RequestId)    extends Termination

  case class Fail(exception: Exception)

  type AsyncRunner = ((Either[Exception, Any] => Unit) => Unit) => RequestId

}
