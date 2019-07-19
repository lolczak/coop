package io.rebelapps.coop

import java.util.concurrent.atomic.AtomicReference

package object execution {

  case object Alive

  sealed trait Result
  case class Return(value: Any) extends Result

  sealed trait Termination extends Result
  case class Suspended(ref: AtomicReference[Option[Any]]) extends Termination
  case class Fail(exception: Exception)

}
