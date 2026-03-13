package com.seed4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

@UnitTest
class Seed4JCliAppTest {

  @Test
  void shouldDelegateStartupToTheLauncher() {
    RecordingLauncher launcher = new RecordingLauncher();

    int exitCode = Seed4JCliApp.run(new String[] { "--version" }, launcher);

    assertThat(exitCode).isEqualTo(23);
    assertThat(launcher.arguments()).containsExactly("--version");
  }

  private static final class RecordingLauncher implements Seed4JCliApp.EntryPointLauncher {

    private String[] arguments;

    @Override
    public int launch(String[] args) {
      this.arguments = args;
      return 23;
    }

    String[] arguments() {
      return arguments;
    }
  }

  @Test
  void shouldExitWithTheCodeReturnedByTheLauncherBackedRunPath() {
    RecordingLauncher launcher = new RecordingLauncher();
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.main(new String[] { "--version" }, launcher, exitHandler);

    assertThat(launcher.arguments()).containsExactly("--version");
    assertThat(exitHandler.exitCode()).isEqualTo(23);
  }

  private static final class RecordingExitHandler implements Seed4JCliApp.ExitHandler {

    private Integer exitCode;

    @Override
    public void exit(int exitCode) {
      this.exitCode = exitCode;
    }

    Integer exitCode() {
      return exitCode;
    }
  }

  @Test
  void shouldDelegateThePublicMainPathToTheLauncherFactory() {
    RecordingLauncher launcher = new RecordingLauncher();
    RecordingLauncherFactory launcherFactory = new RecordingLauncherFactory(launcher);
    RecordingExitHandler exitHandler = new RecordingExitHandler();

    Seed4JCliApp.main(new String[] { "--version" }, launcherFactory, exitHandler);

    assertThat(launcherFactory.wasCalled()).isTrue();
    assertThat(launcher.arguments()).containsExactly("--version");
    assertThat(exitHandler.exitCode()).isEqualTo(23);
  }

  private static final class RecordingLauncherFactory implements Seed4JCliApp.EntryPointLauncherFactory {

    private final Seed4JCliApp.EntryPointLauncher launcher;
    private boolean called;

    private RecordingLauncherFactory(Seed4JCliApp.EntryPointLauncher launcher) {
      this.launcher = launcher;
    }

    @Override
    public Seed4JCliApp.EntryPointLauncher create() {
      called = true;
      return launcher;
    }

    boolean wasCalled() {
      return called;
    }
  }

  @Test
  void shouldDelegateThePublicMainToTheLauncherBackedDependenciesPath() {
    RecordingLauncher launcher = new RecordingLauncher();
    RecordingLauncherFactory launcherFactory = new RecordingLauncherFactory(launcher);
    RecordingExitHandler exitHandler = new RecordingExitHandler();
    RecordingMainDependencies dependencies = new RecordingMainDependencies(launcherFactory, exitHandler);

    Seed4JCliApp.main(new String[] { "--version" }, dependencies);

    assertThat(dependencies.wasLauncherFactoryRequested()).isTrue();
    assertThat(dependencies.wasExitHandlerRequested()).isTrue();
    assertThat(launcher.arguments()).containsExactly("--version");
    assertThat(exitHandler.exitCode()).isEqualTo(23);
  }

  private static final class RecordingMainDependencies implements Seed4JCliApp.MainDependencies {

    private final Seed4JCliApp.EntryPointLauncherFactory launcherFactory;
    private final Seed4JCliApp.ExitHandler exitHandler;
    private boolean launcherFactoryRequested;
    private boolean exitHandlerRequested;

    private RecordingMainDependencies(Seed4JCliApp.EntryPointLauncherFactory launcherFactory, Seed4JCliApp.ExitHandler exitHandler) {
      this.launcherFactory = launcherFactory;
      this.exitHandler = exitHandler;
    }

    @Override
    public Seed4JCliApp.EntryPointLauncherFactory launcherFactory() {
      launcherFactoryRequested = true;
      return launcherFactory;
    }

    @Override
    public Seed4JCliApp.ExitHandler exitHandler() {
      exitHandlerRequested = true;
      return exitHandler;
    }

    boolean wasLauncherFactoryRequested() {
      return launcherFactoryRequested;
    }

    boolean wasExitHandlerRequested() {
      return exitHandlerRequested;
    }
  }
}
