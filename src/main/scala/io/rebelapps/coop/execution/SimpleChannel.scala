package io.rebelapps.coop.execution

import java.util.UUID

import io.rebelapps.coop.data.{Channel, Coroutine, ReadChannel, WriteChannel}

class SimpleChannel[A](id: UUID,
                       queueSize: Int,
                       queue: Vector[A] = Vector.empty,
                       readWait: Vector[Fiber[_]] = Vector.empty,
                       writeWait: Vector[Fiber[_]] = Vector.empty
                      )
  extends Channel[A] {

  override def read(): Coroutine[A] = ReadChannel[A](id)

  override def write(elem: A): Coroutine[Unit] = WriteChannel(id, elem)

}
