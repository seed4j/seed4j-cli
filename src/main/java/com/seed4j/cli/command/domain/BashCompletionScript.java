package com.seed4j.cli.command.domain;

import com.seed4j.cli.shared.error.domain.Assert;

public record BashCompletionScript(String content) {
  public BashCompletionScript {
    Assert.notBlank("content", content);
  }
}
