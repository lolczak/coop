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
    tryAwakeCoroutine()
    fiber.getFuture.asInstanceOf[Future[A]]
  }

  private def tryAwakeCoroutine(count: Int = 1): Unit = {
    require(count > 0)
    (1 to count) foreach { _ => pool.execute(() => runLoop()) }
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
            runtimeCtxRef.update { ctx =>
              ctx
                .removeRunning(fiber)
                .addSuspended(fiber.id, fiber)
            }
            go {
              case Left(ex)      => throw ex
              case Right(result) =>
                pool.execute { () =>
                  runtimeCtxRef.update { ctx =>
                    ctx
                      .removeSuspended(fiber.id)
                      ._1
                      .enqueueReady(fiber)
                  }
                  deferred.fill(result)
                  tryAwakeCoroutine()
                }
            }

          case CreateFiber(coroutine) =>
            runtimeCtxRef.update { ctx =>
              ctx
                .removeRunning(fiber)
                .enqueueReady(fiber)
            }
            run(coroutine)
            tryAwakeCoroutine() //wakes creator

          case ChannelCreation(size, deferred) =>
            val id = UUID.randomUUID()
            val channel = new SimpleChannel[Any](id, size)
            runtimeCtxRef.update { ctx =>
              ctx
                .upsertChannel(id, channel)
                .removeRunning(fiber)
                .enqueueReady(fiber)
            }
            deferred.fill(channel)
            tryAwakeCoroutine() //wakes channel creator

          case ChannelRead(id, deferred) =>
            val channel = runtimeCtxRef.get().getChannel(id)
            if (channel.queue.isEmpty && channel.writeWait.nonEmpty) {
              val (ch, (wFiber, wElem)) = channel.getFirstWaitingForWrite()
              deferred.fill(wElem)
              runtimeCtxRef.update { ctx =>
                ctx
                  .upsertChannel(channel.id, ch)
                  .enqueueReady(wFiber)
                  .removeRunning(fiber)
                  .enqueueReady(fiber)
              }
              tryAwakeCoroutine(2) //wakes reader and writer
            } else if (channel.queue.nonEmpty) {
              val (ch, elem) = channel.dequeue()
              deferred.fill(elem)
              runtimeCtxRef.update { ctx =>
                ctx
                  .upsertChannel(channel.id, ch)
                  .removeRunning(fiber)
                  .enqueueReady(fiber)
              }
              if (channel.writeWait.nonEmpty) {
                val (ch2, (wFiber, wElem)) = ch.getFirstWaitingForWrite()
                val currentChannel = ch2.enqueue(wElem)
                runtimeCtxRef.update { ctx =>
                  ctx
                    .upsertChannel(channel.id, currentChannel)
                    .enqueueReady(wFiber)
                }
                tryAwakeCoroutine() //wakes writer
              }
              tryAwakeCoroutine() //wakes trader
            } else {
              val ch = channel.waitForRead(fiber, deferred)
              runtimeCtxRef.update { ctx =>
                ctx
                  .upsertChannel(channel.id, ch)
                  .removeRunning(fiber)
              }
              tryAwakeCoroutine() //wakes writer
            }

          case ChannelWrite(id, elem) =>
            val channel = runtimeCtxRef.get().getChannel(id)
            if (channel.readWait.nonEmpty) {
              val (ch, (f, defVal)) = channel.getFirstWaitingForRead()
              defVal.fill(elem)
              runtimeCtxRef.update { ctx =>
                ctx
                  .removeRunning(fiber)
                  .enqueueReady(fiber)
                  .upsertChannel(channel.id, ch)
                  .enqueueReady(f)
              }
            } else {
              if (channel.queue.size < channel.queueLength) {
                val currentChannel = channel.enqueue(elem)
                runtimeCtxRef.update { ctx =>
                  ctx
                    .upsertChannel(channel.id, currentChannel)
                    .removeRunning(fiber)
                    .enqueueReady(fiber)
                }
              } else {
                val currentChannel = channel.waitForWrite(elem, fiber)
                runtimeCtxRef.update { ctx =>
                  ctx
                    .upsertChannel(channel.id, currentChannel)
                    .removeRunning(fiber)
                }
              }
            }
            tryAwakeCoroutine()//wakes reader

          case _ =>
            println("imposible")
            throw new RuntimeException("imposible")
        }
    }

  }

  def shutdown(): Unit = pool.shutdown()

}
