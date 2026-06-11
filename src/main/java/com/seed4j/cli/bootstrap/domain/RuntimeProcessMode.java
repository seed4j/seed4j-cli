package com.seed4j.cli.bootstrap.domain;

public enum RuntimeProcessMode {
  PARENT,
  CHILD;

  public static RuntimeProcessMode from(boolean childMode) {
    return childMode ? CHILD : PARENT;
  }

  public boolean child() {
    return this == CHILD;
  }
}
