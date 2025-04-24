package tech.jhipster.lite.cli.command;

import tech.jhipster.lite.cli.shared.error.domain.Assert;

public record JHipsterCommand(Command command) {
  public JHipsterCommand {
    Assert.notNull("command", command);
  }
}
