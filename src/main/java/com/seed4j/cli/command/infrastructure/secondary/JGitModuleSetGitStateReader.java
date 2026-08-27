package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetGitState;
import com.seed4j.cli.command.domain.moduleset.ModuleSetGitStateReader;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Component;

@Component
public class JGitModuleSetGitStateReader implements ModuleSetGitStateReader {

  @Override
  public ModuleSetGitState state(ModuleSetProjectPath projectPath) {
    FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(existingPath(projectPath.value()).toFile());
    if (builder.getGitDir() == null) {
      return ModuleSetGitState.NO_WORKTREE;
    }
    try (Repository repository = builder.build()) {
      if (repository.isBare()) {
        return ModuleSetGitState.NO_WORKTREE;
      }
      return Git.wrap(repository).status().call().isClean() ? ModuleSetGitState.CLEAN : ModuleSetGitState.DIRTY;
    } catch (IOException | GitAPIException exception) {
      throw new IllegalStateException("Unable to read Git worktree state", exception);
    }
  }

  private static Path existingPath(Path projectPath) {
    Path existing = projectPath.toAbsolutePath().normalize();
    while (!Files.exists(existing)) {
      existing = existing.getParent();
    }
    return existing;
  }
}
