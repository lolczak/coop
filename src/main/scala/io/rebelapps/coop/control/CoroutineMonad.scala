package io.rebelapps.coop.control

import cats.Monad
import io.rebelapps.coop.data.{Coop, FlatMap, Pure, Map}

object CoroutineMonad extends Monad[Coop] {

  override def map[A, B](fa: Coop[A])(f: A => B): Coop[B] = Map(fa, f)

  override def pure[A](x: A): Coop[A] = Pure(x)

  override def flatMap[A, B](fa: Coop[A])(f: A => Coop[B]): Coop[B] = FlatMap(fa, f)

  override def tailRecM[A, B](a: A)(f: A => Coop[Either[A, B]]): Coop[B] = ??? //todo tailRecM

}
