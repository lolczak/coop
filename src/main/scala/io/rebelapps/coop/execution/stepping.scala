package io.rebelapps.coop.execution

import cats.Monad
import cats.data.State
import io.rebelapps.coop.data._
import io.rebelapps.coop.execution.stack._
import cats.implicits._

object stepping {

  private val M = Monad[State[CallStack, ?]]
  import M._

  private type T = Either[Coroutine[Any], Result]

  def step(exec: AsyncRunner)(coroutine: Coroutine[_]): State[CallStack, T] = {
    coroutine match {
      case Pure(value) =>
        ifM(isEmpty())(
          State.pure[CallStack, T](Return(value).asRight),
          for {
            result          <- pop()
            Continuation(f)  = result
          } yield f(value).asLeft
        )

      case FlatMap(fa, f) =>
        push(Continuation(f)) >> step(exec)(fa)

      case Map(coroutine, f) =>
        push(Continuation(f andThen Pure.apply)) >> step(exec)(coroutine)

      case Async(go) =>
        val reqId = exec(go)
        State.pure[CallStack, T](Suspended(reqId).asRight)

      case CreateChannel(size) =>
        State.pure[CallStack, T](ChannelCreation(size).asRight)

      case ReadChannel(id) =>
        State.pure[CallStack, T](ChannelRead(id).asRight)

      case WriteChannel(id, elem) =>
        State.pure[CallStack, T](ChannelWrite(id, elem).asRight)

      case Eval(thunk) =>
        val value = thunk()
        State.pure[CallStack, T](Pure(value).asLeft)

      case Spawn(coroutine) =>
        State.pure[CallStack, T](CreateFiber(coroutine).asRight)

      case _ => throw new RuntimeException("imposible")
    }
  }

}
