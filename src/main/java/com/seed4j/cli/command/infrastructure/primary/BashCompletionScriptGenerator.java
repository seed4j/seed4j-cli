package com.seed4j.cli.command.infrastructure.primary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

class BashCompletionScriptGenerator {

  private static final String BASH_NEWLINE = "\n";
  private static final String OPTION_CANDIDATE_SEPARATOR = "\t";

  public String generate(CommandSpec rootCommand, BashCompletionValueCompletion valueCompletion) {
    CompletionCandidates candidates = collectCandidates(rootCommand, "");

    return """
    # bash completion for seed4j
    _seed4j_commands_for_path() {
      case "$1" in
    %s\
        *) printf '%%s' '' ;;
        esac
    }

    _seed4j_value_options_for_path() {
      case "$1" in
    %s\
        *) printf '%%s' '' ;;
        esac
    }

    _seed4j_value_candidates_for_option() {
      case "$1"$'\\t'"$2" in
    %s\
        *) printf '%%s' '' ;;
      esac
    }

    _seed4j_completion() {
      local cur prev word path candidates value_options value_candidates option value_prefix candidate
      COMPREPLY=()
      cur="${COMP_WORDS[COMP_CWORD]}"
      prev="${COMP_WORDS[COMP_CWORD - 1]}"
      path=""

      for ((i = 1; i < COMP_CWORD; i++)); do
        word="${COMP_WORDS[i]}"
        [[ "$word" == -* ]] && continue
        candidates="$(_seed4j_commands_for_path "$path")"
        case " $candidates " in
          *" $word "*) path="${path:+$path }$word" ;;
        esac
      done

      value_options="$(_seed4j_value_options_for_path "$path")"
    %s\
      if [[ "$cur" == --*=* ]]; then
        option="${cur%%%%=*}"
        value_prefix="${cur#*=}"
        value_candidates="$(_seed4j_value_candidates_for_option "$path" "$option")"
        if [[ -n "$value_candidates" ]]; then
          while IFS= read -r candidate; do
            [[ "$candidate" == "$value_prefix"* ]] && COMPREPLY+=("$option=$candidate")
          done <<< "$value_candidates"
          return 0
        fi
      fi

      case " $value_options " in
        *" $prev "*) return 0 ;;
      esac

      candidates="$(_seed4j_commands_for_path "$path")"
      COMPREPLY=( $(compgen -W "$candidates" -- "$cur") )
    }

    complete -F _seed4j_completion seed4j
    """.formatted(
      caseStatements(candidates.candidatesByPath()),
      caseStatements(candidates.valueOptionsByPath()),
      valueCandidateCaseStatements(valueCompletion.enabled() ? candidates.valueCandidatesByPathAndOption() : Map.of()),
      separatedValueCompletion(valueCompletion)
    );
  }

  private CompletionCandidates collectCandidates(CommandSpec command, String path) {
    return Stream.concat(
      Stream.of(currentCandidates(command, path)),
      subcommandsByName(command)
        .entrySet()
        .stream()
        .map(entry -> collectCandidates(entry.getValue(), childPath(path, entry.getKey())))
    ).reduce(CompletionCandidates.empty(), CompletionCandidates::merge);
  }

  private CompletionCandidates currentCandidates(CommandSpec command, String path) {
    Map<String, String> candidatesByPath = new TreeMap<>(
      Map.of(path, Stream.concat(subcommandNames(command).stream(), optionNames(command).stream()).collect(Collectors.joining(" ")))
    );
    Map<String, String> valueOptionsByPath = new TreeMap<>(Map.of(path, String.join(" ", valueOptionNames(command))));
    Map<String, List<String>> valueCandidatesByPathAndOption = valueCandidatesByPathAndOption(command, path);

    return new CompletionCandidates(candidatesByPath, valueOptionsByPath, valueCandidatesByPathAndOption);
  }

  private List<String> subcommandNames(CommandSpec command) {
    return subcommandsByName(command).keySet().stream().toList();
  }

