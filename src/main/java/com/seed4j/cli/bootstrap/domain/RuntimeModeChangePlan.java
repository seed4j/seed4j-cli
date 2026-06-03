package com.seed4j.cli.bootstrap.domain;

import java.nio.file.Path;

public interface RuntimeModeChangePlan {
  Path configPath();

  void apply();
}
