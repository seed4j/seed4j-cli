package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class JavaRuntimeExtensionModeSwitcher {

  private final RuntimeExtensionApplicationService runtimeExtensionApplicationService;

  public JavaRuntimeExtensionModeSwitcher(RuntimeExtensionApplicationService runtimeExtensionApplicationService) {
    Assert.notNull("runtimeExtensionApplicationService", runtimeExtensionApplicationService);

    this.runtimeExtensionApplicationService = runtimeExtensionApplicationService;
  }

  public JavaRuntimeExtensionModeSwitch enable() {
    try {
      return new JavaRuntimeExtensionModeSwitch(runtimeExtensionApplicationService.enableExtensionMode());
    } catch (InvalidRuntimeConfigurationException exception) {
      throw new JavaRuntimeExtensionModeSwitchException(exception);
    }
  }

  public JavaRuntimeExtensionModeSwitch disable() {
    try {
      return new JavaRuntimeExtensionModeSwitch(runtimeExtensionApplicationService.disableExtensionMode());
    } catch (InvalidRuntimeConfigurationException exception) {
      throw new JavaRuntimeExtensionModeSwitchException(exception);
    }
  }

  public record JavaRuntimeExtensionModeSwitch(Path configPath) {
    public JavaRuntimeExtensionModeSwitch {
      Assert.notNull("configPath", configPath);
    }
  }

  public static final class JavaRuntimeExtensionModeSwitchException extends RuntimeException {

    public JavaRuntimeExtensionModeSwitchException(InvalidRuntimeConfigurationException cause) {
      super(cause.getMessage(), cause);
    }
  }
}
