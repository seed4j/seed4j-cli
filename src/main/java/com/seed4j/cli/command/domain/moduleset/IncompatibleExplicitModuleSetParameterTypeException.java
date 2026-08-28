package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public class IncompatibleExplicitModuleSetParameterTypeException extends RuntimeException {

  private final String key;
  private final ModuleSetPropertyType expectedType;
  private final ModuleSetPropertyType actualType;

  public IncompatibleExplicitModuleSetParameterTypeException(
    ModuleSetPropertyKey key,
    ModuleSetPropertyType expectedType,
    ModuleSetPropertyType actualType
  ) {
    super(message(key, expectedType, actualType));
    this.key = key.value();
    this.expectedType = expectedType;
    this.actualType = actualType;
  }

  private static String message(ModuleSetPropertyKey key, ModuleSetPropertyType expectedType, ModuleSetPropertyType actualType) {
    Assert.notNull("key", key);
    Assert.notNull("expectedType", expectedType);
    Assert.notNull("actualType", actualType);
    return "Explicit module set parameter %s expects %s but received %s".formatted(key.value(), expectedType, actualType);
  }

  public ModuleSetPropertyKey key() {
    return new ModuleSetPropertyKey(key);
  }

  public ModuleSetPropertyType expectedType() {
    return expectedType;
  }

  public ModuleSetPropertyType actualType() {
    return actualType;
  }
}
