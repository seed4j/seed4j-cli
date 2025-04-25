package tech.jhipster.lite.cli.command.infrastructure.primary;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Configuration
@ComponentScan(
  basePackages = { "tech.jhipster.lite.cli", "tech.jhipster.lite.module" },
  excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "tech.jhipster.lite.cli.cucumber.*")
)
public class CommandContextTestConfig {}
