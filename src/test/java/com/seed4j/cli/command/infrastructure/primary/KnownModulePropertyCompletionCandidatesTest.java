package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.module.domain.resource.Seed4JModulePropertyDefinition;
import org.junit.jupiter.api.Test;

@UnitTest
class KnownModulePropertyCompletionCandidatesTest {

  @Test
  void shouldSuggestKnownNodePackageManagers() {
    Seed4JModulePropertyDefinition property = Seed4JModulePropertyDefinition.mandatoryStringProperty("nodePackageManager")
      .defaultValue("npm")
      .build();

    assertThat(new KnownModulePropertyCompletionCandidates().candidates(property)).containsExactly("npm", "pnpm");
  }

  @Test
  void shouldSuggestKnownSpringConfigurationFormats() {
    Seed4JModulePropertyDefinition property = Seed4JModulePropertyDefinition.mandatoryStringProperty("springConfigurationFormat")
      .defaultValue("yaml")
      .build();

    assertThat(new KnownModulePropertyCompletionCandidates().candidates(property)).containsExactly("yaml", "properties");
  }

  @Test
  void shouldSuggestKnownEndOfLineValues() {
    Seed4JModulePropertyDefinition property = Seed4JModulePropertyDefinition.mandatoryStringProperty("endOfLine")
      .defaultValue("lf")
      .build();

    assertThat(new KnownModulePropertyCompletionCandidates().candidates(property)).containsExactly("lf", "crlf");
  }

  @Test
  void shouldAppendDistinctDefaultValueAfterKnownCandidates() {
    Seed4JModulePropertyDefinition property = Seed4JModulePropertyDefinition.mandatoryStringProperty("nodePackageManager")
      .defaultValue("custom")
      .build();

    assertThat(new KnownModulePropertyCompletionCandidates().candidates(property)).containsExactly("npm", "pnpm", "custom");
  }

  @Test
  void shouldFallbackToDefaultValueForUnknownProperty() {
    Seed4JModulePropertyDefinition property = Seed4JModulePropertyDefinition.mandatoryStringProperty("unknownProperty")
      .defaultValue("unknown-default")
      .build();

    assertThat(new KnownModulePropertyCompletionCandidates().candidates(property)).containsExactly("unknown-default");
  }
}
