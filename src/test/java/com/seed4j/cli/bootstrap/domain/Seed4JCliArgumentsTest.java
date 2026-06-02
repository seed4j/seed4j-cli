package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.shared.error.domain.MissingMandatoryValueException;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliArgumentsTest {

  @Test
  void shouldRejectMissingValues() {
    assertThatThrownBy(() -> new Seed4JCliArguments(null))
      .isExactlyInstanceOf(MissingMandatoryValueException.class)
      .hasMessageContaining("\"values\"");
  }

  @Test
  void shouldKeepADefensiveCopyOfValuesOnConstruction() {
    String[] rawArguments = { "--version" };
    Seed4JCliArguments arguments = new Seed4JCliArguments(rawArguments);

    rawArguments[0] = "list";

    assertThat(arguments.values()).containsExactly("--version");
  }

  @Test
  void shouldReturnADefensiveCopyOfValues() {
    Seed4JCliArguments arguments = new Seed4JCliArguments(new String[] { "--version" });
    String[] values = arguments.values();

    values[0] = "list";

    assertThat(arguments.values()).containsExactly("--version");
  }

  @Test
  void shouldExposeValuesAsAnImmutableList() {
    Seed4JCliArguments arguments = new Seed4JCliArguments(new String[] { "--version", "--debug" });

    List<String> values = arguments.asList();

    assertThat(values).containsExactly("--version", "--debug");
    assertThatThrownBy(() -> values.add("list")).isExactlyInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldTellWhenValueIsPresent() {
    Seed4JCliArguments arguments = new Seed4JCliArguments(new String[] { "--version", "--debug" });

    assertThat(arguments.contains("--debug")).isTrue();
    assertThat(arguments.contains("list")).isFalse();
  }
}
