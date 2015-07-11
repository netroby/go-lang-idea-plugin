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

import com.goide.debugger.delve.dlv.parser.messages.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.*;

public class Delve {
  private static final Logger LOG = Logger.getInstance(Delve.class);

  private final Map<String, DelveVariableObject> myVariableObjectsByExpression = new HashMap<String, DelveVariableObject>();
  private final Map<String, DelveVariableObject> myVariableObjectsByName = new HashMap<String, DelveVariableObject>();
  private final DelveListener myListener;
  private Process myProcess;
  private Boolean myStopping = false;

  private final Thread myReadThread;
  private Thread myWriteThread;

  private String delveHost = "127.0.0.1";
  private int delvePort = 6663;
  private long commandId = 0;

  // Handle to the ASCII character set
  @NotNull private static final Charset ourCharset = Charset.forName("UTF-8");

  // Token which the next Delve command will be sent with
  private long myToken = 0;

  // Commands that are waiting to be sent
  private Map<DelveCommand, DelveEventCallback> myQueuedCommands = new HashMap<DelveCommand, DelveEventCallback>();

  /**
   * Interface for callbacks for results from completed Delve commands.
   */
  public interface DelveEventCallback {
    /**
     * Called when a response to the command is received.
     *
     * @param event The event.
     */
    void onDelveCommandCompleted(DelveEvent event);
  }

  public Delve(final String delvePath, final String goFilePath, final String workingDirectory, DelveListener listener) {
    myListener = listener;
    myReadThread = new Thread(new Runnable() {
      @Override
      public void run() {
        runDelve(delvePath, goFilePath, workingDirectory);
      }
    });
  }

  public void start() {
    if (myReadThread.isAlive()) {
      throw new IllegalStateException("delve has already been started");
    }
    myReadThread.start();
  }

  private void runDelve(String delvePath, String goFilePath, @Nullable String workingDirectory) {
    try {
      // Launch the process
      final String[] commandLine = {
        delvePath,
        "-addr=\"" + delveHost + ":" + delvePort + "\"",
        "-log",
        "-headless",
        "run",
        goFilePath
      };
      File workingDirectoryFile = null;
      if (workingDirectory != null) {
        workingDirectoryFile = new File(workingDirectory);
      }

      Process process = Runtime.getRuntime().exec(commandLine, EnvironmentUtil.getEnvironment(), workingDirectoryFile);
      InputStream processOutput = process.getInputStream();
      InputStream processErrorOutput = process.getErrorStream();

      myQueuedCommands.put(new DelveCommand().setCommand("State"), null);

      // Save a reference to the process and launch the writer thread
      synchronized (this) {
        myProcess = process;
        myWriteThread = new Thread(new Runnable() {
          @Override
          public void run() {
            processCommandsQueue();
          }
        });
        myWriteThread.start();
      }

      // Start listening for data
      byte[] buffer = new byte[4096];
      byte[] errorBuffer = new byte[4096];
      int outputBytes, errorBytes = -1;
      while ((outputBytes = processOutput.read(buffer)) != -1 ||
             (errorBytes = processErrorOutput.read(errorBuffer)) != -1) {
        if (outputBytes != -1) {
          myListener.receiveOutput(new String(buffer));
        }
        if (errorBytes != -1) {
          myListener.receiveErrorOutput(new String(errorBuffer));
        }
      }
    }
    catch (Throwable ex) {
      myListener.onDelveError(ex);
    }
  }

  private void processCommandsQueue() {
    try {
      Map<DelveCommand, DelveEventCallback> queuedCommands = new HashMap<DelveCommand, DelveEventCallback>();
      while (true) {
        synchronized (this) {
          // Wait for some data to process
          while (myQueuedCommands.isEmpty()) {
            wait();
          }

          // Exit cleanly if we are stopping
          if (myStopping) {
            return;
          }

          // Swap the queues
          Map<DelveCommand, DelveEventCallback> tmp = queuedCommands;
          queuedCommands = myQueuedCommands;
          myQueuedCommands = tmp;
        }

        // Send the queued commands to Delve
        for (Map.Entry<DelveCommand, DelveEventCallback> pair : queuedCommands.entrySet()) {
          DelveCommand command = pair.getKey();
          DelveEventCallback callback = pair.getValue();
          myListener.onDelveCommandSent(command);
          runCommand(command, callback);
        }
        queuedCommands.clear();
      }
    }
    catch (InterruptedException ex) {
      // We are exiting
    }
    catch (Throwable ex) {
      myListener.onDelveError(ex);
    }
  }

