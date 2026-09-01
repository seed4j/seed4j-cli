package com.seed4j.cli.command.infrastructure.secondary;

import com.seed4j.cli.Seed4JCliApp;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class BaseJarAgentSkillResources implements BundledAgentSkillResources {

  private static final Path APPLYING_MODULES = Path.of("references/applying-modules.md");
  private static final Path MODULE_SET_PLANNING = Path.of("references/module-set-planning.md");
  private static final Path SKILL = Path.of("SKILL.md");
  private static final String RESOURCE_ROOT = "skills/seed4j-cli/";

  @Override
  public Map<Path, byte[]> read() throws IOException {
    Map<Path, byte[]> resources = new LinkedHashMap<>();
    resources.put(SKILL, read(SKILL));
    resources.put(APPLYING_MODULES, read(APPLYING_MODULES));
    resources.put(MODULE_SET_PLANNING, read(MODULE_SET_PLANNING));
    return Collections.unmodifiableMap(resources);
  }

  private byte[] read(Path relativePath) throws IOException {
    URL resource = resource(relativePath);
    try (InputStream input = resource.openStream()) {
      return input.readAllBytes();
    }
  }

  private static URL resource(Path relativePath) throws IOException {
    String codeSourceRoot = Seed4JCliApp.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
    return URI.create(codeSourceRoot + RESOURCE_ROOT + relativePath.toString().replace('\\', '/')).toURL();
  }
}
