package io.rebelapps.coop.execution

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object CoopThreadFactory extends ThreadFactory {

  private val count = new AtomicInteger(1)

  override def newThread(r: Runnable): Thread = {
    val thread = new Thread(r)
    thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = println(s"Exception caught in thread ${thread.getName}: $e")
    })
    thread.setName(s"coop-${count.getAndAdd(1)}")
    thread
  }
  
}
