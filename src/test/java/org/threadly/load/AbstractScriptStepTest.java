package org.threadly.load;

import static org.junit.Assert.*;

import org.junit.Test;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class AbstractScriptStepTest {
  @Test
  public void getAndSetGlobalParam() throws Exception {
    final String key = StringUtils.makeRandomString(5);
    final String value = StringUtils.makeRandomString(5);
    AbstractScriptStep setStep = new AbstractScriptStep(StringUtils.makeRandomString(5)) {
      @Override
      public void runStep() {
        setGlobalParam(key, value);
      }
    };
    AbstractScriptStep verifyStep = new AbstractScriptStep(StringUtils.makeRandomString(5)) {
      @Override
      public void runStep() {
        assertEquals(value, getGlobalParam(key));
      }
    };
    
    setStep.runStep();
    verifyStep.runStep();
  }
}
