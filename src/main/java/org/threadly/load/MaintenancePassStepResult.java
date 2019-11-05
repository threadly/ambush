package org.threadly.load;

/**
 * {@link StepResult} implementation which indicates a normal completion of a maintenance 
 * step.
 */
class MaintenancePassStepResult extends PassStepResult {
  public MaintenancePassStepResult(CharSequence description) {
    super(description, 0);
  }

  @Override
  public boolean wasMaintanceStep() {
    return true;
  }
}
