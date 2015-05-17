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
 * A Delve variable object. This is returned from a -var-create request.
 */
@SuppressWarnings("unused")
public class DelveVariableObject extends DelveDoneEvent {
  /**
   * Possible display hints.
   */
  public enum DisplayHint {
    Array,
    Map,
    String
  }

  /**
   * The name of the variable object. Note this is NOT the name of the variable.
   */
  public String name;

  /**
   * The expression which the variable object represents.
   */
  public String expression;

  /**
   * The number of children of the object. This is not necessarily reliable for dynamic variable
   * objects, in which case you must use the hasMore field.
   */
  public Integer numChildren;

  /**
   * The scalar value of the variable. This should not be relied upon for aggregate types
   * (structs, etc.) or for dynamic variable objects.
   */
  public String value;

  /**
   * The type of the variable. Note this is the derived type of the object, which does not
   * necessarily match the declared type.
   */
  public String type;

  /**
   * The thread the variable is bound to, if any.
   */
  public Integer threadId;

  /**
   * Whether the variable object is frozen.
   */
  public Boolean isFrozen;

  /**
   * For dynamic objects this specifies whether there appear to be any more children available.
   */
  public Boolean hasMore;

  /**
   * Whether this is a dynamic variable object.
   */
  public Boolean isDynamic;

  /**
   * A hint about how to display the value.
   */
  public DisplayHint displayHint;
}
