package tech.jhipster.lite.cli.shared.progressstatus.domain;

public interface ProgressStatus {
  void show();
  void show(String message);
  void update(String message);
  void hide();
  void success(String message);
  void failure(String message);
}
