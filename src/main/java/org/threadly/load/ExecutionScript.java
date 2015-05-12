package org.threadly.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;

/**
 * <p>This class handles the execution of a completely generated execution script.</p>
 * 
 * @author jent - Mike Jensen
 */
public class ExecutionScript {
  private final int maximumThreadsNeeded;
  private final Collection<TestChainItem> steps;
  private final AtomicReference<List<ListenableFuture<TestResult>>> runningFutureSet;
  
  /**
   * Constructs a new {@link ExecutionScript}.  If the minimum threads needed don't match the 
   * execution graph provided, it may restrict load, or never complete.  
   * 
   * Execution will not proceed to the next step until the previous step has fully completed.
   * 
   * @param maximumThreadsNeeded Minimum number of threads to execute provided steps
   * @param steps Collection of steps which should be executed one after another
   */
  public ExecutionScript(int maximumThreadsNeeded, Collection<TestChainItem> steps) {
    this.maximumThreadsNeeded = maximumThreadsNeeded;
    this.steps = steps;
    runningFutureSet = new AtomicReference<List<ListenableFuture<TestResult>>>(null);
  }
  
  /**
   * Returns the list of futures for the current test script run.  If not currently running this 
   * will be null.
   * 
   * @return List of futures that will complete for the current execution
   */
  public List<ListenableFuture<TestResult>> getRunningFutureSet() {
    return runningFutureSet.get();
  }
  
  /**
   * Starts the execution of the script.  It will traverse through the execution graph an execute 
   * things as previously defined by using the builder.  
   * 
   * This returns a collection of futures.  If an execution step was executed, the future will 
   * return a {@link TestResult}.  That {@link TestResult} will indicate either a successful or 
   * failure in execution.  If a failure does occur then future test steps will NOT be executed.  
   * If a step was never executed due to a failure, those futures will be resolved in an error 
   * (thus calls to {@link ListenableFuture#get()} will throw a 
   * {@link java.util.concurrent.ExecutionException}).  You can use 
   * {@link TestResultCollectionUtils#getFailedResult(Collection)} to see if any steps failed.  
   * This will block till all steps have completed (or a failed test step occurred).  If 
   * {@link TestResultCollectionUtils#getFailedResult(Collection)} returns null, then the test 
   * completed without error. 
   * 
   * @return A collection of futures which will represent each execution step
   */
  public List<ListenableFuture<TestResult>> startScript() {
    final List<ListenableFuture<TestResult>> result = new ArrayList<ListenableFuture<TestResult>>();
    if (! runningFutureSet.compareAndSet(null, result)) {
      throw new IllegalStateException("Script already running in parallel");
    }
    final PriorityScheduler scheduler = new PriorityScheduler(maximumThreadsNeeded + 1);
    scheduler.prestartAllThreads();
    
    Iterator<TestChainItem> it = steps.iterator();
    while (it.hasNext()) {
      result.addAll(it.next().getFutures());
    }
    
    // TODO - move this to a regular class?
    scheduler.execute(new Runnable() {
      @Override
      public void run() {
        Iterator<TestChainItem> it = steps.iterator();
        while (it.hasNext()) {
          TestChainItem stepRunner = it.next();
          stepRunner.runChainItem(ExecutionScript.this, scheduler);
          // this call will block till the step is done, thus preventing execution of the next step
          try {
            if (TestResultCollectionUtils.getFailedResult(stepRunner.getFutures()) != null) {
              FutureUtils.cancelIncompleteFutures(result, true);
              return;
            }
          } catch (InterruptedException e) {
            // let thread exit
            return;
          }
        }
      }
    });
    
    /* with the way FutureUtils works, the ListenableFuture made here wont be able to be 
     * garbage collected, even though we don't have a reference to it.  Thus ensuring we 
     * shutdown the scheduler we created here.
     */
    FutureUtils.makeCompleteFuture(result).addListener(new Runnable() {
      @Override
      public void run() {
        runningFutureSet.set(null);
        scheduler.shutdown();
      }
    });
    
    return result;
  }
  
  /**
   * <p>Interface for chain item, all items provided for execution must implement this interface.  
   * This will require test steps to be wrapped in a class which provides this functionality.</p>
   * 
   * @author jent - Mike Jensen
   */
  public interface TestChainItem {
    /**
     * Run the current items execution.  This may execute out on the provided {@link Executor}, 
     * but returned futures from {@link #getFutures()} should not fully complete until the chain 
     * item completes.
     * 
     * @param script {@link ExecutionScript} which is performing the execution
     * @param executor Executor that parallel work can be farmed off to
     */
    public abstract void runChainItem(ExecutionScript script, Executor executor);

    /**
     * Returns the collection of futures which represent this test.  There should be one future 
     * per test step.  These futures should complete as their respective test steps complete.
     * 
     * @return Collection of futures that provide results from their test steps
     */
    public abstract Collection<? extends SettableListenableFuture<TestResult>> getFutures();
  }
}