  private void runCommand(DelveCommand command, DelveEventCallback callback) {
    commandId++;
    String payload = command.getJsonRPCCommand(commandId);

    String response;
    try {
      response = executeDelveRequest(payload);
    }
    catch (Exception ex) {
      LOG.error(ex);
      return;
    }

    if (callback == null) return;

    if (command.getCommand().equals("State")) {
      DelveStateEvent stateEvent = new DelveStateEvent();
      stateEvent.message = response;
      callback.onDelveCommandCompleted(stateEvent);
      return;
    }

    LOG.error("sending command: " + command + " with callback is not supported yet");
  }

  @Override
  protected synchronized void finalize() throws Throwable {
    super.finalize();

    // Terminate the I/O threads
    if (myReadThread != null) {
      myReadThread.interrupt();
      myReadThread.join();
    }
    if (myWriteThread != null) {
      myStopping = true;
      myWriteThread.notify();
      myWriteThread.interrupt();
      myWriteThread.join();
    }

    // Kill the process
    if (myProcess != null) {
      myProcess.destroy();
    }
  }

  public synchronized void sendCommand(String command) {
    DelveCommand cmd = DelveCommand.fromString(command);
    sendCommand(cmd, new DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        myListener.onDelveEventReceived(event);
      }
    });
  }

  public synchronized void sendCommand(DelveCommand command, DelveEventCallback callback) {
    myQueuedCommands.put(command, callback);
  }

  private String executeDelveRequest(String payload) {
    Socket dlvClient;
    DataOutputStream sendData;
    DataInputStream getData;

    String response = "";

    try {
      dlvClient = new Socket(delveHost, delvePort);
      sendData = new DataOutputStream(dlvClient.getOutputStream());
      getData = new DataInputStream(dlvClient.getInputStream());

      sendData.writeBytes(payload);
      //sendData.writeBytes();

      String responseLine;
      while ((responseLine = getData.readLine()) != null) {
        response += responseLine;
        break;
      }

      getData.close();
      sendData.close();
      dlvClient.close();
    }
    catch (Exception ignored) {
    }

    return response;
  }

  public void evaluateExpression(int thread, int frame, @NotNull final String expression,
                                 @NotNull final DelveEventCallback callback) {
    // TODO: Make this more efficient

    // Create a new variable object if necessary
    DelveVariableObject variableObject = myVariableObjectsByExpression.get(expression);
    if (variableObject == null) {
      DelveCommand command = new DelveCommand()
        .setCommand("--var-create")
        .addParam("--thread " + thread)
        .addParam("--frame " + frame)
        .addParam("- @ " + expression);
      sendCommand(command, new DelveEventCallback() {
        @Override
        public void onDelveCommandCompleted(DelveEvent event) {
          onDelveNewVariableObjectReady(event, expression, callback);
        }
      });
    }

    // Update existing variable objects
    DelveCommand command = new DelveCommand()
      .setCommand("-var-update")
      .addParam("--thread " + thread)
      .addParam("--frame " + frame)
      .addParam("--all-values *");
    sendCommand(command, new DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        HashSet<String> expressions = new HashSet<String>();
        expressions.add(expression);
        onDelveVariableObjectsUpdated(event, expressions, callback);
      }
    });
  }

  public void getVariablesForFrame(final int thread, final int frame,
                                   @NotNull final DelveEventCallback callback) {
    // Get a list of local variables
    DelveCommand command = new DelveCommand()
      .setCommand("-stack-list-variables")
      .addParam("--thread " + thread)
      .addParam("--frame " + frame)
      .addParam("--no-values");
    sendCommand(command, new DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        onDelveVariablesReady(event, thread, frame, callback);
      }
    });
  }

  private void onDelveVariablesReady(DelveEvent event, int thread, int frame,
                                     @NotNull final DelveEventCallback callback) {
    if (event instanceof DelveErrorEvent) {
      callback.onDelveCommandCompleted(event);
      return;
    }
    if (!(event instanceof DelveVariables)) {
      DelveErrorEvent errorEvent = new DelveErrorEvent();
      errorEvent.message = "Unexpected data received from Delve";
      callback.onDelveCommandCompleted(errorEvent);
      LOG.warn("Unexpected event " + event + " received from -stack-list-variables " +
               "request");
      return;
    }

    // Create variable objects for each of the variables if we haven't done so already
    final DelveVariables variables = (DelveVariables)event;
    for (final String variable : variables.variables.keySet()) {
      DelveVariableObject variableObject = myVariableObjectsByExpression.get(variable);
      if (variableObject == null) {
        DelveCommand command = new DelveCommand()
          .setCommand("-var-create")
          .addParam("--thread " + thread)
          .addParam("--frame " + frame)
          .addParam("- @ " + variable);

        sendCommand(command, new DelveEventCallback() {
          @Override
          public void onDelveCommandCompleted(DelveEvent event) {
            onDelveNewVariableObjectReady(event, variable, callback);
          }
        });
      }
    }

    // Update any existing variable objects
    DelveCommand command = new DelveCommand()
      .setCommand("-var-update")
      .addParam("--thread " + thread)
      .addParam("--frame " + frame)
      .addParam("--all-values *");
    sendCommand(command, new DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent event) {
        onDelveVariableObjectsUpdated(event, variables.variables.keySet(), callback);
      }
    });
  }

  private void onDelveNewVariableObjectReady(DelveEvent event, String expression, @NotNull DelveEventCallback callback) {
    if (event instanceof DelveErrorEvent) {
      callback.onDelveCommandCompleted(event);
      return;
    }
    if (!(event instanceof DelveVariableObject)) {
      DelveErrorEvent errorEvent = new DelveErrorEvent();
      errorEvent.message = "Unexpected data received from Delve";
      callback.onDelveCommandCompleted(errorEvent);
      LOG.warn("Unexpected event " + event + " received from -var-create request");
      return;
    }

    DelveVariableObject variableObject = (DelveVariableObject)event;
    if (variableObject.name == null) {
      DelveErrorEvent errorEvent = new DelveErrorEvent();
      errorEvent.message = "Unexpected data received from Delve";
      callback.onDelveCommandCompleted(errorEvent);
      LOG.warn("Variable object returned by Delve does not have a name");
      return;
    }

    // Save the new variable object
    variableObject.expression = expression;
    myVariableObjectsByExpression.put(expression, variableObject);
    myVariableObjectsByName.put(variableObject.name, variableObject);
  }

  /**
   * Callback function for when Delve has responded to our variable objects update request.
   *
   * @param variables The variables the user requested.
   * @param callback  The user-provided callback function.
   * @pa\ram event     The event.
   */
  private void onDelveVariableObjectsUpdated(DelveEvent event, @NotNull Set<String> variables,
                                             @NotNull DelveEventCallback callback) {
    if (event instanceof DelveErrorEvent) {
      callback.onDelveCommandCompleted(event);
      return;
    }
    if (!(event instanceof DelveVariableObjectChanges)) {
      DelveErrorEvent errorEvent = new DelveErrorEvent();
      errorEvent.message = "Unexpected data received from Delve";
      callback.onDelveCommandCompleted(errorEvent);
      LOG.warn("Unexpected event " + event + " received from -var-create request");
      return;
    }

    // Update variable objects with changes
    DelveVariableObjectChanges changes = (DelveVariableObjectChanges)event;
    if (changes.changes != null) {
      for (DelveVariableObjectChange change : changes.changes) {
        if (change.name == null) {
          LOG.warn("Received a Delve variable object change with no name");
          continue;
        }

        DelveVariableObject variableObject = myVariableObjectsByName.get(change.name);
        if (variableObject == null) {
          LOG.warn("Received a Delve variable object change for a variable object " +
                   "that does not exist");
          continue;
        }

        // Set the new value
        switch (change.inScope) {
          case True:
            // Update the value
            variableObject.value = change.value;
            break;

          case False:
            // Reset the value
            variableObject.value = null;
            break;

          default:
            // TODO: Delete the variable object
            variableObject.value = null;
        }

        // Set the new type
        if (change.typeChanged && change.newType != null) {
          variableObject.type = change.newType;
        }
      }
    }

    // Construct the list of variable object the user requested
    DelveVariableObjects list = new DelveVariableObjects();
    list.objects = new ArrayList<DelveVariableObject>();
    for (String expression : variables) {
      DelveVariableObject object = myVariableObjectsByExpression.get(expression);
      if (object != null) {
        list.objects.add(object);
      }
    }

    callback.onDelveCommandCompleted(list);
  }
}
