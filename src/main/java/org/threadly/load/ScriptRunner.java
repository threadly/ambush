package org.threadly.load;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.load.ScriptFactory.TestParameterException;
import org.threadly.util.Clock;
import org.threadly.util.StringUtils;

/**
 * <p>Class which is designed to invoke a provided {@link ScriptFactory} to build the script.  It 
 * then runs the provided script and informs of any errors which occurred.</p>
 *  
 * @author jent - Mike Jensen
 */
public class ScriptRunner {
  /**
   * Main function, usually executed by the JVM on startup.
   * 
   * @param args Arguments for startup, including which test should run and params for that test
   * @throws InterruptedException Thrown if this thread is interrupted while waiting on test to run
   */
  public static void main(String[] args) throws InterruptedException {
    ExecutableScript script = parseArgsAndGetScript(args);
    long start = Clock.accurateForwardProgressingMillis();
    List<ListenableFuture<StepResult>> futures = script.startScript();
    List<StepResult> fails = StepResultCollectionUtils.getAllFailedResults(futures);
    long end = Clock.accurateForwardProgressingMillis();
    if (fails.isEmpty()) {
      System.out.println("All tests passed after running for " + ((end - start) / 1000) + " seconds");
      double averageRunMillis = StepResultCollectionUtils.getAverageRuntime(futures, TimeUnit.MILLISECONDS);
      System.out.println("Average time spent per test step: " + averageRunMillis + " milliseconds");
      StepResult longestStep = StepResultCollectionUtils.getLongestRuntimeStep(futures);
      System.out.println("Longest running step: " + longestStep.getDescription() + 
                           ", ran for: " + longestStep.getRunTime(TimeUnit.MILLISECONDS) + " milliseconds");
    } else {
      System.out.println(fails.size() + " TEST FAILED!!");
      Iterator<StepResult> it = fails.iterator();
      while (it.hasNext()) {
        StepResult tr = it.next();
        System.out.println("Test " + tr.getDescription() + " failed for cause:");
        tr.getError().printStackTrace();
      }
    }
  }
  
  private static ExecutableScript parseArgsAndGetScript(String[] args) {
    if (args.length == 0) {
      System.err.println("No arguments provided, need ScriptFactory class");
      usageAndExit(null);
    }
    
    String classStr = args[0];
    ScriptFactory factory = null;
    try {
      Class<?> factoryClass = Class.forName(classStr);
      try {
        factory = (ScriptFactory)factoryClass.newInstance();
      } catch (ClassCastException e) {
        System.err.println("Class does not seem to be an instance of " + 
                             ScriptFactory.class.getSimpleName() + ": " + classStr);
        usageAndExit(null);
      } catch (InstantiationException e) {
        System.err.println("Failed to call empty cosntructor on: " + classStr);
        e.printStackTrace();
        usageAndExit(null);
      } catch (IllegalAccessException e) {
        System.err.println("Failed to call empty cosntructor on: " + classStr);
        e.printStackTrace();
        usageAndExit(null);
      }
    } catch (ClassNotFoundException e) {
      System.err.println("Could not find class in classpath: " + classStr);
      usageAndExit(null);
    }

    Properties props = new Properties();
    for (int i = 1; i < args.length; i++) {
      int delimIndex = args[i].indexOf('=');
      if (delimIndex < 1) {
        System.err.println("Ignoring unknown key/value argument: " + args[i]);
      } else {
        props.put(args[i].substring(0, delimIndex), args[i].substring(delimIndex + 1));
      }
    }
    
    factory.initialize(props);
    try {
      return factory.buildScript();
    } catch (TestParameterException e) {
      Map<String, String> paramDocs = factory.getPossibleParameters();
      if (paramDocs == null || paramDocs.isEmpty()) {
        throw e;
      }

      System.err.println("Failure with script parameters..." + e.getMessage());
      if (e.getCause() != null) {
        System.err.println("Cause exception: ");
        e.getCause().printStackTrace();
      }
      System.err.println("Possible parameters:");
      System.err.println();
      Iterator<Map.Entry<String, String>> it = paramDocs.entrySet().iterator();
      StringBuilder paramDefs = new StringBuilder();
      while (it.hasNext()) {
        Map.Entry<String, String> param = it.next();
        paramDefs.append(param.getKey());
        if (param.getValue() != null && ! param.getValue().isEmpty()) {
          for (int i = param.getKey().length(); i < 10; i++) {
            paramDefs.append(" ");
          }
          paramDefs.append(" - ").append(param.getValue());
        }
        paramDefs.append(StringUtils.NEW_LINE)
                 .append(StringUtils.NEW_LINE);
      }
      System.err.print(paramDefs.toString());
      
      usageAndExit(factory.getClass().getName());
      throw e;
    }
  }
  
  private static void usageAndExit(String runningScript) {
    if (runningScript == null || runningScript.isEmpty()) {
      runningScript = "script.factory.to.call";
    }
    System.out.println("java " + ScriptRunner.class.getName() + 
                         " " + runningScript + " key1=value1 key2=value2....");
    System.exit(1);
  }
}
