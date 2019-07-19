package io.rebelapps.coop.data

sealed trait Coroutine[+A] {

  def map[B](f: A => B): Coroutine[B] = Map(this, f)

}

case class Pure[+A](value: A) extends Coroutine[A]

case class Eval[+A](thunk: () => A) extends Coroutine[A]

case class Async[+A](runner: (Either[Exception, A] => Unit) => Unit) extends Coroutine[A]

case class Map[A, +B](coroutine: Coroutine[A], f: A => B) extends Coroutine[B]

case class FlatMap[A, +B](fa: Coroutine[A], f: A => Coroutine[B]) extends Coroutine[B]

case class RaiseError(exception: Exception) extends Coroutine[Nothing]

//todo spawn
//todo channel
//todo loop

object Coroutine extends CoroutineInstances {

  def pure[A](value: A): Coroutine[A] = Pure(value)

  def eval[A](thunk: => A): Coroutine[A] = Eval(thunk _)

  def effect[A](body: => A): Coroutine[A] = Eval(body _)

}