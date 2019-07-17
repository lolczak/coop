package io.rebelapps.coop.control

import cats.Monad
import io.rebelapps.coop.data.Coroutine

trait CoroutineInstances {

  implicit val monad: Monad[Coroutine] = CoroutineMonad

}
