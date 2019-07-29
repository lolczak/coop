package io.rebelapps.coop.execution

import java.util.UUID

import io.rebelapps.coop.data.{Channel, Coroutine, ReadChannel, WriteChannel}

case class SimpleChannel[A](id: UUID,
                            queueSize: Int,
                            queue: Vector[A] = Vector.empty,
                            readWait: Vector[Fiber[_]] = Vector.empty,
                            writeWait: Vector[(Fiber[_], Any)] = Vector.empty
                           )
  extends Channel[A] {

  //todo OOD

  def enqueue(elem: A) = this.copy(queue = elem +: queue)

  def dequeue(): (SimpleChannel[A], A) = {
    val tail = queue.last
    this.copy(queue = queue.init) -> tail
  }

  def waitForWrite(elem: A, fiber: Fiber[_]) = this.copy(writeWait = (fiber -> elem) +: writeWait)

  def waitForRead(fiber: Fiber[_]) = this.copy(readWait = fiber +: readWait)

  def getFirstWaitingForRead(): (SimpleChannel[A], Fiber[_]) = {
    val fiber = readWait.last
    this.copy(readWait = readWait.init) -> fiber
  }

  def getFirstWaitingForWrite(): (SimpleChannel[A], (Fiber[_], Any)) = {
    val fiber = writeWait.last
    this.copy(writeWait = writeWait.init) -> fiber
  }

  override def read(): Coroutine[A] = ReadChannel[A](id)

  override def write(elem: A): Coroutine[Unit] = WriteChannel(id, elem)

}
