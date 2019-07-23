package io.rebelapps

import cats.implicits._
import io.rebelapps.coop.data.Coroutine._
import io.rebelapps.coop.scheduler.Scheduler

import scala.concurrent.Await
import scala.concurrent.duration._

object TestMain extends App {

  val fiber1 =
    for {
      value   <- pure { 123 }
      next    <- async[Int] { cb => new Thread(() => { cb(Right(value+1)) }).start() }
      next2    = next + 1
      _       <- spawn { effect { println("spawned") }  }
      result  <- eval { next2 + 3 }
    } yield result

  val fiber2 =
    for {
      _       <- spawn { effect { println("spawned2") }  }
      value   <- pure { 23 }
      next     = value + 1
      next2   <- eval { next + 3 }
      result  <- async[Int] { cb => new Thread(() => { cb(Right(next2+1)) }).start() }
    } yield result

  val future1 = Scheduler.run(fiber1 map(_ + 5))
  val future2 = Scheduler.run(fiber2 map(_ + 5))

  val result1 = Await.result(future1, 10 seconds)
  val result2 = Await.result(future2, 10 seconds)

  println(result1)
  println(result2)

  Scheduler.shutdown()

  //backlog
  //todo 1)async handling
  //todo 2)channels
  //todo 3)bifunctor
  //todo 4)thread pool executor

}
