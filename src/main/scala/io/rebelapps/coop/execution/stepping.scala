package io.rebelapps.coop.execution

import cats.implicits._
import io.rebelapps.coop.data._
import io.rebelapps.coop.execution.stack._

object stepping {

  /**
   * Evaluates one step of coroutine flow.
   *
   * @param fiber
   * @return left when flow can be processed further, right if flow is suspended or terminated
   */
  def step(fiber: Fiber[Any]): Either[Fiber[Any], Exit] = {
    fiber.nextOp match {
      case defVal: DeferredValue[_] if defVal.isEmpty =>
        throw new IllegalStateException("Cannot eval empty deferred")

      case defVal: DeferredValue[_] =>
        val value = defVal.value
        if (fiber.stack.empty()) {
          Finished(value).asRight
        } else {
          val Continuation(f) = fiber.stack.pop()
          fiber.updateFlow(f(value)).asLeft
        } //todo remove duplication

      case Pure(value) =>
        if (fiber.stack.empty()) {
          Finished(value).asRight
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
        val defVal = DeferredValue[Any]
        fiber.updateFlow(defVal)
        AsyncWait(go, defVal).asRight

      case CreateChannel(size) =>
        val defVal = DeferredValue[SimpleChannel[Any]]
        fiber.updateFlow(defVal)
        SuspendedOnChannelCreation(size, defVal).asRight

      case ReadChannel(id) =>
        val defVal = DeferredValue[Any]
        fiber.updateFlow(defVal)
        SuspendedOnChannelRead(id, defVal).asRight

      case WriteChannel(id, elem) =>
        fiber.updateFlow(Pure(()))
        SuspendedOnChannelWrite(id, elem).asRight

      case Eval(thunk) =>
        val value = thunk()
        fiber.updateFlow(Pure(value)).asLeft

      case Spawn(coroutine) =>
        fiber.updateFlow(Pure(()))
        SuspendedOnCoroutineCreation(coroutine).asRight

      case _ => throw new RuntimeException("imposible")
    }
  }

}
