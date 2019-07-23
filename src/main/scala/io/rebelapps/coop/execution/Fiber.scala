package io.rebelapps.coop.execution

import io.rebelapps.coop.data.Coroutine
import io.rebelapps.coop.execution.stack.CallStack

import scala.concurrent.Promise

case class Fiber[A](coroutine: Coroutine[A],
                    callStack: CallStack,
                    promise: Promise[A]
                   )
