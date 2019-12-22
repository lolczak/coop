package io.rebelapps.coop

import java.util.UUID

import io.rebelapps.coop.data.{Coop, DeferredValue}

package object execution {

  sealed trait Exit

  case class Finished(value: Any) extends Exit

  sealed trait Suspension extends Exit

  case class CreateFiber(coroutine: Coop[Any])                                                  extends Suspension
  case class ChannelCreation(size: Int, defVal: DeferredValue[SimpleChannel[Any]])              extends Suspension
  case class ChannelRead(id: UUID, defVal: DeferredValue[Any])                                  extends Suspension
  case class ChannelWrite(id: UUID, value: Any)                                                 extends Suspension
  case class AsyncWait(go: (Either[Exception, _] => Unit) => Unit, defVal: DeferredValue[Any])  extends Suspension

  case class Fail(exception: Exception)

}
