package io.rebelapps.coop.data

import cats.Monad
import io.rebelapps.coop.control.CoroutineMonad

trait CoroutineInstances {

  implicit val monad: Monad[Coroutine] = CoroutineMonad

}
