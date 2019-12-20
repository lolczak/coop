package io.rebelapps.coop.execution

import cats.data.State
import io.rebelapps.coop.data.Coop

object stack {

  sealed trait Frame

  case class Ret(value: Any) extends Frame

  case class Continuation(f: Any => Coop[Any]) extends Frame

  type CallStack = List[Frame]

  val emptyStack = List.empty[Frame]

  def peek() = ???

  def push(frame: Frame): State[CallStack, Unit] = State.modify(tail => frame :: tail)

  def pop(): State[CallStack, Frame] = State(stack => stack.tail -> stack.head)

  def isEmpty(): State[CallStack, Boolean] = State.inspect(_.isEmpty)

  def pushStack(top: CallStack): State[CallStack, Unit] = State { stack => ((top ++ stack), ()) }

}
