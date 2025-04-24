package tech.jhipster.lite.cli.command;

import tech.jhipster.lite.cli.shared.error.domain.Assert;

public record Command(String command) {
  public Command {
    Assert.notBlank("command", command);
  }
}
