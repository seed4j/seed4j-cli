package tech.jhipster.lite.cli.shared.progressstatus.infrastructure.primary;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import tech.jhipster.lite.cli.shared.generation.domain.ExcludeFromGeneratedCodeCoverage;
import tech.jhipster.lite.cli.shared.progressstatus.domain.ProgressStatus;

class SpinnerProgressStatus implements ProgressStatus {

  private static final String ANSI_RESET = "\u001B[0m";
  private static final String ANSI_GREEN = "\u001B[32m";
  private static final String ANSI_RED = "\u001B[31m";
  private static final String ANSI_CYAN = "\u001B[36m";
  private static final String CLEAR_LINE = "\r\033[K";
  private static final String[] SPINNER_FRAMES = { "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏" };
  private static final String DOT = ". ";
  private static final String DOUBLE_DOT = ".. ";
  private static final String TRIPLE_DOT = "... ";
  private static final String EMPTY = " ";
  private static final String LIGHTNING = "...%s⧓%s⚡ ".formatted(ANSI_CYAN, ANSI_RESET);
  private static final String LIGHTNING_DIAMOND = "...%s⧓%s⚡ 💎 ".formatted(ANSI_CYAN, ANSI_RESET);
  private static final String LIGHTNING_DIAMOND_LEAF = "...%s⧓%s⚡ 💎 🍃 ".formatted(ANSI_CYAN, ANSI_RESET);

  private static final String[] SUFFIX_ANIMATION_FRAMES = createSuffixAnimationFrames();

  private ScheduledExecutorService executor;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private String currentMessage = "";
  private int frameIndex = 0;
  private int suffixFrameIndex = 0;

  private static String[] createSuffixAnimationFrames() {
    // @formatter:off
    String[] firstSequence = {
      DOT, DOT,
      DOUBLE_DOT, DOUBLE_DOT,
      TRIPLE_DOT, TRIPLE_DOT,
      LIGHTNING, LIGHTNING, LIGHTNING, LIGHTNING,
      TRIPLE_DOT, TRIPLE_DOT,
      DOUBLE_DOT, DOUBLE_DOT,
      DOT, DOT,
      EMPTY, EMPTY
    };

    String[] secondSequence = {
      DOT, DOT,
      DOUBLE_DOT, DOUBLE_DOT,
      TRIPLE_DOT, TRIPLE_DOT,
      LIGHTNING, LIGHTNING,
      LIGHTNING_DIAMOND, LIGHTNING_DIAMOND,
      LIGHTNING_DIAMOND_LEAF, LIGHTNING_DIAMOND_LEAF, LIGHTNING_DIAMOND_LEAF, LIGHTNING_DIAMOND_LEAF,
      TRIPLE_DOT, TRIPLE_DOT,
      DOUBLE_DOT, DOUBLE_DOT,
      DOT, DOT,
      EMPTY, EMPTY
    };

    return Stream.concat(Arrays.stream(firstSequence), Arrays.stream(secondSequence))
      .toArray(String[]::new);
    // @formatter:on
  }

  @Override
  public void show() {
    show("Processing");
  }

  @Override
  public void show(String message) {
    if (running.compareAndSet(false, true)) {
      currentMessage = message;

      renderFrameSync();

      executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "spinner-animation");
        thread.setDaemon(true);
        return thread;
      });
      executor.scheduleAtFixedRate(this::renderFrame, 0, 120, TimeUnit.MILLISECONDS);
    } else {
      update(message);
    }
  }

  @Override
  public void update(String message) {
    currentMessage = message;

    renderFrameSync();
  }

  @Override
  public void hide() {
    stopSpinner();
  }

  private boolean stopSpinner() {
    if (running.compareAndSet(true, false)) {
      executor.shutdown();
      System.out.print(CLEAR_LINE);
      frameIndex = 0;
      suffixFrameIndex = 0;
      return true;
    }
    return false;
  }

  @Override
  public void success(String message) {
    displayResult(ANSI_GREEN, "✓", message);
  }

  private void displayResult(String color, String symbol, String message) {
    if (stopSpinner()) {
      System.out.println(color + symbol + ANSI_RESET + " " + message);
    }
  }

  @Override
  public void failure(String message) {
    displayResult(ANSI_RED, "✗", message);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Rendering logic is difficult to test")
  private void renderFrameSync() {
    renderSpinner(false);
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Rendering logic is difficult to test")
  private void renderSpinner(boolean updateFrame) {
    if (running.get()) {
      if (updateFrame) {
        frameIndex = (frameIndex + 1) % SPINNER_FRAMES.length;
        suffixFrameIndex = (suffixFrameIndex + 1) % SUFFIX_ANIMATION_FRAMES.length;
      }
      String frame = SPINNER_FRAMES[frameIndex];
      String suffix = SUFFIX_ANIMATION_FRAMES[suffixFrameIndex];
      System.out.print(CLEAR_LINE + ANSI_CYAN + frame + ANSI_RESET + " " + currentMessage + suffix);
    }
  }

  @ExcludeFromGeneratedCodeCoverage(reason = "Rendering logic is difficult to test")
  private void renderFrame() {
    renderSpinner(true);
  }
}
