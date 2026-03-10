package com.seed4j.cli.bootstrap;

public record RuntimeSelection(RuntimeMode mode) {
  public static RuntimeSelection resolve(RuntimeConfiguration runtimeConfiguration) {
    return new RuntimeSelection(RuntimeMode.STANDARD);
  }
}
