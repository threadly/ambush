package org.threadly.load;

/**
 * <p>Indicates what type of step this is.  Use of {@link #Maintenance} and 
 * {@link #AsyncMaintenance} allows you to do operations without having the statistics and results 
 * included in the final results.  In addition the use of specifically {@link #AsyncMaintenance} 
 * allows ambush to try and reduce the impact of running the step.</p>
 * 
 * @author jent - Mike Jensen
 */
public enum ScriptStepType {
  /**
   * This indicates a normal test step.  Meaning it will be ran in the chain normally, and the 
   * results of the step will be gathered and recorded.
   */
  Normal, 
  /**
   * A maintenance step indicates that the results of the step should NOT be considered in 
   * completion statistics.  However failure conditions from a maintenance step will halt the 
   * script.  This is a step which basically is doing internal operations to assist the test.  If 
   * it is not critical when the step runs, and a failure of this step should not fail the script 
   * use {@link #AsyncMaintenance} as a typically better option.
   */
  Maintenance, 
  /**
   * Similar to {@link #Maintenance} this step will not have results or statistics recorded.  There 
   * are two critical differences however with this.  The first being that a failure from an async 
   * step will NOT halt or fail the script.  This is because we must mark the step as complete 
   * before actual execution to ensure that script progression continues.  The second difference 
   * being is that this step's execution point is arbitrary.  Ambush will _attempt_ to run it at a 
   * point where the impact to other steps will be minimized.  The earliest execution could start 
   * is dependent in how it is constructed in the script step chain.
   */
  AsyncMaintenance;
}
