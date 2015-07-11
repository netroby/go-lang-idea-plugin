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

import com.goide.GoIcons;
import com.goide.debugger.delve.dlv.Delve;
import com.goide.debugger.delve.dlv.DelveCommand;
import com.goide.debugger.delve.dlv.parser.messages.DelveErrorEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveEvent;
import com.goide.debugger.delve.dlv.parser.messages.DelveVariableObject;
import com.goide.debugger.delve.dlv.parser.messages.DelveVariableObjects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.frame.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DelveValue extends XValue {
  private static final Icon VAR_ICON = GoIcons.VARIABLE; // todo: detect GoIcons.CONSTANT?
  private static final Logger LOG = Logger.getInstance(DelveValue.class);

  private final Delve myDelve;
  private final DelveVariableObject myVariableObject;

  public DelveValue(@NotNull Delve delve, @NotNull DelveVariableObject o) {
    myDelve = delve;
    myVariableObject = o;
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    String goType = getGoObjectType(myVariableObject.type);
    Boolean hasChildren = myVariableObject.numChildren != null && myVariableObject.numChildren > 0;

    if (goType.equals("string")) {
      handleGoString(node);
      return;
    }

    node.setPresentation(VAR_ICON, goType, notNull(myVariableObject.value), hasChildren);
  }

  public static String getGoObjectType(String originalType) {
    if (originalType.contains("struct string")) {
      return originalType.replace("struct ", "");
    }

    return originalType;
  }

  @Nullable
  @Override
  public XValueModifier getModifier() {
    // TODO: Return null if we don't support editing
    return new DelveValueModifier(myDelve, myVariableObject);
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (myVariableObject.numChildren == null || myVariableObject.numChildren <= 0) {
      node.addChildren(XValueChildrenList.EMPTY, true);
    }

    // Get the children from Delve
    DelveCommand command = new DelveCommand()
      .setCommand("-var-list-children")
      .addParam("--all-values " + myVariableObject.name);
    myDelve.sendCommand(command, new Delve.DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        onDelveChildrenReady(event, node);
      }
    });
  }

  private void onDelveChildrenReady(DelveEvent event, XCompositeNode node) {
    if (event instanceof DelveErrorEvent) {
      node.setErrorMessage(((DelveErrorEvent) event).message);
      return;
    }
    if (!(event instanceof DelveVariableObjects)) {
      node.setErrorMessage("Unexpected data received from Delve");
      LOG.warn("Unexpected event " + event + " received from -var-list-children request");
      return;
    }

    // Inspect the data
    DelveVariableObjects variables = (DelveVariableObjects) event;
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

  private void handleGoString(@NotNull final XValueNode node) {
    DelveCommand command = new DelveCommand()
      .setCommand("-var-list-children")
      .addParam("--all-values " + myVariableObject.name);
    myDelve.sendCommand(command, new Delve.DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        onGoDelveStringReady(node, event);
      }
    });
  }

  private void onGoDelveStringReady(@NotNull XValueNode node, @NotNull DelveEvent event) {
    Pair<String, Boolean> pair = strValue(event);
    String value = pair.first;
    Boolean isString = pair.second;
    if (isString) {
      if (value.equals("0x0")) value = "\"\"";
      node.setPresentation(VAR_ICON, "string (" + value.length() + ")", value, false);
    }
    else {
      Boolean hasChildren = myVariableObject.numChildren != null && myVariableObject.numChildren > 0;
      node.setPresentation(VAR_ICON, "unknown", notNull(myVariableObject.value), hasChildren);
    }
  }

  private static String notNull(@Nullable String value) {
    return StringUtil.notNullize(value, "<null>");
  }

  @NotNull
  private Pair<String, Boolean> strValue(@NotNull DelveEvent event) {
    String value = "Unexpected data received from Delve";
    if (event instanceof DelveErrorEvent) {
      return Pair.create(((DelveErrorEvent)event).message, false);
    }
    else if (!(event instanceof DelveVariableObjects)) {
      LOG.warn("Unexpected event " + event + " received from -var-list-children request");
      return Pair.create(value, false);
    }
    else {
      // Inspect the data
      DelveVariableObjects variables = (DelveVariableObjects)event;
      if (variables.objects != null && !variables.objects.isEmpty()) {
        String stringSubVar = myVariableObject.name + ".str";

        for (DelveVariableObject variable : variables.objects) {
          if (variable.name.equals(stringSubVar)) {
            return Pair.create(variable.value, true);
          }
        }
      }
    }
    return Pair.create(value, false);
  }
}
