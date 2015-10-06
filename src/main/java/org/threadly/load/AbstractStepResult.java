package org.threadly.load;

import java.util.concurrent.TimeUnit;

/**
 * <p>Abstract implementation for the interface {@link StepResult}.</p>
 * 
 * @author jent - Mike Jensen
 */
abstract class AbstractStepResult implements StepResult {
  private final CharSequence description;
  private final long runTimeInNanos;
  
  protected AbstractStepResult(CharSequence description, long runTimeInNanos) {
    this.description = description;
    this.runTimeInNanos = runTimeInNanos;
  }
  
  @Override
  public String getDescription() {
    return description.toString();
  }

  @Override
  public long getRunTime(TimeUnit desiredUnit) {
    return desiredUnit.convert(runTimeInNanos, TimeUnit.NANOSECONDS);
  }
}
