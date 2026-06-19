package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@UnitTest
class BashCompletionScriptGeneratorTest {

  @Test
  void shouldGenerateStaticBashCandidatesFromCommandTree() {
    CommandSpec root = CommandSpec.create().name("seed4j");
    root.addOption(OptionSpec.builder("--debug").type(Boolean.class).build());
    CommandSpec apply = CommandSpec.create().name("apply");
    CommandSpec init = CommandSpec.create().name("init");
    init.addOption(OptionSpec.builder("--project-path").type(String.class).build());
    init.addOption(OptionSpec.builder("--commit").type(Boolean.class).negatable(true).build());
    init.addOption(OptionSpec.builder("--environment").type(String.class).paramLabel("<dev|prod>").build());

    apply.addSubcommand("init", init);
    root.addSubcommand("apply", apply);
    root.addSubcommand("list", CommandSpec.create().name("list"));

    String script = new BashCompletionScriptGenerator().generate(root);

    assertThat(script)
      .contains("_seed4j_completion()")
      .contains("complete -F _seed4j_completion seed4j")
      .contains("'') printf '%s' 'apply list --debug'")
      .contains("'apply') printf '%s' 'init'")
      .contains("'apply init') printf '%s' '--commit --environment --no-commit --project-path'")
      .contains("_seed4j_value_options_for_path()")
      .contains("'apply init') printf '%s' '--environment --project-path'")
      .contains("*\" $prev \"*) return 0 ;;")
      .doesNotContain("dev|prod");
  }
}
