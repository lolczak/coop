package io.rebelapps.coop.execution

import java.util

import cats.implicits._
import io.rebelapps.coop.data._
import io.rebelapps.coop.execution.stack._

import scala.annotation.tailrec

object stepping {

  //  def step(exec: AsyncRunner)(coroutine: Coop[_]): State[CallStack, T] = {
  //todo make it tail recursive
  @tailrec
  def step(exec: AsyncRunner)(coroutine: Coop[_], stack: util.Stack[Frame]): Either[Coop[Any], Result] = {
    coroutine match {
      case Pure(value) =>
        if (stack.empty()) {
          Return(value).asRight
        } else {
          val Continuation(f) = stack.pop()
          f(value).asLeft
        }
      case FlatMap(fa, f) =>
        stack.push(Continuation(f))
        step(exec)(fa, stack)

      case Map(coroutine, f) =>
        stack.push(Continuation(f andThen Pure.apply))
        step(exec)(coroutine, stack)

      case Async(go) =>
        val reqId = exec(go)
        Suspended(reqId).asRight

      case CreateChannel(size) =>
        ChannelCreation(size).asRight

      case ReadChannel(id) =>
        ChannelRead(id).asRight

      case WriteChannel(id, elem) =>
        ChannelWrite(id, elem).asRight

      case Eval(thunk) =>
        val value = thunk()
        Pure(value).asLeft

      case Spawn(coroutine) =>
        CreateFiber(coroutine).asRight

      case _ => throw new RuntimeException("imposible")
    }
  }

}
