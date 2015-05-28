package org.threadly.load;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.util.Clock;
import org.threadly.util.ExceptionHandlerInterface;
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
    runner.runScript();
  }
  
  /**
   * Sets up a default {@link ExceptionHandlerInterface} so that if any uncaught exceptions occur, 
   * the script will display the exception and exit.  There should never be any uncaught 
   * exceptions, this likely would indicate a bug in Ambush. 
   */
  protected static void setupExceptionHandler() {
    ExceptionUtils.setDefaultExceptionHandler(new ExceptionHandlerInterface() {
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
    int hashCode = StringUtils.makeNonNull(t.getMessage()).hashCode();
    if (hashCode == 0) {
      hashCode = -1;
    }
    System.exit(hashCode);
  }
  
  protected ScriptRunner(String[] args) {
    super(args);
  }
  
  protected void runScript() throws InterruptedException {
    long start = Clock.accurateForwardProgressingMillis();
    List<ListenableFuture<StepResult>> futures = script.startScript();
    List<StepResult> fails = StepResultCollectionUtils.getAllFailedResults(futures);
    long end = Clock.accurateForwardProgressingMillis();
    if (fails.isEmpty()) {
      System.out.println("All steps passed!");
    } else {
      Map<String, List<StepResult>> failureCountMap = new HashMap<String, List<StepResult>>();
      System.out.println(fails.size() + " STEPS FAILED!!");
      System.out.println();
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
            System.out.println("Error occured " + e.getValue().size() + " times for the following steps:");
            for (String s : descriptions) {
              System.out.println('\t' + s);
            }
            System.out.println("All share failure cause:");
          } else {
            System.out.println("Step " + e.getValue().get(0).getDescription() + " failed due to:");
          }
          System.out.println(e.getKey());
          System.out.println();
        }
      }
    }
    System.out.println("Totals steps executed: " + futures.size());
    System.out.println("Test execution time: " + ((end - start) / 1000) + " seconds");
    double averageRunMillis = StepResultCollectionUtils.getAverageRuntime(futures, TimeUnit.MILLISECONDS);
    System.out.println("Average time spent per step: " + averageRunMillis + " milliseconds");
    StepResult longestStep = StepResultCollectionUtils.getLongestRuntimeStep(futures);
    System.out.println("Longest running step: " + longestStep.getDescription() + 
                         ", ran for: " + longestStep.getRunTime(TimeUnit.MILLISECONDS) + " milliseconds");
  }
}
