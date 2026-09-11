package com.seed4j.cli.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import com.seed4j.cli.command.domain.distribution.DistributionIdentity;
import com.seed4j.cli.command.domain.distribution.DistributionMetadata;
import com.seed4j.cli.command.domain.distribution.DistributionModuleSlug;
import com.seed4j.cli.command.domain.distribution.ReleaseChannel;
import com.seed4j.cli.command.domain.distribution.Seed4JDependencyCoordinate;
import com.seed4j.cli.command.domain.distribution.Seed4JModuleAvailability;
import com.seed4j.cli.command.domain.distribution.Seed4JUpstreamCommit;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

@UnitTest
class DistributionMetadataApplicationServiceTest {

  @Test
  void shouldExposeImmutableExperimentalDistributionIdentityAndAvailability() {
    Seed4JUpstreamCommit upstreamCommit = new Seed4JUpstreamCommit("0123456789abcdef0123456789abcdef01234567");
    DistributionMetadata metadata = new DistributionMetadata(
      new DistributionIdentity(
        ReleaseChannel.EXPERIMENTAL,
        Seed4JDependencyCoordinate.versioned("io.github.renanfranca", "seed4j-main-snapshot", "snapshot-version"),
        Optional.of(upstreamCommit)
      ),
      new Seed4JModuleAvailability(Set.of(new DistributionModuleSlug("seed4j-extension")))
    );
    DistributionMetadataApplicationService service = new DistributionMetadataApplicationService(() -> metadata);

    DistributionMetadata result = service.metadata();

    assertThat(result).isEqualTo(metadata);
    assertThat(result.identity().upstreamCommit()).contains(upstreamCommit);
    assertThat(result.moduleAvailability().available("seed4j-extension")).isFalse();
  }
}
