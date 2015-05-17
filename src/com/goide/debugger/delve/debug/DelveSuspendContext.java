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
import com.goide.debugger.delve.dlv.parser.messages.DelveStoppedEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveThread;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DelveSuspendContext extends XSuspendContext {
  private DelveExecutionStack myStack;
  @NotNull private final DelveExecutionStack[] myStacks;

  /**
   * @param delve       Handle to the Delve instance.
   * @param stopEvent The stop event that caused the suspension.
   * @param threads   Thread information, if available.
   */
  public DelveSuspendContext(Delve delve, @NotNull DelveStoppedEvent stopEvent, @Nullable List<DelveThread> threads) {
    // Add all the threads to our list of stacks
    List<DelveExecutionStack> stacks = new ArrayList<DelveExecutionStack>();
    if (threads != null) {
      // Sort the list of threads by ID
      Collections.sort(threads, new Comparator<DelveThread>() {
        @Override
        public int compare(@NotNull DelveThread o1, @NotNull DelveThread o2) {
          return o1.id.compareTo(o2.id);
        }
      });

      for (DelveThread thread : threads) {
        DelveExecutionStack stack = new DelveExecutionStack(delve, thread);
        stacks.add(stack);
        if (thread.id.equals(stopEvent.threadId)) {
          myStack = stack;
        }
      }
    }

    if (myStack == null) {
      // No thread object is available so we have to construct our own
      DelveThread thread = new DelveThread();
      thread.id = stopEvent.threadId;
      thread.frame = stopEvent.frame;
      myStack = new DelveExecutionStack(delve, thread);
      stacks.add(0, myStack);
    }

    myStacks = stacks.toArray(new DelveExecutionStack[stacks.size()]);
  }

  @Nullable
  @Override
  public XExecutionStack getActiveExecutionStack() {
    return myStack;
  }

  @NotNull
  @Override
  public XExecutionStack[] getExecutionStacks() {
    return myStacks;
  }
}
