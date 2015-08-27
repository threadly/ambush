package org.threadly.load;

import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class FailureTestStep extends TestStep {
  private final RuntimeException failure;

  public FailureTestStep() {
    this(StringUtils.makeRandomString(5), 0);
  }

  protected FailureTestStep(String identifier) {
    this(identifier, 0, new RuntimeException());
  }

  protected FailureTestStep(String identifier, RuntimeException failure) {
    this(identifier, 0, failure);
  }

  public FailureTestStep(int runTime) {
    this(StringUtils.makeRandomString(5), runTime);
  }

  protected FailureTestStep(String identifier, int runTime) {
    this(identifier, runTime, new RuntimeException());
  }

  protected FailureTestStep(String identifier, int runTime, RuntimeException failure) {
    super(identifier, runTime);
    this.failure = failure;
  }

  @Override
  public void runStep() {
    super.runStep();
    
    throw failure;
  }
}
