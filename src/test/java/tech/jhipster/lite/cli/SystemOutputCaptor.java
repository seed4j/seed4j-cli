package tech.jhipster.lite.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

class SystemOutputCaptor implements AutoCloseable {

  private final PrintStream originalOut;
  private final PrintStream originalErr;
  private final ByteArrayOutputStream outContent;

  SystemOutputCaptor() {
    this.originalOut = System.out;
    this.originalErr = System.err;
    this.outContent = new ByteArrayOutputStream();

    PrintStream capture = new PrintStream(outContent);
    System.setOut(capture);
    System.setErr(capture);
  }

  String getOutput() {
    return outContent.toString();
  }

  @Override
  public void close() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }
}
