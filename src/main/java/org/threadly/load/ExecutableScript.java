package org.threadly.load;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.SubmitterExecutor;
import org.threadly.concurrent.future.ExecuteOnGetFutureTask;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ImmediateResultListenableFuture;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.concurrent.wrapper.limiter.RateLimiterExecutor;
import org.threadly.util.ArgumentVerifier;
import org.threadly.util.ExceptionUtils;

/**
 * <p>This class handles the execution of a completely generated execution script.</p>
 * 
 * @author jent - Mike Jensen
 */
public class ExecutableScript {
  private static final int MAXIMUM_PRESTART_THREAD_COUNT = 1000;
  
  protected final int neededThreadQty;
  protected final ExecutionItem startExecutionItem;
  protected final ScriptAssistant scriptAssistant;
  
  /**
   * Constructs a new {@link ExecutableScript}.  If the minimum threads needed don't match the 
   * execution graph provided, it may restrict load, or never complete.  
   * 
   * Execution will not proceed to the next step until the previous step has fully completed.
   * 
   * @param neededThreadQty Minimum number of threads to execute provided steps
   * @param startExecutionItem Execution item which represents the script
   */
  public ExecutableScript(int neededThreadQty, ExecutionItem startExecutionItem) {
    if (! startExecutionItem.getChildItems().hasChildren()) {
      throw new IllegalArgumentException("Can not construct script with no steps");
    }
    ArgumentVerifier.assertGreaterThanZero(neededThreadQty, "neededThreadQty");
    
    this.neededThreadQty = neededThreadQty;
    this.startExecutionItem = startExecutionItem;
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
    // copy result list to handle generics madness
    final ArrayList<ListenableFuture<StepResult>> result = new ArrayList<ListenableFuture<StepResult>>(0);
    startExecutionItem.prepareForRun();
    result.addAll(startExecutionItem.getFutures());
    result.trimToSize();

    CharsDeduplicator.clearCache();
    
    scriptAssistant.start(neededThreadQty + 1, result);
    
    // perform a gc before starting execution so that we can run as smooth as possible
    System.gc();
    
    // TODO - move this to a regular class?
    scriptAssistant.scheduler.get().execute(new Runnable() {
      @Override
      public void run() {
        startExecutionItem.itemReadyForExecution(scriptAssistant);
        // this call will block till the step is done, thus preventing execution of the next step
        try {
          if (StepResultCollectionUtils.getFailedResult(result) != null) {
            FutureUtils.cancelIncompleteFutures(scriptAssistant.getGlobalRunningFutureSet(), true);
            return;
          }
        } catch (InterruptedException e) {
          // let thread exit
          return;
        } finally {
          startExecutionItem.runComplete();
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
    private final AtomicBoolean running;
    private final AtomicReference<PriorityScheduler> scheduler;
    private final AtomicReference<List<ListenableFuture<StepResult>>> futures;
    private final AtomicBoolean markedFailure;
    private final ArrayList<Runnable> failureListeners;
    private volatile ListenableFuture<?> completionFuture;
    private volatile SubmitterExecutor limiter;
    
    private ScriptAssistant(ScriptAssistant scriptAssistant) {
      running = scriptAssistant.running;
      scheduler = scriptAssistant.scheduler;
      futures = scriptAssistant.futures;
      markedFailure = scriptAssistant.markedFailure;
      failureListeners = scriptAssistant.failureListeners;
      limiter = scriptAssistant.limiter;
      completionFuture = scriptAssistant.completionFuture;
      
      /* with the way FutureUtils works, the ListenableFuture made here wont be able to be 
       * garbage collected, even though we don't have a reference to it.  Thus ensuring we 
       * cleanup our running references.
       */
      completionFuture.addListener(new Runnable() {
        @Override
        public void run() {
          limiter = null;
        }
      });
    }
    
    public ScriptAssistant() {
      running = new AtomicBoolean(false);
      scheduler = new AtomicReference<PriorityScheduler>(null);
      futures = new AtomicReference<List<ListenableFuture<StepResult>>>(null);
      markedFailure = new AtomicBoolean(false);
      failureListeners = new ArrayList<Runnable>(1);
      limiter = null;
    }

    @Override
    public void registerFailureNotification(Runnable listener) {
      synchronized (failureListeners) {
        if (markedFailure.get()) {
          ExceptionUtils.runRunnable(listener);
        } else {
          failureListeners.add(listener);
        }
      }
    }

    @Override
    public void markGlobalFailure() {
      if (! markedFailure.get() && markedFailure.compareAndSet(false, true)) {
        synchronized (failureListeners) {
          for (Runnable r :  failureListeners) {
            ExceptionUtils.runRunnable(r);
          }
          failureListeners.clear();
        }
        List<ListenableFuture<StepResult>> futures = this.futures.get();
        if (futures != null) {
          // try to short cut any steps we can
          // Sadly this is a duplicate from other cancels, but since we are not garunteed to be 
          // able to cancel here, we still need those points
          FutureUtils.cancelIncompleteFutures(futures, true);
        }
      }
    }

    @Override
    public boolean getMarkedGlobalFailure() {
      return markedFailure.get();
    }

    public void start(int threadPoolSize, List<ListenableFuture<StepResult>> futures) {
      if (! running.compareAndSet(false, true)) {
        throw new IllegalStateException("Already running");
      }
      PriorityScheduler ps;
      if (threadPoolSize > MAXIMUM_PRESTART_THREAD_COUNT) {
        ps = new PriorityScheduler(1000);
        // just prestart the maximum, then allow the pool to grow beyond that
        // if rate limiting is used, our actual needed thread count may be lower than this number
        ps.prestartAllThreads();
        ps.setPoolSize(threadPoolSize);
      } else {
        ps = new PriorityScheduler(threadPoolSize);
        ps.prestartAllThreads();
      }
      
      scheduler.set(ps);
      this.futures.set(Collections.unmodifiableList(futures));
      
      /* with the way FutureUtils works, the ListenableFuture made here wont be able to be 
       * garbage collected, even though we don't have a reference to it.  Thus ensuring we 
       * cleanup our running references.
       */
      completionFuture = FutureUtils.makeCompleteFuture(futures);
      completionFuture.addListener(new Runnable() {
        @Override
        public void run() {
          scheduler.set(null);
          limiter = null;
          running.set(false);
        }
      });
      
      synchronized (failureListeners) {
        failureListeners.trimToSize();
      }
    }

    @Override
    public List<ListenableFuture<StepResult>> getGlobalRunningFutureSet() {
      return futures.get();
    }
    
    @Override
    public ListenableFuture<?> executeIfStillRunning(ExecutionItem item, boolean forceAsync) {
      // the existence of the scheduler (and possibly limiter) indicate still running
      SubmitterExecutor limiter = this.limiter;
      if (limiter != null && ! item.isChainExecutor()) {
        return limiter.submit(wrapInRunnable(item));
      } else {
        PriorityScheduler scheduler = this.scheduler.get();
        if (scheduler != null) {
          if (forceAsync) {
            ExecuteOnGetFutureTask<?> result = new ExecuteOnGetFutureTask<Void>(wrapInRunnable(item));
            scheduler.execute(result);
            return result;
          } else {
            item.itemReadyForExecution(this);
          }
        }
      }
      return ImmediateResultListenableFuture.NULL_RESULT;
    }
    
    private Runnable wrapInRunnable(final ExecutionItem item) {
      return new Runnable() {
        @Override
        public void run() {
          item.itemReadyForExecution(ScriptAssistant.this);
        }
      };
    }
    
    @Override
    public void setStepPerSecondLimit(double newLimit) {
      if (newLimit <= 0) {
        limiter = null;
      } else {
        PriorityScheduler scheduler = this.scheduler.get();
        if (scheduler != null) {
          limiter = new RateLimiterExecutor(scheduler, newLimit);
        }
      }
    }
    
    @Override
    public ScriptAssistant makeCopy() {
      return new ScriptAssistant(this);
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
     * Set a handler for when {@link #itemReadyForExecution(ExecutionAssistant)} is invoked.  If a handler 
     * is set on a step, it is expected to defer to that handler, and NOT perform any further 
     * action.
     * 
     * @param handler Handler to defer to, or {@code null} to unset handler
     */
    public void setStartHandler(StepStartHandler handler);
    
    /**
     * Called to allow the {@link ExecutionItem} do any cleanup, or other operations needed to 
     * ensure a smooth invocation of {@link #itemReadyForExecution(ExecutionAssistant)}.
     */
    public void prepareForRun();
    
    public void runComplete();

    /**
     * Indicates the item is ready for execution.  If a {@link StepStartHandler} is set, this 
     * invocation should defer to that.  If it is not set, then the items execution should start.  
     * This may execute async on the provided {@link ExecutionAssistant}, but returned futures 
     * from {@link #getFutures()} should not complete until the chain item completes.
     * 
     * @param assistant {@link ExecutionAssistant} which is performing the execution
     */
    public void itemReadyForExecution(ExecutionAssistant assistant);
    
    /**
     * Check if this execution item directly applies changes to the provided 
     * {@link ExecutionAssistant}.
     * 
     * @return {@code true} if the step manipulates the assistant
     */
    public boolean manipulatesExecutionAssistant();
    
    /**
     * Check to see if this {@link ExecutionItem} is responsible for executing other items.  A 
     * {@code true} here would indicate this is not a real step, but rather a point in the chain 
     * needed for the graph structure.
     * 
     * @return {@code true} if synthetic step for chain execution
     */
    public boolean isChainExecutor();

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
    // TODO - I don't like how we are constructing objects for this, can we avoid these calls?
    public ChildItems getChildItems();
    
    /**
     * <p>Class which represents child items which may be executed by this instance of an 
     * {@link ExecutionItem}.</p>
     * 
     * @author jent - Mike Jensen
     */
    public interface ChildItems extends Iterable<ExecutionItem> {
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
      @Override
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
       * @param item ExecutionItem to be executed
       * @param forceAsync {@code false} to potentially allow execution inside calling thread
       * @return Future that will complete when runnable has finished running
       */
      public ListenableFuture<?> executeIfStillRunning(ExecutionItem item, boolean forceAsync);
      
      /**
       * Changes what the limit is for how many steps per second are allowed to execute.  Delays 
       * in step execution are NOT factored in step run time.  Provide {@code 0} to set no limit 
       * and allow step execution to run as fast as possible.
       * 
       * @param newLimit Limit of steps run per second
       */
      public void setStepPerSecondLimit(double newLimit);

      /**
       * Returns the list of futures for the current test script run.  If not currently running this 
       * will be null.
       * 
       * @return List of futures that will complete for the current execution
       */
      public List<ListenableFuture<StepResult>> getGlobalRunningFutureSet();
      
      /**
       * Copies this assistant.  The copied assistant will be backed by the same scheduler and 
       * futures.  However things which are chain sensitive (like the execution limit) will be 
       * copied in their initial state, but changes will not impact previous copies.
       *  
       * @return A new assistant instance
       */
      public ExecutionAssistant makeCopy();
      
      /**
       * Register a listener to be invoked if a failure occurs.  This listener will be invoked 
       * when any steps within the script invoke {@link #markGlobalFailure()}.
       * 
       * @param listener Listener to be invoked on failure
       */
      public void registerFailureNotification(Runnable listener);
      
      /**
       * Mark the execution as failure.  This will invoke listeners registered by 
       * {@link #registerFailureNotification(Runnable)}.
       */
      public void markGlobalFailure();
      
      /**
       * Checks to see if {@link #markGlobalFailure()} has been invoked or not.
       * 
       * @return {@code true} if the script has been marked as failure
       */
      public boolean getMarkedGlobalFailure();
    }
    
    /**
     * <p>Interface to be invoked if set on a step at start.  This can be used for multiple 
     * reasons, one may to just get an indication that a step is ready to execute.  Another may be 
     * to set a pre-run condition.  Meaning that this could prevent step execution, and just be 
     * invoked to indicate that a step is ready to execute.</p>
     * 
     * @author jent - Mike Jensen
     */
    public interface StepStartHandler {
      /**
       * Invoked by the {@link ExecutionItem} when 
       * {@link ExecutionItem#itemReadyForExecution(ExecutionAssistant)} is invoked.
       * 
       * @param step Step which is being invoked
       * @param assistant Assistant the step is being invoked with
       */
      public void readyToRun(ExecutionItem step, ExecutionAssistant assistant);
    }
  }
}