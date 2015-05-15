package org.threadly.load;

/**
 * <p>Interface for the minimum API for any test step to be executed.</p>
 * 
 * @author jent - Mike Jensen
 */
public interface ScriptStepInterface {
  /**
   * Get the name or identifier that was provided at construction for this test step.
   * 
   * @return Script step name
   */
  public String getIdentifier();
  
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