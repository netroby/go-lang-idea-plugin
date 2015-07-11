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

import com.goide.debugger.delve.debug.breakpoints.DelveBreakpointHandler;
import com.goide.debugger.delve.debug.breakpoints.DelveBreakpointProperties;
import com.goide.debugger.delve.dlv.Delve;
import com.goide.debugger.delve.dlv.DelveCommand;
import com.goide.debugger.delve.dlv.DelveListener;
import com.goide.debugger.delve.dlv.parser.messages.*;
import com.goide.debugger.delve.run.DelveExecutionResult;
import com.goide.debugger.delve.run.DelveRunConfiguration;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class DelveDebugProcess extends XDebugProcess implements DelveListener {
  private static final Logger LOG = Logger.getInstance(DelveDebugProcess.class);

  private final DelveDebuggerEditorsProvider myEditorsProvider = new DelveDebuggerEditorsProvider();
  @NotNull private final ConsoleView myConsole;
  @NotNull private final DelveBreakpointHandler myBreakpointHandler;
  private final DelveRunConfiguration myConfiguration;
  @NotNull private final Delve myDelve;
  private final DelveConsoleView myDelveConsole;
  private final SimpleDateFormat myTimeFormat = new SimpleDateFormat("HH:mm:ss.SSS");

  /**
   * Constructor; launches delve
   */
  public DelveDebugProcess(@NotNull XDebugSession session, DelveExecutionResult executionResult) {
    super(session);

    myConsole = (ConsoleView)executionResult.getExecutionConsole();

    myConfiguration = executionResult.getConfiguration();
    String delvePath = myConfiguration.DELVE_PATH;
    String goFilePath = myConfiguration.GO_FILE_PATH;
    String workingDirectoryPath = myConfiguration.WORKING_DIRECTORY_PATH;
    myDelve = new Delve(delvePath, goFilePath, workingDirectoryPath, this);
    myDelveConsole = new DelveConsoleView(myDelve, session.getProject());
    myBreakpointHandler = new DelveBreakpointHandler(myDelve, this);
    myDelve.start();
  }

  @NotNull
  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return new DelveBreakpointHandler[]{myBreakpointHandler};
  }

  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @Override
  public void startStepOver() {
    LOG.warn("startStepOver: stub");
  }

  @Override
  public void startStepInto() {
    LOG.warn("startStepInto: stub");
  }

  @Override
  public void startStepOut() {
    LOG.warn("startStepOut: stub");
  }

  @Override
  public void resume() {
    LOG.warn("resume: stub");
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position) {
    LOG.warn("runToPosition: stub");
  }

  @NotNull
  @Override
  public ExecutionConsole createConsole() {
    return myConsole;
  }

  @NotNull
  @Override
  public XDebugTabLayouter createTabLayouter() {
    return new XDebugTabLayouter() {
      @Override
      public void registerAdditionalContent(@NotNull RunnerLayoutUi ui) {
        Content delveConsoleContent = ui.createContent("DelveConsoleContent",
                                                       myDelveConsole.getComponent(), "Delve Console", AllIcons.Debugger.Console,
                                                       myDelveConsole.getPreferredFocusableComponent());
        delveConsoleContent.setCloseable(false);

        // Create the actions
        final DefaultActionGroup consoleActions = new DefaultActionGroup();
        AnAction[] actions = myDelveConsole.getConsole().createConsoleActions();
        for (AnAction action : actions) {
          consoleActions.add(action);
        }
        delveConsoleContent.setActions(consoleActions, ActionPlaces.DEBUGGER_TOOLBAR,
                                       myDelveConsole.getConsole().getPreferredFocusableComponent());

        ui.addContent(delveConsoleContent, 2, PlaceInGrid.bottom, false);
      }
    };
  }

  /**
   * Called when a Delve error occurs.
   *
   * @param ex The exception
   */
  public void onDelveError(final Throwable ex) {
    LOG.error("Delve error", ex);
  }

  /**
   * Called when Delve has started.
   */
  public void onDelveStarted() {
    // TODO Add default start commands here
  }

  /**
   * Called whenever a command is sent to Delve.
   *
   * @param command The command that was sent.
   */
  public void onDelveCommandSent(DelveCommand command) {
    myDelveConsole
      .getConsole()
      .print(myTimeFormat.format(new Date())
             + " " + command.getId()
             + "> " + command.getJsonRPCCommand()
             + "\n",
             ConsoleViewContentType.USER_INPUT);
  }

  @Override
  public void receiveOutput(String output) {
    myDelveConsole
      .getConsole()
      .print(output, ConsoleViewContentType.NORMAL_OUTPUT);
  }

  @Override
  public void receiveErrorOutput(String errorOutput) {
    myDelveConsole
      .getConsole()
      .print(errorOutput, ConsoleViewContentType.ERROR_OUTPUT);
  }

  /**
   * Called when a Delve event is received.
   *
   * @param event The event.
   */
  public void onDelveEventReceived(DelveEvent event) {
    if (event instanceof DelveStoppedEvent) {
      // Target has stopped
      onDelveStoppedEvent((DelveStoppedEvent)event);
    }
    else if (event instanceof DelveRunningEvent) {
      // Target has started
      getSession().sessionResumed();
    }
  }

  /**
   * Handles a 'target stopped' event from Delve.
   *
   * @param event The event
   */
  private void onDelveStoppedEvent(@NotNull final DelveStoppedEvent event) {
    // Get information about the threads
    DelveCommand command = new DelveCommand().setCommand("-thread-info");

    myDelve.sendCommand(command, new Delve.DelveEventCallback() {
      @Override
      public void onDelveCommandCompleted(DelveEvent threadInfoEvent) {
        onDelveThreadInfoReady(threadInfoEvent, event);
      }
    });
  }

  /**
   * Callback function for when Delve has responded to our thread information request.
   *
   * @param threadInfoEvent The event.
   * @param stoppedEvent    The 'target stopped' event that caused us to make the request.
   */
  private void onDelveThreadInfoReady(DelveEvent threadInfoEvent, @NotNull DelveStoppedEvent stoppedEvent) {
    List<DelveThread> threads = null;

    if (threadInfoEvent instanceof DelveErrorEvent) {
      LOG.warn("Failed to get thread information: " +
               ((DelveErrorEvent)threadInfoEvent).message);
    }
    else if (!(threadInfoEvent instanceof DelveThreadInfo)) {
      LOG.warn("Unexpected event " + threadInfoEvent + " received from -thread-info " +
               "request");
    }
    else {
      threads = ((DelveThreadInfo)threadInfoEvent).threads;
    }

    // Handle the event
    handleTargetStopped(stoppedEvent, threads);
  }

  /**
   * Handles a 'target stopped' event.
   *
   * @param stoppedEvent The event.
   * @param threads      Thread information, if available.
   */
  private void handleTargetStopped(@NotNull DelveStoppedEvent stoppedEvent, List<DelveThread> threads) {
    DelveSuspendContext suspendContext = new DelveSuspendContext(myDelve, stoppedEvent, threads);

    // Find the breakpoint if necessary
    XBreakpoint<DelveBreakpointProperties> breakpoint = null;
    if (stoppedEvent.reason == DelveStoppedEvent.Reason.BreakpointHit &&
        stoppedEvent.breakpointNumber != null) {
      breakpoint = myBreakpointHandler.findBreakpoint(stoppedEvent.breakpointNumber);
    }

    if (breakpoint != null) {
      // TODO: Support log expressions
      boolean suspendProcess = getSession().breakpointReached(breakpoint, null,
                                                              suspendContext);
      if (!suspendProcess) {
        // Resume execution
        resume();
      }
    }
    else {
      getSession().positionReached(suspendContext);
    }
  }

  @NotNull
  public Delve getDelve() {
    return myDelve;
  }
}
