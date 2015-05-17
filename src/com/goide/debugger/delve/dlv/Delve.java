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

import com.goide.debugger.delve.dlv.parser.DelveParser;
import com.goide.debugger.delve.dlv.parser.DelveRecord;
import com.goide.debugger.delve.dlv.parser.messages.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

public class Delve {
  private static final Logger LOG = Logger.getInstance(Delve.class);
  private final String USER_AGENT = ApplicationInfo.getInstance().getFullVersion();
  private final String BASE_URL = "http://127.0.0.1:6663";

  private final Map<String, DelveVariableObject> myVariableObjectsByExpression = new HashMap<String, DelveVariableObject>();
  private final Map<String, DelveVariableObject> myVariableObjectsByName = new HashMap<String, DelveVariableObject>();
  private final DelveListener myListener;
  private Process myProcess;
  private Boolean myStopping = false;

  private final Thread myReadThread;
  private Thread myWriteThread;
  private boolean myFirstRecord;

  // Handle to the ASCII character set
  @NotNull private static final Charset ourCharset = Charset.forName("UTF-8");

  // Token which the next Delve command will be sent with
  private long myToken = 0;

  // Commands that are waiting to be sent
  private List<CommandData> myQueuedCommands = new ArrayList<CommandData>();

  // Commands that have been sent to GDB and are awaiting a response
  private final Map<Long, CommandData> myPendingCommands = new HashMap<Long, CommandData>();

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

  private static class CommandData {
    /**
     * The command
     */
    @NotNull
    String command;

    /**
     * The user provided callback; may be null
     */
    @Nullable
    DelveEventCallback callback;

    CommandData(@NotNull String command) {
      this.command = command;
    }

    CommandData(@NotNull String command, @Nullable DelveEventCallback callback) {
      this.command = command;
      this.callback = callback;
    }
  }

  public Delve(final String delvePath, final String workingDirectory, DelveListener listener) {
    myListener = listener;
    myReadThread = new Thread(new Runnable() {
      @Override
      public void run() {
        runDelve(delvePath, workingDirectory);
      }
    });
  }

  public void start() {
    if (myReadThread.isAlive()) {
      throw new IllegalStateException("delve has already been started");
    }
    myReadThread.start();
  }

  private void runDelve(String delvePath, @Nullable String workingDirectory) {
    try {
      // Launch the process
      final String[] commandLine = {
        delvePath,
        "-addr=\"127.0.0.1:6663\"",
        "-log",
        "-headless",
        "run",
        "/home/florinp/golang/src/github.com/dlsniper/u/u.go"
      };
      File workingDirectoryFile = null;
      if (workingDirectory != null) {
        workingDirectoryFile = new File(workingDirectory);
      }

      Process process = Runtime.getRuntime().exec(commandLine, EnvironmentUtil.getEnvironment(), workingDirectoryFile);
      InputStream stream = process.getInputStream();

      // Queue startup commands
      // TODO here we should list the version and stuff maybe? If not, skip it
      sendCommand("state", new DelveEventCallback() {
        @Override
        public void onDelveCommandCompleted(DelveEvent event) {
          onStateReady(event);
        }
      });

      // Save a reference to the process and launch the writer thread
      synchronized (this) {
        myProcess = process;
        myWriteThread = new Thread(new Runnable() {
          @Override
          public void run() {
            processWriteQueue();
          }
        });
        myWriteThread.start();
      }

      // Start listening for data
      DelveParser parser = new DelveParser();
      byte[] buffer = new byte[4096];
      int bytes;
      while ((bytes = stream.read(buffer)) != -1) {
        // Process the data
        try {
          parser.process(buffer, bytes);
        }
        catch (IllegalArgumentException ex) {
          LOG.error("Delve parsing error. Current buffer contents: \"" +
                    new String(buffer, 0, bytes) + "\"", ex);
          myListener.onDelveError(ex);
          return;
        }

        // Handle the records
        List<DelveRecord> records = parser.getRecords();
        for (DelveRecord record : records) {
          handleRecord(record);
        }
        records.clear();
      }
    }
    catch (Throwable ex) {
      myListener.onDelveError(ex);
    }
  }

