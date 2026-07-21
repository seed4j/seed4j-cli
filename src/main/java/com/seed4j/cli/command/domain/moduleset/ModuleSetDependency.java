package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetDependency(ModuleSetDependencyType type, String value) implements Comparable<ModuleSetDependency> {
  public ModuleSetDependency {
    Assert.notNull("type", type);
    Assert.notBlank("value", value);
  }

  public String token() {
    return type.name().toLowerCase() + ":" + value;
  }

  @Override
  public int compareTo(ModuleSetDependency other) {
    return token().compareTo(other.token());
  }
}
