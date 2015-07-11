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
import com.goide.debugger.delve.dlv.parser.messages.DelveDoneEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveErrorEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveVariableObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XValueModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Value modifier for Delve variables.
 */
public class DelveValueModifier extends XValueModifier {
  private static final Logger LOG = Logger.getInstance(DelveValueModifier.class);

  // Handle to the Delve instance
  private final Delve myDelve;
  private final DelveVariableObject myVariableObject;

  /**
   * Constructor.
   *
   * @param delve          Handle to the delve instance.
   * @param variableObject The variable object to modify.
   */
  public DelveValueModifier(Delve delve, DelveVariableObject variableObject) {
    myDelve = delve;
    myVariableObject = variableObject;
  }

  /**
   * Sets the new value.
   *
   * @param expression The expression to evaluate to set the value.
   * @param callback   The callback for when the operation is complete.
   */
  @Override
  public void setValue(@NotNull String expression, @NotNull final XModificationCallback callback) {
    // TODO: Format the expression properly
    DelveCommand command = new DelveCommand()
      .setCommand("-var-assign")
      .addParam(myVariableObject.name + " " + expression);
    myDelve.sendCommand(command, new Delve.DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        onDelveNewValueReady(event, callback);
      }
    });
  }

  /**
   * Returns the initial value to show in the editor.
   *
   * @return The initial value to show in the editor.
   */
  @Nullable
  @Override
  public String getInitialValueEditorText() {
    return myVariableObject.value;
  }

  /**
   * Callback function for when Delve has responded to our variable change request.
   *
   * @param event    The event.
   * @param callback The callback passed to setValue().
   */
  private static void onDelveNewValueReady(DelveEvent event, @NotNull XModificationCallback callback) {
    if (event instanceof DelveErrorEvent) {
      callback.errorOccurred(((DelveErrorEvent)event).message);
      return;
    }
    if (!(event instanceof DelveDoneEvent)) {
      callback.errorOccurred("Unexpected data received from Delve");
      LOG.warn("Unexpected event " + event + " received from -var-assign request");
      return;
    }

    // Notify the caller
    callback.valueModified();
  }
}
