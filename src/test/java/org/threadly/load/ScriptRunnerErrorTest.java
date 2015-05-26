package org.threadly.load;

import static org.junit.Assert.*;

import org.junit.Test;
import org.threadly.load.ScriptFactory.ScriptParameterException;
import org.threadly.load.ScriptFactoryTest.TestScriptFactory;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class ScriptRunnerErrorTest {
  @Test
  public void emptyArgsTest() {
    assertTrue(new TestScriptRunner(new String[0]).usageAndExitCalled);
  }
  
  @Test
  public void unknownClassTest() {
    assertTrue(new TestScriptRunner(new String[]{StringUtils.randomString(5)}).usageAndExitCalled);
  }
  
  @Test
  public void classNotInstanceOfScriptRunnerTest() {
    assertTrue(new TestScriptRunner(new String[]{java.util.ArrayList.class.getName()})
                 .usageAndExitCalled);
  }
  
  @SuppressWarnings("unused")
  @Test (expected = ScriptParameterException.class)
  public void errorDurringScriptGenerationTest() {
    new TestScriptRunner(new String[]{TestScriptFactory.class.getName()});
  }
  
  private static class TestScriptRunner extends ScriptRunner {
    protected boolean usageAndExitCalled;
    
    protected TestScriptRunner(String[] args) {
      super(args);
    }

    @Override
    protected void usageAndExit(String runningScript) {
      usageAndExitCalled = true;
    }
  }
}
