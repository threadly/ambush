package org.threadly.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
  protected final Collection<TestChainItem> stepRunners;
  private final AtomicBoolean finalized;
  private final AtomicReference<List<? extends ListenableFuture<TestResult>>> runningFutureSet;
  private int maximumThreadsNeeded;
  private Exception replacementException = null;

  protected AbstractScriptBuilder(AbstractScriptBuilder sourceBuilder) {
    if (sourceBuilder == null) {
      stepRunners = new ArrayList<TestChainItem>();
      maximumThreadsNeeded = 1;
    } else {
      sourceBuilder.replaced();
      this.stepRunners = sourceBuilder.stepRunners;
      this.maximumThreadsNeeded = sourceBuilder.maximumThreadsNeeded;
    }
    this.finalized = new AtomicBoolean(false);
    runningFutureSet = new AtomicReference<List<? extends ListenableFuture<TestResult>>>(null);
  }
  
  /**
   * Adds a step in the current test position which will log out the percent of completion for the 
   * entire test script.  The provided future will complete once the test has reached this point 
   * in the script.  The resulting double provided by the future is the percent of completion at 
   * this moment.  You can easily log this by adding a 
   * {@link org.threadly.concurrent.future.FutureCallback} to the returned future.  
   * 
   * If there are steps running in parallel at the time of execution for this progress future it 
   * should be noted the number is a best guess, as no locking occurs during determining the 
   * current progress.
   * 
   * @return A future which provide a double representing the percent of how much of the script has completed
   */
  public ListenableFuture<Double> addProgressFuture() {
    SettableListenableFuture<Double> slf = new SettableListenableFuture<Double>();
    addStep(new ProgressTestStep(slf));
    return slf;
  }
  
  /**
   * Returns the list of futures for the current test script run.  If not currently running this 
   * will be null.
   * 
   * @return List of futures that will complete for the current execution
   */
  protected List<? extends ListenableFuture<TestResult>> getRunningFutureSet() {
    return runningFutureSet.get();
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
   * Add a {@link TestChainItem} to this builder.  This is a private API so that we can add test 
   * steps which need access to the executing {@link AbstractScriptBuilder}.
   * 
   * @param chainItem Item to add to current execution chain
   */
  protected abstract void addStep(TestChainItem chainItem);

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
   * {@link TestResultCollectionUtils#getFailedResult(Collection)} to see if any steps failed.  
   * This will block till all steps have completed (or a failed test step occurred).  If 
   * {@link TestResultCollectionUtils#getFailedResult(Collection)} returns null, then the test 
   * completed without error. 
   * 
   * @return A collection of futures which will represent each execution step
   */
  public List<? extends ListenableFuture<TestResult>> startScript() {
    maybeFinalize();
    
    final List<SettableListenableFuture<TestResult>> result = new ArrayList<SettableListenableFuture<TestResult>>();
    if (! runningFutureSet.compareAndSet(null, result)) {
      throw new IllegalStateException("Script already running in parallel");
    }
    final PriorityScheduler scheduler = new PriorityScheduler(maximumThreadsNeeded + 1);
    scheduler.prestartAllThreads();
    
    Iterator<TestChainItem> it = stepRunners.iterator();
    while (it.hasNext()) {
      result.addAll(it.next().getFutures());
    }
    
    // TODO - move this to a regular class?
    scheduler.execute(new Runnable() {
      @Override
      public void run() {
        Iterator<TestChainItem> it = stepRunners.iterator();
        while (it.hasNext()) {
          TestChainItem stepRunner = it.next();
          stepRunner.runChainItem(AbstractScriptBuilder.this, scheduler);
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
   * <p>Test step which will report the current running test progress.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class ProgressTestStep implements TestChainItem {
    private final SettableListenableFuture<Double> slf;

    public ProgressTestStep(SettableListenableFuture<Double> slf) {
      this.slf = slf;
    }

    @Override
    public void runChainItem(AbstractScriptBuilder runningScriptBuilder, Executor executor) {
      List<? extends ListenableFuture<?>> scriptFutures = runningScriptBuilder.getRunningFutureSet();
      double doneCount = 0;
      Iterator<? extends ListenableFuture<?>> it = scriptFutures.iterator();
      while (it.hasNext()) {
        if (it.next().isDone()) {
          doneCount++;
        }
      }
      
      slf.setResult((doneCount / scriptFutures.size()) * 100);
    }

    @Override
    public Collection<? extends SettableListenableFuture<TestResult>> getFutures() {
      return Collections.emptyList();
    }
  }
  
  /**
   * <p>Interface for chain item, all items provided for execution must implement this interface.  
   * This will require test steps to be wrapped in a class which provides this functionality.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected interface TestChainItem {
    public abstract void runChainItem(AbstractScriptBuilder runningScriptBuilder, Executor executor);

    public abstract Collection<? extends SettableListenableFuture<TestResult>> getFutures();
  }
  
  /**
   * <p>Basic abstract implementation for every test step collection to execute it's tests.  This 
   * also provides the minimum API that any collection of steps must implement.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected abstract static class StepCollectionRunner implements TestChainItem {
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
    public void runChainItem(AbstractScriptBuilder runningScriptBuilder, Executor executor) {
      Iterator<TestChainItem> it = steps.iterator();
      while (it.hasNext()) {
        it.next().runChainItem(runningScriptBuilder, executor);
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
    public Collection<SettableListenableFuture<TestResult>> getFutures() {
      SettableListenableFuture<TestResult> slf = testStepRunner;
      return Collections.singletonList(slf);
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
      super(false);
      this.testStep = testStep;
    }
    
    @Override
    public void run() {
      setRunningThread(Thread.currentThread());
      
      long startNanos = Clock.systemNanoTime();
      TestResult result;
      try {
        testStep.runTest();
        long endNanos = Clock.systemNanoTime();
        result = new TestResult(testStep.getIdentifier(), endNanos - startNanos);
      } catch (Throwable t) {
        long endNanos = Clock.systemNanoTime();
        result = new TestResult(testStep.getIdentifier(), endNanos - startNanos, t);
      }
      setResult(result);
    }
  }
}
