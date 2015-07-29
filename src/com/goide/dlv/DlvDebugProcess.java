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

package com.goide.dlv;

import com.goide.GoFileType;
import com.goide.GoLanguage;
import com.goide.dlv.breakpoint.DlvBreakpointHandler;
import com.goide.dlv.breakpoint.DlvBreakpointProperties;
import com.goide.dlv.protocol.DlvApi;
import com.goide.dlv.protocol.DlvRequest;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.socketConnection.ConnectionStatus;
import com.intellij.util.io.socketConnection.SocketConnectionListener;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.DebugProcessImpl;
import org.jetbrains.debugger.connection.RemoteVmConnection;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DlvDebugProcess extends DebugProcessImpl<RemoteVmConnection> implements Disposable {
  public static final Consumer<Throwable> THROWABLE_CONSUMER = new Consumer<Throwable>() {
    @Override
    public void consume(@NotNull Throwable throwable) {
      throwable.printStackTrace();
    }
  };

  private Consumer<DlvApi.DebuggerState> myStateConsumer = new Consumer<DlvApi.DebuggerState>() {
    @Override
    public void consume(@Nullable final DlvApi.DebuggerState o) {
      if (o == null || o.exited) {
        getSession().stop();
        return;
      }

      final XBreakpoint<DlvBreakpointProperties> find = findBreak(o.breakPoint);
      final DlvCommandProcessor processor = getProcessor();
      Promise<List<DlvApi.Location>> stackPromise = processor.send(new DlvRequest.Stacktrace());
      stackPromise.processed(new Consumer<List<DlvApi.Location>>() {
        @Override
        public void consume(@NotNull List<DlvApi.Location> locations) {
          DlvSuspendContext context = new DlvSuspendContext(o.currentThread.id, locations, processor);
          if (find == null) {
            getSession().positionReached(context);
          }
          else {
            getSession().breakpointReached(find, null, context);
          }
        }
      });
      stackPromise.rejected(THROWABLE_CONSUMER);
    }

    @Nullable
    private XBreakpoint<DlvBreakpointProperties> findBreak(final DlvApi.Breakpoint point) {
      return point != null ? ContainerUtil.find(breakpoints, new Condition<XBreakpoint<DlvBreakpointProperties>>() {
        @Override
        public boolean value(@NotNull XBreakpoint<DlvBreakpointProperties> b) {
          return Comparing.equal(b.getUserData(ID), point.id);
        }
      }) : null;
    }
  };

  private DlvCommandProcessor getProcessor() {
    return ((DlvVm)getVm()).getCommandProcessor();
  }

  public DlvDebugProcess(@NotNull XDebugSession session,
                         @NotNull RemoteVmConnection connection) {
    super(session, connection, new MyEditorsProvider(), null, null);
    breakpointHandlers = new XBreakpointHandler[]{new DlvBreakpointHandler(this)};
  }

  @Override
  protected boolean isVmStepOutCorrect() {
    return false;
  }

  @Override
  public void dispose() {
    // todo
  }

  private final AtomicBoolean breakpointsInitiated = new AtomicBoolean();
  private final AtomicBoolean connectedListenerAdded = new AtomicBoolean();


  @Override
  public boolean checkCanInitBreakpoints() {
    if (connection.getState().getStatus() == ConnectionStatus.CONNECTED) {
      // breakpointsInitiated could be set in another thread and at this point work (init breakpoints) could be not yet performed
      return initBreakpointHandlersAndSetBreakpoints(false);
    }

    if (connectedListenerAdded.compareAndSet(false, true)) {
      connection.addListener(new SocketConnectionListener() {
        @Override
        public void statusChanged(@NotNull ConnectionStatus status) {
          if (status == ConnectionStatus.CONNECTED) {
            initBreakpointHandlersAndSetBreakpoints(true);
          }
        }
      });
    }
    return false;
  }

  private boolean initBreakpointHandlersAndSetBreakpoints(boolean setBreakpoints) {
    if (!breakpointsInitiated.compareAndSet(false, true)) return false;

    assert getVm() != null : "Vm should be initialized";

    if (setBreakpoints) {
      doSetBreakpoints();
      resume();
    }

    return true;
  }

  private void doSetBreakpoints() {
    AccessToken token = ReadAction.start();
    try {
      getSession().initBreakpoints();
    }
    finally {
      token.finish();
    }
  }

  public static final Key<Integer> ID = Key.create("ID");

  @NotNull Set<XBreakpoint<DlvBreakpointProperties>> breakpoints = ContainerUtil.newConcurrentSet();

  public void addBreakpoint(@NotNull final XLineBreakpoint<DlvBreakpointProperties> breakpoint) {
    XSourcePosition breakpointPosition = breakpoint.getSourcePosition();
    if (breakpointPosition == null) return;
    VirtualFile file = breakpointPosition.getFile();
    int line = breakpointPosition.getLine();
    DlvVm vm = (DlvVm)getVm();
    Promise<DlvApi.Breakpoint> promise = vm.getCommandProcessor().send(new DlvRequest.SetBreakpoint(file.getCanonicalPath(), line + 1));
    promise.processed(new Consumer<DlvApi.Breakpoint>() {
      @Override
      public void consume(@Nullable DlvApi.Breakpoint b) {
        if (b != null) {
          breakpoint.putUserData(ID, b.id);
          breakpoints.add(breakpoint);
          getSession().updateBreakpointPresentation(breakpoint, AllIcons.Debugger.Db_verified_breakpoint, null);
        }
      }
    });
    promise.rejected(new Consumer<Throwable>() {
      @Override
      public void consume(@Nullable Throwable t) {
        getSession().updateBreakpointPresentation(breakpoint, AllIcons.Debugger.Db_invalid_breakpoint, t == null ? null : t.getMessage());
      }
    });
  }

  public void removeBreakpoint(@NotNull XLineBreakpoint<DlvBreakpointProperties> breakpoint) {
    XSourcePosition breakpointPosition = breakpoint.getSourcePosition();
    if (breakpointPosition == null) return;
    Integer id = breakpoint.getUserData(ID);
    breakpoints.remove(breakpoint);
    if (id == null) return;
    DlvVm vm = (DlvVm)getVm();
    Promise<DlvApi.Breakpoint> promise = vm.getCommandProcessor().send(new DlvRequest.ClearBreakpoint(id));
    promise.rejected(THROWABLE_CONSUMER);
  }

  private void send(@NotNull
                    @MagicConstant(stringValues = {DlvApi.NEXT, DlvApi.CONTINUE, DlvApi.HALT, DlvApi.SWITCH_THREAD, DlvApi.STEP}) String name) {
    Promise<DlvApi.DebuggerState> promise = getProcessor().send(new DlvRequest.Command(name));
    promise.processed(myStateConsumer);
    promise.rejected(THROWABLE_CONSUMER);
  }

  @Override
  public void startStepOver() {
    send(DlvApi.NEXT);
  }

  @Override
  public void startStepInto() {
    send(DlvApi.STEP);
  }

  @Override
  public void startStepOut() {
    // todo
  }

  @Override
  public void resume() {
    send(DlvApi.CONTINUE);
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position) {
    // todo
  }

  @Override
  public void stop() {
    getSession().stop();
  }

  private static class MyEditorsProvider extends XDebuggerEditorsProviderBase {
    @NotNull
    @Override
    public FileType getFileType() {
      return GoFileType.INSTANCE;
    }

    @Override
    protected PsiFile createExpressionCodeFragment(@NotNull Project project,
                                                   @NotNull String text,
                                                   PsiElement context,
                                                   boolean isPhysical) {
      return PsiFileFactory.getInstance(project).createFileFromText("a.go", GoLanguage.INSTANCE, text);
    }
  }
}