package io.rebelapps.coop.execution

import java.util

import io.rebelapps.coop.data.Coop
import io.rebelapps.coop.execution.stack.Frame

import scala.concurrent.Promise

case class Fiber[A](coroutine: Coop[A],
                    callStack: util.Stack[Frame],
                    promise: Promise[A]
                   )
