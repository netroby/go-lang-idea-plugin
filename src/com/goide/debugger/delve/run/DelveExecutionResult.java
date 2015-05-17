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

package com.goide.debugger.delve.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import org.jetbrains.annotations.NotNull;

public class DelveExecutionResult extends DefaultExecutionResult {
  public DelveRunConfiguration myConfiguration;

  public DelveExecutionResult(ExecutionConsole console,
                            @NotNull ProcessHandler processHandler,
                            DelveRunConfiguration configuration) {
    super(console, processHandler);
    myConfiguration = configuration;
  }

  public DelveRunConfiguration getConfiguration() {
    return myConfiguration;
  }
}
