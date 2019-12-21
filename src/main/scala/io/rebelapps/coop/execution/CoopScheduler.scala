package io.rebelapps.coop.execution

import java.util.UUID
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicReference

import io.rebelapps.coop.data.Coop
import io.rebelapps.coop.execution.atomic._

import scala.annotation.tailrec
import scala.concurrent.Future

object CoopScheduler {

  private val pool = new ScheduledThreadPoolExecutor(1, CoopThreadFactory)

  private val runtimeCtxRef = new AtomicReference[RuntimeCtx](new RuntimeCtx)

  def run[A](coroutine: Coop[A]): Future[A] = {
    val fiber = Fiber[Any](coroutine)
    runtimeCtxRef.update(_.enqueueReady(fiber))
    tryAwakeOneCoroutine()
    fiber.getFuture.asInstanceOf[Future[A]]
  }

  private def tryAwakeOneCoroutine(): Unit = pool.execute(() => runLoop())

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
            val requestId = UUID.randomUUID()
            runtimeCtxRef.update(_.removeRunning(fiber))
            runtimeCtxRef.update(_.addSuspended(requestId, fiber))
            go {
              case Left(ex)      => throw ex
              case Right(result) =>
                pool.execute { () =>
                  val fiber = runtimeCtxRef.modify_(_.removeSuspended(requestId))
                  runtimeCtxRef.update(_.enqueueReady(fiber))
                  deferred.fill(result)
                  tryAwakeOneCoroutine()
                }
            }

          case CreateFiber(coroutine) =>
            runtimeCtxRef.update(_.removeRunning(fiber))
            runtimeCtxRef.update(_.enqueueReady(fiber))
            run(coroutine)
            tryAwakeOneCoroutine() //wakes creator

          case ChannelCreation(size, deferred) =>
            val id = UUID.randomUUID()
            val channel = new SimpleChannel[Any](id, size)
            runtimeCtxRef.update(_.upsertChannel(id, channel))
            runtimeCtxRef.update(_.removeRunning(fiber))
            runtimeCtxRef.update(_.enqueueReady(fiber))
            deferred.fill(channel)
            tryAwakeOneCoroutine() //wakes channel creator

          case ChannelRead(id, deferred) =>
            val channel = runtimeCtxRef.get().getChannel(id)
            if (channel.queue.isEmpty && channel.writeWait.nonEmpty) {
              val (ch, (wFiber, wElem)) = channel.getFirstWaitingForWrite()
              runtimeCtxRef.update(_.upsertChannel(channel.id, ch))
              runtimeCtxRef.update(_.enqueueReady(wFiber))
              deferred.fill(wElem)
              runtimeCtxRef.update(_.removeRunning(fiber))
              runtimeCtxRef.update(_.enqueueReady(fiber))
              tryAwakeOneCoroutine() //wakes reader
              tryAwakeOneCoroutine() //wakes writer
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
                runtimeCtxRef.update(_.enqueueReady(wFiber))
                tryAwakeOneCoroutine() //wakes writer
              }
              tryAwakeOneCoroutine() //wakes trader
            } else {
              runtimeCtxRef.update(_.removeRunning(fiber))
              val ch = channel.waitForRead(fiber, deferred)
              runtimeCtxRef.update(_.upsertChannel(channel.id, ch))
              tryAwakeOneCoroutine() //wakes writer
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
            } else {
              if (channel.queue.size < channel.queueLength) {
                val currentChannel = channel.enqueue(elem)
                runtimeCtxRef.update(_.upsertChannel(channel.id, currentChannel))
                runtimeCtxRef.update(_.removeRunning(fiber))
                runtimeCtxRef.update(_.enqueueReady(fiber))
              } else {
                val currentChannel = channel.waitForWrite(elem, fiber)
                runtimeCtxRef.update(_.upsertChannel(channel.id, currentChannel))
                runtimeCtxRef.update(_.removeRunning(fiber))
              }
            }
            tryAwakeOneCoroutine()//wakes reader

          case _ =>
            println("imposible")
            throw new RuntimeException("imposible")
        }
    }

  }

  def shutdown(): Unit = pool.shutdown()

}
