package org.threadly.load;

import org.junit.Test;
import org.threadly.test.concurrent.AsyncVerifier;
import org.threadly.util.Clock;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class ScriptRunnerBasicScriptTest {
  private static final String boolKey = "bool";
  private static final String intKey = "int";
  private static final String longKey = "long";
  private static final String doubleKey = "double";
  private static final String stringKey = "str";
  private static final boolean boolTestVal = true;
  private static final int intTestVal = 10;
  private static final long longTestVal = Clock.lastKnownTimeMillis();
  private static final double doubleTestVal = Clock.lastKnownTimeMillis() / 1000.;
  private static final String stringTestVal = StringUtils.makeRandomString(5);
  private static AsyncVerifier av = new AsyncVerifier();
  
  @Test
  public void runSimpleScriptTest() throws Exception {
    String[] args = new String[] {SimpleScriptFactory.class.getName(), 
                                  boolKey + "=" + boolTestVal, 
                                  intKey + "=" + intTestVal, 
                                  longKey + "=" + longTestVal, 
                                  doubleKey + "=" + doubleTestVal, 
                                  stringKey + "=" + stringTestVal};
    ScriptRunner.main(args);
    av.waitForTest();
  }
  
  public static class SimpleScriptFactory extends ScriptFactory {
    @Override
    public ExecutableScript buildScript() {
      SequentialScriptBuilder scriptBuilder = new SequentialScriptBuilder();

      scriptBuilder.addStep(new AbstractScriptStep("bool verifier") {
        @Override
        public void runStep() throws Exception {
          av.assertEquals(boolTestVal, getBoolValue(boolKey));
        }
      });
      scriptBuilder.addStep(new AbstractScriptStep("int verifier") {
        @Override
        public void runStep() throws Exception {
          av.assertEquals(intTestVal, getIntValue(intKey));
        }
      });
      scriptBuilder.addStep(new AbstractScriptStep("long verifier") {
        @Override
        public void runStep() throws Exception {
          av.assertEquals(longTestVal, getLongValue(longKey));
        }
      });
      scriptBuilder.addStep(new AbstractScriptStep("double verifier") {
        @Override
        public void runStep() throws Exception {
          av.assertEquals(doubleTestVal, getDoubleValue(doubleKey));
        }
      });
      scriptBuilder.addStep(new AbstractScriptStep("string verifier") {
        @Override
        public void runStep() throws Exception {
          av.assertEquals(stringTestVal, getStringValue(stringKey));
        }
      });
      scriptBuilder.addStep(new AbstractScriptStep("end step") {
        @Override
        public void runStep() throws Exception {
          av.signalComplete();
        }
      });
      
      return scriptBuilder.build();
    }
  }
}
