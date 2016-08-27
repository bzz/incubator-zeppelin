/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.zeppelin.python2;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Properties;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.python2.rpc.PythonInterpreterGrpc;
import org.apache.zeppelin.python2.rpc.PythonInterpreterGrpc.PythonInterpreterBlockingStub;
import org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.CodeInterpreteRequest;
import org.apache.zeppelin.python2.rpc.PythonInterpreterOuterClass.InterpetedResult;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Python interpreter for Zeppelin.
 *
 * Current implementation is thread-safe,
 * but by design it can serve only 1 simultanius request at the time:
 * it delegates everything to a single Python process.
 *
 * So it's intended to be used with FIFOScheduler only.
 */
public class PythonInterpreter2 extends Interpreter {
  private static final Logger LOG = LoggerFactory.getLogger(PythonInterpreter2.class);

  public static final String INTERPRETER_PY = "/interpreter.py";
  public static final String ZEPPELIN_PYTHON = "zeppelin.python";
  public static final String DEFAULT_ZEPPELIN_PYTHON = "python";
  public static final String MAX_RESULT = "zeppelin.python.maxResult";

  private int maxResult;
  private int serverPort = 9090;
  private String serverHost =  "localhost";

  private PythonInterpreterBlockingStub blockingStub;


  public PythonInterpreter2(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    LOG.info("Starting Python interpreter .....");
    LOG.info("Python path is set to:" + property.getProperty(ZEPPELIN_PYTHON));

    //TODO(bzz):
    //%dep grpcio || %dep pip install grpcio
    //this.serverPort = `pick open port`
    //start `interpreter.py serverPort`

    ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
        .forAddress(this.serverHost, this.serverPort)
        .usePlaintext(true);
    ManagedChannel channel = channelBuilder.build();
    blockingStub = PythonInterpreterGrpc.newBlockingStub(channel);
  }

  @Override
  public void close() {
    LOG.info("closing Python interpreter .....");
    blockingStub.shutdown(null);
//    LOG.error("Can't close the interpreter", e);
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    if (cmd == null || cmd.isEmpty()) {
      return new InterpreterResult(Code.SUCCESS, "");
    }
    String noteId = contextInterpreter.getNoteId();
    return sendCommandToPython(cmd, noteId);
  }

  @Override
  public void cancel(InterpreterContext context) {
//      LOG.error("Can't interrupt the python interpreter", e);
  }

  @Override
  public int getProgress(InterpreterContext context) {
    //TODO(bzz): get progreess!
    return 0;
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    //TODO(bzz): get progreess!
    return null;
  }

  @Override
  public FormType getFormType() {
    return FormType.NATIVE;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
        PythonInterpreter2.class.getName() + this.hashCode());
  }

  /**
   * Sends given text to Python interpreter
   *
   * @param noteId id of the notebook, to separate contexts: variables, definitions, etc
   * @param cmd Python expression text
   * @return output
   */
  InterpreterResult sendCommandToPython(String code, String noteId) {
    LOG.debug("Sending : \n" + (code.length() > 200 ? code.substring(0, 200) + "..." : code));
    CodeInterpreteRequest cmd = CodeInterpreteRequest.newBuilder()
        .setCode(code)
        .setNoteId(noteId) //pass same 'noteId', for 'shared' interpreter context for all notes
        .build();
    InterpetedResult fromPythonProcess;
    try {
      fromPythonProcess = blockingStub.interprete(cmd);
    } catch (StatusRuntimeException e) {
      LOG.error("Error when sending commands to Python process", e);
      String errorMsg = e.getMessage()
          .replace("UNKNOWN: Exception calling application:", "Exception in interpreter.py:");
      if (e.getCause() instanceof java.net.ConnectException) {
        errorMsg = "Failed to communicate to Python interpreter.py on " +
            this.serverHost + ":" + this.serverPort;
      }
      return new InterpreterResult(Code.ERROR, errorMsg);
    }
    InterpreterResult result;
    if (gotSuccess(fromPythonProcess)) {
      result = new InterpreterResult(Code.SUCCESS, fromPythonProcess.getOutput());
    } else {
      result = new InterpreterResult(Code.ERROR, fromPythonProcess.getOutput());
    }
    LOG.debug("Got : \n" + result);
    return result;
  }

  private static boolean gotSuccess(InterpetedResult result) {
    //TODO(bzz): replace string \w enum
    return result != null && result.getStatus().toLowerCase().contains("success");
  }

  /*public Boolean isPy4jInstalled() {
    String output = sendCommandToPython("\n\nimport py4j\n");
    return !output.contains("ImportError");
  }*/

  private static int findRandomOpenPortOnAllLocalInterfaces() {
    Integer port = -1;
    try (ServerSocket socket = new ServerSocket(0);) {
      port = socket.getLocalPort();
      socket.close();
    } catch (IOException e) {
      LOG.error("Can't find an open port", e);
    }
    return port;
  }

}
