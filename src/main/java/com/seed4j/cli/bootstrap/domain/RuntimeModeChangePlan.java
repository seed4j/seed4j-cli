package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.nio.file.Path;

public interface RuntimeModeChangePlan {
  Path configPath();

  void apply() throws IOException;
}
