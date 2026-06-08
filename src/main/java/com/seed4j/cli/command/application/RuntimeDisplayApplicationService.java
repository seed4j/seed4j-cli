package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.RuntimeDisplay;
import com.seed4j.cli.command.domain.RuntimeDisplayReader;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class RuntimeDisplayApplicationService {

  private final RuntimeDisplayReader runtimeDisplayReader;

  public RuntimeDisplayApplicationService(RuntimeDisplayReader runtimeDisplayReader) {
    Assert.notNull("runtimeDisplayReader", runtimeDisplayReader);

    this.runtimeDisplayReader = runtimeDisplayReader;
  }

  public RuntimeDisplay activeRuntime() {
    return runtimeDisplayReader.activeRuntime();
  }
}
