package org.threadly.load;

/**
 * <p>Class which represents the result of an executed test step.  This provides if the test 
 * completed in error (via {@link #getError()} being not {@code null}).  As well as the runtime it 
 * took to execute the test step.
 * 
 * @author jent - Mike Jensen
 */
public class TestResult {
  private final String description;
  private final long runTimeInNanos;
  private final Throwable error;
  
  protected TestResult(String description, long runTimeInNanos) {
    this(description, runTimeInNanos, null);
  }
  
  protected TestResult(String description, long runTimeInNanos, Throwable error) {
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
   * @return nanosecond test executed for
   */
  public long runTimeInNanos() {
    return runTimeInNanos;
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
