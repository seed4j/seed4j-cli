package com.seed4j.cli.command.infrastructure.primary;

import static com.seed4j.cli.command.infrastructure.primary.CliFixture.commandLine;
import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.IntegrationTest;
import com.seed4j.cli.command.application.DistributionMetadataApplicationService;
import com.seed4j.cli.command.domain.distribution.DistributionIdentity;
import com.seed4j.cli.command.domain.distribution.DistributionMetadata;
import com.seed4j.cli.command.domain.distribution.DistributionModuleSlug;
import com.seed4j.cli.command.domain.distribution.ReleaseChannel;
import com.seed4j.cli.command.domain.distribution.Seed4JDependencyCoordinate;
import com.seed4j.cli.command.domain.distribution.Seed4JModuleAvailability;
import com.seed4j.cli.command.domain.distribution.Seed4JUpstreamCommit;
import com.seed4j.history.infrastructure.primary.JavaProjectHistories;
import com.seed4j.module.application.Seed4JModulesApplicationService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import picocli.CommandLine;

@ExtendWith(OutputCaptureExtension.class)
@IntegrationTest
class Seed4JCommandsFactoryTest {

  @Autowired
  private JavaProjectHistories projects;

  @Autowired
  private Seed4JModulesApplicationService modules;

  @Test
  void shouldShowHelpMessageWhenNoCommand(CapturedOutput output) {
    String[] args = {};

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isEqualTo(2);
    assertThat(output)
      .contains("Seed4J CLI")
      .contains("-h, --help      Show this help message and exit.")
      .contains("-V, --version   Print version information and exit.")
      .contains("Commands:");
  }

  @Test
  void shouldDiscloseExperimentalDistributionOnlyInRootHelp(CapturedOutput output) {
    DistributionMetadata experimentalMetadata = new DistributionMetadata(
      new DistributionIdentity(
        ReleaseChannel.EXPERIMENTAL,
        Seed4JDependencyCoordinate.versioned("io.github.renanfranca", "seed4j-engine", "2.2.1-main.20260907.055800.0123456789ab-SNAPSHOT"),
        Optional.of(new Seed4JUpstreamCommit("0123456789abcdef0123456789abcdef01234567"))
      ),
      new Seed4JModuleAvailability(Set.of(new DistributionModuleSlug("seed4j-extension")))
    );
    Seed4JCommandsFactory factory = new Seed4JCommandsFactory(
      List.of(),
      new Seed4JVersionProvider("1", "2", new DistributionMetadataApplicationService(() -> experimentalMetadata)),
      new DistributionMetadataApplicationService(() -> experimentalMetadata)
    );
    String[] args = { "--help" };

    int exitCode = new CommandLine(factory.buildCommandSpec()).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output)
      .contains("EXPERIMENTAL distribution")
      .contains("tracks Seed4J main through an unofficial")
      .contains("snapshot that can expire")
      .contains("npm install -g seed4j-cli@latest");
  }

  @Test
  void shouldShowExperimentalChannelAndExactUpstreamCommitInVersionOutput(CapturedOutput output) {
    Seed4JUpstreamCommit upstreamCommit = new Seed4JUpstreamCommit("0123456789abcdef0123456789abcdef01234567");
    DistributionMetadata experimentalMetadata = new DistributionMetadata(
      new DistributionIdentity(
        ReleaseChannel.EXPERIMENTAL,
        Seed4JDependencyCoordinate.versioned("io.github.renanfranca", "seed4j-engine", "2.2.1-main.20260907.055800.0123456789ab-SNAPSHOT"),
        Optional.of(upstreamCommit)
      ),
      new Seed4JModuleAvailability(Set.of(new DistributionModuleSlug("seed4j-extension")))
    );
    DistributionMetadataApplicationService distribution = new DistributionMetadataApplicationService(() -> experimentalMetadata);
    Seed4JCommandsFactory factory = new Seed4JCommandsFactory(
      List.of(),
      new Seed4JVersionProvider("3.0.0-experimental.4", "externally-overridden-version", distribution),
      distribution
    );
    String[] args = { "--version" };

    int exitCode = new CommandLine(factory.buildCommandSpec()).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output)
      .contains("Seed4J CLI v3.0.0-experimental.4")
      .contains("Release channel: experimental")
      .contains("Seed4J version: 2.2.1-main.20260907.055800.0123456789ab-SNAPSHOT")
      .doesNotContain("externally-overridden-version")
      .contains("Seed4J upstream commit: " + upstreamCommit.value());
  }

  @Test
  void shouldRenderVersionOutputUsingProjectBuildMetadata(CapturedOutput output) {
    String[] args = { "--version" };

    int exitCode = commandLine(modules, projects, "9.9.9", "8.8.8").execute(args);

    assertThat(exitCode).isZero();
    assertThat(output).contains("Seed4J CLI v9.9.9").contains("Seed4J version: 8.8.8");
  }

  @Test
  void shouldUseSafeFallbackWhenNoVersionMetadataIsAvailable(CapturedOutput output) {
    String[] args = { "--version" };

    int exitCode = commandLine(modules, projects, "", "").execute(args);

    assertThat(exitCode).isZero();
    assertThat(output)
      .contains("Seed4J CLI vunknown")
      .contains("Seed4J version: unknown")
      .contains("Runtime mode: standard")
      .doesNotContain("vnull")
      .doesNotContain("version: null")
      .doesNotContain("Distribution ID")
      .doesNotContain("Distribution version");
  }

  @Test
  void shouldShowVersion(CapturedOutput output) {
    String[] args = { "--version" };

    int exitCode = commandLine(modules, projects).execute(args);

    assertThat(exitCode).isZero();
    assertThat(output)
      .contains("Seed4J CLI v1")
      .contains("Seed4J version: 2")
      .contains("Runtime mode: standard")
      .doesNotContain("Distribution ID")
      .doesNotContain("Distribution version");
  }
}
