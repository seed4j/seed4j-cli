package com.seed4j.cli.bootstrap.domain;

public interface RuntimeModeChangePlan {
  RuntimeModeConfigurationPath configPath();

  void apply();
}
