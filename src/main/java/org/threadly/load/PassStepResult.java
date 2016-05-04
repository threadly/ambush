package org.threadly.load;

/**
 * <p>{@link StepResult} implementation which indicates a normal completion.</p>
 * 
 * @author jent - Mike Jensen
 */
class PassStepResult extends AbstractStepResult {
  public PassStepResult(CharSequence description, long runTimeInNanos) {
    super(description, runTimeInNanos);
  }

  @Override
  public Throwable getError() {
    return null;
  }

  @Override
  public boolean wasMaintanceStep() {
    return false;
  }
}
