package com.seed4j.cli.command.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.moduleset.ModuleSetGitState;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@UnitTest
class JGitModuleSetGitStateReaderTest {

  @Test
  void shouldReportNoWorktreeOutsideGitRepository(@TempDir Path projectPath) {
    JGitModuleSetGitStateReader reader = new JGitModuleSetGitStateReader();

    ModuleSetGitState state = reader.state(new ModuleSetProjectPath(projectPath));

    assertThat(state).isEqualTo(ModuleSetGitState.NO_WORKTREE);
  }

  @Test
  void shouldReportNoWorktreeForBareRepository(@TempDir Path repositoryPath) throws Exception {
    try (Git ignored = Git.init().setBare(true).setDirectory(repositoryPath.toFile()).call()) {
    }
    JGitModuleSetGitStateReader reader = new JGitModuleSetGitStateReader();

    ModuleSetGitState state = reader.state(new ModuleSetProjectPath(repositoryPath));

    assertThat(state).isEqualTo(ModuleSetGitState.NO_WORKTREE);
  }

  @Test
  void shouldReportCleanWorktree(@TempDir Path projectPath) throws Exception {
    initializeCleanWorktree(projectPath);
    JGitModuleSetGitStateReader reader = new JGitModuleSetGitStateReader();

    ModuleSetGitState state = reader.state(new ModuleSetProjectPath(projectPath));

    assertThat(state).isEqualTo(ModuleSetGitState.CLEAN);
  }

  @Test
  void shouldNormalizeCorruptIndexFailureAndPreserveCause(@TempDir Path projectPath) throws Exception {
    initializeCleanWorktree(projectPath);
    Files.writeString(projectPath.resolve(".git/index"), "not a Git index");
    JGitModuleSetGitStateReader reader = new JGitModuleSetGitStateReader();

    assertThatThrownBy(() -> reader.state(new ModuleSetProjectPath(projectPath)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Unable to read Git worktree state")
      .cause()
      .isInstanceOf(JGitInternalException.class);
  }

  @ParameterizedTest
  @EnumSource(DirtyChange.class)
  void shouldReportDirtyWorktreeForEveryGitChangeKind(DirtyChange change, @TempDir Path projectPath) throws Exception {
    initializeCleanWorktree(projectPath);
    change.apply(projectPath);
    JGitModuleSetGitStateReader reader = new JGitModuleSetGitStateReader();

    ModuleSetGitState state = reader.state(new ModuleSetProjectPath(projectPath));

    assertThat(state).isEqualTo(ModuleSetGitState.DIRTY);
  }

  private static void initializeCleanWorktree(Path projectPath) throws Exception {
    try (Git git = Git.init().setDirectory(projectPath.toFile()).call()) {
      Files.writeString(projectPath.resolve("tracked.txt"), "initial");
      git.add().addFilepattern("tracked.txt").call();
      PersonIdent author = new PersonIdent("Seed4J CLI test", "test@seed4j.com");
      git.commit().setMessage("Initial commit").setAuthor(author).setCommitter(author).call();
    }
  }

  private enum DirtyChange {
    TRACKED,
    STAGED,
    UNTRACKED;

    private void apply(Path projectPath) throws Exception {
      switch (this) {
        case TRACKED -> Files.writeString(projectPath.resolve("tracked.txt"), "changed");
        case STAGED -> {
          Files.writeString(projectPath.resolve("staged.txt"), "staged");
          try (Git git = Git.open(projectPath.toFile())) {
            git.add().addFilepattern("staged.txt").call();
          }
        }
        case UNTRACKED -> Files.writeString(projectPath.resolve("untracked.txt"), "untracked");
      }
    }
  }
}
