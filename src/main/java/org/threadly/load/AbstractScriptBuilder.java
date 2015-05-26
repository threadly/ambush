package org.threadly.load;

import java.util.ArrayList;
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
  
  protected void maybeUpdatedMaximumThreads(int currentValue) {
    if (neededThreadCount < currentValue) {
      neededThreadCount = currentValue;
    }
  }
  
  private void maybeFinalize() {
    verifyValid();
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
   * Finalizes the script and compiles into an executable form.
   * 
   * @return A script which can be started
   */
  public ExecutableScript build() {
    maybeFinalize();
    return new ExecutableScript(neededThreadCount, stepRunners);
  }
  
  /**
   * <p>A simple implementation of {@link ChildItems} which takes in a list of items that it is 
   * holding.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class ChildItemContainer implements ChildItems {
    private final List<ExecutionItem> items;
    private final boolean runSequentially;
    
    protected ChildItemContainer() {
      this(null, true);
    }
    
    protected ChildItemContainer(List<ExecutionItem> items, boolean runSequentially) {
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
        return Collections.unmodifiableList(items).iterator();
      }
    }
  }
  
  /**
   * <p>Test step which will report the current running test progress.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class ProgressScriptStep implements ExecutionItem {
    private final SettableListenableFuture<Double> slf;

    public ProgressScriptStep(SettableListenableFuture<Double> slf) {
      this.slf = slf;
    }

    @Override
    public void prepareForRun() {
      // nothing to do here
    }

    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      try {
        List<? extends ListenableFuture<?>> scriptFutures = assistant.getRunningFutureSet();
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
    public List<? extends SettableListenableFuture<StepResult>> getFutures() {
      return Collections.emptyList();
    }

    @Override
    public ExecutionItem makeCopy() {
      // this does not need a copy
      return this;
    }

    @Override
    public ChildItems getChildItems() {
      return new ChildItemContainer();
    }

    @Override
    public String toString() {
      return ProgressScriptStep.class.getSimpleName();
    }
  }
  
  /**
   * <p>Basic abstract implementation for every test step collection to execute it's tests.  This 
   * also provides the minimum API that any collection of steps must implement.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected abstract static class StepCollectionRunner implements ExecutionItem {
    // TODO - move from arrayLists to arrays to save memory
    protected final ArrayList<ExecutionItem> steps;
    private final ArrayList<SettableListenableFuture<StepResult>> futures;
    
    public StepCollectionRunner() {
      steps = new ArrayList<ExecutionItem>();
      futures = new ArrayList<SettableListenableFuture<StepResult>>();
    }

    @Override
    public void prepareForRun() {
      steps.trimToSize();
      futures.trimToSize();
    }
    
    // TODO - remove?
    @Override
    public abstract StepCollectionRunner makeCopy();
    
    public void addItem(ExecutionItem item) {
      futures.addAll(item.getFutures());
      steps.add(item);
    }
    
    @Override
    public List<? extends SettableListenableFuture<StepResult>> getFutures() {
      return futures;
    }
    
    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      Iterator<ExecutionItem> it = steps.iterator();
      while (it.hasNext()) {
        it.next().runChainItem(assistant);
      }
    }
    
    @Override
    public String toString() {
      return steps.toString();
    }
  }
  
  /**
   * <p>The basic implementation for running a single test step.  The actual execution will depend 
   * on the type of builder.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected abstract static class AbstractScriptStepWrapper implements ExecutionItem {
    public final ScriptStepRunner scriptStepRunner;
    
    public AbstractScriptStepWrapper(ScriptStepInterface scriptStep) {
      scriptStepRunner = new ScriptStepRunner(scriptStep);
    }

    @Override
    public void prepareForRun() {
      // nothing to do here
    }

    @Override
    public List<SettableListenableFuture<StepResult>> getFutures() {
      SettableListenableFuture<StepResult> slf = scriptStepRunner;
      return Collections.singletonList(slf);
    }

    @Override
    public ChildItems getChildItems() {
      return new ChildItemContainer();
    }
    
    @Override
    public String toString() {
      return scriptStepRunner.toString();
    }
  }
  
  /**
   * <p>{@link Runnable} implementation for how a {@link ScriptStepInterface} is executed.  This 
   * class also represents the future for the test step execution.  If executed it is guaranteed 
   * to provide a {@link StepResult} (with or without error).</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class ScriptStepRunner extends SettableListenableFuture<StepResult>
                                          implements Runnable {
    protected final ScriptStepInterface scriptStep;
    
    protected ScriptStepRunner(ScriptStepInterface scriptStep) {
      super(false);
      this.scriptStep = scriptStep;
    }
    
    @Override
    public void run() {
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
    public String toString() {
      return scriptStep.getIdentifier();
    }
  }
}
