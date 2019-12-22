package io.rebelapps.coop.execution

import java.util.UUID
import java.util.concurrent.{ScheduledThreadPoolExecutor, Semaphore}
import java.util.concurrent.atomic.AtomicReference

import io.rebelapps.coop.data.Coop
import io.rebelapps.coop.execution.atomic._

import scala.annotation.tailrec
import scala.concurrent.Future

class CoopScheduler(poolSize: Int) {

  private val pool = new ScheduledThreadPoolExecutor(poolSize, CoopThreadFactory)

  private val runtimeCtxRef = new AtomicReference[RuntimeCtx](new RuntimeCtx)

  private val resumeCount = new Semaphore(0)

  @volatile
  private var running = false

  def start(): Unit = {
    running = true
    (1 to poolSize) foreach { _ => pool.execute(() => runLoop()) }
  }

  def run[A](coroutine: Coop[A]): Future[A] = {
    val fiber = Fiber[Any](coroutine)
    runtimeCtxRef.update(_.enqueueReady(fiber))
    tryResume()
    fiber.getFuture.asInstanceOf[Future[A]]
  }

  private def tryResume(count: Int = 1): Unit = {
    require(count > 0)
    resumeCount.release(count)
  }

  def runLoop(): Unit = {
    do {
      resumeCount.acquire()
      if (!running) return
      runtimeCtxRef.modifyWhen(_.hasReadyFibers()) { _.moveFirstReadyToRunning() } match {
        case None =>
          println("nothing to do")

        case Some((_, fiber)) =>
          @tailrec
          def go(fiber: Fiber[Any]): Exit = {
            val maybeResult = stepping.step(fiber)
            maybeResult match {
              case Left(next) => go(next)
              case Right(result) => result
            }
          }

          val result = go(fiber)

          result match {
            case Finished(value) =>
              runtimeCtxRef.update(_.removeRunning(fiber))
              fiber.complete(value)

            case AsyncWait(go, deferred) =>
              runtimeCtxRef.update { ctx =>
                ctx
                  .removeRunning(fiber)
                  .addSuspended(fiber)
              }
              go {
                case Left(ex) => throw ex
                case Right(result) =>
                  deferred.fill(result)
                  runtimeCtxRef.update { ctx =>
                    ctx
                      .removeSuspended(fiber)
                      .enqueueReady(fiber)
                  }
                  tryResume()
              }

            case CreateFiber(coroutine) =>
              runtimeCtxRef.update { ctx =>
                ctx
                  .removeRunning(fiber)
                  .enqueueReady(fiber)
              }
              run(coroutine)
              tryResume() //resume creator

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
              tryResume() //resume channel creator

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
                tryResume(2) //resume reader and writer
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
                  tryResume() //resume writer
                }
                tryResume() //resume trader
              } else {
                val ch = channel.waitForRead(fiber, deferred)
                runtimeCtxRef.update { ctx =>
                  ctx
                    .upsertChannel(channel.id, ch)
                    .removeRunning(fiber)
                }
                tryResume() //resume writer
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
              tryResume() //resume reader

            case _ =>
              println("imposible")
              throw new RuntimeException("imposible")
          }
      }

    } while (true)
  }

  def shutdown(): Unit = {
    running = false
    resumeCount.release(poolSize)
    pool.shutdown()
  }

}
