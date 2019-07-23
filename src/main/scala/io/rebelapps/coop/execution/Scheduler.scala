package io.rebelapps.coop.execution

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ScheduledThreadPoolExecutor, ThreadFactory}

import cats.Monad
import cats.data.State
import io.rebelapps.coop.data.Coroutine
import io.rebelapps.coop.execution.stack.CallStack

import scala.concurrent.{Future, Promise}

object Scheduler {

  private val threadFactory = new ThreadFactory {

    private val count = new AtomicInteger(1)

    override def newThread(r: Runnable): Thread = {
      val thread = new Thread(r)
      thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
        override def uncaughtException(t: Thread, e: Throwable): Unit = println(s"Exception caught in thread ${thread.getName}: $e")
      })
      thread.setName(s"coop-${count.getAndAdd(1)}")
      thread
    }
  }

  private val pool = new ScheduledThreadPoolExecutor(1, threadFactory)

  @volatile
  private var running: Vector[Fiber[Any]] = Vector.empty

  @volatile
  private var ready: Vector[Fiber[Any]] = Vector.empty

  @volatile
  private var suspended: Map[UUID, Fiber[Any]] = Map.empty

  def run[A](coroutine: Coroutine[A]): Future[A] = {
    val promise = Promise[Any]()
    val fiber = Fiber[Any](coroutine, stack.emptyStack, promise)
    pool.execute { () =>
      ready = fiber +: ready
      runLoop()
    }
    promise.future.asInstanceOf[Future[A]]
  }

  def runLoop(): Unit = {
    if (ready.nonEmpty) {
      val fiber = ready.last
      ready = ready.init
      running = fiber +: running

      val M = Monad[State[CallStack, ?]]
      val op = M.tailRecM(fiber.coroutine)(c => RunLoop.step(exec)(c))

      val (currentStack, result) = op.run(fiber.callStack).value
      result match {
        case Return(value) =>
          running = running.filter(_ != fiber)
          fiber.promise.success(value)

        case _ =>
          println("imposible")
          throw new RuntimeException("imposible")

//        case Suspended(requestId) =>
//          running = running.filter(_ != fiber)
//          val currentFiber = fiber.copy(callStack = currentStack)
//          suspended = suspended + (requestId -> currentFiber)
//
//        case CreateFiber(coroutine) =>
//          running = running.filter(_ != fiber)
//          val currentFiber = fiber.copy(callStack = currentStack)
//          ready = currentFiber +: ready
//          run(coroutine)
//          pool.execute(() => runLoop())
      }
    } else {
      println("nothing to do")
    }
  }

  val exec: AsyncRunner = { go =>
    val requestId = UUID.randomUUID()
    go {
      case Left(ex) => throw ex
      case Right(r) => throw new RuntimeException("imposible")
      //        pool.execute { () =>
//          val fiber = suspended(requestId)
//          suspended = suspended - requestId
//          val currentStack = push(Val(r)).run(fiber.callStack).value._1
//          val currentFiber = fiber.copy(callStack = currentStack)
//          ready = currentFiber +: ready
//          runLoop()
//        }
    }
    requestId
  }

  def shutdown(): Unit = pool.shutdown()

}
