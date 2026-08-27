package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPath;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathStatus;
import com.seed4j.cli.command.domain.moduleset.ModuleSetProjectPathValidator;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class NioModuleSetProjectPathValidator implements ModuleSetProjectPathValidator {

  @Override
  public ModuleSetProjectPathStatus validate(ModuleSetProjectPath projectPath) {
    Path destination = projectPath.value();
    if (Files.isDirectory(destination)) {
      return Files.isExecutable(destination) && Files.isWritable(destination)
        ? ModuleSetProjectPathStatus.VALID
        : ModuleSetProjectPathStatus.NOT_ACCESSIBLE;
    }
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      return ModuleSetProjectPathStatus.NOT_DIRECTORY;
    }
    return apparentlyCreatable(destination);
  }

  private static ModuleSetProjectPathStatus apparentlyCreatable(Path destination) {
    Path ancestor = destination.toAbsolutePath().normalize().getParent();
    while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
      ancestor = ancestor.getParent();
    }
    if (!Files.isDirectory(ancestor)) {
      return ModuleSetProjectPathStatus.NOT_APPARENTLY_CREATABLE;
    }
    return Files.isExecutable(ancestor) && Files.isWritable(ancestor)
      ? ModuleSetProjectPathStatus.VALID
      : ModuleSetProjectPathStatus.NOT_APPARENTLY_CREATABLE;
  }
}
