package org.threadly.load;

/**
 * Interface for the required API for any test step to be executed.
 */
public interface ScriptStep {
  /**
   * Get the name or identifier that was provided at construction for this test step.
   * 
   * @return Script step name
   */
  public CharSequence getIdentifier();
  
  /**
   * Checked before step execution.  This indicates what type of script step this is, and thus 
   * how results from the step should be handled.
   * 
   * @return A non-null type to indicate how this step should be ran
   */
  public ScriptStepType getStepType();
  
  /**
   * This must be implemented for the actual execution of the script step.  This invocation should 
   * block until the step has completed.  If the script completed without error just return 
   * normally.  If the test failed, it is expected this will throw an exception which represents 
   * the error.
   * 
   * @throws Exception Thrown if any failure occurred while running the test
   */
  public void runStep() throws Exception;
}
