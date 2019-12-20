package io.rebelapps.coop.execution

import io.rebelapps.coop.data.Coop

object stack {

  sealed trait Frame
  case class Ret(value: Any) extends Frame
  case class Continuation(f: Any => Coop[Any]) extends Frame

}
