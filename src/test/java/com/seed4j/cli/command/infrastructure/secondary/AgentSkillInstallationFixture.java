package com.seed4j.cli.command.infrastructure.secondary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class AgentSkillInstallationFixture {

  private final Path skillsDirectory;
  private final Path destination;
  private final Path siblingSkill;

  AgentSkillInstallationFixture(Path skillsDirectory) {
    this.skillsDirectory = skillsDirectory;
    destination = skillsDirectory.resolve("seed4j-cli");
    siblingSkill = skillsDirectory.resolve("sibling-skill/SKILL.md");
  }

  FileSystemAgentSkillInstaller installer(AgentSkillFileOperations fileOperations) {
    return new FileSystemAgentSkillInstaller(scope -> destination, this::bundledFiles, fileOperations);
  }

  Map<Path, byte[]> bundledFiles() {
    Map<Path, byte[]> files = new LinkedHashMap<>();
    files.put(Path.of("SKILL.md"), "skill entrypoint\n".getBytes(StandardCharsets.UTF_8));
    files.put(Path.of("references/applying-modules.md"), "individual module\n".getBytes(StandardCharsets.UTF_8));
    files.put(Path.of("references/module-set-planning.md"), "module set\n".getBytes(StandardCharsets.UTF_8));
    return Map.copyOf(files);
  }

  Map<Path, String> bundledFileSnapshot() {
    return snapshot(bundledFiles());
  }

  void writeModifiedSkill() throws IOException {
    Files.createDirectories(destination.resolve("references"));
    Files.writeString(destination.resolve("SKILL.md"), "manually modified\n");
    Files.writeString(destination.resolve("references/stale.md"), "stale\n");
  }

  void writePreviousBinarySkill() throws IOException {
    writeDestinationFiles(previousBinaryFiles());
  }

  Map<Path, String> previousBinaryFileSnapshot() {
    return snapshot(previousBinaryFiles());
  }

  private static Map<Path, byte[]> previousBinaryFiles() {
    return Map.of(Path.of("SKILL.md"), new byte[] { 0, 1, 2, 3 }, Path.of("references/legacy.md"), new byte[] { 4, 5, 6 });
  }

  void writePreviousTextSkill() throws IOException {
    writeDestinationFiles(previousTextFiles());
  }

  Map<Path, String> previousTextFileSnapshot() {
    return snapshot(previousTextFiles());
  }

  private static Map<Path, byte[]> previousTextFiles() {
    return Map.of(Path.of("SKILL.md"), "previous skill\n".getBytes(StandardCharsets.UTF_8));
  }

  private void writeDestinationFiles(Map<Path, byte[]> files) throws IOException {
    for (Map.Entry<Path, byte[]> file : files.entrySet()) {
      Path target = destination.resolve(file.getKey());
      Files.createDirectories(target.getParent());
      Files.write(target, file.getValue());
    }
  }

  void writeSiblingSkill() throws IOException {
    Files.createDirectories(siblingSkill.getParent());
    Files.writeString(siblingSkill, "sibling\n");
  }

  Map<Path, String> destinationFileSnapshot() throws IOException {
    Map<Path, byte[]> files = new LinkedHashMap<>();
    try (Stream<Path> destinationFiles = Files.walk(destination)) {
      for (Path file : destinationFiles.filter(Files::isRegularFile).toList()) {
        files.put(destination.relativize(file), Files.readAllBytes(file));
      }
    }
    return snapshot(files);
  }

  private static Map<Path, String> snapshot(Map<Path, byte[]> files) {
    Map<Path, String> snapshot = new LinkedHashMap<>();
    files.forEach((path, content) -> snapshot.put(path, Base64.getEncoder().encodeToString(content)));
    return Map.copyOf(snapshot);
  }

  List<String> skillEntries() throws IOException {
    try (Stream<Path> entries = Files.list(skillsDirectory)) {
      return entries.map(path -> path.getFileName().toString()).toList();
    }
  }

  Path skillsDirectory() {
    return skillsDirectory;
  }

  Path destination() {
    return destination;
  }

  Path siblingSkill() {
    return siblingSkill;
  }
}
