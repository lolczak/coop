package io.rebelapps.coop.data

trait Channel[A] {

  def read(): Coroutine[A]

  def write(elem: A): Coroutine[Unit]

}
