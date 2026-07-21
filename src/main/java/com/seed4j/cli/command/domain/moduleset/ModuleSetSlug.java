package com.seed4j.cli.command.domain.moduleset;

import com.seed4j.cli.shared.error.domain.Assert;

public record ModuleSetSlug(String value) implements Comparable<ModuleSetSlug> {
  public ModuleSetSlug {
    Assert.notBlank("value", value);
  }

  @Override
  public int compareTo(ModuleSetSlug other) {
    return value.compareTo(other.value);
  }
}
