package org.threadly.load;

/**
 * {@link StepResult} implementation which indicates an error condition during a maintenance step.
 */
class MaintenanceErrorStepResult extends ErrorStepResult {
  protected MaintenanceErrorStepResult(CharSequence description, Throwable error) {
    super(description, 0, error);
  }

  @Override
  public boolean wasMaintanceStep() {
    return true;
  }
}
