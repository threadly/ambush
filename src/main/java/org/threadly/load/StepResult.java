package org.threadly.load;

import java.util.concurrent.TimeUnit;

/**
 * <p>Class which represents the result of an executed test step.  This provides if the test 
 * completed in error (via {@link #getError()} being not {@code null}).  As well as the runtime it 
 * took to execute the test step.
 * 
 * @author jent - Mike Jensen
 */
public class StepResult {
  private final String description;
  private final long runTimeInNanos;
  private final Throwable error;
  
  protected StepResult(String description, long runTimeInNanos) {
    this(description, runTimeInNanos, null);
  }
  
  protected StepResult(String description, long runTimeInNanos, Throwable error) {
    this.description = description;
    this.runTimeInNanos = runTimeInNanos;
    this.error = error;
  }
  
  /**
   * Get description of the test.
   * 
   * @return Description of ran test
   */
  public String getDescription() {
    return description;
  }
  
  /**
   * Check how long the test executed for until it completed normally, or in an error state.
   * 
   * @param desiredUnit TimeUnit which result should be provided in
   * @return nanosecond test executed for
   */
  public long getRunTime(TimeUnit desiredUnit) {
    return desiredUnit.convert(runTimeInNanos, TimeUnit.NANOSECONDS);
  }
  
  /**
   * Get the error result if it occurred.  This will return null if the test completed normally.
   * 
   * @return Thrown error during execution or {@code null} if executed normally
   */
  public Throwable getError() {
    return error;
  }
}
