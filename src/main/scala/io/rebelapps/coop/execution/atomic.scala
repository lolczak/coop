package io.rebelapps.coop.execution

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import scala.annotation.tailrec

object atomic {

  implicit class AtomicRefOps[A](ref: AtomicReference[A]) {

    def update(f: A => A): A =
      ref.updateAndGet(new UnaryOperator[A] {
        override def apply(t: A): A = f(t)
      })

    def modify[B](f: A => (A, B)): (A, B) = {
      @tailrec
      def loop(): (A, B) = {
        val prev = ref.get()
        val (next, result) = f(prev)
        if (ref.compareAndSet(prev, next)) {
          return next -> result
        } else {
          loop()
        }
      }

      loop()
    }

    def modify_[B](f: A => (A, B)): B = modify(f)._2

    def modifyWith[B](f: PartialFunction[A, (A, B)]): Option[(A, B)] = {
      @tailrec
      def loop(): Option[(A, B)] = {
        val prev = ref.get()
        if (f.isDefinedAt(prev)) {
          val (next, result) = f(prev)
          if (ref.compareAndSet(prev, next)) {
            return Some(next -> result)
          } else {
            loop()
          }
        } else {
          return None
        }
      }

      loop()
    }

    def modifyWith_[B](f: PartialFunction[A, (A, B)]): Option[B] = modifyWith(f).map(_._2)

    def modifyWhen[B](cond: A => Boolean)(f: A => (A, B)): Option[(A, B)] = {
      @tailrec
      def loop(): Option[(A, B)] = {
        val prev = ref.get()
        if (cond(prev)) {
          val (next, result) = f(prev)
          if (ref.compareAndSet(prev, next)) {
            return Some(next -> result)
          } else {
            loop()
          }
        } else {
          return None
        }
      }

      loop()
    }

    def modifyWhen_[B](cond: A => Boolean)(f: A => (A, B)): Option[B] = modifyWhen(cond)(f).map(_._2)

  }

}
