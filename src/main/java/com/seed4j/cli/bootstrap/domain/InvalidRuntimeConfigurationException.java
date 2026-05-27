package com.seed4j.cli.bootstrap.domain;

import java.util.Optional;

public class InvalidRuntimeConfigurationException extends RuntimeException {

  public InvalidRuntimeConfigurationException(String message) {
    super(message);
  }

  public InvalidRuntimeConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }

  public static InvalidRuntimeConfigurationException technicalError(String baseMessage, Throwable cause) {
    return new InvalidRuntimeConfigurationException(messageWithTechnicalDetails(baseMessage, cause), cause);
  }

  private static String messageWithTechnicalDetails(String baseMessage, Throwable cause) {
    return technicalDetail(cause)
      .map(detail -> baseMessage + " Details: " + detail)
      .orElse(baseMessage);
  }

  private static Optional<String> technicalDetail(Throwable cause) {
    return Optional.ofNullable(cause)
      .map(InvalidRuntimeConfigurationException::detailValue)
      .filter(detail -> !detail.isBlank());
  }

  private static String detailValue(Throwable cause) {
    return Optional.ofNullable(cause.getMessage())
      .filter(message -> !message.isBlank())
      .orElseGet(() -> cause.getClass().getSimpleName());
  }
}
