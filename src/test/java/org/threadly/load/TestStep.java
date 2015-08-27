package org.threadly.load;

import org.threadly.test.concurrent.TestRunnable;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class TestStep extends AbstractScriptStep {
  private final TestRunnable testRunnable;

  public TestStep() {
    this(StringUtils.makeRandomString(5));
  }

  public TestStep(String identifier) {
    this(identifier, 0);
  }

  public TestStep(String identifier, int runTime) {
    super(identifier);
    
    this.testRunnable = new TestStepTestRunnable(runTime);
  }

  @Override
  public void runStep() {
    testRunnable.run();
  }
  
  public void handleRunStart() {
    // designed to be overridden
  }
  
  public void handleRunFinish() {
    // designed to be overridden
  }
  
  public int getRunCount() {
    return testRunnable.getRunCount();
  }
  
  public void blockTillStarted() {
    testRunnable.blockTillStarted();
  }
  
  public void blockTillFinished() {
    testRunnable.blockTillFinished();
  }
  
  protected class TestStepTestRunnable extends TestRunnable {
    public TestStepTestRunnable() {
      super();
    }
    
    public TestStepTestRunnable(int runTime) {
      super(runTime);
    }
    
    @Override
    public void handleRunStart() {
      TestStep.this.handleRunStart();
    }
    
    @Override
    public void handleRunFinish() {
      TestStep.this.handleRunFinish();
    }
  }
}
