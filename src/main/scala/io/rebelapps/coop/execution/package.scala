package io.rebelapps.coop

import java.util.UUID

package object execution {

  type RequestId = UUID

  case object Alive
  sealed trait Result

  case class Return(value: Any) extends Result
  sealed trait Termination extends Result
  case class Suspended(requestId: RequestId) extends Termination

  case class Fail(exception: Exception)

  type AsyncRunner = ((Either[Exception, Any] => Unit) => Unit) => RequestId

}
