package org.threadly.load;

import static org.junit.Assert.*;

import org.junit.Test;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class AbstractTestStepTest {
  @Test
  public void getAndSetGlobalParam() throws Exception {
    final String key = StringUtils.randomString(5);
    final String value = StringUtils.randomString(5);
    AbstractTestStep setStep = new AbstractTestStep(StringUtils.randomString(5)) {
      @Override
      public void runTest() {
        setGlobalParam(key, value);
      }
    };
    AbstractTestStep verifyStep = new AbstractTestStep(StringUtils.randomString(5)) {
      @Override
      public void runTest() {
        assertEquals(value, getGlobalParam(key));
      }
    };
    
    setStep.runTest();
    verifyStep.runTest();
  }
}
