/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved
 *
 *    http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file
 */

package org.fusesource.fabric.apollo.amqp.protocol.interceptors.test_interceptors

import org.fusesource.fabric.apollo.amqp.protocol.interfaces.Interceptor
import org.fusesource.fabric.apollo.amqp.codec.interfaces.AMQPFrame
import collection.mutable.Queue
import org.apache.activemq.apollo.util.Logging

/**
 *
 */

class TaskExecutingInterceptor extends Interceptor  {
  protected def _send(frame: AMQPFrame, tasks: Queue[() => Unit]) = {
    printf("Tasks : %s\n", tasks)
    tasks.dequeueAll((x) => {
      x()
      true
    })
  }

  protected def _receive(frame: AMQPFrame, tasks: Queue[() => Unit]) = {
    incoming.receive(frame, tasks)
  }
}