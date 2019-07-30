package io.rebelapps.coop.execution

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ScheduledThreadPoolExecutor, ThreadFactory}

import cats.Monad
import cats.data.State
import io.rebelapps.coop.data.{Channel, Coroutine, Nop, Pure}
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

  private var channels: Map[UUID, SimpleChannel[Any]] = Map.empty

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
      val op = M.tailRecM(fiber.coroutine)(stepping.step(exec)(_))

      val (currentStack, result) = op.run(fiber.callStack).value
      result match {
        case Return(value) =>
          running = running.filter(_ != fiber)
          fiber.promise.success(value)

        case Suspended(requestId) =>
          running = running.filter(_ != fiber)
          val currentFiber = fiber.copy(callStack = currentStack)
          suspended = suspended + (requestId -> currentFiber)

        case CreateFiber(coroutine) =>
          running = running.filter(_ != fiber)
          val currentFiber = fiber.copy(callStack = currentStack, coroutine = Pure(()))
          ready = currentFiber +: ready
          run(coroutine)
          pool.execute(() => runLoop())

        case ChannelCreation(size) =>
          val id = UUID.randomUUID()
          val channel = new SimpleChannel[Any](id, size)
          channels = channels + (id -> channel)
          running = running.filter(_ != fiber)
          val currentFiber = fiber.copy(callStack = currentStack, coroutine = Pure(channel))
          ready = currentFiber +: ready
          pool.execute(() => runLoop())

        case ChannelRead(id) =>
          val channel = channels(id)
          if (channel.queue.nonEmpty) {
            val (ch, elem) = channel.dequeue()
            channels = channels + (channel.id -> ch)
            val currentFiber = fiber.copy(callStack = currentStack, coroutine = Pure(elem))
            running = running.filter(_ != fiber)
            ready = currentFiber +: ready
            if (channel.writeWait.nonEmpty) {
              val (ch2, (wFiber, wElem)) = ch.getFirstWaitingForWrite()
              val currentChannel = ch2.enqueue(wElem)
              channels = channels + (channel.id -> currentChannel)
              ready = wFiber.asInstanceOf[Fiber[Any]] +: ready
              pool.execute(() => runLoop())
            }
            pool.execute(() => runLoop())
          } else {
            running = running.filter(_ != fiber)
            val currentFiber = fiber.copy(callStack = currentStack, coroutine = Nop)
            val ch = channel.waitForRead(currentFiber)
            channels = channels + (channel.id -> ch)
            pool.execute(() => runLoop())
          }

        case ChannelWrite(id, elem) =>
          val channel = channels(id)
          if (channel.readWait.nonEmpty) {
            running = running.filter(_ != fiber)
            val currentFiber = fiber.copy(callStack = currentStack, coroutine = Pure(()))
            ready = currentFiber +: ready
            val (ch, f) = channel.getFirstWaitingForRead()
            channels = channels + (channel.id -> ch)
            val newFiber = f.asInstanceOf[Fiber[Any]].copy(coroutine = Pure(elem))
            ready = newFiber +: ready
            pool.execute(() => runLoop())
          } else {
            if (channel.queue.size < channel.queueSize) {
              val currentChannel = channel.enqueue(elem)
              channels = channels + (channel.id -> currentChannel)
              running = running.filter(_ != fiber)
              val currentFiber = fiber.copy(callStack = currentStack, coroutine = Pure(()))
              ready = currentFiber +: ready
              pool.execute(() => runLoop())
            } else {
              val currentFiber = fiber.copy(callStack = currentStack, coroutine = Pure(()))
              val currentChannel = channel.waitForWrite(elem, currentFiber)
              channels = channels + (channel.id -> currentChannel)
              running = running.filter(_ != fiber)
              pool.execute(() => runLoop()) //?
            }
          }

        case _ =>
          println("imposible")
          throw new RuntimeException("imposible")
      }
    } else {
      println("nothing to do")
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
          val currentFiber = fiber.copy(coroutine = Pure(r))
          ready = currentFiber +: ready
          runLoop()
        }
    }
    requestId
  }

  def shutdown(): Unit = pool.shutdown()

}
