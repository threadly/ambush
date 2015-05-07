package org.threadly.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.util.Clock;

/**
 * <p>Provides the shared implementation among all execution step builders.  This also defines the 
 * minimum API set that all builders must implement.</p>
 * 
 * @author jent - Mike Jensen
 */
public abstract class AbstractScriptBuilder {
  protected final Collection<StepCollectionRunner> stepRunners;
  private final AtomicBoolean finalized;
  private int maximumThreadsNeeded;
  private Exception replacementException = null;

  protected AbstractScriptBuilder(AbstractScriptBuilder sourceBuilder) {
    if (sourceBuilder == null) {
      stepRunners = new ArrayList<StepCollectionRunner>();
      maximumThreadsNeeded = 1;
    } else {
      sourceBuilder.replaced();
      this.stepRunners = sourceBuilder.stepRunners;
      this.maximumThreadsNeeded = sourceBuilder.maximumThreadsNeeded;
    }
    this.finalized = new AtomicBoolean(false);
  }

  /**
   * Get builder which will switch to parallel construction.  Steps added to the returned 
   * builder will execute in parallel.  This returned parallel builders steps will not be 
   * executed until ALL sequential steps from this builder are completed.
   * 
   * By requesting a sequential builder steps can no longer be added to THIS builder.  
   * Attempting to do so will result in an {@link IllegalStateException}.
   * 
   * @return Builder which you can add additional test steps to run in parallel
   */
  public abstract ParallelScriptBuilder inParallel();
  
  /**
   * Get builder which will switch to sequential construction.  Steps added to the returned 
   * builder will execute one after the other.  This returned sequential builders steps will not 
   * be executed until ALL parallel steps from this builder are completed.
   * 
   * By requesting a sequential builder steps can no longer be added to THIS builder.  
   * Attempting to do so will result in an {@link IllegalStateException}.
   * 
   * @return Builder which you can add additional test steps to run sequentially
   */
  public abstract SequentialScriptBuilder inSequence();

  /**
   * Add a step to this builder.  For more specific step addition descriptions please see: 
   * {@link SequentialScriptBuilder#addStep(TestStepInterface)} and 
   * {@link ParallelScriptBuilder#addStep(TestStepInterface)}.
   * 
   * @param step Test step to add to builder
   */
  public abstract void addStep(TestStepInterface step);

  /**
   * Add a sequential series of steps to this builder.  Since the behavior of this depends on the 
   * current step, please see a more specific step addition behavior descriptions here: 
   * {@link SequentialScriptBuilder#addSteps(SequentialScriptBuilder)} and 
   * {@link ParallelScriptBuilder#addSteps(SequentialScriptBuilder)}.
   * 
   * @param sequentialSteps Test steps to be added to this builder
   */
  public abstract void addSteps(SequentialScriptBuilder sequentialSteps);

  /**
   * Add a parallel series of steps to this builder.  Since the behavior of this depends on the 
   * current step, please see a more specific step addition behavior descriptions here: 
   * {@link SequentialScriptBuilder#addSteps(SequentialScriptBuilder)} and 
   * {@link ParallelScriptBuilder#addSteps(SequentialScriptBuilder)}.
   * 
   * @param parallelSteps Test steps to be added to this builder
   */
  public abstract void addSteps(ParallelScriptBuilder parallelSteps);
  
  protected int getMaximumThreadsNeeded() {
    return maximumThreadsNeeded;
  }
  
  protected void maybeUpdatedMaximumThreads(int currentValue) {
    if (maximumThreadsNeeded < currentValue) {
      maximumThreadsNeeded = currentValue;
    }
  }
  
  private void maybeFinalize() {
    verifyValid();
    if (! finalized.getAndSet(true)) {
      finalizeStep();
    }
  }
  
  /**
   * Called when the step is about to be either executed or replaced.  This finalizes the step 
   * to add it to the execution chain if it makes sense to do so.
   */
  protected abstract void finalizeStep();
  
  protected void replaced() {
    maybeFinalize();
    replacementException = new Exception();
  }
  
