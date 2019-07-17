package io.rebelapps.coop.data

import io.rebelapps.coop.control.CoroutineInstances

sealed trait Coroutine[+A] {

  def map[B](f: A => B): Coroutine[B] = Map(this, f)

}

case class Pure[+A](value: A) extends Coroutine[A]

case class Delay[+A](thunk: () => A) extends Coroutine[A]

case class Async[+A](runner: (Either[Exception, A] => Unit) => Unit) extends Coroutine[A]

case class Map[A, +B](coroutine: Coroutine[A], f: A => B) extends Coroutine[B]

case class FlatMap[A, +B](fa: Coroutine[A], f: A => Coroutine[B]) extends Coroutine[B]

case class RaiseError(exception: Exception) extends Coroutine[Nothing]

//todo

object Coroutine extends CoroutineInstances {

  def pure[A](value: A): Coroutine[A] = Pure(value)

  def delay[A](thunk: => A): Coroutine[A] = Delay(thunk _)


}