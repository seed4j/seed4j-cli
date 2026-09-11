package com.seed4j.cli.command.domain.distribution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.seed4j.cli.UnitTest;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

@UnitTest
class DistributionMetadataTest {

  private static final Seed4JUpstreamCommit UPSTREAM_COMMIT = new Seed4JUpstreamCommit("0123456789abcdef0123456789abcdef01234567");

  @Test
  void shouldRejectExperimentalIdentityWithoutUpstreamCommit() {
    Seed4JDependencyCoordinate coordinate = Seed4JDependencyCoordinate.versioned(
      "io.github.renanfranca",
      "seed4j-main-snapshot",
      "snapshot-version"
    );

    assertThatThrownBy(() -> new DistributionIdentity(ReleaseChannel.EXPERIMENTAL, coordinate, Optional.empty()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Experimental distribution identity requires dependency version and upstream commit");
  }

  @Test
  void shouldRejectExperimentalIdentityWithoutDependencyVersion() {
    Seed4JDependencyCoordinate coordinate = Seed4JDependencyCoordinate.unversioned("io.github.renanfranca", "seed4j-main-snapshot");

    assertThatThrownBy(() -> new DistributionIdentity(ReleaseChannel.EXPERIMENTAL, coordinate, Optional.of(UPSTREAM_COMMIT)))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Experimental distribution identity requires dependency version and upstream commit");
  }

  @Test
  void shouldRejectUnavailableModulesForStableDistribution() {
    DistributionIdentity identity = DistributionMetadata.stable().identity();
    Seed4JModuleAvailability availability = new Seed4JModuleAvailability(Set.of(new DistributionModuleSlug("seed4j-extension")));

    assertThatThrownBy(() -> new DistributionMetadata(identity, availability))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Stable distribution must make every Seed4J module available");
  }
}
