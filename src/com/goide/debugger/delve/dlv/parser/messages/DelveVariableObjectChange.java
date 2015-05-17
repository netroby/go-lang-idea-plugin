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

/**
 * The changes to a Delve variable object since the last update.
 */
@SuppressWarnings("unused")
public class DelveVariableObjectChange {
  /**
   * Possible scope values for the variable object.
   */
  public enum InScope {
    /**
     * The variable object is in scope and the value is valid.
     */
    True,
    /**
     * The value is not currently valid, but may come back into scope.
     */
    False,
    /**
     * The variable object is no longer valid because the file being debugged has changed.
     */
    Invalid
  }

  /**
   * The name of the variable object.
   */
  public String name;

  /**
   * The value of the variable.
   */
  public String value;

  /**
   * Whether the variable object is still in scope.
   */
  public InScope inScope;

  /**
   * Whether the value's type has changed since the last update
   */
  public Boolean typeChanged;

  /**
   * The new type, if typeChanged is true.
   */
  public String newType;

  /**
   * The new number of children of the object.
   */
  public Integer newNumChildren;

  /**
   * A hint about how to display the value.
   */
  public DelveVariableObject.DisplayHint displayHint;

  /**
   * For dynamic objects this specifies whether there appear to be any more children available.
   */
  public Boolean hasMore;

  /**
   * Whether this is a dynamic variable object.
   */
  public Boolean isDynamic;
}
