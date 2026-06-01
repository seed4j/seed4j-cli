package com.seed4j.cli.bootstrap.infrastructure.secondary;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("seed4j.cli.runtime")
class RuntimeSelectionProperties {

  private String mode;
  private Distribution distribution = new Distribution();

  String getMode() {
    return mode;
  }

  void setMode(String mode) {
    this.mode = mode;
  }

  Distribution getDistribution() {
    return distribution;
  }

  void setDistribution(Distribution distribution) {
    this.distribution = distribution;
  }

  static class Distribution {

    private String id;
    private String version;

    String getId() {
      return id;
    }

    void setId(String id) {
      this.id = id;
    }

    String getVersion() {
      return version;
    }

    void setVersion(String version) {
      this.version = version;
    }
  }
}