  private void processWriteQueue() {
    try {
      OutputStream stream;
      List<CommandData> queuedCommands = new ArrayList<CommandData>();
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

          // Do the processing we need before dropping out of synchronised mode
          stream = myProcess.getOutputStream();

          // Insert the commands into the pending queue
          long token = myToken;
          for (CommandData command : myQueuedCommands) {
            myPendingCommands.put(token++, command);
          }

          // Swap the queues
          List<CommandData> tmp = queuedCommands;
          queuedCommands = myQueuedCommands;
          myQueuedCommands = tmp;
        }

        // Send the queued commands to GDB
        StringBuilder sb = new StringBuilder();
        for (CommandData command : queuedCommands) {
          // Construct the message
          long token = myToken++;
          myListener.onDelveCommandSent(command.command, token);

          sb.append(token);
          sb.append(command.command);
          sb.append("\r\n");
        }
        queuedCommands.clear();

        // Send the messages
        byte[] message = sb.toString().getBytes(ourCharset);
        stream.write(message);
        stream.flush();
      }
    }
    catch (InterruptedException ex) {
      // We are exiting
    }
    catch (Throwable ex) {
      myListener.onDelveError(ex);
    }
  }

  private void handleRecord(@NotNull DelveRecord record) {
    switch (record.type) {
      case Target:
      case Console:
      case Log:
        //handleStreamRecord((GdbMiStreamRecord)record);
        break;

      case Immediate:
      case Exec:
      case Notify:
      case Status:
        //handleResultRecord((GdbMiResultRecord)record);
        break;
    }

    // If this is the first record we have received we know we are fully started, so notify the
    // listener
    if (myFirstRecord) {
      myFirstRecord = false;
      myListener.onDelveStarted();
    }
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

  private void onStateReady(DelveEvent event) {
    if (event instanceof DelveErrorEvent) {
      LOG.warn("Failed to get GDB capabilities list: " + ((DelveErrorEvent)event).message);
      return;
    }
    if (!(event instanceof DelveStateEvent)) {
      LOG.warn("Unexpected event " + event + " received from state request");
      return;
    }
  }

  public String sendCommand(String command) {
    return sendCommand(command, "");
  }

  private String sendCommand(String command, String arguments) {
    String url = BASE_URL;
    String requestType = "GET";
    if ("state".equals(command)) {
      url += "/state";
    } else {
      LOG.error(String.format("Command %s not found", command));
      return "";
    }

    String response = "";
    try {
      if (requestType == "GET") {
        response = executeGet(url);
      } else if (requestType == "POST") {
        response = executePost(url, arguments);
      }
    } catch (Exception ex) {
      LOG.error(ex);
    }

    return response;
  }

  public synchronized void sendCommand(String command, DelveEventCallback callback) {
    if (command.equals("state")) {
      String response = sendCommand("state", "");
      DelveStateEvent stateEvent = new DelveStateEvent();
      stateEvent.message = response;
      callback.onDelveCommandCompleted(stateEvent);
      return;
    }

    LOG.error("sending command: " + command + " with callback is not supported yet");
  }

  private String executeGet(String url) throws Exception {
    URL obj = new URL(url);
    HttpURLConnection con = (HttpURLConnection) obj.openConnection();
    con.setRequestMethod("GET");
    con.setRequestProperty("User-Agent", USER_AGENT);
    int responseCode = con.getResponseCode();
    LOG.info("\nSending 'GET' request to URL : " + url);
    LOG.info("Response Code : " + responseCode);

    if (responseCode != 200) {
      throw new Exception("Unexpected response received");
    }

    String inputLine;
    StringBuilder response = new StringBuilder();

    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
    try {
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
    } finally {
      in.close();
    }

    return response.toString();
  }

  private String executePost(String url, String payload) throws Exception {
    URL obj = new URL(url);
    HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
    con.setRequestMethod("POST");
    con.setRequestProperty("User-Agent", USER_AGENT);
    con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

    // Send post request
    con.setDoOutput(true);
    DataOutputStream wr = new DataOutputStream(con.getOutputStream());;
    try {
      wr.writeBytes(payload);
      wr.flush();
    } finally {
      wr.close();
    }

    int responseCode = con.getResponseCode();
    LOG.info("\nSending 'POST' request to URL : " + url);
    LOG.info("Post parameters : " + payload);
    LOG.info("Response Code : " + responseCode);

    if (responseCode != 200) {
      throw new Exception("Unexpected response received");
    }

    String inputLine;
    StringBuilder response = new StringBuilder();
    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
    try {
      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
    } finally {
      in.close();
    }

    return response.toString();
  }

  public void evaluateExpression(int thread, int frame, @NotNull final String expression,
                                 @NotNull final DelveEventCallback callback) {
    // TODO: Make this more efficient

    // Create a new variable object if necessary
    DelveVariableObject variableObject = myVariableObjectsByExpression.get(expression);
    if (variableObject == null) {
      String command = "-var-create --thread " + thread + " --frame " + frame + " - @ " + expression;
      sendCommand(command, new DelveEventCallback() {
        @Override
        public void onDelveCommandCompleted(DelveEvent event) {
          onDelveNewVariableObjectReady(event, expression, callback);
        }
      });
    }

    // Update existing variable objects
    sendCommand("-var-update --thread " + thread + " --frame " + frame + " --all-values *",
                new DelveEventCallback() {
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
    String command = "-stack-list-variables --thread " + thread + " --frame " + frame +
                     " --no-values";
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
        String command = "-var-create --thread " + thread + " --frame " + frame + " - @ " +
                         variable;
        sendCommand(command, new DelveEventCallback() {
          @Override
          public void onDelveCommandCompleted(DelveEvent event) {
            onDelveNewVariableObjectReady(event, variable, callback);
          }
        });
      }
    }

    // Update any existing variable objects
    sendCommand("-var-update --thread " + thread + " --frame " + frame + " --all-values *",
                new DelveEventCallback() {
                  @Override
                  public void onDelveCommandCompleted(DelveEvent event) {
                    onDelveVariableObjectsUpdated(event, variables.variables.keySet(), callback);
                  }
                });
  }

  private void onDelveNewVariableObjectReady(DelveEvent event, String expression,
                                           @NotNull DelveEventCallback callback) {
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
   * @param event     The event.
   * @param variables The variables the user requested.
   * @param callback  The user-provided callback function.
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
