package io.rebelapps.coop.execution

import cats.data.State
import shapeless.{:+:, CNil}

object stack {

  type Frame = Eval :+: Continuation :+: Val :+: CNil

  type CallStack = List[Frame]

  val emptyStack = List.empty[Frame]

  def push(frame: Frame): State[CallStack, Unit] = State.modify(tail => frame :: tail)

  def pop(): State[CallStack, Frame] = State(stack => stack.tail -> stack.head)

}
