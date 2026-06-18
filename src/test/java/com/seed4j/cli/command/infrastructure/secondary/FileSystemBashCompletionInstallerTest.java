package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.BashCompletionInstallationException;
import com.seed4j.cli.command.domain.BashCompletionInstallationResult;
import com.seed4j.cli.command.domain.BashCompletionScript;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@UnitTest
class FileSystemBashCompletionInstallerTest {

  @Test
  void shouldInstallBashCompletionScriptInUserCompletionDirectory() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-bash-completion-");
    Path installedScript = userHome.resolve(".local/share/bash-completion/completions/seed4j");
    Files.createDirectories(installedScript.getParent());
    Files.writeString(installedScript, "stale completion");
    FileSystemBashCompletionInstaller installer = new FileSystemBashCompletionInstaller(userHome);

    BashCompletionInstallationResult result = installer.install(new BashCompletionScript("complete -F _seed4j seed4j\n"));

    assertThat(Files.readString(installedScript)).isEqualTo("complete -F _seed4j seed4j\n");
    assertThat(result.path().path()).isEqualTo(installedScript);
  }

  @Test
  void shouldTranslateFileSystemFailures() throws IOException {
    Path userHome = Files.createTempDirectory("seed4j-cli-bash-completion-");
    Files.createDirectories(userHome.resolve(".local/share"));
    Files.writeString(userHome.resolve(".local/share/bash-completion"), "not a directory");
    FileSystemBashCompletionInstaller installer = new FileSystemBashCompletionInstaller(userHome);

    assertThatThrownBy(() -> installer.install(new BashCompletionScript("complete -F _seed4j seed4j\n")))
      .isExactlyInstanceOf(BashCompletionInstallationException.class)
      .hasMessage("Could not install Bash completion script.")
      .hasCauseInstanceOf(IOException.class);
  }
}
