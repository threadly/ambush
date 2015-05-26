package org.threadly.load;

import java.util.Iterator;
import java.util.List;
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
  
  private void runScript() throws InterruptedException {
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
}
