package com.seed4j.cli.command.infrastructure.primary;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import picocli.CommandLine.Model.CommandSpec;

class BashCompletionScriptGenerator {

  private static final String BASH_NEWLINE = "\n";

  public String generate(CommandSpec rootCommand, BashCompletionValueCompletion valueCompletion) {
    BashCompletionCandidates candidates = new BashCompletionCandidateCollector().collect(rootCommand);

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

    _seed4j_completion_quote_value() {
      case "$1" in
        *[!a-zA-Z0-9_./:@%%+=,-]*|'')
          local quoted="${1//\\\\/\\\\\\\\}"
          quoted="${quoted//\\"/\\\\\\"}"
          quoted="${quoted//\\$/\\\\\\$}"
          quoted="${quoted//\\`/\\\\\\`}"
          printf '%%s' "\\"$quoted\\""
          ;;
        *) printf '%%s' "$1" ;;
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
            [[ "$candidate" == "$value_prefix"* ]] && COMPREPLY+=("$option=$(_seed4j_completion_quote_value "$candidate")")
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
      if [[ "$cur" == "=" && "$prev" == --* ]]; then
        value_candidates="$(_seed4j_value_candidates_for_option "$path" "$prev")"
        if [[ -n "$value_candidates" ]]; then
          while IFS= read -r candidate; do
            COMPREPLY+=("$(_seed4j_completion_quote_value "$candidate")")
          done <<< "$value_candidates"
          return 0
        fi
      fi

      if [[ "$prev" == "=" && "${COMP_WORDS[COMP_CWORD - 2]}" == --* ]]; then
        option="${COMP_WORDS[COMP_CWORD - 2]}"
        value_candidates="$(_seed4j_value_candidates_for_option "$path" "$option")"
        if [[ -n "$value_candidates" ]]; then
          while IFS= read -r candidate; do
            [[ "$candidate" == "$cur"* ]] && COMPREPLY+=("$(_seed4j_completion_quote_value "$candidate")")
          done <<< "$value_candidates"
          return 0
        fi
      fi

      value_candidates="$(_seed4j_value_candidates_for_option "$path" "$prev")"
      if [[ -n "$value_candidates" ]]; then
        while IFS= read -r candidate; do
          [[ "$candidate" == "$cur"* ]] && COMPREPLY+=("$(_seed4j_completion_quote_value "$candidate")")
        done <<< "$value_candidates"
        return 0
      fi

    """;
  }

  private String quote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }
}
