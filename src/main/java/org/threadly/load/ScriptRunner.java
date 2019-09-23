package org.threadly.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.util.Clock;
import org.threadly.util.ExceptionHandler;
import org.threadly.util.ExceptionUtils;
import org.threadly.util.StringUtils;

/**
 * <p>Class which is designed to invoke a provided {@link ScriptFactory} to build the script.  It 
 * then runs the provided script and informs of any errors which occurred.</p>
 *  
 * @author jent - Mike Jensen
 */
public class ScriptRunner extends AbstractScriptFactoryInitializer {
  private static final double[] RETURNED_PERCENTILES = new double[]{50, 75, 80, 85, 90, 95, 
                                                                    98, 99, 99.5, 99.9, 100};
  private static final boolean TRIM_AMBUSH_STACK_AWAY = true;
  /**
   * Main function, usually executed by the JVM on startup.
   * 
   * @param args Arguments for startup, including which test should run and params for that test
   * @throws Exception Thrown if error or interruption while waiting for script
   */
  public static void main(String[] args) throws Exception {
    setupExceptionHandler();
    ScriptRunner runner = null;
    try {
      runner = new ScriptRunner(args);
    } catch (Throwable t) {
      System.err.println("Unexpected failure when building script: ");
      printFailureAndExit(t);
    }
    System.exit(runner.runScript());
  }
  
  /**
   * Sets up a default {@link ExceptionHandlerInterface} so that if any uncaught exceptions occur, 
   * the script will display the exception and exit.  There should never be any uncaught 
   * exceptions, this likely would indicate a bug in Ambush. 
   */
  protected static void setupExceptionHandler() {
    ExceptionUtils.setDefaultExceptionHandler(new ExceptionHandler() {
      @Override
      public void handleException(Throwable thrown) {
        synchronized (this) { // synchronized to prevent terminal corruption from multiple failures
          System.err.println("Unexpected uncaught exception: ");
          printFailureAndExit(thrown);
        }
      }
    });
  }
  
  /**
   * Prints the throwable to standard error then exits with a non-zero exit code which matches to 
   * the throwable's message.
   * 
   * @param t The throwable which caused the failure
   */
  protected static void printFailureAndExit(Throwable t) {
    t.printStackTrace();
    int hashCode = StringUtils.nullToEmpty(t.getMessage()).hashCode();
    if (hashCode == 0) {
      hashCode = -1;
    }
    if (hashCode > 0) {
      hashCode *= -1;
    }
    System.exit(hashCode);
  }
  
  protected ScriptRunner(String[] args) {
    super(args);
  }
  
  /**
   * Prints the usage for the expected arguments to be taken in from the main class, and then 
   * exits with a non-zero status code.
   * 
   * @param buildingScript Possible script that is loaded, or {@code null} if unknown
   */
  @Override
  protected void handleInitializationFailure(String buildingScript) {
    if (buildingScript == null || buildingScript.isEmpty()) {
      buildingScript = "script.factory.to.call";
    }
    System.err.println("java " + this.getClass().getName() + 
                         " " + buildingScript + " key1=value1 key2=value2....");
    System.exit(-1);
  }
  
  /**
   * Outputs/logs this message/output from the script execution/results.  By default this reports 
   * to {@link System#out}.println(String).  This can be overridden to use other loggers.
   * 
   * @param msg String output from runner
   */
  protected void out(String msg) {
    System.out.println(msg);
  }
  
