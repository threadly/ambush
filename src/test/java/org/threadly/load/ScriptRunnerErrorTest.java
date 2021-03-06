package org.threadly.load;

import static org.junit.Assert.*;

import org.junit.Test;
import org.threadly.load.ParameterStore.ParameterException;
import org.threadly.load.ScriptFactoryTest.TestScriptFactory;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class ScriptRunnerErrorTest {
  @Test
  public void emptyArgsTest() {
    assertTrue(new TestScriptRunner(new String[0]).intializationFailureInvoked);
  }
  
  @Test
  public void unknownClassTest() {
    assertTrue(new TestScriptRunner(new String[]{StringUtils.makeRandomString(5)}).intializationFailureInvoked);
  }
  
  @Test
  public void classNotInstanceOfScriptRunnerTest() {
    assertTrue(new TestScriptRunner(new String[]{java.util.ArrayList.class.getName()})
                 .intializationFailureInvoked);
  }
  
  @Test
  public void classHasNoEmptyConstructorTest() {
    assertTrue(new TestScriptRunner(new String[]{NoEmptyArgConstructorFactory.class.getName()})
                 .intializationFailureInvoked);
  }
  
  @SuppressWarnings("unused")
  @Test (expected = ParameterException.class)
  public void errorDurringScriptGenerationTest() {
    new TestScriptRunner(new String[]{TestScriptFactory.class.getName()});
  }
  
  @Test
  public void runScriptStepFailureTest() throws Exception {
    TestScriptRunner runner = new TestScriptRunner(new String[]{ErrorScriptFactory.class.getName()});
    runner.runScript();
    // no exception thrown
    assertFalse(runner.intializationFailureInvoked);
  }
  
  private static class TestScriptRunner extends ScriptRunner {
    protected boolean intializationFailureInvoked;
    
    protected TestScriptRunner(String[] args) {
      super(args);
    }

    @Override
    protected void handleInitializationFailure(String runningScript) {
      intializationFailureInvoked = true;
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
      builder.addStep(new AbstractScriptStep("fail step") {
        @Override
        public void runStep() throws Exception {
          throw new Exception("step failure");
        }
      });
      
      return builder.build();
    }
  }
}
