package com.seed4j.cli.command.infrastructure.primary;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

record BashCompletionCandidates(
  Map<String, String> candidatesByPath,
  Map<String, String> valueOptionsByPath,
  Map<String, List<String>> valueCandidatesByPathAndOption
) {
  BashCompletionCandidates {
    candidatesByPath = immutableSortedMap(candidatesByPath);
    valueOptionsByPath = immutableSortedMap(valueOptionsByPath);
    valueCandidatesByPathAndOption = immutableSortedCandidateMap(valueCandidatesByPathAndOption);
  }

  private static Map<String, String> immutableSortedMap(Map<String, String> values) {
    return Collections.unmodifiableMap(new TreeMap<>(values));
  }

  private static Map<String, List<String>> immutableSortedCandidateMap(Map<String, List<String>> candidates) {
    TreeMap<String, List<String>> immutableCandidates = candidates
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()), (left, right) -> right, TreeMap::new));

    return Collections.unmodifiableMap(immutableCandidates);
  }

  static BashCompletionCandidates empty() {
    return new BashCompletionCandidates(Map.of(), Map.of(), Map.of());
  }

  BashCompletionCandidates merge(BashCompletionCandidates other) {
    Map<String, String> mergedCandidatesByPath = new TreeMap<>(candidatesByPath);
    Map<String, String> mergedValueOptionsByPath = new TreeMap<>(valueOptionsByPath);
    Map<String, List<String>> mergedValueCandidatesByPathAndOption = new TreeMap<>(valueCandidatesByPathAndOption);

    mergedCandidatesByPath.putAll(other.candidatesByPath());
    mergedValueOptionsByPath.putAll(other.valueOptionsByPath());
    mergedValueCandidatesByPathAndOption.putAll(other.valueCandidatesByPathAndOption());

    return new BashCompletionCandidates(mergedCandidatesByPath, mergedValueOptionsByPath, mergedValueCandidatesByPathAndOption);
  }
}
