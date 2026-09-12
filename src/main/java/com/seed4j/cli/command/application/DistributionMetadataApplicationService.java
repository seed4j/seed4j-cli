package com.seed4j.cli.command.application;

import com.seed4j.cli.command.domain.distribution.DistributionMetadata;
import com.seed4j.cli.command.domain.distribution.DistributionMetadataReader;
import com.seed4j.cli.shared.error.domain.Assert;
import org.springframework.stereotype.Service;

@Service
public class DistributionMetadataApplicationService {

  private final DistributionMetadata metadata;

  public DistributionMetadataApplicationService(DistributionMetadataReader metadataReader) {
    Assert.notNull("metadataReader", metadataReader);
    metadata = metadataReader.read();
  }

  public DistributionMetadata metadata() {
    return metadata;
  }
}
