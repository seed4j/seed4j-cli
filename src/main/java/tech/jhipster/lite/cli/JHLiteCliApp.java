package tech.jhipster.lite.cli;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import tech.jhipster.lite.JHLiteApp;
import tech.jhipster.lite.cli.shared.exit.domain.SpringApplicationExit;
import tech.jhipster.lite.cli.shared.exit.domain.SystemExit;
import tech.jhipster.lite.cli.shared.spinnerprogress.domain.SpinnerProgress;
import tech.jhipster.lite.cli.shared.spinnerprogress.infrastructure.primary.SpinnerProgressProvider;

@SpringBootApplication(scanBasePackageClasses = { JHLiteApp.class, JHLiteCliApp.class })
public class JHLiteCliApp {

  public static void main(String[] args) {
    SpinnerProgress spinnerProgress = SpinnerProgressProvider.get();
    spinnerProgress.show("Loading JHipster Lite CLI");

    ConfigurableApplicationContext context = new SpringApplicationBuilder(JHLiteCliApp.class)
      .bannerMode(Banner.Mode.OFF)
      .web(WebApplicationType.NONE)
      .lazyInitialization(true)
      .listeners(event -> handleApplicationEvent(event, spinnerProgress))
      .run(args);

    SystemExit systemExit = context.getBean(SystemExit.class);
    SpringApplicationExit springApplicationExit = context.getBean(SpringApplicationExit.class);

    int exitCode = springApplicationExit.exit(context);

    systemExit.exit(exitCode);
  }

  private static void handleApplicationEvent(Object event, SpinnerProgress spinnerProgress) {
    if (event instanceof ApplicationStartedEvent) {
      spinnerProgress.success("JHipster Lite CLI is ready");
    }
  }
}
