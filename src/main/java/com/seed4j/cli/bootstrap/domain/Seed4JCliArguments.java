package com.seed4j.cli.bootstrap.domain;

import com.seed4j.cli.shared.error.domain.Assert;
import com.seed4j.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
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

  @Override
  @ExcludeFromGeneratedCodeCoverage
  public boolean equals(Object other) {
    return other instanceof Seed4JCliArguments(String[] thatValues) && Arrays.equals(values, thatValues);
  }

  @Override
  @ExcludeFromGeneratedCodeCoverage
  public int hashCode() {
    return Arrays.hashCode(values);
  }

  @Override
  @ExcludeFromGeneratedCodeCoverage
  public String toString() {
    return "Seed4JCliArguments[values=" + Arrays.toString(values) + "]";
  }
}
