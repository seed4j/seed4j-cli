package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;
import picocli.CommandLine.Model.OptionSpec;

class ProjectPathOptionSpecFactory {

  static final String OPTION_NAME = "--project-path";

  OptionSpec create() {
    return OptionSpec.builder(OPTION_NAME)
      .description("Project Path Folder")
      .paramLabel("<projectpath>")
      .defaultValue(".")
      .completionCandidates(List.of("."))
      .type(String.class)
      .build();
  }
}
