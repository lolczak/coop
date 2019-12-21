package io.rebelapps.coop.execution

import java.util

import io.rebelapps.coop.data.Coop
import io.rebelapps.coop.execution.stack.Frame

import scala.concurrent.{Future, Promise}

/**
 * Represents execution of coroutine.
 *
 * @param coroutine
 * @tparam A
 */
class Fiber[A](var coroutine: Coop[A]) {

  val stack: util.Stack[Frame] = new util.Stack()

  private val promise: Promise[A] = Promise[A]()

  def updateFlow(coop: Coop[A]): Fiber[A] = {
    coroutine = coop
    this
  }

  def complete(value: A): Fiber[A] = {
    promise.success(value)
    this
  }

  def getFuture: Future[A] = promise.future

}

object Fiber {

  def apply[A](coroutine: Coop[A]): Fiber[A] = new Fiber(coroutine)

}
