package io.rebelapps.coop.execution

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import cats.Monad
import cats.data.State
import io.rebelapps.coop.data._
import io.rebelapps.coop.execution.stack._
import cats.implicits._

object RunLoop {

  private val M = Monad[State[CallStack, ?]]
  import M._

  def createCallStack(coroutine: Coroutine[Any]): CallStack = {
    def go(current: Coroutine[Any]): State[CallStack, Either[Coroutine[Any], Unit]]  =
      current match {
        case Map(fa: Coroutine[Any], f: (Any => Any)) =>
          push(Continuation(f andThen Pure.apply)) >> State.pure(fa.asLeft)

        case FlatMap(fa: Coroutine[Any], f: (Any => Coroutine[Any])) =>
          push(Continuation(f)) >> State.pure(fa.asLeft)

        case Pure(value) =>
          push(Val(value)).map(_.asRight)

        case Eval(thunk) =>
          push(Evaluation(thunk)).map(_.asRight)

        case Async(go) =>
          push(AsyncCall(go)).map(_.asRight)

        case _ => State.set(emptyStack).map(_.asRight) //todo raise error
      }

    val op = M.tailRecM(coroutine)(go)
    op
      .run(emptyStack)
      .value
      ._1
  }

  def step(exec: AsyncRunner): State[CallStack, Either[Alive.type, Result]] =
    for {
      frame  <- pop()
      result <- frame match {
        case Val(value) =>
          def cont(): State[CallStack, Either[Alive.type, Result]] = {
            for {
              next            <- pop()
              Continuation(f)  = next
              _               <- pushStack(createCallStack(f(value)))
            } yield Alive.asLeft
          }
          ifM(isEmpty())(State.pure((Return(value): Result).asRight[Alive.type]), cont())

        case Evaluation(thunk) =>
          val value = thunk()
          ifM(isEmpty())(State.pure((Return(value): Result).asRight[Alive.type]), push(Val(value)) >> State.pure(Alive.asLeft[Result]))

        case AsyncCall(go) =>
          val reqId = exec(go)
          State.pure[CallStack, Either[Alive.type, Result]]((Suspended(reqId): Result).asRight[Alive.type])

        case Continuation(f) =>
          throw new RuntimeException("Impossible")

      }
    } yield result

  def run[A](coroutine: Coroutine[A]): A = {
    var stack = createCallStack(coroutine)

    val ref = new AtomicReference[Option[Any]](None)

    val exec: AsyncRunner = { go =>
      go {
        case Left(ex) => throw ex
        case Right(r) => ref.set(Some(r))
      }
      UUID.randomUUID()
    }

    do {
      val op = M.tailRecM(Alive)(_ => step(exec))

      val (currentStack, result) = op .run(stack).value
      result match {
        case Return(value) =>
          return value.asInstanceOf[A]

        case Suspended(requestId) =>
          while (ref.get().isEmpty) {
            Thread.sleep(100)
          }
          val newStack = push(Val(ref.get().get)).run(currentStack).value._1
          stack = newStack
      }

    } while (true)
    ().asInstanceOf[A]
  }


}
