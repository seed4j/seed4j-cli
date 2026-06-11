package com.seed4j.cli.bootstrap.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliArgumentsTest {

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

  @Test
  void shouldCompareValuesByContent() {
    Seed4JCliArguments arguments = new Seed4JCliArguments(new String[] { "--version", "--debug" });
    Seed4JCliArguments sameArguments = new Seed4JCliArguments(new String[] { "--version", "--debug" });

    assertThat(arguments).isEqualTo(sameArguments).hasSameHashCodeAs(sameArguments);
  }

  @Test
  void shouldNotMatchDifferentValues() {
    Seed4JCliArguments arguments = new Seed4JCliArguments(new String[] { "--version", "--debug" });
    Seed4JCliArguments differentArguments = new Seed4JCliArguments(new String[] { "--version" });

    assertThat(arguments).isNotEqualTo(differentArguments);
  }

  @Test
  void shouldRenderValuesByContent() {
    Seed4JCliArguments arguments = new Seed4JCliArguments(new String[] { "--version", "--debug" });

    assertThat(arguments).hasToString("Seed4JCliArguments[values=[--version, --debug]]");
  }
}
