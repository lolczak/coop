package io.rebelapps.coop.scheduler

import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor

import cats.Monad
import cats.data.State
import io.rebelapps.coop.data.Coroutine
import io.rebelapps.coop.execution.RunLoop.{createCallStack, step}
import io.rebelapps.coop.execution.stack.{CallStack, Val, push}
import io.rebelapps.coop.execution.{Alive, AsyncRunner, Return, Suspended}

import scala.concurrent.{Future, Promise}

object Scheduler {

  private val pool = new ScheduledThreadPoolExecutor(1)

  private var running: Vector[Fiber[Any]] = Vector.empty
  private var ready: Vector[Fiber[Any]] = Vector.empty

  private var suspended: Map[UUID, Fiber[Any]] = Map.empty

  def run[A](coroutine: Coroutine[A]): Future[A] = {
    val stack = createCallStack(coroutine)
    val promise = Promise[Any]()
    val fiber = Fiber[Any](coroutine, stack, promise)
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
      val op = M.tailRecM(Alive)(_ => step(exec))

      val (currentStack, result) = op.run(fiber.callStack).value
      result match {
        case Return(value) =>
          running = running.filter(_ != fiber)
          fiber.promise.success(value)

        case Suspended(requestId) =>
          running = running.filter(_ != fiber)
          val currentFiber = fiber.copy(callStack = currentStack)
          suspended = suspended + (requestId -> currentFiber)
      }
    }
  }

  val exec: AsyncRunner = { go =>
    val requestId = UUID.randomUUID()
    go {
      case Left(ex) => throw ex
      case Right(r) =>
        pool.execute { () =>
          val fiber = suspended(requestId)
          suspended = suspended - requestId
          val currentStack = push(Val(r)).run(fiber.callStack).value._1
          val currentFiber = fiber.copy(callStack = currentStack)
          ready = currentFiber +: ready
          runLoop()
        }
    }
    requestId
  }

  def shutdown() = pool.shutdown()

}
