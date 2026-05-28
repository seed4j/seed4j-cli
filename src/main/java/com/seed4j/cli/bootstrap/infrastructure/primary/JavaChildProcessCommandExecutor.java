package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.Seed4JCliLauncherFactory;
import java.io.IOException;
import java.util.List;

class JavaChildProcessCommandExecutor implements Seed4JCliLauncherFactory.CommandExecutor {

  @FunctionalInterface
  interface ProcessStarter {
    StartedProcess start(List<String> command) throws IOException;
  }

  @FunctionalInterface
  interface StartedProcess {
    int waitFor() throws InterruptedException;
  }

  private final ProcessStarter processStarter;

  JavaChildProcessCommandExecutor() {
    this(JavaChildProcessCommandExecutor::startProcess);
  }

  JavaChildProcessCommandExecutor(ProcessStarter processStarter) {
    this.processStarter = processStarter;
  }

  private static StartedProcess startProcess(List<String> command) throws IOException {
    Process process = new ProcessBuilder(command).inheritIO().start();
    return process::waitFor;
  }

  @Override
  public int execute(List<String> command) {
    try {
      return processStarter.start(command).waitFor();
    } catch (IOException ioException) {
      throw InvalidRuntimeConfigurationException.technicalError("Could not launch child process.", ioException);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw InvalidRuntimeConfigurationException.technicalError("Child process execution was interrupted.", interruptedException);
    }
  }
}
