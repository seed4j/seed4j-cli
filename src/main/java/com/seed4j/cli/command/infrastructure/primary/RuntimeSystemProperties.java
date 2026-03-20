package com.seed4j.cli.command.infrastructure.primary;

import java.util.Map;

@FunctionalInterface
interface RuntimeSystemProperties {
  Map<String, String> values();
}
