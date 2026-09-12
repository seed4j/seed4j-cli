package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.cli.command.domain.distribution.ReleaseChannel;

final class UnavailableModuleDiagnosticRenderer {

  String render(String moduleSlug, ReleaseChannel channel) {
    return "ERROR: Module '%s' is unavailable in the %s channel because Central Portal snapshots expire and generated extensions would not remain rebuildable. Install seed4j-cli@latest to generate a stable Seed4J extension. No changes were applied.%n".formatted(
      moduleSlug,
      channel.name().toLowerCase()
    );
  }
}
