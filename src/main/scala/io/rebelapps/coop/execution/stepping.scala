package io.rebelapps.coop.execution

import java.util

import cats.implicits._
import io.rebelapps.coop.data._
import io.rebelapps.coop.execution.stack._

import scala.annotation.tailrec

object stepping {

  /**
   * Evaluates one step of coroutine flow.
   *
   * @param exec
   * @param fiber
   * @return left when flow can be processed further, right if flow is suspended or terminated
   */
  def step(exec: AsyncRunner)(fiber: Fiber[Any]): Either[Fiber[Any], Result] = {
    fiber.coroutine match {
      case Pure(value) =>
        if (fiber.stack.empty()) {
          Return(value).asRight
        } else {
          val Continuation(f) = fiber.stack.pop()
          fiber.updateFlow(f(value)).asLeft
        }
      case FlatMap(fa, f) =>
        fiber.stack.push(Continuation(f))
        fiber.updateFlow(fa).asLeft

      case Map(coroutine, f) =>
        fiber.stack.push(Continuation(f andThen Pure.apply))
        fiber.updateFlow(coroutine).asLeft

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
        fiber.updateFlow(Pure(value)).asLeft

      case Spawn(coroutine) =>
        CreateFiber(coroutine).asRight

      case _ => throw new RuntimeException("imposible")
    }
  }

}
