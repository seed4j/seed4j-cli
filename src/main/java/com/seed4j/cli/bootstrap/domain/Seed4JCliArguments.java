package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Arrays;
import java.util.List;

public record Seed4JCliArguments(String[] values) {
  public Seed4JCliArguments {
    Assert.notNull("values", values);
    values = values.clone();
  }

  @Override
  public String[] values() {
    return values.clone();
  }

  public List<String> asList() {
    return List.copyOf(Arrays.asList(values));
  }

  public boolean contains(String value) {
    return asList().contains(value);
  }
}
