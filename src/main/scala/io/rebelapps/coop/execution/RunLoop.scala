package io.rebelapps.coop.execution

import cats.Monad
import cats.data.State
import io.rebelapps.coop.data._
import io.rebelapps.coop.execution.stack._
import cats.implicits._

import scala.annotation.tailrec

object RunLoop {

  private val M = Monad[State[CallStack, ?]]
  import M._

  def createCallStack(coroutine: Coroutine[Any]): CallStack = {
    //todo tail rec version
    def loop(coroutine: Coroutine[Any]): State[CallStack, Unit]  =
      coroutine match {
        case Map(fa: Coroutine[Any], f: (Any => Any)) =>
          push(Continuation(f andThen Pure.apply)) >> loop(fa)

        case FlatMap(fa: Coroutine[Any], f: (Any => Coroutine[Any])) =>
          push(Continuation(f)) >> loop(fa)

        case Pure(value) =>
          push(Return(value))

        case Eval(thunk) =>
          push(Evaluation(thunk))

        case _ => State.set(emptyStack) //todo async, raise error
      }

    loop(coroutine)
      .run(emptyStack)
      .value
      ._1
  }

  def step(): State[CallStack, Option[Any]] =
    for {
      frame  <- pop()
      result <- frame match {
        case Return(value) =>
          def cont(): State[CallStack, Option[Any]] = {
            for {
              next            <- pop()
              Continuation(f)  = next
              _               <- pushStack(createCallStack(f(value)))
            } yield None
          }
          ifM(isEmpty())(State.pure(Some(value): Option[Any]), cont())

        case Evaluation(thunk) =>
          val value = thunk()
          ifM(isEmpty())(State.pure(Some(value): Option[Any]), push(Return(value)) >> State.pure(None: Option[Any]))

        case Continuation(f) =>
          throw new RuntimeException("Impossible")

      }
    } yield result

  def go[A](coroutine: Coroutine[A]): A = {
    val initialStack = createCallStack(coroutine)

    val go = () => step().map(_.toRight(()))

    val op = M.tailRecM(())(_ => go())

    op
      .run(initialStack)
      .value
      ._2
      .asInstanceOf[A]
  }


}
