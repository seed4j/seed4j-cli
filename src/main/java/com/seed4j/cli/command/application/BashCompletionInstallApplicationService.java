package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.BashCompletionInstallationResult;
import com.seed4j.cli.command.domain.BashCompletionInstaller;
import com.seed4j.cli.command.domain.BashCompletionScript;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class BashCompletionInstallApplicationService {

  private final BashCompletionInstaller bashCompletionInstaller;

  public BashCompletionInstallApplicationService(BashCompletionInstaller bashCompletionInstaller) {
    Assert.notNull("bashCompletionInstaller", bashCompletionInstaller);

    this.bashCompletionInstaller = bashCompletionInstaller;
  }

  public BashCompletionInstallationResult install(BashCompletionScript script) {
    Assert.notNull("script", script);

    return bashCompletionInstaller.install(script);
  }
}
