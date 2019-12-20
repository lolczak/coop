package io.rebelapps.coop.data

import java.util.UUID

sealed trait Coop[+A] {

  def map[B](f: A => B): Coop[B] = Map(this, f)

}

case class Pure[+A](value: A) extends Coop[A]

case class Eval[+A](thunk: () => A) extends Coop[A]

case class Async[+A](go: (Either[Exception, A] => Unit) => Unit) extends Coop[A]

case class Map[A, +B](coroutine: Coop[A], f: A => B) extends Coop[B]

case class FlatMap[A, +B](fa: Coop[A], f: A => Coop[B]) extends Coop[B]

case class Spawn(coroutine: Coop[_]) extends Coop[Unit]

case class CreateChannel[A](size: Int) extends Coop[Channel[A]]

case class ReadChannel[A](id: UUID) extends Coop[A]

case class WriteChannel[A](id: UUID, elem: A) extends Coop[Unit]

case object Nop extends Coop[Nothing]

//case class RaiseError(exception: Exception) extends Coroutine[Nothing]

object Coop extends CoroutineInstances {

  def pure[A](value: A): Coop[A] = Pure(value)

  def eval[A](thunk: => A): Coop[A] = Eval(thunk _)

  def effect[A](body: => A): Coop[A] = Eval(body _)

  def async[A](go: (Either[Exception, A] => Unit) => Unit): Coop[A] = Async(go)

  def spawn[A](coroutine: Coop[A]): Coop[Unit] = Spawn(coroutine)

  def makeChan[A](size: Int): Coop[Channel[A]] = new CreateChannel[A](size)

}