  /**
   * Invoked once the script has finished, either in success or failure.  This can overridden if 
   * the default logging behavior wants to be changed.
   * 
   * @param rawFutures Futures for test steps, all now completed with either a result, or canceled
   * @param fails Steps which failed, keep in mind that canceled steps wont be represented here
   * @param runDurationMillis The duration in milliseconds that it took to run the entire script
   * @throws Exception Thrown if unexpected error
   */
  protected void handleRunFinish(List<ListenableFuture<StepResult>> rawFutures, 
                                 List<StepResult> fails, long runDurationMillis) throws Exception {
    if (fails.isEmpty()) {
      out("All steps passed!");
    } else {
      Map<String, List<StepResult>> failureCountMap = new HashMap<String, List<StepResult>>();
      out(fails.size() + " STEPS FAILED!!" + System.lineSeparator());
      {
        Iterator<StepResult> it = fails.iterator();
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
          sb.setLength(0);
          StepResult tr = it.next();
          Throwable t = tr.getError();
          while (t != null) {
            if (sb.length() > 0) {
              // will have line separator from last loop
              sb.append("Caused by: ");
            }
            
            sb.append(t.toString()).append(System.lineSeparator());
            StackTraceElement[] origStack = t.getStackTrace();
            StackTraceElement[] trimmedStack;
            if (TRIM_AMBUSH_STACK_AWAY) {
              String packageStr = ScriptRunner.class.getPackage().getName();
              int i = 1;
              for (; i < origStack.length; i++) {
                if (origStack[i].getClassName().startsWith(packageStr)) {
                  break;
                }
              }
              trimmedStack = Arrays.copyOf(origStack, i);
            } else {
              trimmedStack = origStack;
            }
            ExceptionUtils.writeStackTo(trimmedStack, sb);
            
            t = t.getCause();
          }
          String errorMsg = sb.toString();
          List<StepResult> currentSteps = failureCountMap.get(errorMsg);
          if (currentSteps == null) {
            currentSteps = new ArrayList<StepResult>(1);
            failureCountMap.put(errorMsg, currentSteps);
          }
          currentSteps.add(tr);
        }
      }
      {
        Iterator<Map.Entry<String, List<StepResult>>> it = failureCountMap.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry<String, List<StepResult>> e = it.next();
          if (e.getValue().size() > 1) {
            List<String> descriptions = new ArrayList<String>(e.getValue().size());
            for (StepResult sr : e.getValue()) {
              if (! descriptions.contains(sr.getDescription())) {
                descriptions.add(sr.getDescription());
              }
            }
            out("Error occured " + e.getValue().size() + " times for the following steps:");
            for (String s : descriptions) {
              out('\t' + s);
            }
            out("All share failure cause:");
          } else {
            out("Step " + e.getValue().get(0).getDescription() + " failed due to:");
          }
          out(e.getKey() + System.lineSeparator());
        }
      }
    }
    int totalExecuted = 0;
    for (ListenableFuture<StepResult> f : rawFutures) {
      if (! f.isCancelled()) {
        totalExecuted++;
      }
    }
    out("Totals steps executed: " + totalExecuted + " / " + rawFutures.size());
    out("Test execution time: " + (runDurationMillis / 1000) + " seconds");
    double averageRunMillis = StepResultCollectionUtils.getRunTimeAverage(rawFutures, TimeUnit.MILLISECONDS);
    out("Average time spent per step: " + averageRunMillis + " milliseconds");
    Map<Double, StepResult> percentileResults = StepResultCollectionUtils.getRunTimePercentiles(rawFutures, 
                                                                                                RETURNED_PERCENTILES);
    for (Map.Entry<Double, StepResult> e : percentileResults.entrySet()) {
      if (e.getKey() < 100) {
        out("Percentile " + e.getKey() + ": " + e.getValue().getRunTime(TimeUnit.MILLISECONDS) + " milliseconds");
      }
    }
    
    StepResult longestStep = percentileResults.get(RETURNED_PERCENTILES[RETURNED_PERCENTILES.length - 1]);
    out("Longest running step: " + longestStep.getDescription() + 
          ", ran for: " + longestStep.getRunTime(TimeUnit.MILLISECONDS) + " milliseconds");
  }
  
  /**
   * Starts the execution of the script.  This executes and reports the output to 
   * {@link #out(String)}.  That output includes tracked details during execution like speed and 
   * success or failures.
   * 
   * @return Number of failed steps
   * @throws Exception Thrown if error or interruption while waiting for script
   */
  protected int runScript() throws Exception {
    long start = Clock.accurateForwardProgressingMillis();
    List<ListenableFuture<StepResult>> futures = script.startScript();
    List<StepResult> fails = StepResultCollectionUtils.getAllFailedResults(futures);
    long end = Clock.accurateForwardProgressingMillis();
    
    handleRunFinish(futures, fails, end - start);
    
    return fails.size();
  }
}
