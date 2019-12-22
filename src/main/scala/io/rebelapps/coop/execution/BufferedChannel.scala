package io.rebelapps.coop.execution

import java.util.UUID

import io.rebelapps.coop.data.{Channel, Coop, DeferredValue, ReadChannel, WriteChannel}

import scala.collection.immutable.Queue

case class BufferedChannel[A](id: UUID,
                              bufferLength: Int,
                              buffer: Queue[A] = Queue.empty,
                              readerQueue: Queue[(Fiber[Any], DeferredValue[Any])] = Queue.empty,
                              writerQueue: Queue[(Fiber[Any], Any)] = Queue.empty
                           )
  extends Channel[A] {

  def enqueue(elem: A): BufferedChannel[A] = {
    this.copy(buffer = buffer.enqueue(elem))
  }

  def dequeue(): (BufferedChannel[A], A) = {
    val (elem, q) = buffer.dequeue
    this.copy(buffer = q) -> elem
  }

  def waitForWrite(elem: A, fiber: Fiber[Any]): BufferedChannel[A] = this.copy(writerQueue = writerQueue.enqueue(fiber -> elem))

  def waitForRead(fiber: Fiber[Any], defVal: DeferredValue[Any]): BufferedChannel[A] = this.copy(readerQueue = readerQueue.enqueue(fiber -> defVal))

  def removeFirstWaitingForRead(): (BufferedChannel[A], (Fiber[Any], DeferredValue[Any])) = {
    val (fiber, q) = readerQueue.dequeue
    this.copy(readerQueue = q) -> fiber
  }

  def removeFirstWaitingForWrite(): (BufferedChannel[A], (Fiber[Any], Any)) = {
    val (fiber, q) = writerQueue.dequeue
    this.copy(writerQueue = q) -> fiber
  }

  override def read(): Coop[A] = ReadChannel[A](id)

  override def write(elem: A): Coop[Unit] = WriteChannel(id, elem)

}
