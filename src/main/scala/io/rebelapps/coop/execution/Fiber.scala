package io.rebelapps.coop.execution

import io.rebelapps.coop.data.Coop
import io.rebelapps.coop.execution.stack.CallStack

import scala.concurrent.Promise

case class Fiber[A](coroutine: Coop[A],
                    callStack: CallStack,
                    promise: Promise[A]
                   )
