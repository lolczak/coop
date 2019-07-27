package io.rebelapps.coop.control

import cats.Monad
import io.rebelapps.coop.data.{Coroutine, FlatMap, Pure, Map}

object CoroutineMonad extends Monad[Coroutine] {

  override def map[A, B](fa: Coroutine[A])(f: A => B): Coroutine[B] = Map(fa, f)

  override def pure[A](x: A): Coroutine[A] = Pure(x)

  override def flatMap[A, B](fa: Coroutine[A])(f: A => Coroutine[B]): Coroutine[B] = FlatMap(fa, f)

  override def tailRecM[A, B](a: A)(f: A => Coroutine[Either[A, B]]): Coroutine[B] = ??? //todo tailRecM

}
