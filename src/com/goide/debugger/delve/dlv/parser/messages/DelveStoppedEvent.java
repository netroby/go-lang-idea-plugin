/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Mihai Toader, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.debugger.delve.dlv.parser.messages;

import java.util.List;

public class DelveStoppedEvent extends DelveEvent {
  /**
   * Possible reasons that can cause the program to stop.
   */
  public enum Reason {
    BreakpointHit,
    WatchpointTrigger,
    ReadWatchpointTrigger,
    AccessWatchpointTrigger,
    FunctionFinished,
    LocationReached,
    WatchpointScope,
    EndSteppingRange,
    ExitedSignalled,
    Exited,
    ExitedNormally,
    SignalReceived,
    SolibEvent,
    Fork,
    Vfork,
    SyscallEntry,
    Exec
  }

  /**
   * The reason the target stopped.
   */
  public Reason reason;

  /**
   * The breakpoint disposition.
   */
  public DelveBreakpoint.BreakpointDisposition breakpointDisposition;

  /**
   * The breakpoint number.
   */
  public Integer breakpointNumber;

  /**
   * The current point of execution.
   */
  public DelveStackFrame frame;

  /**
   * The thread of execution.
   */
  public Integer threadId;

  /**
   * Flag indicating whether all threads were stopped. If false, stoppedThreads contains a list of
   * the threads that were stopped.
   */
  public Boolean allStopped;

  /**
   * A list of the threads that were stopped. This will be null if allStopped is true.
   */
  public List<Integer> stoppedThreads;
}
