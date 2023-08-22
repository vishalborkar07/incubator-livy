/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.livy.server.interactive

import java.util.Date

import scala.concurrent.duration.{Deadline, Duration, DurationInt, FiniteDuration}

import org.apache.livy.sessions.Session.RecoveryMetadata
import org.apache.livy.LivyConf
import org.apache.livy.server.SessionServlet
import org.apache.livy.sessions.{Session, SessionManager}

trait SessionTimeout {
  protected val sessionTimeout: FiniteDuration

  private var _lastTimeout: Date = _ // For reporting purpose
  private var timeDeadline: Option[Deadline] = None

  def timeout(): Unit = synchronized {
    if (sessionTimeout > Duration.Zero) {
      timeDeadline = Some(sessionTimeout.fromNow)
    }

    _lastTimeout = new Date()
  }

  def lastTimeout: Date = synchronized {
    _lastTimeout
  }

  def sessionExpired: Boolean = synchronized {
    timeDeadline.exists(_.isOverdue())
  }
}

trait SessionTimeoutNotifier[S <: Session with SessionTimeout, R <: RecoveryMetadata]
  extends SessionServlet[S, R] {

  abstract override protected def withUnprotectedSession(fn: (S => Any)): Any = {
    super.withUnprotectedSession { s =>
      s.timeout()
      fn(s)
    }
  }

  abstract override protected def withViewAccessSession(fn: (S => Any)): Any = {
    super.withViewAccessSession { s =>
      s.timeout()
      fn(s)
    }
  }

  abstract override protected def withModifyAccessSession(fn: (S) => Any): Any = {
    super.withModifyAccessSession { s =>
      s.timeout()
      fn(s)
    }
  }
}

/**
 * A SessionManager trait.
 * It will create a thread that periodically deletes sessions with expired heartbeat.
 */
trait SessionTimeoutWatchdog[S <: Session with SessionTimeout, R <: RecoveryMetadata] {
  self: SessionManager[S, R] =>
  var sessionExpired: Boolean = false
  private val watchdogThread = new Thread(s"TimeoutWatchdog-${self.getClass.getName}") {
    override def run(): Unit = {
      val interval = livyConf.getTimeAsMs(LivyConf.HEARTBEAT_WATCHDOG_INTERVAL)
      info("Timeout watchdog thread started.")
      while (true) {
        Thread.sleep(5000)
        sessionExpired = !sessionExpired
        deleteExpiredSessions()
        Thread.sleep(interval)
      }
    }

  }

  protected def start(): Unit = {
    assert(!watchdogThread.isAlive())

    watchdogThread.setDaemon(true)
    watchdogThread.start()
  }

  private[interactive] def deleteExpiredSessions(): Unit = {
    // Delete takes time. If we use .filter().foreach() here, the time difference between we check
    // expiration and the time we delete the session might be huge. To avoid that, check expiration
    // inside the foreach block.
    sessions.values.foreach { s =>
      if (sessionExpired == true) {
        //info(s"Session ${s.id} expired. Last heartbeat is at ${s.lastHeartbeat}.")
        try {
          delete(s)
        } catch {
          case t: Throwable =>
            warn(s"Exception was thrown when deleting expired session ${s.id}", t)
        }
      }
    }
  }
}