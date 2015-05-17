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

import java.util.Map;

/**
 * Class representing information about a stack frame from Delve.
 */
@SuppressWarnings("unused")
public class DelveStackFrame {
  /**
   * The position of the frame within the stack, where zero is the top of the stack.
   */
  public Integer level;

  /**
   * The execution address.
   */
  public Long address;

  /**
   * The name of the function.
   */
  public String function;

  /**
   * The arguments to the function.
   */
  public Map<String, String> arguments;

  /**
   * The relative path to the file being executed.
   */
  public String fileRelative;

  /**
   * The absolute path to the file being executed.
   */
  public String fileAbsolute;

  /**
   * The line number being executed.
   */
  public Integer line;

  /**
   * The module where the function is defined.
   */
  public String module;
}
