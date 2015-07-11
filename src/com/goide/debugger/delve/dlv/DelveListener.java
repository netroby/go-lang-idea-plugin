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

package com.goide.debugger.delve.dlv;


import com.goide.debugger.delve.dlv.parser.messages.DelveEvent;

/**
 * Interface that users of the Delve class must implement to receive events.
 */
public interface DelveListener {
  /**
   * Called when a Delve error occurs.
   *
   * @param ex The exception
   */
  void onDelveError(Throwable ex);

  /**
   * Called when Delve has started.
   */
  void onDelveStarted();

  /**
   * Called whenever a command is sent to Delve.
   *
   * @param command The command that we've sent to delve
   */
  void onDelveCommandSent(DelveCommand command);

  /**
   * Called when an event is received from Delve.
   *
   * @param event The event.
   */
  void onDelveEventReceived(DelveEvent event);

  /**
   * Called when we receive output from the process
   *
   * @param output process output
   */
  void receiveOutput(String output);

  /**
   * Called when we receive error output from the process
   *
   * @param errorOutput process output
   */
  void receiveErrorOutput(String errorOutput);
}
