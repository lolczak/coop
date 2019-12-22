package io.rebelapps.coop.execution

import java.util.UUID
import java.util.concurrent.{ScheduledThreadPoolExecutor, Semaphore}
import java.util.concurrent.atomic.AtomicReference

import io.rebelapps.coop.data.{Coop, DeferredValue}
import io.rebelapps.coop.execution.atomic._

import scala.annotation.tailrec
import scala.concurrent.Future
import CoopScheduler._

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

  private def runLoop(): Unit = {
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

            case SuspendedOnCoroutineCreation(coroutine) =>
              runtimeCtxRef.update { ctx =>
                ctx
                  .removeRunning(fiber)
                  .enqueueReady(fiber)
              }
              run(coroutine)
              tryResume() //resume creator

            case SuspendedOnChannelCreation(size, deferred) =>
              val id = UUID.randomUUID()
              val channel = new SimpleChannel[Any](id, size)
              runtimeCtxRef.update { ctx =>
                ctx
                  .insertChannel(id, channel)
                  .removeRunning(fiber)
                  .enqueueReady(fiber)
              }
              deferred.fill(channel)
              tryResume() //resume channel creator

            case SuspendedOnChannelRead(id, deferred) =>
              val channelRef = runtimeCtxRef.get().getChannelRef(id)
              channelRef.modifyWith_[ReadCase] {
                case channel if channel.queue.isEmpty && channel.writeWait.nonEmpty =>
                  val (ch, (wFiber, wElem)) = channel.getFirstWaitingForWrite()
                  ch -> EmptyQueueNonEmptyWaitingWriters(wFiber, wElem)

                case channel if channel.queue.nonEmpty && channel.writeWait.isEmpty =>
                  val (ch, elem) = channel.dequeue()
                  ch -> NonEmptyQueueEmptyWaitingWriters(elem)

                case channel if channel.queue.nonEmpty && channel.writeWait.nonEmpty =>
                  val (ch, elem) = channel.dequeue()
                  val (ch2, (wFiber, wElem)) = ch.getFirstWaitingForWrite()
                  ch2.enqueue(wElem) -> NonEmptyQueueNonEmptyWaitingWriters(wFiber, elem)

                case channel =>
                  channel.waitForRead(fiber, deferred) -> BlockOnRead

              } match {
                case Some(EmptyQueueNonEmptyWaitingWriters(wFiber, wElem)) =>
                  deferred.fill(wElem)
                  runtimeCtxRef.update { ctx =>
                    ctx
                      .enqueueReady(wFiber)
                      .removeRunning(fiber)
                      .enqueueReady(fiber)
                  }
                  tryResume(2) //resume reader and writer

                case Some(NonEmptyQueueEmptyWaitingWriters(elem)) =>
                  deferred.fill(elem)
                  runtimeCtxRef.update { ctx =>
                    ctx
                      .removeRunning(fiber)
                      .enqueueReady(fiber)
                  }
                  tryResume()

                case Some(NonEmptyQueueNonEmptyWaitingWriters(wFiber, elem)) =>
                  deferred.fill(elem)
                  runtimeCtxRef.update { ctx =>
                    ctx
                      .removeRunning(fiber)
                      .enqueueReady(fiber)
                      .enqueueReady(wFiber)
                  }
                  tryResume(2)

                case Some(BlockOnRead) =>
                  runtimeCtxRef.update(_.removeRunning(fiber))
                  tryResume() //resume writer

                case None =>
                  println("impossible read case")
                  throw new RuntimeException
              }

            case SuspendedOnChannelWrite(id, elem) =>
              val channelRef = runtimeCtxRef.get().getChannelRef(id)
              channelRef.modifyWith_[WriteCase] {
                case channel if channel.readWait.nonEmpty =>
                  val (ch, (reader, defVal)) = channel.getFirstWaitingForRead()
                  ch -> NonEmptyWaitingReaders(reader, defVal)

                case channel if channel.readWait.isEmpty && channel.queue.size < channel.queueLength =>
                  channel.enqueue(elem) -> EmptyReadWaitAndQueueNotFull

                case channel if channel.readWait.isEmpty && channel.queue.size >= channel.queueLength =>
                  channel.waitForWrite(elem, fiber) -> EmptyReadWaitAndQueueFull

              } match {
                case Some(NonEmptyWaitingReaders(reader, defVal)) =>
                  defVal.fill(elem)
                  runtimeCtxRef.update { ctx =>
                    ctx
                      .removeRunning(fiber)
                      .enqueueReady(fiber)
                      .enqueueReady(reader)
                  }
                  tryResume() //resume reader

                case Some(EmptyReadWaitAndQueueNotFull) =>
                  runtimeCtxRef.update { ctx =>
                    ctx
                      .removeRunning(fiber)
                      .enqueueReady(fiber)
                  }
                  tryResume() //resume reader

                case Some(EmptyReadWaitAndQueueFull) =>
                  runtimeCtxRef.update(_.removeRunning(fiber))
                  tryResume() //resume reader

                case None =>
                  println("impossible write case")
                  throw new RuntimeException
              }

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

object CoopScheduler {

  sealed trait ReadCase
  case class EmptyQueueNonEmptyWaitingWriters(writer: Fiber[Any], elem: Any)    extends ReadCase
  case class NonEmptyQueueEmptyWaitingWriters(elem: Any)                        extends ReadCase
  case class NonEmptyQueueNonEmptyWaitingWriters(writer: Fiber[Any], elem: Any) extends ReadCase
  case object BlockOnRead                                                       extends ReadCase

  sealed trait WriteCase
  case class NonEmptyWaitingReaders(reader: Fiber[Any], deferred: DeferredValue[Any]) extends WriteCase
  case object EmptyReadWaitAndQueueNotFull                                            extends WriteCase
  case object EmptyReadWaitAndQueueFull                                               extends WriteCase

}
