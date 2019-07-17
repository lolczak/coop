package io.rebelapps.coop.execution

import cats.data.State
import io.rebelapps.coop.data._
import io.rebelapps.coop.execution.stack._
import shapeless._
import cats.implicits._

object RunLoop {

//  def step[A](coroutine: Coroutine[A])

  def createCallStack(coroutine: Coroutine[Any]): CallStack = {
    def loop(coroutine: Coroutine[Any]): State[CallStack, Unit]  =
      coroutine match {
        case Map(fa: Coroutine[Any], f: (Any => Any)) =>
          push(Coproduct[Frame](Continuation(f andThen Pure.apply))) >> loop(fa)

        case FlatMap(fa: Coroutine[Any], f: (Any => Coroutine[Any])) =>
          push(Coproduct[Frame](Continuation(f))) >> loop(fa)

        case Pure(value) =>
          push(Coproduct[Frame](Val(value)))

        case Delay(thunk) =>
          push(Coproduct[Frame](Eval(thunk)))

        case _ => State.set(emptyStack)
      }

    loop(coroutine)
      .run(emptyStack)
      .value
      ._1
  }


}
