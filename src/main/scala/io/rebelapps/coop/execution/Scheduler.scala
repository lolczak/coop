package io.rebelapps.coop.execution

import java.util
import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor

import io.rebelapps.coop.data.{Coop, Nop, Pure}
import io.rebelapps.coop.execution.stack.Frame

import scala.annotation.tailrec
import scala.concurrent.Future

object Scheduler {

  private val pool = new ScheduledThreadPoolExecutor(1, CoopThreadFactory)

  @volatile
  private var running: Vector[Fiber[Any]] = Vector.empty

  @volatile
  private var ready: Vector[Fiber[Any]] = Vector.empty

  @volatile
  private var suspended: Map[UUID, Fiber[Any]] = Map.empty

  private var channels: Map[UUID, SimpleChannel[Any]] = Map.empty

  def run[A](coroutine: Coop[A]): Future[A] = {
    val fiber = Fiber[Any](coroutine)
    pool.execute { () =>
      ready = fiber +: ready
      runLoop()
    }
    fiber.getFuture.asInstanceOf[Future[A]]
  }

  def runLoop(): Unit = {
    if (ready.nonEmpty) {
      val fiber = ready.last
      ready = ready.init
      running = fiber +: running

      @tailrec
      def go(fiber: Fiber[Any]): Result = {
        val maybeResult = stepping.step(exec)(fiber)
        maybeResult match {
          case Left(next)    => go(next)
          case Right(result) => result
        }
      }

      val result = go(fiber)

      result match {
        case Return(value) =>
          running = running.filterNot(_ eq fiber)
          fiber.complete(value)

        case Suspended(requestId) =>
          running = running.filterNot(_ eq fiber)
          val currentFiber = fiber
          suspended = suspended + (requestId -> currentFiber)

        case CreateFiber(coroutine) =>
          running = running.filterNot(_ eq fiber)
          val currentFiber = fiber.updateFlow(Pure(()))
          ready = currentFiber +: ready
          run(coroutine)
          pool.execute(() => runLoop())

        case ChannelCreation(size) =>
          val id = UUID.randomUUID()
          val channel = new SimpleChannel[Any](id, size)
          channels = channels + (id -> channel)
          running = running.filterNot(_ eq fiber)
          val currentFiber = fiber.updateFlow(Pure(channel))
          ready = currentFiber +: ready
          pool.execute(() => runLoop())

        case ChannelRead(id) =>
          val channel = channels(id)
          if (channel.queue.isEmpty && channel.writeWait.nonEmpty) {
            val (ch, (wFiber, wElem)) = channel.getFirstWaitingForWrite()
            channels = channels + (channel.id -> ch)
            ready = wFiber.asInstanceOf[Fiber[Any]] +: ready
            val currentFiber = fiber.updateFlow(Pure(wElem))
            running = running.filterNot(_ eq fiber)
            ready = currentFiber +: ready
            pool.execute(() => runLoop())
            pool.execute(() => runLoop())
          } else if (channel.queue.nonEmpty) {
            val (ch, elem) = channel.dequeue()
            channels = channels + (channel.id -> ch)
            val currentFiber = fiber.updateFlow(Pure(elem))
            running = running.filterNot(_ eq fiber)
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
            running = running.filterNot(_ eq fiber)
            val currentFiber = fiber.updateFlow(Nop)
            val ch = channel.waitForRead(currentFiber)
            channels = channels + (channel.id -> ch)
            pool.execute(() => runLoop())
          }

        case ChannelWrite(id, elem) =>
          val channel = channels(id)
          if (channel.readWait.nonEmpty) {
            running = running.filterNot(_ eq fiber)
            val currentFiber = fiber.updateFlow(Pure(()))
            ready = currentFiber +: ready
            val (ch, f) = channel.getFirstWaitingForRead()
            channels = channels + (channel.id -> ch)
            val newFiber = f.asInstanceOf[Fiber[Any]].updateFlow(Pure(elem))
            ready = newFiber +: ready
            pool.execute(() => runLoop())
          } else {
            if (channel.queue.size < channel.queueLength) {
              val currentChannel = channel.enqueue(elem)
              channels = channels + (channel.id -> currentChannel)
              running = running.filterNot(_ eq fiber)
              val currentFiber = fiber.updateFlow(Pure(()))
              ready = currentFiber +: ready
              pool.execute(() => runLoop())
            } else {
              val currentFiber = fiber.updateFlow(Pure(()))
              val currentChannel = channel.waitForWrite(elem, currentFiber)
              channels = channels + (channel.id -> currentChannel)
              running = running.filterNot(_ eq fiber)
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
          val currentFiber = fiber.updateFlow(Pure(r))
          ready = currentFiber +: ready
          runLoop()
        }
    }
    requestId
  }

  def shutdown(): Unit = pool.shutdown()

}
