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

package com.goide.debugger.delve.debug.breakpoints;

import com.goide.debugger.delve.debug.DelveDebugProcess;
import com.goide.debugger.delve.dlv.Delve;
import com.goide.debugger.delve.dlv.DelveCommand;
import com.goide.debugger.delve.dlv.parser.messages.DelveBreakpoint;
import com.goide.debugger.delve.dlv.parser.messages.DelveErrorEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveEvent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DelveBreakpointHandler  extends XBreakpointHandler<XLineBreakpoint<DelveBreakpointProperties>> {
  private static final Logger LOG = Logger.getInstance(DelveBreakpointHandler.class);

  private final BidirectionalMap<Integer, XLineBreakpoint<DelveBreakpointProperties>> myBreakpoints = new BidirectionalMap<Integer, XLineBreakpoint<DelveBreakpointProperties>>();

  private final Delve myDelve;
  private final DelveDebugProcess myDebugProcess;

  public DelveBreakpointHandler(Delve dlv, DelveDebugProcess debugProcess) {
    super(DelveBreakpointType.class);
    myDelve = dlv;
    myDebugProcess = debugProcess;
  }

  @Override
  public void registerBreakpoint(@NotNull final XLineBreakpoint<DelveBreakpointProperties> breakpoint) {
    // Check if the breakpoint already exists
    Integer number = findBreakpointNumber(breakpoint);
    if (number != null) {
      // Re-enable the breakpoint
      myDelve.sendCommand("-break-enable " + number);
    }
    else {
      // Set the breakpoint
      XSourcePosition sourcePosition = breakpoint.getSourcePosition();
      if (sourcePosition == null) {
        return;
      }

      DelveCommand command = new DelveCommand()
        .setCommand("-break-insert")
        .addParam("-f " + sourcePosition.getFile().getPath() + ":" + (sourcePosition.getLine() + 1));
      myDelve.sendCommand(command, new Delve.DelveEventCallback() {
        @Override
        public void onDelveCommandCompleted(DelveEvent event) {
          onDelveBreakpointReady(event, breakpoint);
        }
      });
    }
  }

  @Override
  public void unregisterBreakpoint(@NotNull XLineBreakpoint<DelveBreakpointProperties> breakpoint, boolean temporary) {
    Integer number = findBreakpointNumber(breakpoint);
    if (number == null) {
      LOG.error("Cannot remove breakpoint; could not find it in breakpoint table");
      return;
    }

    if (!temporary) {
      // Delete the breakpoint
      myDelve.sendCommand("-break-delete " + number);
      synchronized (myBreakpoints) {
        myBreakpoints.remove(number);
      }
    }
    else {
      // Disable the breakpoint
      myDelve.sendCommand("-break-disable " + number);
    }
  }

  private void onDelveBreakpointReady(DelveEvent event,
                                    @NotNull XLineBreakpoint<DelveBreakpointProperties> breakpoint) {
    if (event instanceof DelveErrorEvent) {
      myDebugProcess.getSession().updateBreakpointPresentation(breakpoint,
                                                               AllIcons.Debugger.Db_invalid_breakpoint, ((DelveErrorEvent)event).message);
      return;
    }
    if (!(event instanceof DelveBreakpoint)) {
      myDebugProcess.getSession().updateBreakpointPresentation(breakpoint,
                                                               AllIcons.Debugger.Db_invalid_breakpoint,
                                                               "Unexpected data received from Delve");
      LOG.warn("Unexpected event " + event + " received from -break-insert request");
      return;
    }

    // Save the breakpoint
    DelveBreakpoint delveBreakpoint = (DelveBreakpoint)event;
    if (delveBreakpoint.number == null) {
      myDebugProcess.getSession().updateBreakpointPresentation(breakpoint,
                                                               AllIcons.Debugger.Db_invalid_breakpoint,
                                                               "No breakpoint number received from Delve");
      LOG.warn("No breakpoint number received from delve after -break-insert request");
      return;
    }

    synchronized (myBreakpoints) {
      myBreakpoints.put(delveBreakpoint.number, breakpoint);
    }

    // Mark the breakpoint as set
    // TODO: Don't do this yet if the breakpoint is pending
    myDebugProcess.getSession().updateBreakpointPresentation(breakpoint,
                                                             AllIcons.Debugger.Db_verified_breakpoint, null);
  }

  public XLineBreakpoint<DelveBreakpointProperties> findBreakpoint(int number) {
    synchronized (myBreakpoints) {
      return myBreakpoints.get(number);
    }
  }

  @Nullable
  public Integer findBreakpointNumber(XLineBreakpoint<DelveBreakpointProperties> breakpoint) {
    List<Integer> numbers;
    synchronized (myBreakpoints) {
      numbers = myBreakpoints.getKeysByValue(breakpoint);
    }

    if (numbers == null || numbers.isEmpty()) {
      return null;
    }
    else if (numbers.size() > 1) {
      LOG.warn("Found multiple breakpoint numbers for breakpoint; only returning the " +
               "first");
    }
    return numbers.get(0);
  }
}
