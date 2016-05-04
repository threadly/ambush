package org.threadly.load;

/**
 * <p>{@link StepResult} implementation which indicates a normal completion of a maintenance 
 * step.</p>
 * 
 * @author jent - Mike Jensen
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
