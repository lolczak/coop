package io.rebelapps.coop.execution

import java.util
import java.util.UUID

import io.rebelapps.coop.data.Coop
import io.rebelapps.coop.execution.stack.Frame

import scala.concurrent.{Future, Promise}

/**
 * Represents execution of coroutine. It is a mutable object.
 *
 * @param nextOp
 * @tparam A
 */
class Fiber[A](var nextOp: Coop[A]) {

  val id = UUID.randomUUID()

  val stack: util.Stack[Frame] = new util.Stack()

  private val promise: Promise[A] = Promise[A]()

  def updateFlow(coop: Coop[A]): Fiber[A] = {
    nextOp = coop
    this
  }

  def complete(value: A): Fiber[A] = {
    promise.success(value)
    this
  }

  def getFuture: Future[A] = promise.future

  override def hashCode(): Int = id.hashCode()

  override def equals(obj: Any): Boolean =
    obj match {
      case that: Fiber[_] => this.id == that.id
      case _              => false
    }

}

object Fiber {

  def apply[A](coroutine: Coop[A]): Fiber[A] = new Fiber(coroutine)

}
