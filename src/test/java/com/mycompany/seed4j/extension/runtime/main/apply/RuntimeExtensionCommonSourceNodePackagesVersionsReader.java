package com.mycompany.seed4j.extension.runtime.main.apply;

import static com.seed4j.module.domain.nodejs.Seed4JNodePackagesVersionSource.COMMON;

import com.seed4j.module.domain.nodejs.NodePackage;
import com.seed4j.module.domain.nodejs.NodePackagesVersions;
import com.seed4j.module.infrastructure.secondary.nodejs.NodePackagesVersionsReader;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Repository;

@Repository
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "seed4j.cli.runtime.mode", havingValue = "extension")
public class RuntimeExtensionCommonSourceNodePackagesVersionsReader implements NodePackagesVersionsReader {

  @Override
  public NodePackagesVersions get() {
    return NodePackagesVersions.builder()
      .put(COMMON.build(), List.of(new NodePackage("prettier", "3.6.2")))
      .build();
  }
}
