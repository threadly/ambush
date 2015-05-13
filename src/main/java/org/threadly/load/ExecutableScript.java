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
public class ExecutableScript {
  protected final int neededThreadQty;
  protected final Collection<ExecutionItem> steps;
  protected final AtomicReference<List<ListenableFuture<StepResult>>> runningFutureSet;
  
  /**
   * Constructs a new {@link ExecutableScript}.  If the minimum threads needed don't match the 
   * execution graph provided, it may restrict load, or never complete.  
   * 
   * Execution will not proceed to the next step until the previous step has fully completed.
   * 
   * @param neededThreadQty Minimum number of threads to execute provided steps
   * @param steps Collection of steps which should be executed one after another
   */
  public ExecutableScript(int neededThreadQty, Collection<ExecutionItem> steps) {
    this.neededThreadQty = neededThreadQty;
    this.steps = steps;
    runningFutureSet = new AtomicReference<List<ListenableFuture<StepResult>>>(null);
  }
  
  /**
   * Returns how many threads will be started when the script is executed.
   * 
   * @return Number of threads needed to run script as provided
   */
  public int getThreadQtyNeeded() {
    return neededThreadQty;
  }
  
  /**
   * Creates a copy of the {@link ExecutionItem} chain.  This could be provided to 
   * {@link #ExecutableScript(int, Collection)} to produce another runnable script.
   *  
   * @return Copy of execution graph
   */
  public Collection<ExecutionItem> makeItemsCopy() {
    List<ExecutionItem> result = new ArrayList<ExecutionItem>(steps.size());
    Iterator<ExecutionItem> it = steps.iterator();
    while (it.hasNext()) {
      result.add(it.next().makeCopy());
    }
    return result;
  }
  
  /**
   * Returns the list of futures for the current test script run.  If not currently running this 
   * will be null.
   * 
   * @return List of futures that will complete for the current execution
   */
  public List<ListenableFuture<StepResult>> getRunningFutureSet() {
    return runningFutureSet.get();
  }
  
  /**
   * Starts the execution of the script.  It will traverse through the execution graph an execute 
   * things as previously defined by using the builder.  
   * 
   * This returns a collection of futures.  If an execution step was executed, the future will 
   * return a {@link StepResult}.  That {@link StepResult} will indicate either a successful or 
   * failure in execution.  If a failure does occur then future test steps will NOT be executed.  
   * If a step was never executed due to a failure, those futures will be resolved in an error 
   * (thus calls to {@link ListenableFuture#get()} will throw a 
   * {@link java.util.concurrent.ExecutionException}).  You can use 
   * {@link StepResultCollectionUtils#getFailedResult(Collection)} to see if any steps failed.  
   * This will block till all steps have completed (or a failed test step occurred).  If 
   * {@link StepResultCollectionUtils#getFailedResult(Collection)} returns null, then the test 
   * completed without error. 
   * 
   * @return A collection of futures which will represent each execution step
   */
  public List<ListenableFuture<StepResult>> startScript() {
    final List<ListenableFuture<StepResult>> result = new ArrayList<ListenableFuture<StepResult>>();
    if (! runningFutureSet.compareAndSet(null, result)) {
      throw new IllegalStateException("Script already running in parallel");
    }
    final PriorityScheduler scheduler = new PriorityScheduler(neededThreadQty + 1);
    scheduler.prestartAllThreads();
    
    Iterator<ExecutionItem> it = steps.iterator();
    while (it.hasNext()) {
      result.addAll(it.next().getFutures());
    }
    
    // TODO - move this to a regular class?
    scheduler.execute(new Runnable() {
      @Override
      public void run() {
        Iterator<ExecutionItem> it = steps.iterator();
        while (it.hasNext()) {
          ExecutionItem stepRunner = it.next();
          stepRunner.runChainItem(ExecutableScript.this, scheduler);
          // this call will block till the step is done, thus preventing execution of the next step
          try {
            if (StepResultCollectionUtils.getFailedResult(stepRunner.getFutures()) != null) {
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
  public interface ExecutionItem {
    /**
     * Run the current items execution.  This may execute out on the provided {@link Executor}, 
     * but returned futures from {@link #getFutures()} should not fully complete until the chain 
     * item completes.
     * 
     * @param script {@link ExecutableScript} which is performing the execution
     * @param executor Executor that parallel work can be farmed off to
     */
    public void runChainItem(ExecutableScript script, Executor executor);

    /**
     * Returns the collection of futures which represent this test.  There should be one future 
     * per test step.  These futures should complete as their respective test steps complete.
     * 
     * @return Collection of futures that provide results from their test steps
     */
    public Collection<? extends SettableListenableFuture<StepResult>> getFutures();
    
    /**
     * Produces a copy of the item so that it can be run in another execution chain.
     * 
     * @return A copy of the test item
     */
    public ExecutionItem makeCopy();
  }
}