  protected void verifyValid() {
    if (replacementException != null) {
      throw new RuntimeException("This builder has been replaced, " + 
                                   "caused by exception will indicate the stack of where it was replaced", 
                                 replacementException);
    }
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
   * {@link #getFailedResult(Collection)} to see if any steps failed.  This will block till 
   * all steps have completed (or a failed test step occurred).  If 
   * {@link #getFailedResult(Collection)} returns null, then the test completed without error. 
   * 
   * @return A collection of futures which will represent each execution step
   */
  public List<? extends ListenableFuture<TestResult>> startScript() {
    maybeFinalize();
    
    final List<SettableListenableFuture<TestResult>> result = new ArrayList<SettableListenableFuture<TestResult>>();
    final PriorityScheduler scheduler = new PriorityScheduler(maximumThreadsNeeded + 1);
    scheduler.prestartAllThreads();
    
    Iterator<StepCollectionRunner> it = stepRunners.iterator();
    while (it.hasNext()) {
      result.addAll(it.next().getFutures());
    }
    
    // TODO - move this to a regular class?
    scheduler.execute(new Runnable() {
      @Override
      public void run() {
        Iterator<StepCollectionRunner> it = stepRunners.iterator();
        while (it.hasNext()) {
          StepCollectionRunner stepRunner = it.next();
          stepRunner.runSteps(scheduler);
          // this call will block till the step is done, thus preventing execution of the next step
          try {
            if (getFailedResult(stepRunner.getFutures()) != null) {
              markUncompleteAsFailure(result);
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
        scheduler.shutdown();
      }
    });
    
    return result;
  }
  
  /**
   * Looks through collection of futures looking for any {@link TestResult}'s that are in a failure 
   * state.  Once it finds the first one it returns it.  If this was a parallel step, it is 
   * possible there are additional failures not returned (it just returns the first failure it 
   * finds).  This call blocks until all the test steps have completed, and if none are in a 
   * failure state, it will return {@code null}.
   * 
   * @param futures Future collection to iterate over and inspect
   * @return Failed TestResult or {@code null} if no failures occurred
   * @throws InterruptedException Thrown if the thread is interrupted while waiting for {@link TestResult}
   */
  protected static TestResult getFailedResult(Collection<? extends ListenableFuture<TestResult>> futures) 
      throws InterruptedException {
    Iterator<? extends ListenableFuture<TestResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        TestResult tr = it.next().get();
        if (tr.getError() != null) {
          return tr;
        }
      } catch (ExecutionException e) {
        // should not be possible
        throw new RuntimeException(e);
      }
    }
    
    return null;
  }
  
  /**
   * Will set any futures which have not completed as failure with the provided failure.
   * 
   * @param futures List of futures to iterate over looking for uncompleted {@link SettableListenableFuture}
   */
  protected static void markUncompleteAsFailure(Collection<? extends SettableListenableFuture<TestResult>> futures) {
    Exception failure = null;
    Iterator<? extends SettableListenableFuture<TestResult>> it = futures.iterator();
    while (it.hasNext()) {
      SettableListenableFuture<TestResult> future = it.next();
      if (! future.isDone()) {
        if (failure == null) {
          failure = new Exception();
        }
        future.setFailure(failure);
      }
    }
  }
  
  /**
   * <p>Interface for chain item, all items provided for execution must implement this interface.  
   * This will require test steps to be wrapped in a class which provides this functionality.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected interface TestChainItem {
    public abstract void runChainItem(Executor executor);

    public abstract Collection<? extends SettableListenableFuture<TestResult>> getFutures();
  }
  
  /**
   * <p>Basic abstract implementation for every test step collection to execute it's tests.  This 
   * also provides the minimum API that any collection of steps must implement.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class StepCollectionRunner implements TestChainItem {
    protected final List<TestChainItem> steps;
    private final List<SettableListenableFuture<TestResult>> futures;
    
    public StepCollectionRunner() {
      steps = new ArrayList<TestChainItem>();
      futures = new ArrayList<SettableListenableFuture<TestResult>>();
    }
    
    public void addItem(TestChainItem item) {
      futures.addAll(item.getFutures());
      steps.add(item);
    }
    
    @Override
    public Collection<? extends SettableListenableFuture<TestResult>> getFutures() {
      return futures;
    }

    @Override
    public void runChainItem(Executor executor) {
      runSteps(executor);
    }
    
    /**
     * Runs all steps in the collection.
     * 
     * @param executor Executor to use if needed to farm out execution
     */
    public void runSteps(Executor executor) {
      Iterator<TestChainItem> it = steps.iterator();
      while (it.hasNext()) {
        it.next().runChainItem(executor);
      }
    }
  }
  
  /**
   * <p>The basic implementation for running a single test step.  The actual execution will depend 
   * on the type of builder.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected abstract static class AbstractTestStepWrapper implements TestChainItem {
    public final TestStepRunner testStepRunner;
    
    public AbstractTestStepWrapper(TestStepInterface testStep) {
      testStepRunner = new TestStepRunner(testStep);
    }
    
    @Override
    public abstract void runChainItem(Executor executor);

    @Override
    public Collection<SettableListenableFuture<TestResult>> getFutures() {
      List<SettableListenableFuture<TestResult>> result = new ArrayList<SettableListenableFuture<TestResult>>(1);
      result.add(testStepRunner);
      return result;
    }
  }
  
  /**
   * <p>{@link Runnable} implementation for how a {@link TestStepInterface} is executed.  This 
   * class also represents the future for the test step execution.  If executed it is guaranteed 
   * to provide a {@link TestResult} (with or without error).</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class TestStepRunner extends SettableListenableFuture<TestResult>
                                        implements Runnable {
    private final TestStepInterface testStep;
    
    protected TestStepRunner(TestStepInterface testStep) {
      this.testStep = testStep;
    }
    
    @Override
    public void run() {
      long startNanos = Clock.systemNanoTime();
      try {
        testStep.runTest();
        long endNanos = Clock.systemNanoTime();
        setResult(new TestResult(testStep.getIdentifier(), endNanos - startNanos));
      } catch (Throwable t) {
        long endNanos = Clock.systemNanoTime();
        setResult(new TestResult(testStep.getIdentifier(), endNanos - startNanos, t));
      }
    }
  }
}
