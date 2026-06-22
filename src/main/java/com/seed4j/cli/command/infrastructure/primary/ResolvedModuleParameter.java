package com.seed4j.cli.command.infrastructure.primary;

record ResolvedModuleParameter(String name, Object value, ModuleParameterSource source, String cliOption) {}
