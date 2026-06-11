package com.seed4j.cli.bootstrap.domain;

public enum RuntimeExtensionReplacementStatus {
  NEW_INSTALLATION,
  REPLACED_ACTIVE_RUNTIME;

  public static RuntimeExtensionReplacementStatus from(boolean runtimeReplaced) {
    return runtimeReplaced ? REPLACED_ACTIVE_RUNTIME : NEW_INSTALLATION;
  }

  public boolean replaced() {
    return this == REPLACED_ACTIVE_RUNTIME;
  }
}
