package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.infrastructure.primary.PreSpringBootstrapTestSupport.CliLaunchResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class ModuleCatalogOutput {

  private static final String ROW_INDENT = " ".repeat(2);
  private static final String COLUMN_SEPARATOR = " ".repeat(2);
  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

  private ModuleCatalogOutput() {}

  static List<String> slugsIn(String output) {
    return output.lines().map(ModuleCatalogOutput::slugFromLine).flatMap(Optional::stream).toList();
  }

  private static Optional<String> slugFromLine(String line) {
    if (!line.startsWith(ROW_INDENT)) {
      return Optional.empty();
    }

    String moduleColumns = line.substring(ROW_INDENT.length());
    int firstColumnSeparatorIndex = moduleColumns.indexOf(COLUMN_SEPARATOR);
    if (firstColumnSeparatorIndex < 0) {
      return Optional.empty();
    }

    String candidateSlug = moduleColumns.substring(0, firstColumnSeparatorIndex);
    if (!SLUG_PATTERN.matcher(candidateSlug).matches()) {
      return Optional.empty();
    }

    return Optional.of(candidateSlug);
  }

  static Comparison compare(CliLaunchResult standardLaunch, CliLaunchResult extensionLaunch) {
    return new Comparison(
      standardLaunch.exitCode(),
      extensionLaunch.exitCode(),
      slugsIn(standardLaunch.output()),
      slugsIn(extensionLaunch.output())
    );
  }

  record Comparison(int standardExitCode, int extensionExitCode, List<String> standardSlugs, List<String> extensionSlugs) {
    List<Integer> exitCodes() {
      return List.of(standardExitCode, extensionExitCode);
    }

    Set<String> addedSlugs() {
      return difference(Set.copyOf(extensionSlugs), Set.copyOf(standardSlugs));
    }

    Set<String> removedSlugs() {
      return difference(Set.copyOf(standardSlugs), Set.copyOf(extensionSlugs));
    }

    private Set<String> difference(Set<String> sourceSlugs, Set<String> slugsToExclude) {
      Set<String> remainingSlugs = new LinkedHashSet<>();
      for (String sourceSlug : sourceSlugs) {
        if (!slugsToExclude.contains(sourceSlug)) {
          remainingSlugs.add(sourceSlug);
        }
      }
      return remainingSlugs;
    }
  }
}
