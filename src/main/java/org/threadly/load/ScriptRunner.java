package org.threadly.load;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.util.Clock;

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
    ExecutionScript script = parseArgsAndGetScript(args);
    long start = Clock.accurateForwardProgressingMillis();
    List<ListenableFuture<TestResult>> futures = script.startScript();
    List<TestResult> fails = TestResultCollectionUtils.getAllFailedResults(futures);
    long end = Clock.accurateForwardProgressingMillis();
    if (fails.isEmpty()) {
      System.out.println("All tests passed after running for " + ((end - start) / 1000) + " seconds");
      double averageRunMillis = TestResultCollectionUtils.getAverageRuntime(futures, TimeUnit.MILLISECONDS);
      System.out.println("Average time spent per test step: " + averageRunMillis + " milliseconds");
      TestResult longestStep = TestResultCollectionUtils.getLongestRuntimeStep(futures);
      System.out.println("Longest running step: " + longestStep.getDescription() + 
                           ", ran for: " + longestStep.getRunTime(TimeUnit.MILLISECONDS) + " milliseconds");
    } else {
      System.out.println(fails.size() + " TEST FAILED!!");
      Iterator<TestResult> it = fails.iterator();
      while (it.hasNext()) {
        TestResult tr = it.next();
        System.out.println("Test " + tr.getDescription() + " failed for cause:");
        tr.getError().printStackTrace();
      }
    }
  }
  
  private static ExecutionScript parseArgsAndGetScript(String[] args) {
    if (args.length == 0) {
      System.err.println("No arguments provided, need ScriptFactory class");
      usageAndExit();
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
        usageAndExit();
      } catch (InstantiationException e) {
        System.err.println("Failed to call empty cosntructor on: " + classStr);
        e.printStackTrace();
        usageAndExit();
      } catch (IllegalAccessException e) {
        System.err.println("Failed to call empty cosntructor on: " + classStr);
        e.printStackTrace();
        usageAndExit();
      }
    } catch (ClassNotFoundException e) {
      System.err.println("Could not find class in classpath: " + classStr);
      usageAndExit();
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
    
    return factory.buildScript();
  }
  
  private static void usageAndExit() {
    System.out.println("java " + ScriptRunner.class.getName() + 
                         " script.factory.to.call key1=value1 key2=value2....");
    System.exit(1);
  }
}
