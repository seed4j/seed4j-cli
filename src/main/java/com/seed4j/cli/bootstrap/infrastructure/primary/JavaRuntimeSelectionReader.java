package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.domain.RuntimeDistributionId;
import com.seed4j.cli.bootstrap.domain.RuntimeDistributionVersion;
import com.seed4j.cli.bootstrap.domain.RuntimeMode;
import com.seed4j.cli.bootstrap.domain.RuntimeSelection;
import com.seed4j.cli.shared.error.domain.Assert;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JavaRuntimeSelectionReader {

  private final RuntimeSelection runtimeSelection;

  public JavaRuntimeSelectionReader(RuntimeSelection runtimeSelection) {
    Assert.notNull("runtimeSelection", runtimeSelection);

    this.runtimeSelection = runtimeSelection;
  }

  public JavaRuntimeSelection activeRuntimeSelection() {
    return new JavaRuntimeSelection(
      runtimeSelection.mode() == RuntimeMode.EXTENSION,
      runtimeSelection.distributionId().map(RuntimeDistributionId::id),
      runtimeSelection.distributionVersion().map(RuntimeDistributionVersion::version)
    );
  }

  public record JavaRuntimeSelection(boolean extension, Optional<String> distributionId, Optional<String> distributionVersion) {
    public JavaRuntimeSelection {
      Assert.notNull("distributionId", distributionId);
      Assert.notNull("distributionVersion", distributionVersion);
    }
  }
}
