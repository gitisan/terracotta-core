/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.objectserver.locks;

import com.tc.async.api.Sink;
import com.tc.object.locks.ServerLockContextStateMachine;
import com.tc.objectserver.locks.timer.LockTimer;
import com.tc.objectserver.locks.timer.TimerCallback;

public class LockHelper {
  private final LockTimer                     lockTimer;
  private final Sink<LockResponseContext>                          lockSink;
  private final LockStore                     lockStore;
  private final ServerLockContextStateMachine contextStateMachine;
  private final TimerCallback                 timerCallback;

  public LockHelper(Sink<LockResponseContext> lockSink, LockStore lockStore, TimerCallback timerCallback) {
    this.lockTimer = new LockTimer();
    this.lockSink = lockSink;
    this.lockStore = lockStore;
    this.timerCallback = timerCallback;
    this.contextStateMachine = new ServerLockContextStateMachine();
  }

  public LockTimer getLockTimer() {
    return lockTimer;
  }

  public Sink<LockResponseContext> getLockSink() {
    return lockSink;
  }

  public LockStore getLockStore() {
    return lockStore;
  }

  public ServerLockContextStateMachine getContextStateMachine() {
    return contextStateMachine;
  }

  public TimerCallback getTimerCallback() {
    return timerCallback;
  }
}
