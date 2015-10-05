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
    assertTrue(new TestScriptRunner(new String[]{StringUtils.makeRandomString(5)}).usageAndExitCalled);
  }
  
  @Test
  public void classNotInstanceOfScriptRunnerTest() {
    assertTrue(new TestScriptRunner(new String[]{java.util.ArrayList.class.getName()})
                 .usageAndExitCalled);
  }
  
  @Test
  public void classHasNoEmptyConstructorTest() {
    assertTrue(new TestScriptRunner(new String[]{NoEmptyArgConstructorFactory.class.getName()})
                 .usageAndExitCalled);
  }
  
  @SuppressWarnings("unused")
  @Test (expected = ScriptParameterException.class)
  public void errorDurringScriptGenerationTest() {
    new TestScriptRunner(new String[]{TestScriptFactory.class.getName()});
  }
  
  @Test
  public void runScriptStepFailureTest() throws InterruptedException {
    TestScriptRunner runner = new TestScriptRunner(new String[]{ErrorScriptFactory.class.getName()});
    runner.runScript();
    // no exception thrown
    assertFalse(runner.usageAndExitCalled);
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
  
  protected static class NoEmptyArgConstructorFactory extends ScriptFactory {
    public NoEmptyArgConstructorFactory(@SuppressWarnings("unused") String argument) {
      // nothing needed to happen here
    }

    @Override
    public ExecutableScript buildScript() {
      throw new UnsupportedOperationException();
    }
  }
  
  protected static class ErrorScriptFactory extends ScriptFactory {
    @Override
    public ExecutableScript buildScript() {
      SequentialScriptBuilder builder = new SequentialScriptBuilder();
      builder.addStep(new ScriptStep() {
        @Override
        public String getIdentifier() {
          return "fail step";
        }

        @Override
        public void runStep() throws Exception {
          throw new Exception("step failure");
        }
      });
      
      return builder.build();
    }
  }
}
