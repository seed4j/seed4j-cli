package tech.jhipster.lite.cli;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tech.jhipster.lite.JHLiteApp;
import tech.jhipster.lite.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;

@SpringBootApplication(scanBasePackageClasses = { JHLiteApp.class, JHLiteCliApplicationApp.class })
@ExcludeFromGeneratedCodeCoverage(reason = "Not testing logs")
public class JHLiteCliApplicationApp {

  public static void main(String[] args) {
    ConfigurableApplicationContext context = new SpringApplicationBuilder(JHLiteCliApplicationApp.class)
      .bannerMode(Banner.Mode.OFF)
      .web(WebApplicationType.NONE)
      .lazyInitialization(true)
      .run(args);
    System.exit(SpringApplication.exit(context));
  }
}
