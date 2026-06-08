package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.application.RuntimeExtensionApplicationService;
import com.seed4j.cli.bootstrap.domain.InvalidRuntimeConfigurationException;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallRequest;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionInstallResult;
import com.seed4j.cli.bootstrap.domain.RuntimeExtensionJarPath;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class JavaRuntimeExtensionInstaller {

  private final RuntimeExtensionApplicationService runtimeExtensionApplicationService;

  public JavaRuntimeExtensionInstaller(RuntimeExtensionApplicationService runtimeExtensionApplicationService) {
    Assert.notNull("runtimeExtensionApplicationService", runtimeExtensionApplicationService);

    this.runtimeExtensionApplicationService = runtimeExtensionApplicationService;
  }

  public JavaRuntimeExtensionInstallation install(JavaRuntimeExtensionInstallationRequest request) {
    Assert.notNull("request", request);

    RuntimeExtensionInstallResult result;
    try {
      result = runtimeExtensionApplicationService.install(
        new RuntimeExtensionInstallRequest(
          RuntimeExtensionJarPath.from(request.extensionJarPath()),
          new RuntimeDistributionId(request.distributionId()),
          new RuntimeDistributionVersion(request.distributionVersion())
        )
      );
    } catch (InvalidRuntimeConfigurationException exception) {
      throw new JavaRuntimeExtensionInstallationException(exception);
    }

    return new JavaRuntimeExtensionInstallation(
      result.extensionJarPath(),
      result.metadataPath(),
      result.configPath(),
      result.runtimeReplaced()
    );
  }

  public record JavaRuntimeExtensionInstallationRequest(String extensionJarPath, String distributionId, String distributionVersion) {
    public JavaRuntimeExtensionInstallationRequest {
      Assert.notBlank("extensionJarPath", extensionJarPath);
      Assert.notBlank("distributionId", distributionId);
      Assert.notBlank("distributionVersion", distributionVersion);
    }
  }

  public record JavaRuntimeExtensionInstallation(Path extensionJarPath, Path metadataPath, Path configPath, boolean runtimeReplaced) {
    public JavaRuntimeExtensionInstallation {
      Assert.notNull("extensionJarPath", extensionJarPath);
      Assert.notNull("metadataPath", metadataPath);
      Assert.notNull("configPath", configPath);
    }
  }

  public static final class JavaRuntimeExtensionInstallationException extends RuntimeException {

    public JavaRuntimeExtensionInstallationException(InvalidRuntimeConfigurationException cause) {
      super(cause.getMessage(), cause);
    }
  }
}
