package io.rebelapps.coop

import java.util.UUID

import io.rebelapps.coop.data.Coroutine

package object execution {

  type RequestId = UUID

  sealed trait Result

  case class Return(value: Any) extends Result
  case class CreateFiber(coroutine: Coroutine[Any]) extends Result

  sealed trait Termination extends Result
  case class Suspended(requestId: RequestId) extends Termination

  case class Fail(exception: Exception)

  type AsyncRunner = ((Either[Exception, Any] => Unit) => Unit) => RequestId

}
