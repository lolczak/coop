package io.rebelapps.coop

import io.rebelapps.coop.data.Coroutine
import shapeless.:+:

package object go {

  case class Val(value: Any)

  case class Eval(thunk: () => Any)

  case class Subroutine(f: Any => Coroutine[Any])

  type Frame = Val :+: Eval :+: Subroutine

  type CallStack = List[Frame]

  def push(frame: Frame)

}
