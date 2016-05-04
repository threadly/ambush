package org.threadly.load;

import java.util.concurrent.TimeUnit;

/**
 * <p>Interface which represents the result of an executed test step.  This provides if the test 
 * completed in error (via {@link #getError()} being not {@code null}).  As well as the runtime it 
 * took to execute the test step.</p>
 * 
 * @author jent - Mike Jensen
 */
public interface StepResult {
  /**
   * Indicates if the result of this step was of type {@link ScriptStepType#Maintenance} or 
   * {@link ScriptStepType#AsyncMaintenance}.  In either of those conditions the run time will 
   * always be zero, and thus should not be included in the resulting statistics.
   * 
   * @return {@code true} if the step was a maintance step
   */
  public boolean wasMaintanceStep();
  
  /**
   * Get description of the test.
   * 
   * @return Description of ran test
   */
  public String getDescription();
  
  /**
   * Check how long the test executed for until it completed normally, or in an error state.
   * 
   * @param desiredUnit TimeUnit which result should be provided in
   * @return nanosecond test executed for
   */
  public long getRunTime(TimeUnit desiredUnit);
  
  /**
   * Get the error result if it occurred.  This will return null if the test completed normally.
   * 
   * @return Thrown error during execution or {@code null} if executed normally
   */
  public Throwable getError();
}
