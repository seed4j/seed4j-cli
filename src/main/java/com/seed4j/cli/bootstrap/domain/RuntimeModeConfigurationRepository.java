package com.seed4j.cli.bootstrap.domain;

public interface RuntimeModeConfigurationRepository {
  RuntimeModeChangePlan prepareModeChange(RuntimeMode targetMode);

  RuntimeMode readMode();
}
