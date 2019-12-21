package io.rebelapps.coop.execution

import java.util.UUID

import io.rebelapps.coop.data.{Channel, Coop, DeferredValue, ReadChannel, WriteChannel}

case class SimpleChannel[A](id: UUID,
                            queueLength: Int,
                            queue: Vector[A] = Vector.empty,
                            readWait: Vector[(Fiber[Any], DeferredValue[Any])] = Vector.empty,
                            writeWait: Vector[(Fiber[Any], Any)] = Vector.empty
                           )
  extends Channel[A] {

  //todo OOD

  def enqueue(elem: A) = {
    println(s"enque $id size: ${queue.size+1}")
    this.copy(queue = elem +: queue)
  }

  def dequeue(): (SimpleChannel[A], A) = {
    println(s"deque $id size: ${queue.size-1}")
    val tail = queue.last
    this.copy(queue = queue.init) -> tail
  }

  def waitForWrite(elem: A, fiber: Fiber[Any]) = this.copy(writeWait = (fiber -> elem) +: writeWait)

  def waitForRead(fiber: Fiber[Any], defVal: DeferredValue[Any]) = this.copy(readWait = (fiber -> defVal) +: readWait)

  def getFirstWaitingForRead(): (SimpleChannel[A], (Fiber[Any], DeferredValue[Any])) = {
    val fiber = readWait.last
    this.copy(readWait = readWait.init) -> fiber
  }

  def getFirstWaitingForWrite(): (SimpleChannel[A], (Fiber[Any], Any)) = {
    val fiber = writeWait.last
    this.copy(writeWait = writeWait.init) -> fiber
  }

  override def read(): Coop[A] = ReadChannel[A](id)

  override def write(elem: A): Coop[Unit] = WriteChannel(id, elem)

}
