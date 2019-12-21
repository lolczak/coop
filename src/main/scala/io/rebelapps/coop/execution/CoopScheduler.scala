package io.rebelapps.coop.execution

import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference

import io.rebelapps.coop.data.{Coop, DeferredValue, Nop, Pure}
import io.rebelapps.coop.execution.atomic._

import scala.annotation.tailrec
import scala.concurrent.Future

object CoopScheduler {

  private val pool = new ScheduledThreadPoolExecutor(1, CoopThreadFactory)

  private val runtimeCtxRef = new AtomicReference[RuntimeCtx](new RuntimeCtx)

  def run[A](coroutine: Coop[A]): Future[A] = {
    val fiber = Fiber[Any](coroutine)
    pool.execute { () =>
      runtimeCtxRef.update(_.enqueueReady(fiber))
      runLoop()
    }
    fiber.getFuture.asInstanceOf[Future[A]]
  }

  def runLoop(): Unit = {
    runtimeCtxRef.modifyWhen(_.hasReadyFibers()) { _.moveFirstReadyToRunning() } match {
      case None =>
        println("nothing to do")

      case Some((_, fiber)) =>
        @tailrec
        def go(fiber: Fiber[Any]): Result = {
          val maybeResult = stepping.step(fiber)
          maybeResult match {
            case Left(next)    => go(next)
            case Right(result) => result
          }
        }

        val result = go(fiber)

        result match {
          case Return(value) =>
            runtimeCtxRef.update(_.removeRunning(fiber))
            fiber.complete(value)

          case Suspended(go, deferred) =>
            val requestId = exec(deferred)(go)
            runtimeCtxRef.update(_.removeRunning(fiber))
            runtimeCtxRef.update(_.addSuspended(requestId, fiber))

          case CreateFiber(coroutine) =>
            runtimeCtxRef.update(_.removeRunning(fiber))
            runtimeCtxRef.update(_.enqueueReady(fiber))
            run(coroutine)
            pool.execute(() => runLoop())

          case ChannelCreation(size, deferred) =>
            val id = UUID.randomUUID()
            val channel = new SimpleChannel[Any](id, size)
            runtimeCtxRef.update(_.upsertChannel(id, channel))
            runtimeCtxRef.update(_.removeRunning(fiber))
            runtimeCtxRef.update(_.enqueueReady(fiber))
            deferred.fill(channel)
            pool.execute(() => runLoop())

          case ChannelRead(id, deferred) =>
            val channel = runtimeCtxRef.get().getChannel(id)
            if (channel.queue.isEmpty && channel.writeWait.nonEmpty) {
              val (ch, (wFiber, wElem)) = channel.getFirstWaitingForWrite()
              runtimeCtxRef.update(_.upsertChannel(channel.id, ch))
              runtimeCtxRef.update(_.enqueueReady(wFiber.asInstanceOf[Fiber[Any]]))
              deferred.fill(wElem)
              runtimeCtxRef.update(_.removeRunning(fiber))
              runtimeCtxRef.update(_.enqueueReady(fiber))
              pool.execute(() => runLoop())
              pool.execute(() => runLoop())
            } else if (channel.queue.nonEmpty) {
              val (ch, elem) = channel.dequeue()
              runtimeCtxRef.update(_.upsertChannel(channel.id, ch))
              deferred.fill(elem)
              runtimeCtxRef.update(_.removeRunning(fiber))
              runtimeCtxRef.update(_.enqueueReady(fiber))
              if (channel.writeWait.nonEmpty) {
                val (ch2, (wFiber, wElem)) = ch.getFirstWaitingForWrite()
                val currentChannel = ch2.enqueue(wElem)
                runtimeCtxRef.update(_.upsertChannel(channel.id, currentChannel))
                runtimeCtxRef.update(_.enqueueReady(wFiber.asInstanceOf[Fiber[Any]]))
                pool.execute(() => runLoop())
              }
              pool.execute(() => runLoop())
            } else {
              runtimeCtxRef.update(_.removeRunning(fiber))
              val ch = channel.waitForRead(fiber, deferred)
              runtimeCtxRef.update(_.upsertChannel(channel.id, ch))
              pool.execute(() => runLoop())
            }

          case ChannelWrite(id, elem) =>
            val channel = runtimeCtxRef.get().getChannel(id)
            if (channel.readWait.nonEmpty) {
              runtimeCtxRef.update(_.removeRunning(fiber))
              runtimeCtxRef.update(_.enqueueReady(fiber))
              val (ch, (f, defVal)) = channel.getFirstWaitingForRead()
              runtimeCtxRef.update(_.upsertChannel(channel.id, ch))
              defVal.fill(elem)
              runtimeCtxRef.update(_.enqueueReady(f))
              pool.execute(() => runLoop())//todo move outside ifelse
            } else {
              if (channel.queue.size < channel.queueLength) {
                val currentChannel = channel.enqueue(elem)
                runtimeCtxRef.update(_.upsertChannel(channel.id, currentChannel))
                runtimeCtxRef.update(_.removeRunning(fiber))
                runtimeCtxRef.update(_.enqueueReady(fiber))
                pool.execute(() => runLoop())//todo move outside ifelse
              } else {
                val currentChannel = channel.waitForWrite(elem, fiber)
                runtimeCtxRef.update(_.upsertChannel(channel.id, currentChannel))
                runtimeCtxRef.update(_.removeRunning(fiber))
                pool.execute(() => runLoop()) //todo move outside ifelse
              }
            }

          case _ =>
            println("imposible")
            throw new RuntimeException("imposible")
        }
    }

  }

  type AsyncRunner = DeferredValue[Any] => ((Either[Exception, Any] => Unit) => Unit) => RequestId

  val exec: AsyncRunner = defVal => { go =>
    val requestId = UUID.randomUUID()
    go {
      case Left(ex) => throw ex
      case Right(result) =>
        pool.execute { () =>
          val fiber = runtimeCtxRef.modify_(_.removeSuspended(requestId))
          runtimeCtxRef.update(_.enqueueReady(fiber))
          defVal.fill(result)
          runLoop()
        }
    }
    requestId
  }

  def shutdown(): Unit = pool.shutdown()

}
