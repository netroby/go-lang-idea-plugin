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

import com.goide.GoIcons;
import com.goide.runconfig.GoConfigurationFactoryBase;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class DelveRunConfigurationType extends ConfigurationTypeBase {
  protected DelveRunConfigurationType() {
    super("GoDelveRunConfigurationType", "Go Delve Debug", "Go Delve debug configuration", GoIcons.DEBUG);
    addFactory(new GoConfigurationFactoryBase(DelveRunConfigurationType.this) {
      @NotNull
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new DelveRunConfiguration("", project, this);
      }

      @Override
      public boolean canConfigurationBeSingleton() {
        return false;
      }
    });
  }

  @NotNull
  public ConfigurationFactory getFactory() {
    ConfigurationFactory factory = ArrayUtil.getFirstElement(super.getConfigurationFactories());
    assert factory != null;
    return factory;
  }

  public static DelveRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(DelveRunConfigurationType.class);
  }
}
