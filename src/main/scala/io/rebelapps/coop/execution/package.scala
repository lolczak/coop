package io.rebelapps.coop

import io.rebelapps.coop.data.Coroutine

package object execution {

  case class Val(value: Any)

  case class Eval(thunk: () => Any)

  case class Continuation(f: Any => Coroutine[Any])

}
