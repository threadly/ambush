package org.threadly.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.ExecutableScript.ExecutionItem.ChildItems;
import org.threadly.util.Clock;

/**
 * <p>Provides the shared implementation among all execution step builders.  This also defines the 
 * minimum API set that all builders must implement.</p>
 * 
 * @author jent - Mike Jensen
 */
public abstract class AbstractScriptBuilder {
  protected final ArrayList<ExecutionItem> stepRunners;
  private final AtomicBoolean finalized;
  private int neededThreadCount;
  private Exception replacementException = null;

  protected AbstractScriptBuilder(AbstractScriptBuilder sourceBuilder) {
    if (sourceBuilder == null) {
      stepRunners = new ArrayList<ExecutionItem>();
      neededThreadCount = 1;
    } else {
      sourceBuilder.replaced();
      this.stepRunners = sourceBuilder.stepRunners;
      this.neededThreadCount = sourceBuilder.neededThreadCount;
    }
    this.finalized = new AtomicBoolean(false);
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
   * If the script is stopped (likely due to an error or step failure), this returned future will 
   * complete in an error state (ie {@link ListenableFuture#get()} will throw a 
   * {@code ExecutionException}.
   * 
   * @return A future which provide a double representing the percent of how much of the script has completed
   */
  public ListenableFuture<Double> addProgressFuture() {
    SettableListenableFuture<Double> slf = new SettableListenableFuture<Double>(false);
    addStep(new ProgressScriptStep(slf));
    return slf;
  }
  
  /**
   * Sets a new limit for the script steps to be executed at.  This will only take place for steps 
   * which execute AFTER this point in the script.  This is effectively adding a task at this 
   * point in the script that will set an limit on how fast the steps can run.  If you want your 
   * entire script to adher to this limit it must be done before any steps are added.  
   * 
   * This allows you to control request rates, allowing you at run time to adjust the rate at 
   * which executions are occurring.  
   * 
   * Provide a value of zero to disable this and allow steps to execute as fast as possible.
   * 
   * @param stepsPerSecondLimit Steps per second allowed to execute
   */
  public void setMaxScriptStepRate(double stepsPerSecondLimit) {
    addStep(new RateAdjustmentStep(stepsPerSecondLimit));
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
   * {@link SequentialScriptBuilder#addStep(ScriptStepInterface)} and 
   * {@link ParallelScriptBuilder#addStep(ScriptStepInterface)}.
   * 
   * @param step Test step to add to builder
   */
  public abstract void addStep(ScriptStepInterface step);
  
  /**
   * Add a {@link ExecutionItem} to this builder.  This is a private API so that we can add test 
   * steps which need access to the executing {@link AbstractScriptBuilder}.
   * 
   * @param chainItem Item to add to current execution chain
   */
  protected abstract void addStep(ExecutionItem chainItem);

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
  
  /**
   * Call to check how many threads this script will need to execute at the current build point.  
   * This can give you an idea of how intensely parallel this script is.
   * 
   * @return Number of threads to run script at it's most parallel point
   */
  public int getNeededThreadCount() {
    return neededThreadCount;
  }
  
  /**
   * Make a copy of the script chain.  This is necessary if you want to add the chain multiple 
   * times to another chain.  The returned chain will execute the same script step instances (so 
   * if added to a parallel builder make sure the script steps are thread safe).
   * 
   * @return A copy builder
   */
  public abstract AbstractScriptBuilder makeCopy();
  
  protected void maybeUpdatedMaximumThreads(int currentValue) {
    if (neededThreadCount < currentValue) {
      neededThreadCount = currentValue;
    }
  }
  
  /**
   * Finalizes the script construction if it has not already finalized.  This is not the same as {@link #replaced}
   */
  private void maybeFinalize() {
    verifyNotReplaced();
    if (! finalized.getAndSet(true)) {
      finalizeStep();
      stepRunners.trimToSize();
    }
  }
  
  /**
   * Called when the step is about to be either executed or replaced.  This finalizes the step 
   * to add it to the execution chain if it makes sense to do so.
   */
  protected abstract void finalizeStep();
  
  /**
   * Marks this builder as replaced.  Once replaced no operations can continue to happen on this 
   * builder.  All further building must be done on the builder which is replacing this one.
   */
  protected void replaced() {
    maybeFinalize();
    replacementException = new Exception();
  }
  
  /**
   * Verifies this builder is still valid to build on.  Meaning it has not been finalized or 
   * replaced by another builder.
   */
  protected void verifyValid() {
    verifyNotReplaced();
    if (finalized.get()) {
      throw new IllegalStateException("Script finalized");
    }
  }
  
  /**
   * Verifies that this builder has not been replaced by another builder.  This is a subset check 
   * of {@link #verifyValid()}.
   */
  protected void verifyNotReplaced() {
    if (replacementException != null) {
      throw new RuntimeException("This builder has been replaced, " + 
                                   "caused by exception will indicate the stack of where it was replaced", 
                                 replacementException);
    }
  }
  
  /**
   * Finalizes the script and compiles into an executable form.
   * 
   * @return A script which can be started
   */
  public ExecutableScript build() {
    maybeFinalize();
    if (stepRunners.isEmpty()) {
      throw new IllegalStateException("No steps added to script to build");
    }
    return new ExecutableScript(neededThreadCount, stepRunners);
  }
  
  /**
   * <p>A simple implementation of {@link ChildItems} which takes in a list of items that it is 
   * holding.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class ChildItemContainer implements ChildItems {
    protected final ExecutionItem[] items;
    protected final boolean runSequentially;
    
    protected ChildItemContainer() {
      this(null, true);
    }
    
    protected ChildItemContainer(ExecutionItem[] items, boolean runSequentially) {
      this.items = items;
      this.runSequentially = runSequentially;
    }

    @Override
    public boolean itemsRunSequential() {
      return runSequentially;
    }

    @Override
    public boolean hasChildren() {
      return items != null;
    }

    @Override
    public Iterator<ExecutionItem> iterator() {
      if (items == null) {
        return Collections.<ExecutionItem>emptyList().iterator();
      } else {
        return Collections.unmodifiableList(Arrays.asList(items)).iterator();
      }
    }
  }
  
  /**
   * <p>{@link ExecutionItem} which changes the limit at which steps can be executed.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class RateAdjustmentStep extends GhostExecutionItem {
    private final double newRateLimit;
    
    public RateAdjustmentStep(double newRateLimit) {
      this.newRateLimit = newRateLimit;
    }
    
    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      assistant.setStepPerSecondLimit(newRateLimit);
    }

    @Override
    public String toString() {
      return RateAdjustmentStep.class.getSimpleName();
    }

    @Override
    public boolean manipulatesExecutionAssistant() {
      return true;
    }
  }
  
  /**
   * <p>Test step which will report the current running test progress.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class ProgressScriptStep extends GhostExecutionItem {
    private final SettableListenableFuture<Double> slf;

    public ProgressScriptStep(SettableListenableFuture<Double> slf) {
      this.slf = slf;
    }

    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      try {
        List<? extends ListenableFuture<?>> scriptFutures = assistant.getGlobalRunningFutureSet();
        double doneCount = 0;
        Iterator<? extends ListenableFuture<?>> it = scriptFutures.iterator();
        while (it.hasNext()) {
          if (it.next().isDone()) {
            doneCount++;
          }
        }
        
        slf.setResult((doneCount / scriptFutures.size()) * 100);
      } catch (Exception e) {
        slf.setFailure(e);
      }
    }

    @Override
    public String toString() {
      return ProgressScriptStep.class.getSimpleName();
    }

    @Override
    public boolean manipulatesExecutionAssistant() {
      return false;
    }
  }
  
  /**
   * <p>Abstract implementation for any {@link ExecutionItem} which should not be ever seen to the 
   * user.  Either by a future returned as a step, or copied into a new chain.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected abstract static class GhostExecutionItem implements ExecutionItem {
    @Override
    public void prepareForRun() {
      // nothing to do here
    }
    
    @Override
    public List<? extends SettableListenableFuture<StepResult>> getFutures() {
      return Collections.emptyList();
    }

    @Override
    public ExecutionItem makeCopy() {
      return null;
    }

    @Override
    public ChildItems getChildItems() {
      return new ChildItemContainer();
    }

    @Override
    public boolean isChainExecutor() {
      return false;
    }
  }
  
  /**
   * <p>Basic abstract implementation for every test step collection to execute it's tests.  This 
   * also provides the minimum API that any collection of steps must implement.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected abstract static class StepCollectionRunner implements ExecutionItem {
    private final ArrayList<SettableListenableFuture<StepResult>> futures;
    private ExecutionItem[] steps;
    
    public StepCollectionRunner() {
      steps = new ExecutionItem[0];
      futures = new ArrayList<SettableListenableFuture<StepResult>>();
    }

    /**
     * Call to check how many steps are in this collection.
     * 
     * @return Number of steps this collection runs
     */
    public int getStepCount() {
      return steps.length;
    }
    
    /**
     * Returns the backing array of steps which will be ran by this collection.
     * 
     * @return Array of items which will be ran
     */
    public ExecutionItem[] getSteps() {
      return steps;
    }

    @Override
    public void prepareForRun() {
      futures.trimToSize();
    }
    
    private ExecutionItem[] makeStepsCopy(int extraEndSpace) {
      ExecutionItem[] newSteps = new ExecutionItem[steps.length + extraEndSpace];
      System.arraycopy(steps, 0, newSteps, 0, steps.length);
      return newSteps;
    }
    
    /**
     * Adds an {@link ExecutionItem} to this collection of steps to run.
     * 
     * @param item Item to be added, can not be {@code null}
     */
    public void addItem(ExecutionItem item) {
      futures.addAll(item.getFutures());
      
      ExecutionItem[] newSteps = makeStepsCopy(1);
      newSteps[steps.length] = item;
      steps = newSteps;
    }

    /**
     * Adds an array of {@link ExecutionItem}'s to this collection of steps to run.
     * 
     * @param items Items to be added, can not be {@code null}
     */
    public void addItems(ExecutionItem[] items) {
      if (items.length == 0) {
        return;
      }
      
      for (ExecutionItem ei : items) {
        futures.addAll(ei.getFutures());
      }
      
      ExecutionItem[] newSteps = makeStepsCopy(items.length);
      System.arraycopy(items, 0, newSteps, steps.length, items.length);
      steps = newSteps;
    }
    
    @Override
    public List<? extends SettableListenableFuture<StepResult>> getFutures() {
      return futures;
    }
    
    @Override
    public String toString() {
      return Arrays.toString(steps);
    }

    @Override
    public boolean manipulatesExecutionAssistant() {
      return false;
    }

    @Override
    public boolean isChainExecutor() {
      return true;
    }
  }

  /**
   * <p>Implementation for executing a {@link ScriptStepInterface} instance.  This class also 
   * represents the future for the test step execution.  If executed it is guaranteed to provide a 
   * {@link StepResult} (with or without error).</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class ScriptStepRunner extends SettableListenableFuture<StepResult>
                                          implements ExecutionItem {
    protected final ScriptStepInterface scriptStep;
    
    public ScriptStepRunner(ScriptStepInterface scriptStep) {
      super(false);
      this.scriptStep = scriptStep;
    }

    @Override
    public void prepareForRun() {
      // nothing to do here
    }

    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      setRunningThread(Thread.currentThread());
      
      long startNanos = Clock.systemNanoTime();
      StepResult result;
      try {
        scriptStep.runStep();
        long endNanos = Clock.systemNanoTime();
        result = new StepResult(scriptStep.getIdentifier(), endNanos - startNanos);
      } catch (Throwable t) {
        long endNanos = Clock.systemNanoTime();
        result = new StepResult(scriptStep.getIdentifier(), endNanos - startNanos, t);
      }
      setResult(result);
    }

    @Override
    public ScriptStepRunner makeCopy() {
      return new ScriptStepRunner(scriptStep);
    }

    @Override
    public List<SettableListenableFuture<StepResult>> getFutures() {
      SettableListenableFuture<StepResult> slf = this;
      return Collections.singletonList(slf);
    }

    @Override
    public ChildItems getChildItems() {
      return new ChildItemContainer();
    }
    
    @Override
    public String toString() {
      return scriptStep.getIdentifier();
    }

    @Override
    public boolean manipulatesExecutionAssistant() {
      return false;
    }

    @Override
    public boolean isChainExecutor() {
      return false;
    }
  }
}
