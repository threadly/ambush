package org.threadly.load;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.SubmitterExecutorInterface;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.concurrent.limiter.RateLimiterExecutor;

/**
 * <p>This class handles the execution of a completely generated execution script.</p>
 * 
 * @author jent - Mike Jensen
 */
public class ExecutableScript {
  protected final int neededThreadQty;
  protected final ExecutionItem[] steps;
  protected final ScriptAssistant scriptAssistant;
  
  /**
   * Constructs a new {@link ExecutableScript}.  If the minimum threads needed don't match the 
   * execution graph provided, it may restrict load, or never complete.  
   * 
   * Execution will not proceed to the next step until the previous step has fully completed.
   * 
   * @param neededThreadQty Minimum number of threads to execute provided steps
   * @param steps Collection of steps which should be executed one after another
   */
  public ExecutableScript(int neededThreadQty, List<ExecutionItem> steps) {
    this.neededThreadQty = neededThreadQty;
    this.steps = steps.toArray(new ExecutionItem[steps.size()]);
    scriptAssistant = new ScriptAssistant();
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
   * {@link #ExecutableScript(int, List)} to produce another runnable script.
   *  
   * @return Copy of execution graph
   */
  public List<ExecutionItem> makeItemsCopy() {
    ArrayList<ExecutionItem> result = new ArrayList<ExecutionItem>(steps.length);
    for (ExecutionItem step : steps) {
      ExecutionItem copy = step.makeCopy();
      if (copy != null) {
        result.add(copy);
      }
    }
    result.trimToSize();
    return result;
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
   * {@link StepResultCollectionUtils#getFailedResult(java.util.Collection)} to see if any steps failed.  
   * This will block till all steps have completed (or a failed test step occurred).  If 
   * {@link StepResultCollectionUtils#getFailedResult(java.util.Collection)} returns null, then the test 
   * completed without error. 
   * 
   * @return A collection of futures which will represent each execution step
   */
  public List<ListenableFuture<StepResult>> startScript() {
    ArrayList<ListenableFuture<StepResult>> result = new ArrayList<ListenableFuture<StepResult>>();
    for (ExecutionItem step : steps) {
      step.prepareForRun();
      result.addAll(step.getFutures());
    }
    result.trimToSize();
    
    scriptAssistant.start(neededThreadQty + 1, result);
    
    // perform a gc before starting execution so that we can run as smooth as possible
    System.gc();
    
    // TODO - move this to a regular class?
    scriptAssistant.scheduler.execute(new Runnable() {
      @Override
      public void run() {
        for (ExecutionItem step : steps) {
          step.runChainItem(scriptAssistant);
          // this call will block till the step is done, thus preventing execution of the next step
          try {
            if (StepResultCollectionUtils.getFailedResult(step.getFutures()) != null) {
              FutureUtils.cancelIncompleteFutures(scriptAssistant.getRunningFutureSet(), true);
              return;
            }
          } catch (InterruptedException e) {
            // let thread exit
            return;
          }
        }
      }
    });
    
    return result;
  }
  
  /**
   * <p>Small class for managing access and needs from running script steps.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class ScriptAssistant implements ExecutionItem.ExecutionAssistant {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SubmitterExecutorInterface limiter = null;
    private PriorityScheduler scheduler = null;
    private List<ListenableFuture<StepResult>> futures = null;
    
    public void start(int threadPoolSize, List<ListenableFuture<StepResult>> futures) {
      if (! running.compareAndSet(false, true)) {
        throw new IllegalStateException("Already running");
      }
      scheduler = new PriorityScheduler(threadPoolSize);
      scheduler.prestartAllThreads();
      this.futures = Collections.unmodifiableList(futures);
      
      /* with the way FutureUtils works, the ListenableFuture made here wont be able to be 
       * garbage collected, even though we don't have a reference to it.  Thus ensuring we 
       * cleanup our running references.
       */
      FutureUtils.makeCompleteFuture(futures).addListener(new Runnable() {
        @Override
        public void run() {
          scheduler = null;
          limiter = null;
          running.set(false);
        }
      });
    }

    @Override
    public List<ListenableFuture<StepResult>> getRunningFutureSet() {
      return futures;
    }
    
    @Override
    public ListenableFuture<?> executeAsyncIfStillRunning(Runnable toRun, boolean realStep) {
      SubmitterExecutorInterface limiter = this.limiter;
      if (realStep && limiter != null) {
        return limiter.submit(toRun);
      } else {
        PriorityScheduler scheduler = this.scheduler;
        if (scheduler != null) {
          return scheduler.submit(toRun);
        }
      }
      return FutureUtils.immediateResultFuture(null);
    }
    
    @Override
    public void setStepPerSecondLimit(int newLimit) {
      if (newLimit < 1) {
        limiter = null;
      } else {
        PriorityScheduler scheduler = this.scheduler;
        if (scheduler != null) {
          limiter = new RateLimiterExecutor(scheduler, newLimit);
        }
      }
    }
  }
  
  /**
   * <p>Interface for chain item, all items provided for execution must implement this interface.  
   * This will require test steps to be wrapped in a class which provides this functionality.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected interface ExecutionItem {
    /**
     * Called to allow the {@link ExecutionItem} do any cleanup, or other operations needed to 
     * ensure a smooth invokation of {@link #runChainItem(ExecutionAssistant)}.
     */
    public void prepareForRun();
    
    /**
     * Run the current items execution.  This may execute async on the provided 
     * {@link ExecutionAssistant}, but returned futures from {@link #getFutures()} should not fully 
     * complete until the chain item completes.
     * 
     * @param assistant {@link ExecutionAssistant} which is performing the execution
     */
    public void runChainItem(ExecutionAssistant assistant);

    /**
     * Returns the collection of futures which represent this test.  There should be one future 
     * per test step.  These futures should complete as their respective test steps complete.
     * 
     * @return Collection of futures that provide results from their test steps
     */
    public List<? extends SettableListenableFuture<StepResult>> getFutures();
    
    /**
     * Produces a copy of the item so that it can be run in another execution chain.
     * 
     * @return A copy of the test item
     */
    public ExecutionItem makeCopy();
    
    /**
     * Get information about if this {@link ExecutionItem} has child items it runs or not.  This 
     * can be used to understand the graph structure for execution.
     * 
     * @return A implementation of {@link ChildItems} to understand child execution
     */
    public ChildItems getChildItems();
    
    /**
     * <p>Class which represents child items which may be executed by this instance of an 
     * {@link ExecutionItem}.</p>
     * 
     * @author jent - Mike Jensen
     */
    public interface ChildItems {
      /**
       * Check to know if this item runs child items sequentially, waiting till one finishes 
       * before starting the next one.
       * 
       * @return {@code true} if child items are run sequentially
       */
      public boolean itemsRunSequential();

      /**
       * Check to see if this execution item runs other items.
       * 
       * @return {@code true} if this {@link ExecutionItem} runs multiple {@link ExecutionItem}'s
       */
      public boolean hasChildren();

      /**
       * Get an iterator which will iterate over the executable items.  No modifications should be 
       * done through this iterator.
       * 
       * @return Iterator of items that will be executed when this item is executed
       */
      public Iterator<ExecutionItem> iterator();
    }
    
    /**
     * <p>Class passed to the test item at the start of execution.  This can provide information 
     * and facilities it can use to perform it's execution.</p>
     * 
     * @author jent - Mike Jensen
     */
    public interface ExecutionAssistant {
      /**
       * This farms off tasks on to another thread for execution.  This may not execute if the script 
       * has already stopped (likely from an error or failed step).  In those cases the task's future 
       * was already canceled so execution should not be needed.
       * 
       * @param toRun Task to be executed
       * @param realStep {@code true} if executing step from graph, not a helper task
       * @return Future that will complete when runnable has finished running
       */
      public ListenableFuture<?> executeAsyncIfStillRunning(Runnable toRun, boolean realStep);
      
      /**
       * Changes what the limit is for how many steps per second are allowed to execute.  Delays 
       * in step execution are NOT factored in step run time.  Provide {@code 0} to set no limit 
       * and allow step execution to run as fast as possible.
       * 
       * @param newLimit Limit of steps run per second
       */
      public void setStepPerSecondLimit(int newLimit);

      /**
       * Returns the list of futures for the current test script run.  If not currently running this 
       * will be null.
       * 
       * @return List of futures that will complete for the current execution
       */
      public List<ListenableFuture<StepResult>> getRunningFutureSet();
    }
  }
}