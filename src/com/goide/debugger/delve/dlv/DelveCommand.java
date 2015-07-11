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

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DelveCommand {
  private long id = 0;
  private String method = "";
  private ArrayList<Object> params = new ArrayList<Object>();

  @NotNull
  public String getCommand() {
    return method;
  }

  public DelveCommand setCommand(@NotNull String command) {
    this.method = command;
    return this;
  }

  @NotNull
  public List<Object> getArguments() {
    return params;
  }

  public DelveCommand addParam(@NotNull Object argument) {
    params.add(argument);
    return this;
  }

  public long getId() {
    return id;
  }

  public String getJsonRPCCommand(long commandId) {
    id = commandId;
    String cmd = method;
    if (!method.startsWith("RPCServer.")) {
      method = "RPCServer.".concat(method);
    }
    Gson gson = new Gson();
    String json = gson.toJson(this);
    method = cmd;
    return json;
  }

  public String getJsonRPCCommand() {
    String cmd = method;
    if (!method.startsWith("RPCServer.")) {
      method = "RPCServer.".concat(method);
    }
    Gson gson = new Gson();
    String json = gson.toJson(this);
    method = cmd;
    return json;
  }

  public static DelveCommand fromString(String command) {
    Gson gson = new Gson();
    return gson.fromJson(command, DelveCommand.class);
  }
}
