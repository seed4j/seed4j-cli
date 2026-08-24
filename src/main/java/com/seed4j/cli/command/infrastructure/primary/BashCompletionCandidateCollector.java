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

class BashCompletionCandidateCollector {

  private static final String OPTION_CANDIDATE_SEPARATOR = "\t";

  BashCompletionCandidates collect(CommandSpec rootCommand) {
    return collect(rootCommand, "");
  }

  private BashCompletionCandidates collect(CommandSpec command, String path) {
    return Stream.concat(
      Stream.of(currentCandidates(command, path)),
      subcommandsByName(command)
        .entrySet()
        .stream()
        .map(entry -> collect(entry.getValue(), childPath(path, entry.getKey())))
    ).reduce(BashCompletionCandidates.empty(), BashCompletionCandidates::merge);
  }

  private BashCompletionCandidates currentCandidates(CommandSpec command, String path) {
    Map<String, String> candidatesByPath = new TreeMap<>(
      Map.of(path, Stream.concat(subcommandNames(command).stream(), optionNames(command).stream()).collect(Collectors.joining(" ")))
    );
    Map<String, String> valueOptionsByPath = new TreeMap<>(Map.of(path, String.join(" ", valueOptionNames(command))));
    Map<String, List<String>> valueCandidatesByPathAndOption = valueCandidatesByPathAndOption(command, path);

    return new BashCompletionCandidates(candidatesByPath, valueOptionsByPath, valueCandidatesByPathAndOption);
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
}
