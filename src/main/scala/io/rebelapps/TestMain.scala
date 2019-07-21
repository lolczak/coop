package io.rebelapps

import cats.implicits._
import io.rebelapps.coop.data.Coroutine._
import io.rebelapps.coop.execution.RunLoop
import io.rebelapps.coop.scheduler.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration._

object TestMain extends App {

  val fiber =
    for {
      value   <- pure { 123 }
      next     = value + 1
      next2   <- eval { next + 3 }
      result  <- async[Int] { cb => new Thread(() => { cb(Right(next2+1)) }).start() }
    } yield result

  println(fiber map(_ + 5))

  val callStack = RunLoop.createCallStack(fiber)

  callStack foreach println

  val future = Scheduler.run(fiber map(_ + 5))

  val result = Await.result(future, 10 seconds)

  println(result)

  //backlog
  //todo 1)async handling
  //todo 2)channels
  //todo 3)bifunctor
  //todo 4)thread pool executor

}
