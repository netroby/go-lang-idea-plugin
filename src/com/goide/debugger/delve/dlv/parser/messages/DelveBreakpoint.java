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

import com.goide.debugger.delve.dlv.parser.DelveValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A breakpoint. This is returned from a -break-insert request.
 */
@SuppressWarnings("unused")
public class DelveBreakpoint extends DelveDoneEvent {
  /**
   * Possible breakpoint types.
   */
  public enum Type {
    Breakpoint,
    Watchpoint,
    Catchpoint
  }

  /**
   * Possible catchpoint types.
   */
  public enum CatchType {
    Load,
    Unload
  }

  /**
   * Possible breakpoint dispositions.
   */
  public enum BreakpointDisposition {
    /**
     * The breakpoint will not be deleted.
     */
    Keep,
    /**
     * The breakpoint will be deleted at the next stop.
     */
    Del
  }

  /**
   * Possible address availability types.
   */
  public enum AddressAvailability {
    /**
     * The breakpoint address is available.
     */
    Available,
    /**
     * The breakpoint is pending.
     */
    Pending,
    /**
     * The breakpoint has multiple locations.
     */
    Multiple
  }

  /**
   * Possible breakpoint condition evaluators.
   */
  public enum ConditionEvaluator {
    Host,
    Target
  }

  /**
   * The breakpoint number. If this is one location of a multi-location breakpoint this will be
   * the number of the main breakpoint and instanceNumber will be filled with the instance number.
   */
  public Integer number;

  /**
   * The instance number for multi-location breakpoints.
   */
  public Integer instanceNumber;

  /**
   * The type of breakpoint.
   */
  public Type type;

  /**
   * The type of catchpoint. This will be null if type is not Catchpoint.
   */
  public CatchType catchType;

  /**
   * The breakpoint disposition.
   */
  public BreakpointDisposition disposition;

  /**
   * Whether the breakpoint is enabled.
   */
  public Boolean enabled;

  /**
   * Availability of the breakpoint address.
   */
  public AddressAvailability addressAvailability;

  /**
   * The breakpoint address. This will be null if addressAvailability is not Available.
   */
  public Long address;

  /**
   * The name of the function the breakpoint is in, if known.
   */
  public String function;

  /**
   * The relative path to the file the breakpoint is in, if known.
   */
  public String fileRelative;

  /**
   * The absolute path to the file the breakpoint is in, if known.
   */
  public String fileAbsolute;

  /**
   * The line number the breakpoint is on, if known.
   */
  public Integer line;

  /**
   * If the source file is not known this may hold the address of the breakpoint, possibly
   * followed by a symbol name.
   */
  public String at;

  /**
   * If the breakpoint is pending this field holds the text used to set the breakpoint.
   */
  public String pending;

  /**
   * Where the breakpoint's condition is evaluated.
   */
  public ConditionEvaluator conditionEvaluator;

  /**
   * If this is a thread-specific breakpoint, this identifies the thread in which the breakpoint
   * can trigger.
   */
  public Integer thread;

  /**
   * The thread groups to which this breakpoint applies.
   */
  public List<String> threadGroups;

  /**
   * The task identifier of the Ada task this breakpoint is restricted to, if any.
   */
  public String adaTask;

  /**
   * The condition expression, if any.
   */
  public String condition;

  /**
   * The ignore count of the breakpoint.
   */
  public Integer ignoreCount;

  /**
   * The enable count of the breakpoint.
   */
  public Integer enableCount;

  /**
   * The name of the static tracepoint marker, if any.
   */
  public String staticTracepointMarker;

  /**
   * The watchpoint mask, if set.
   */
  public String watchpointMask;

  /**
   * The tracepoint's pass count.
   */
  public Integer tracepointPassCount;

  /**
   * The original location of the breakpoint.
   */
  public String originalLocation;

  /**
   * The number of times the breakpoint has been hit.
   */
  public Integer hitCount;

  /**
   * Whether the tracepoint is installed.
   */
  public Boolean tracepointInstalled;

  /**
   * Unspecified extra data.
   */
  public String extra;

  /**
   * Value processor for number.
   */
  @Nullable
  public Integer processNumber(@NotNull DelveValue value) {
    if (value.type != DelveValue.Type.String) {
      return null;
    }

    int dotIndex = value.string.indexOf('.');
    if (dotIndex == -1) {
      return Integer.parseInt(value.string);
    }
    else {
      return Integer.parseInt(value.string.substring(0, dotIndex));
    }
  }

  /**
   * Value processor for instanceNumber.
   */
  @Nullable
  public Integer processInstanceNumber(@NotNull DelveValue value) {
    if (value.type != DelveValue.Type.String) {
      return null;
    }

    int dotIndex = value.string.indexOf('.');
    if (dotIndex == -1) {
      return null;
    }
    else {
      return Integer.parseInt(value.string.substring(dotIndex + 1));
    }
  }

  /**
   * Value processor for addressAvailability.
   */
  @Nullable
  public AddressAvailability processAddressAvailability(@NotNull DelveValue value) {
    if (value.type != DelveValue.Type.String) {
      return null;
    }

    if (value.string.equals("<PENDING>")) {
      return AddressAvailability.Pending;
    }
    if (value.string.equals("<MULTIPLE>")) {
      return AddressAvailability.Multiple;
    }

    // Make sure the string is a valid integer
    if (value.string.startsWith("0x")) {
      try {
        Long.parseLong(value.string.substring(2), 16);
        return AddressAvailability.Available;
      }
      catch (Throwable ex) {
        return null;
      }
    }

    return null;
  }

  /**
   * Value processor for address.
   */
  @Nullable
  public Long processAddress(@NotNull DelveValue value) {
    if (value.type != DelveValue.Type.String || value.string.equals("<PENDING>") ||
        value.string.equals("<MULTIPLE>")) {
      return null;
    }
    return new Long(42);
  }
}
