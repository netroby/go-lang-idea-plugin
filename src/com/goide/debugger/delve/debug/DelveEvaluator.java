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
import com.goide.debugger.delve.dlv.parser.messages.DelveErrorEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveVariableObject;
import com.goide.debugger.delve.dlv.parser.messages.DelveVariableObjects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Expression evaluator for Delve.
 */
public class DelveEvaluator extends XDebuggerEvaluator {
  private static final Logger LOG = Logger.getInstance(DelveEvaluator.class);

  // The Delve instance
  private final Delve myDelve;

  // The evaluation context
  private final int myThread;
  private final int myFrame;

  /**
   * Constructor.
   *
   * @param delve    Handle to the Delve instance.
   * @param thread The thread to evaluate expressions in.
   * @param frame  The frame to evaluate expressions in.
   */
  public DelveEvaluator(Delve delve, int thread, int frame) {
    myDelve = delve;
    myThread = thread;
    myFrame = frame;
  }

  /**
   * Evaluates the given expression.
   *
   * @param expression         The expression to evaluate.
   * @param callback           The callback function.
   * @param position ??
   */
  @Override
  public void evaluate(@NotNull String expression, @NotNull final XEvaluationCallback callback, @Nullable XSourcePosition position) {
    myDelve.evaluateExpression(myThread, myFrame, expression, new Delve.DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        onDelveExpressionReady(event, callback);
      }
    });
  }

  /**
   * Indicates whether we can evaluate code fragments.
   *
   * @return Whether we can evaluate code fragments.
   */
  @Override
  public boolean isCodeFragmentEvaluationSupported() {
    // TODO: Add support for this if possible
    return false;
  }

  /**
   * Callback function for when Delve has responded to our expression evaluation request.
   *
   * @param event    The event.
   * @param callback The callback passed to evaluate().
   */
  private void onDelveExpressionReady(DelveEvent event, @NotNull XEvaluationCallback callback) {
    if (event instanceof DelveErrorEvent) {
      callback.errorOccurred(((DelveErrorEvent)event).message);
      return;
    }
    if (!(event instanceof DelveVariableObjects)) {
      callback.errorOccurred("Unexpected data received from Delve");
      LOG.warn("Unexpected event " + event + " received from expression request");
      return;
    }

    DelveVariableObjects variableObjects = (DelveVariableObjects)event;
    if (variableObjects.objects.isEmpty()) {
      callback.errorOccurred("Failed to evaluate expression");
      return;
    }

    DelveVariableObject variableObject = variableObjects.objects.get(0);
    if (variableObject.value == null) {
      callback.errorOccurred("Failed to evaluate expression");
      return;
    }

    callback.evaluated(new DelveValue(myDelve, variableObject));
  }
}
