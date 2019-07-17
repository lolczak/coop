package io.rebelapps

import cats.implicits._
import io.rebelapps.coop.data.Coroutine.{delay, pure}
import io.rebelapps.coop.execution.RunLoop

object TestMain extends App {

  val fiber =
    for {
      value  <- pure { 123 }
      next = value + 1
      result <- delay { next + 3 }
    } yield result

  println(fiber map(_ - 5))

  val callStack = RunLoop.createCallStack(fiber)

  callStack foreach println

  val result = RunLoop.go(fiber map(_ - 5))

  println(result)

}
