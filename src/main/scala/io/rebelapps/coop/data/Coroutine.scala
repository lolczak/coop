package io.rebelapps.coop.data

import java.util.UUID

sealed trait Coroutine[+A] {

  def map[B](f: A => B): Coroutine[B] = Map(this, f)

}

case class Pure[+A](value: A) extends Coroutine[A]

case class Eval[+A](thunk: () => A) extends Coroutine[A]

case class Async[+A](go: (Either[Exception, A] => Unit) => Unit) extends Coroutine[A]

case class Map[A, +B](coroutine: Coroutine[A], f: A => B) extends Coroutine[B]

case class FlatMap[A, +B](fa: Coroutine[A], f: A => Coroutine[B]) extends Coroutine[B]

case class Spawn(coroutine: Coroutine[_]) extends Coroutine[Unit]

case class CreateChannel[A](size: Int) extends Coroutine[Channel[A]]

case class ReadChannel[A](id: UUID) extends Coroutine[A]

case class WriteChannel[A](id: UUID, elem: A) extends Coroutine[Unit]

//case class RaiseError(exception: Exception) extends Coroutine[Nothing]

object Coroutine extends CoroutineInstances {

  def pure[A](value: A): Coroutine[A] = Pure(value)

  def eval[A](thunk: => A): Coroutine[A] = Eval(thunk _)

  def effect[A](body: => A): Coroutine[A] = Eval(body _)

  def async[A](go: (Either[Exception, A] => Unit) => Unit): Coroutine[A] = Async(go)

  def spawn[A](coroutine: Coroutine[A]): Coroutine[Unit] = Spawn(coroutine)

  def makeChan[A](size: Int): Coroutine[Channel[A]] = new CreateChannel[A](size)

}