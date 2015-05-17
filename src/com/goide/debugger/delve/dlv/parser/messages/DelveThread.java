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

import org.jetbrains.annotations.NotNull;

/**
 * Class representing information about an execution thread from Delve.
 */
@SuppressWarnings("unused")
public class DelveThread {
  /**
   * Possible states for the thread.
   */
  public enum State {
    Stopped,
    Running
  }

  /**
   * Flag indicating whether this is the current thread.
   */
  public Boolean current;

  /**
   * The Delve identifier.
   */
  public Integer id;

  /**
   * The target identifier.
   */
  public String targetId;

  /**
   * Extra information about the thread in a target-specific format
   */
  public String details;

  /**
   * The name of the thread, if available.
   */
  public String name;

  /**
   * The stack frame currently executing in the thread.
   */
  public DelveStackFrame frame;

  /**
   * The thread's state.
   */
  public State state;

  // TODO: Should this be an integer?
  /**
   * The core on which the thread is running, if known.
   */
  public String core;

  /**
   * Formats the thread into a string suitable to be prevented to the user.
   *
   * @return The formatted thread name.
   */
  @NotNull
  public String formatName() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append(id);
    sb.append("]");

    if (name != null) {
      sb.append(" ");
      sb.append(name);
    }
    else if (targetId != null) {
      sb.append(" ");
      sb.append(targetId);
    }

    if (frame != null && frame.function != null) {
      sb.append(" :: ");
      sb.append(frame.function);
      sb.append("()");
    }

    return sb.toString();
  }
}
