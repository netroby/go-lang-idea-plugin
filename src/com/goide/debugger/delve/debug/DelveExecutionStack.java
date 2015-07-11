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

package com.goide.debugger.delve.debug;

import com.goide.debugger.delve.dlv.Delve;
import com.goide.debugger.delve.dlv.DelveCommand;
import com.goide.debugger.delve.dlv.parser.messages.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DelveExecutionStack extends XExecutionStack {
  private static final Logger LOG = Logger.getInstance(DelveExecutionStack.class);

  private final Delve myDelve;
  @NotNull private final DelveThread myThread;
  private DelveExecutionStackFrame myTopFrame;

  /**
   * Constructor.
   *
   * @param delve    Handle to the Delve instance.
   * @param thread The thread.
   */
  public DelveExecutionStack(Delve delve, @NotNull DelveThread thread) {
    super(thread.formatName());

    myDelve = delve;
    myThread = thread;

    // Get the top of the stack
    if (thread.frame != null) {
      myTopFrame = new DelveExecutionStackFrame(delve, myThread.id, thread.frame);
    }
  }

  /**
   * Returns the frame at the top of the stack.
   *
   * @return The stack frame.
   */
  @Nullable
  @Override
  public XStackFrame getTopFrame() {
    return myTopFrame;
  }

  /**
   * Gets the stack trace starting at the given index. This passes the request and returns
   * immediately; the data is supplied to container asynchronously.
   *
   * @param firstFrameIndex The first frame to retrieve, where 0 is the top of the stack.
   * @param container       Container into which the stack frames are inserted.
   */
  @Override
  public void computeStackFrames(final int firstFrameIndex, @NotNull final XStackFrameContainer container) {
    // Just get the whole stack
    DelveCommand command = new DelveCommand().setCommand("-stack-list-frames");
    myDelve.sendCommand(command, new Delve.DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        onDelveStackTraceReady(event, firstFrameIndex, container);
      }
    });
  }

  /**
   * Callback function for when Delve has responded to our stack trace request.
   *
   * @param event           The event.
   * @param firstFrameIndex The first frame from the list to use.
   * @param container       The container passed to computeStackFrames().
   */
  private void onDelveStackTraceReady(DelveEvent event, int firstFrameIndex,
                                    @NotNull XStackFrameContainer container) {
    if (event instanceof DelveErrorEvent) {
      container.errorOccurred(((DelveErrorEvent)event).message);
      return;
    }
    if (!(event instanceof DelveStackTrace)) {
      container.errorOccurred("Unexpected data received from Delve");
      LOG.warn("Unexpected event " + event + " received from -stack-list-frames request");
      return;
    }

    // Inspect the stack trace
    DelveStackTrace stackTrace = (DelveStackTrace)event;
    if (stackTrace.stack == null || stackTrace.stack.isEmpty()) {
      // No data
      container.addStackFrames(new ArrayList<XStackFrame>(0), true);
    }

    // Build a list of DelveExecutionStaceFrames
    List<DelveExecutionStackFrame> stack = new ArrayList<DelveExecutionStackFrame>();
    for (int i = firstFrameIndex; i < stackTrace.stack.size(); ++i) {
      DelveStackFrame frame = stackTrace.stack.get(i);
      stack.add(new DelveExecutionStackFrame(myDelve, myThread.id, frame));
    }

    // Pass the data on
    container.addStackFrames(stack, true);
  }
}
