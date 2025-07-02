package tech.jhipster.lite.cli.shared.spinnerprogress.domain;

public interface SpinnerProgress {
  void show();
  void show(String message);
  void update(String message);
  void hide();
  void success(String message);
  void failure(String message);
}
