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
import com.goide.debugger.delve.dlv.parser.messages.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class DelveExecutionStackFrame extends XStackFrame {
  private static final Logger LOG = Logger.getInstance(DelveExecutionStackFrame.class);
  private final Delve myDelve;
  private final int myThread;
  private final DelveStackFrame myFrame;
  private final int myFrameNo;
  @NotNull private final DelveEvaluator myEvaluator;

  /**
   * Constructor.
   *
   * @param delve    Handle to the Delve instance.
   * @param thread The thread the frame is in.
   * @param frame  The Delve stack frame to wrap.
   */
  public DelveExecutionStackFrame(Delve delve, int thread, DelveStackFrame frame) {
    myDelve = delve;
    myThread = thread;
    myFrame = frame;

    // The top frame doesn't have a level set
    myFrameNo = myFrame.level == null ? 0 : myFrame.level;
    myEvaluator = new DelveEvaluator(myDelve, myThread, myFrameNo); // todo: was lazy, but not now, please check
  }

  /**
   * Returns an object that can be used to determine if two stack frames after a debugger step
   * are the same.
   *
   * @return The equality object.
   */
  @Nullable
  @Override
  public Object getEqualityObject() {
    // TODO: This would be better if we could actually determine if two frames represent the
    // same position, but it doesn't really matter, and this is good enough to stop the debugger
    // from collapsing variable trees when we step the debugger
    return DelveExecutionStackFrame.class;
  }

  /**
   * Returns an expression evaluator in the context of this stack frame.
   */
  @Nullable
  @Override
  public XDebuggerEvaluator getEvaluator() {
    return myEvaluator;
  }

  /**
   * Gets the source position of the stack frame, if available.
   *
   * @return The source position, or null if it is not available.
   */
  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    if (myFrame.fileAbsolute == null || myFrame.line == null) {
      return null;
    }

    String path = myFrame.fileAbsolute.replace(File.separatorChar, '/');
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) {
      return null;
    }

    return XDebuggerUtil.getInstance().createPosition(file, myFrame.line - 1);
  }

  /**
   * Controls the presentation of the frame in the stack trace.
   *
   * @param component The stack frame visual component.
   */
  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    if (myFrame.address == null) {
      component.append(XDebuggerBundle.message("invalid.frame"),
                       SimpleTextAttributes.ERROR_ATTRIBUTES);
      return;
    }

    // Format the frame information
    XSourcePosition sourcePosition = getSourcePosition();
    if (myFrame.function != null) {
      // Strip any arguments from the function name
      String function = myFrame.function;
      int parenIndex = function.indexOf('(');
      if (parenIndex != -1) {
        function = function.substring(0, parenIndex);
      }

      if (sourcePosition != null) {
        component.append(function + "():" + (sourcePosition.getLine() + 1),
                         SimpleTextAttributes.REGULAR_ATTRIBUTES);
        component.append(" (" + sourcePosition.getFile().getName() + ")",
                         SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
      }
      else {
        component.append(function + "()", SimpleTextAttributes.GRAY_ATTRIBUTES);
        component.append(" (", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        if (myFrame.module != null) {
          component.append(myFrame.module + ":",
                           SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
        component.append("0x" + Long.toHexString(myFrame.address) + ")",
                         SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
      }
    }
    else if (sourcePosition != null) {
      component.append(
        sourcePosition.getFile().getName() + ":" + (sourcePosition.getLine() + 1),
        SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else {
      String addressStr = "0x" + Long.toHexString(myFrame.address);
      component.append(addressStr, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
    component.setIcon(AllIcons.Debugger.StackFrame);
  }

  /**
   * Gets the variables available on this frame. This passes the request and returns immediately;
   * the data is supplied to node asynchronously.
   *
   * @param node The node into which the variables are inserted.
   */
  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    // TODO: This can be called multiple times if the user changes the value of a variable. We
    // shouldn't really call -stack-list-variables more than once in this case (i.e., only call
    // -var-update after the first call)
    myDelve.getVariablesForFrame(myThread, myFrameNo, new Delve.DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        onDelveVariablesReady(event, node);
      }
    });
  }

  /**
   * Callback function for when Delve has responded to our stack variables request.
   *
   * @param event The event.
   * @param node  The node passed to computeChildren().
   */
  private void onDelveVariablesReady(DelveEvent event, @NotNull final XCompositeNode node) {
    if (event instanceof DelveErrorEvent) {
      node.setErrorMessage(((DelveErrorEvent)event).message);
      return;
    }
    if (!(event instanceof DelveVariableObjects)) {
      node.setErrorMessage("Unexpected data received from Delve");
      LOG.warn("Unexpected event " + event + " received from variable objects request");
      return;
    }

    // Inspect the data
    DelveVariableObjects variables = (DelveVariableObjects)event;
    if (variables.objects == null || variables.objects.isEmpty()) {
      // No data
      node.addChildren(XValueChildrenList.EMPTY, true);
    }

    // Build a XValueChildrenList
    XValueChildrenList children = new XValueChildrenList(variables.objects.size());
    for (DelveVariableObject variable : variables.objects) {
      children.add(variable.expression, new DelveValue(myDelve, variable));
    }
    node.addChildren(children, true);
  }
}
