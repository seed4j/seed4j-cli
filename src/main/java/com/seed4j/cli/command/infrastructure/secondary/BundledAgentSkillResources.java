package com.seed4j.cli.command.infrastructure.secondary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@FunctionalInterface
interface BundledAgentSkillResources {
  Map<Path, byte[]> read() throws IOException;
}
