package org.threadly.load;

import java.util.ArrayList;
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
  /**
   * Main function, usually executed by the JVM on startup.
   * 
   * @param args Arguments for startup, including which test should run and params for that test
   * @throws InterruptedException Thrown if this thread is interrupted while waiting on test to run
   */
  public static void main(String[] args) throws InterruptedException {
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
   * Outputs/logs this message/output from the script execution/results.  By default this reports 
   * to {@link System#out}.println(String).  This can be overridden to use other loggers.
   * 
   * @param msg String output from runner
   */
  protected void out(String msg) {
    System.out.println(msg);
  }
  
  /**
   * Starts the execution of the script.  This executes and reports the output to 
   * {@link #out(String)}.  That output includes tracked details during execution like speed and 
   * success or failures.
   * 
   * @throws InterruptedException Thrown if thread is interrupted during execution
   * @return Number of failed steps
   */
  protected int runScript() throws InterruptedException {
    long start = Clock.accurateForwardProgressingMillis();
    List<ListenableFuture<StepResult>> futures = script.startScript();
    List<StepResult> fails = StepResultCollectionUtils.getAllFailedResults(futures);
    long end = Clock.accurateForwardProgressingMillis();
    if (fails.isEmpty()) {
      out("All steps passed!");
    } else {
      Map<String, List<StepResult>> failureCountMap = new HashMap<String, List<StepResult>>();
      out(fails.size() + " STEPS FAILED!!" + StringUtils.NEW_LINE);
      {
        Iterator<StepResult> it = fails.iterator();
        while (it.hasNext()) {
          StepResult tr = it.next();
          String errorMsg = ExceptionUtils.stackToString(tr.getError());
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
          out(e.getKey() + StringUtils.NEW_LINE);
        }
      }
    }
    int totalExecuted = 0;
    for (ListenableFuture<StepResult> f : futures) {
      if (! f.isCancelled()) {
        totalExecuted++;
      }
    }
    out("Totals steps executed: " + totalExecuted + " / " + futures.size());
    out("Test execution time: " + ((end - start) / 1000) + " seconds");
    double averageRunMillis = StepResultCollectionUtils.getAverageRuntime(futures, TimeUnit.MILLISECONDS);
    out("Average time spent per step: " + averageRunMillis + " milliseconds");
    StepResult longestStep = StepResultCollectionUtils.getLongestRuntimeStep(futures);
    out("Longest running step: " + longestStep.getDescription() + 
          ", ran for: " + longestStep.getRunTime(TimeUnit.MILLISECONDS) + " milliseconds");
    
    return fails.size();
  }
}
