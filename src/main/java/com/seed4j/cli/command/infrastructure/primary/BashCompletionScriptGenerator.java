package com.seed4j.cli.command.infrastructure.primary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

class BashCompletionScriptGenerator {

  public String generate(CommandSpec rootCommand) {
    Map<String, String> candidatesByPath = new TreeMap<>();
    Map<String, String> valueOptionsByPath = new TreeMap<>();
    collectCandidates(rootCommand, "", candidatesByPath, valueOptionsByPath);

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

    _seed4j_completion() {
      local cur prev word path candidates value_options
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
      case " $value_options " in
        *" $prev "*) return 0 ;;
      esac

      candidates="$(_seed4j_commands_for_path "$path")"
      COMPREPLY=( $(compgen -W "$candidates" -- "$cur") )
    }

    complete -F _seed4j_completion seed4j
    """.formatted(caseStatements(candidatesByPath), caseStatements(valueOptionsByPath));
  }

  private void collectCandidates(
    CommandSpec command,
    String path,
    Map<String, String> candidatesByPath,
    Map<String, String> valueOptionsByPath
  ) {
    List<String> candidates = new ArrayList<>();
    candidates.addAll(visibleSubcommandNames(command));
    candidates.addAll(optionNames(command));

    candidatesByPath.put(path, String.join(" ", candidates));
    valueOptionsByPath.put(path, String.join(" ", valueOptionNames(command)));

    visibleSubcommands(command).forEach((name, subcommand) ->
      collectCandidates(subcommand, childPath(path, name), candidatesByPath, valueOptionsByPath)
    );
  }

  private List<String> visibleSubcommandNames(CommandSpec command) {
    return visibleSubcommands(command).keySet().stream().toList();
  }

  private Map<String, CommandSpec> visibleSubcommands(CommandSpec command) {
    Map<String, CommandSpec> visibleSubcommands = new TreeMap<>();
    command.subcommands().forEach((name, subcommand) -> {
      if (!subcommand.getCommandSpec().usageMessage().hidden()) {
        visibleSubcommands.put(name, subcommand.getCommandSpec());
      }
    });

    return visibleSubcommands;
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

  private String caseStatements(Map<String, String> candidatesByPath) {
    StringBuilder statements = new StringBuilder();
    candidatesByPath.forEach((path, candidates) ->
      statements.append("    ").append(quote(path)).append(") printf '%s' ").append(quote(candidates)).append(" ;;\n")
    );

    return statements.toString();
  }

  private String quote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }
}
