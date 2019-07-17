package io.rebelapps.coop.execution

import cats.Monad
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
          push(Continuation(f andThen Pure.apply)) >> loop(fa)

        case FlatMap(fa: Coroutine[Any], f: (Any => Coroutine[Any])) =>
          push(Continuation(f)) >> loop(fa)

        case Pure(value) =>
          push(Val(value))

        case Delay(thunk) =>
          push(Eval(thunk))

        case _ => State.set(emptyStack)
      }

    loop(coroutine)
      .run(emptyStack)
      .value
      ._1
  }

  def go[A](coroutine: Coroutine[A]): A = {
    val initialStack = createCallStack(coroutine)

    def loop(maybeValue: Option[Any]): State[CallStack, Option[Any]] =
      for {
        frame <- pop()
        result <- frame match {
          case Val(value) =>
            Monad[State[CallStack, ?]].ifM(isEmpty())(State.pure(Some(value): Option[Any]), loop(Some(value)))

          case Eval(thunk) =>
            val value = thunk()
            Monad[State[CallStack, ?]].ifM(isEmpty())(State.pure(Some(value): Option[Any]), loop(Some(value)))

          case Continuation(f) =>
            val result = f(maybeValue.get)
            val top = createCallStack(result)
            pushStack(top) >> loop(None)
        }
      } yield result

    loop(None)
      .run(initialStack)
      .value
      ._2
      .get
      .asInstanceOf[A]
  }


}
