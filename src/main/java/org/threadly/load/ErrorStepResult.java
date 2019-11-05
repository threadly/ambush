package org.threadly.load;

import org.threadly.util.ArgumentVerifier;

/**
 * {@link StepResult} implementation which indicates an error condition.
 */
class ErrorStepResult extends AbstractStepResult {
  private final Throwable error;

  protected ErrorStepResult(CharSequence description, long runTimeInNanos, Throwable error) {
    super(description, runTimeInNanos);
    
    ArgumentVerifier.assertNotNull(error, "error");
    
    this.error = error;
  }

  @Override
  public Throwable getError() {
    return error;
  }

  @Override
  public boolean wasMaintanceStep() {
    return false;
  }
}
