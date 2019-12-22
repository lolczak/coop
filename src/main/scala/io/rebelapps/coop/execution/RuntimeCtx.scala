package io.rebelapps.coop.execution

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.collection.immutable.Queue

case class RuntimeCtx(running: Set[Fiber[Any]] = Set.empty,
                      ready: Queue[Fiber[Any]] = Queue.empty,
                      suspended: Set[Fiber[Any]] = Set.empty,
                      channels: Map[UUID, AtomicReference[BufferedChannel[Any]]] = Map.empty) {

  def enqueueReady(fiber: Fiber[Any]): RuntimeCtx = {
    this.copy(ready = ready.enqueue(fiber))
  }

  def hasReadyFibers(): Boolean = ready.nonEmpty

  def moveFirstReadyToRunning(): (RuntimeCtx, Fiber[Any]) = {
    val (fiber, queue) = ready.dequeue
    this.copy(ready = queue, running = running + fiber) -> fiber
  }

  def removeRunning(fiber: Fiber[Any]): RuntimeCtx = this.copy(running = running - fiber)

  def removeSuspended(fiber: Fiber[Any]): RuntimeCtx = {
    this.copy(suspended = suspended - fiber)
  }

  def addSuspended(fiber: Fiber[Any]): RuntimeCtx = {
    this.copy(suspended = suspended + fiber)
  }

  def insertChannel(id: UUID, channel: BufferedChannel[Any]): RuntimeCtx = {
    this.copy(channels = channels + (id -> new AtomicReference(channel)))
  }

  def getChannelRef(id: UUID): AtomicReference[BufferedChannel[Any]] = channels(id)

}
