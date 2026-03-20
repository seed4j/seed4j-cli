package com.seed4j.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class SystemOutputCaptor implements AutoCloseable {

  private final PrintStream originalOut;
  private final PrintStream originalErr;
  private final ByteArrayOutputStream standardOutputContent;
  private final ByteArrayOutputStream standardErrorContent;

  public SystemOutputCaptor() {
    this.originalOut = System.out;
    this.originalErr = System.err;
    this.standardOutputContent = new ByteArrayOutputStream();
    this.standardErrorContent = new ByteArrayOutputStream();

    System.setOut(new PrintStream(standardOutputContent));
    System.setErr(new PrintStream(standardErrorContent));
  }

  public String getOutput() {
    return getStandardOutput() + getStandardError();
  }

  public String getStandardOutput() {
    return standardOutputContent.toString();
  }

  public String getStandardError() {
    return standardErrorContent.toString();
  }

  @Override
  public void close() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }
}
