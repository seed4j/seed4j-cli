package com.seed4j.cli.command.infrastructure.secondary;

import java.nio.file.Path;

@FunctionalInterface
interface WorkingDirectoryReader {
  Path current();
}
