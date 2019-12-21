package io.rebelapps.coop.execution

import java.util.UUID

case class RuntimeCtx(running: Vector[Fiber[Any]] = Vector.empty,
                      ready: Vector[Fiber[Any]] = Vector.empty,
                      suspended: Map[UUID, Fiber[Any]] = Map.empty,
                      channels: Map[UUID, SimpleChannel[Any]] = Map.empty) {

  def enqueueReady(fiber: Fiber[Any]): RuntimeCtx = {
    this.copy(ready = fiber +: ready)
  }

  def hasReadyFibers(): Boolean = ready.nonEmpty

  def moveFirstReadyToRunning(): (RuntimeCtx, Fiber[Any]) = {
    val fiber = ready.last
    this.copy(ready = ready.init, running = fiber +: running) -> fiber
  }

  def removeRunning(fiber: Fiber[_]): RuntimeCtx = this.copy(running = running.filterNot(_ eq fiber))

  def removeSuspended(requestId: RequestId): (RuntimeCtx, Fiber[Any]) = {
    val fiber = suspended(requestId)
    this.copy(suspended = suspended - requestId) -> fiber
  }

  def addSuspended(requestId: RequestId, fiber: Fiber[Any]): RuntimeCtx = {
    this.copy(suspended = suspended + (requestId -> fiber))
  }

  def upsertChannel(id: UUID, channel: SimpleChannel[Any]): RuntimeCtx = {
    this.copy(channels = channels + (id -> channel))
  }

  def getChannel(id: UUID): SimpleChannel[Any] = channels(id)

}
