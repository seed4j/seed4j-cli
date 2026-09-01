package com.seed4j.cli.bootstrap.infrastructure.primary;

import com.seed4j.cli.bootstrap.domain.Seed4JCliHome;
import com.seed4j.cli.shared.error.domain.Assert;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class JavaSeed4JCliHomeReader {

  private final Seed4JCliHome cliHome;

  public JavaSeed4JCliHomeReader(Seed4JCliHome cliHome) {
    Assert.notNull("cliHome", cliHome);
    this.cliHome = cliHome;
  }

  public Path path() {
    return cliHome.path();
  }
}
