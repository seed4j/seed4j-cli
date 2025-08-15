package com.seed4j.cli;

import com.seed4j.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tech.jhipster.lite.JHLiteApp;

@SpringBootApplication(scanBasePackageClasses = { JHLiteApp.class, Seed4JCliApp.class })
@ExcludeFromGeneratedCodeCoverage(reason = "Not testing logs")
public class Seed4JCliApp {

  private static final String CONFIG_FILE_NAME = "/.config/seed4j-cli.yml";
  private static final String SPRING_CONFIG_TEMPLATE = "spring.config.location=classpath:/config/,file:%s";

  public static void main(String[] args) {
    ConfigurableApplicationContext context = loadExternalConfigFile(createApplicationBuilder()).run(args);

    System.exit(SpringApplication.exit(context));
  }

  private static SpringApplicationBuilder createApplicationBuilder() {
    return new SpringApplicationBuilder(Seed4JCliApp.class)
      .bannerMode(Banner.Mode.OFF)
      .web(WebApplicationType.NONE)
      .lazyInitialization(true);
  }

  private static SpringApplicationBuilder loadExternalConfigFile(SpringApplicationBuilder builder) {
    return Optional.of(getConfigPath())
      .filter(configPath -> Files.exists(Path.of(configPath)))
      .map(configPath -> builder.properties(SPRING_CONFIG_TEMPLATE.formatted(configPath)))
      .orElse(builder);
  }

  private static String getConfigPath() {
    return System.getProperty("user.home") + CONFIG_FILE_NAME;
  }
}
