package io.rebelapps.coop.data

trait Channel[A] {

  def read(): Coop[A]

  def write(elem: A): Coop[Unit]

}
