package com.seed4j.cli.bootstrap.application;

public class PreSpringBootstrapApplicationService {

  @FunctionalInterface
  public interface Launcher {
    int launch(String[] args, boolean childMode);
  }

  private final Launcher launcher;
  private final boolean childMode;

  public PreSpringBootstrapApplicationService(Launcher launcher, boolean childMode) {
    this.launcher = launcher;
    this.childMode = childMode;
  }

  public int launch(String[] args) {
    return launcher.launch(args, childMode);
  }
}
