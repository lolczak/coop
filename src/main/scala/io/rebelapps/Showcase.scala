package io.rebelapps

import cats.implicits._
import io.rebelapps.coop.data.{Channel, Coop}
import io.rebelapps.coop.data.Coop._
import io.rebelapps.coop.execution.CoopScheduler

import scala.concurrent.Await
import scala.concurrent.duration._

object Showcase extends App {

  val BufSize = 0

  val GenMsg = 100

  def loop(inbound: Channel[Int], outbound: Channel[Int]): Coop[Unit] =
    (inbound.read() >>= ((i: Int) => outbound.write(i * 2))) >> loop(inbound, outbound)

  val fiber1 =
    for {
      value    <- pure { 123 }
      inbound  <- makeChan[Int](BufSize)
      outbound <- makeChan[Int](BufSize)
      _        <- spawn { effect { println("spawned") } >> loop(inbound, outbound) }
      _        <- effect(println("after spawn"))
      result1  <- (1 to GenMsg).toList.traverse(i => inbound.write(i) >> outbound.read())
      _        <- effect(println("consumed"))
      _        <- effect(println(s"sum:$result1"))
      result2  <- pure { result1.sum + 1 }
      next     <- async[Int] { cb => new Thread(() => { cb(Right(result2+1)) }).start() }
      next2    =  next + 1
      result   <- eval { next2 + 1 }
    } yield result

  val fiber2 =
    for {
      _       <- spawn { effect { println("spawned2") }  }
      value   <- pure { 23 }
      next     = value + 1
      next2   <- eval { next + 3 }
      result  <- async[Int] { cb => new Thread(() => { cb(Right(next2+1)) }).start() }
    } yield result

  println(fiber1)

  val future1 = CoopScheduler.run(fiber1 map (_ + 5))
  val future2 = CoopScheduler.run(fiber2 map(_ + 5))

  val result1 = Await.result(future1, 10 seconds)
  val result2 = Await.result(future2, 10 seconds)

  println(result1)
  println(result2)

  require(result1 == 10109)
  require(result2 == 33)
  CoopScheduler.shutdown()

  //backlog
  //todo *)refactoring
  //todo *)channel release
  //todo *)channel multiplexer
  //todo *)bifunctor
  //todo *)thread pool executor
  //todo *)use logger to debug
  //todo *)detect double locks

}
