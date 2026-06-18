package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.BashCompletionInstallationException;
import com.seed4j.cli.command.domain.BashCompletionInstallationPath;
import com.seed4j.cli.command.domain.BashCompletionInstallationResult;
import com.seed4j.cli.command.domain.BashCompletionInstaller;
import com.seed4j.cli.command.domain.BashCompletionScript;
import com.seed4j.cli.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FileSystemBashCompletionInstaller implements BashCompletionInstaller {

  private static final Path COMPLETION_SCRIPT_PATH = Path.of(".local/share/bash-completion/completions/seed4j");

  private final Path userHome;

  FileSystemBashCompletionInstaller(Path userHome) {
    Assert.notNull("userHome", userHome);

    this.userHome = userHome;
  }

  @Override
  public BashCompletionInstallationResult install(BashCompletionScript script) {
    Assert.notNull("script", script);

    Path installationPath = userHome.resolve(COMPLETION_SCRIPT_PATH);

    try {
      Files.createDirectories(installationPath.getParent());
      Files.writeString(installationPath, script.content());

      return new BashCompletionInstallationResult(new BashCompletionInstallationPath(installationPath));
    } catch (IOException exception) {
      throw new BashCompletionInstallationException("Could not install Bash completion script.", exception);
    }
  }
}
