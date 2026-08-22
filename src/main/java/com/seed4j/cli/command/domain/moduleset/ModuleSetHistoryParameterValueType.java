package com.seed4j.cli.command.domain.moduleset;

public enum ModuleSetHistoryParameterValueType {
  STRING,
  INTEGER,
  BOOLEAN,
  UNSUPPORTED;

  public static ModuleSetHistoryParameterValueType from(ModuleSetPropertyType type) {
    return switch (type) {
      case STRING -> STRING;
      case INTEGER -> INTEGER;
      case BOOLEAN -> BOOLEAN;
    };
  }
}
