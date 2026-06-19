package com.seed4j.cli.command.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import com.seed4j.cli.UnitTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

@UnitTest
class BashCompletionScriptGeneratorTest {

  @TempDir
  private Path tempDir;

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

  @Test
  void shouldCompleteOptionValueCandidateAsSingleSuggestionWhenOptionValueIsNext() throws IOException, InterruptedException {
    String script = new BashCompletionScriptGenerator().generate(commandWithProjectNameCandidates(List.of("Seed4J Sample Application")));

    List<String> completions = completions(script, "seed4j", "apply", "init", "--project-name", "");

    assertThat(completions).containsExactly("Seed4J Sample Application");
  }

  @Test
  void shouldCompleteOptionValueCandidateWhenValueIsAssignedWithEquals() throws IOException, InterruptedException {
    String script = new BashCompletionScriptGenerator().generate(commandWithProjectNameCandidates(List.of("Seed4J Sample Application")));

    List<String> completions = completions(script, "seed4j", "apply", "init", "--project-name=");

    assertThat(completions).containsExactly("--project-name=Seed4J Sample Application");
  }

  @Test
  void shouldSkipOptionValueCandidatesWhenValueCompletionIsDisabled() throws IOException, InterruptedException {
    String script = new BashCompletionScriptGenerator().generate(
      commandWithProjectNameCandidates(List.of("Seed4J Sample Application")),
      false
    );

    List<String> completions = completions(script, "seed4j", "apply", "init", "--project-name", "");

    assertThat(completions).isEmpty();
  }

  @Test
  void shouldNotSuggestOptionValuesWhenOptionHasEmptyCandidates() throws IOException, InterruptedException {
    String script = new BashCompletionScriptGenerator().generate(commandWithProjectNameCandidates(List.of()));

    List<String> completions = completions(script, "seed4j", "apply", "init", "--project-name", "");

    assertThat(completions).isEmpty();
  }

  private CommandSpec commandWithProjectNameCandidates(List<String> candidates) {
    CommandSpec root = CommandSpec.create().name("seed4j");
    CommandSpec apply = CommandSpec.create().name("apply");
    CommandSpec init = CommandSpec.create().name("init");
    init.addOption(OptionSpec.builder("--project-name").type(String.class).completionCandidates(candidates).build());
    apply.addSubcommand("init", init);
    root.addSubcommand("apply", apply);

    return root;
  }

  private List<String> completions(String script, String... words) throws IOException, InterruptedException {
    Path scriptPath = tempDir.resolve("seed4j-completion.bash");
    Files.writeString(scriptPath, script);
    ProcessBuilder processBuilder = new ProcessBuilder(
      "bash",
      "-c",
      """
      source "$1"
      shift
      COMP_WORDS=("$@")
      COMP_CWORD=$((${#COMP_WORDS[@]} - 1))
      _seed4j_completion
      if (( ${#COMPREPLY[@]} > 0 )); then
        printf '%s\n' "${COMPREPLY[@]}"
      fi
      """,
      "bash",
      scriptPath.toString()
    );
    processBuilder.command().addAll(List.of(words));
    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();

    assertThat(exitCode).isZero();

    return output.lines().toList();
  }
}
