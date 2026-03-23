package com.seed4j.cli.command.infrastructure.primary;

import com.seed4j.module.application.Seed4JModulesApplicationService;
import com.seed4j.module.domain.resource.Seed4JModuleResource;
import com.seed4j.module.domain.resource.Seed4JModulesResources;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;

@Component
class ListModulesCommand implements Seed4JCommand, Callable<Integer> {

  private static final int MINIMAL_SPACES_BETWEEN_COLUMNS = 2;

  private final Seed4JModulesApplicationService modules;

  public ListModulesCommand(Seed4JModulesApplicationService modules) {
    this.modules = modules;
  }

  @Override
  public CommandSpec spec() {
    CommandSpec spec = CommandSpec.wrapWithoutInspection(this).name("list");
    spec.usageMessage().description("List available seed4j modules");

    return spec;
  }

  @Override
  public String name() {
    return "list";
  }

  @Override
  public Integer call() {
    Seed4JModulesResources modulesResources = modules.resources();
    List<ListModuleRow> rows = modulesResources.stream().sorted(byModuleSlug()).map(ListModulesCommand::toRow).toList();
    System.out.printf("Available seed4j modules (%s):%n", rows.size());
    ListColumnsLayout columnsLayout = ListColumnsLayout.from(rows);
    printHeader(columnsLayout);
    rows.forEach(row -> printRow(row, columnsLayout));

    return ExitCode.OK;
  }

  private static Comparator<Seed4JModuleResource> byModuleSlug() {
    return Comparator.comparing(moduleResource -> moduleResource.slug().get());
  }

  private static ListModuleRow toRow(Seed4JModuleResource moduleResource) {
    return new ListModuleRow(moduleResource.slug().get(), "-", moduleResource.apiDoc().operation().get());
  }

  private static void printHeader(ListColumnsLayout columnsLayout) {
    System.out.printf(
      "  %s%s%s%s%s%n",
      padRight("Module", columnsLayout.moduleWidth()),
      columnSeparator(),
      padRight("Dependencies", columnsLayout.dependenciesWidth()),
      columnSeparator(),
      "Description"
    );
  }

  private static void printRow(ListModuleRow row, ListColumnsLayout columnsLayout) {
    System.out.printf(
      "  %s%s%s%s%s%n",
      padRight(row.module(), columnsLayout.moduleWidth()),
      columnSeparator(),
      padRight(row.dependencies(), columnsLayout.dependenciesWidth()),
      columnSeparator(),
      row.description()
    );
  }

  private static String columnSeparator() {
    return " ".repeat(MINIMAL_SPACES_BETWEEN_COLUMNS);
  }

  private static String padRight(String value, int width) {
    int spaces = Math.max(0, width - value.length());
    return value + " ".repeat(spaces);
  }

  private record ListModuleRow(String module, String dependencies, String description) {}

  private record ListColumnsLayout(int moduleWidth, int dependenciesWidth) {
    private static ListColumnsLayout from(List<ListModuleRow> rows) {
      int moduleWidth = Math.max("Module".length(), maxModuleWidth(rows));
      int dependenciesWidth = Math.max("Dependencies".length(), maxDependenciesWidth(rows));

      return new ListColumnsLayout(moduleWidth, dependenciesWidth);
    }

    private static int maxModuleWidth(List<ListModuleRow> rows) {
      return rows.stream().map(ListModuleRow::module).mapToInt(String::length).max().orElse(0);
    }

    private static int maxDependenciesWidth(List<ListModuleRow> rows) {
      return rows.stream().map(ListModuleRow::dependencies).mapToInt(String::length).max().orElse(0);
    }
  }
}
