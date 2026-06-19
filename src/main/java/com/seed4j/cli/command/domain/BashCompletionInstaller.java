package com.seed4j.cli.command.domain;

public interface BashCompletionInstaller {
  BashCompletionInstallationResult install(BashCompletionScript script);
}