  private Map<String, CommandSpec> subcommandsByName(CommandSpec command) {
    return command
      .subcommands()
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getCommandSpec(), (left, right) -> right, TreeMap::new));
  }

  private List<String> optionNames(CommandSpec command) {
    return Stream.concat(
      command
        .options()
        .stream()
        .flatMap(option -> Arrays.stream(option.names())),
      command.negatedOptionsMap().keySet().stream()
    )
      .sorted()
      .distinct()
      .toList();
  }

  private List<String> valueOptionNames(CommandSpec command) {
    return command
      .options()
      .stream()
      .filter(this::requiresValue)
      .flatMap(option -> Arrays.stream(option.names()))
      .sorted()
      .distinct()
      .toList();
  }

  private Map<String, List<String>> valueCandidatesByPathAndOption(CommandSpec command, String path) {
    return command
      .options()
      .stream()
      .filter(option -> option.completionCandidates() != null)
      .flatMap(option -> Arrays.stream(option.names()).map(name -> Map.entry(pathAndOption(path, name), completionCandidates(option))))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right, TreeMap::new));
  }

  private List<String> completionCandidates(OptionSpec option) {
    List<String> candidates = new ArrayList<>();
    option.completionCandidates().forEach(candidates::add);

    return candidates;
  }

  private boolean requiresValue(OptionSpec option) {
    Class<?> type = option.type();

    return type != boolean.class && type != Boolean.class;
  }

  private String childPath(String path, String childName) {
    if (path.isBlank()) {
      return childName;
    }

    return path + " " + childName;
  }

  private String pathAndOption(String path, String option) {
    return path + OPTION_CANDIDATE_SEPARATOR + option;
  }

  private String caseStatements(Map<String, String> candidatesByPath) {
    return candidatesByPath
      .entrySet()
      .stream()
      .map(entry -> "    %s) printf '%%s' %s ;;%s".formatted(quote(entry.getKey()), quote(entry.getValue()), BASH_NEWLINE))
      .collect(Collectors.joining());
  }

  private String valueCandidateCaseStatements(Map<String, List<String>> valueCandidatesByPathAndOption) {
    return valueCandidatesByPathAndOption
      .entrySet()
      .stream()
      .filter(entry -> !entry.getValue().isEmpty())
      .map(entry -> "    %s) printf '%%s\\n' %s ;;%s".formatted(quote(entry.getKey()), quotedValues(entry.getValue()), BASH_NEWLINE))
      .collect(Collectors.joining());
  }

  private String quotedValues(List<String> values) {
    return values.stream().map(this::quote).collect(Collectors.joining(" "));
  }

  private String separatedValueCompletion(BashCompletionValueCompletion valueCompletion) {
    if (!valueCompletion.enabled()) {
      return "";
    }

    return """
      value_candidates="$(_seed4j_value_candidates_for_option "$path" "$prev")"
      if [[ -n "$value_candidates" ]]; then
        while IFS= read -r candidate; do
          [[ "$candidate" == "$cur"* ]] && COMPREPLY+=("$candidate")
        done <<< "$value_candidates"
        return 0
      fi

    """;
  }

  private String quote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private record CompletionCandidates(
    Map<String, String> candidatesByPath,
    Map<String, String> valueOptionsByPath,
    Map<String, List<String>> valueCandidatesByPathAndOption
  ) {
    private static CompletionCandidates empty() {
      return new CompletionCandidates(new TreeMap<>(), new TreeMap<>(), new TreeMap<>());
    }

    private CompletionCandidates merge(CompletionCandidates other) {
      Map<String, String> mergedCandidatesByPath = new TreeMap<>(candidatesByPath);
      Map<String, String> mergedValueOptionsByPath = new TreeMap<>(valueOptionsByPath);
      Map<String, List<String>> mergedValueCandidatesByPathAndOption = new TreeMap<>(valueCandidatesByPathAndOption);

      mergedCandidatesByPath.putAll(other.candidatesByPath());
      mergedValueOptionsByPath.putAll(other.valueOptionsByPath());
      mergedValueCandidatesByPathAndOption.putAll(other.valueCandidatesByPathAndOption());

      return new CompletionCandidates(mergedCandidatesByPath, mergedValueOptionsByPath, mergedValueCandidatesByPathAndOption);
    }
  }
}
