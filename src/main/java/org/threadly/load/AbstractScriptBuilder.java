package org.threadly.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.ExecutableScript.ExecutionItem.ChildItems;
import org.threadly.load.ExecutableScript.ExecutionItem.StepStartHandler;
import org.threadly.util.ArgumentVerifier;
import org.threadly.util.Clock;
import org.threadly.util.ExceptionUtils;

/**
 * <p>Provides the shared implementation among all execution step builders.  This also defines the 
 * minimum API set that all builders must implement.</p>
 * 
 * @author jent - Mike Jensen
 */
public abstract class AbstractScriptBuilder {
  private int neededThreadCount;
  private Exception replacementException;

  protected AbstractScriptBuilder() {
    neededThreadCount = 1;
    replacementException = null;
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
   * Add a step to this builder.  For more specific step addition descriptions please see: 
   * {@link SequentialScriptBuilder#addStep(ScriptStep)} and 
   * {@link ParallelScriptBuilder#addStep(ScriptStep)}.
   * 
   * @param step Test step to add to builder
   */
  public abstract void addStep(ScriptStep step);
  
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
   * @param sequentialSteps Script steps to be added to this builder
   */
  public abstract void addSteps(SequentialScriptBuilder sequentialSteps);

  /**
   * Add a parallel series of steps to this builder.  Since the behavior of this depends on the 
   * current step, please see a more specific step addition behavior descriptions here: 
   * {@link SequentialScriptBuilder#addSteps(SequentialScriptBuilder)} and 
   * {@link ParallelScriptBuilder#addSteps(SequentialScriptBuilder)}.
   * 
   * @param parallelSteps Script steps to be added to this builder
   */
  public abstract void addSteps(ParallelScriptBuilder parallelSteps);
  
  /**
   * Adds steps for an unknown builder type.  This does instance of checks and defers to either 
   * {@link #addSteps(ParallelScriptBuilder)} or {@link #addSteps(SequentialScriptBuilder)}.
   * 
   * @param scriptBuilder Script builder to add steps from
   */
  public void addSteps(AbstractScriptBuilder scriptBuilder) {
    if (scriptBuilder == null) {
      return;
    } else if (scriptBuilder instanceof SequentialScriptBuilder) {
      addSteps((SequentialScriptBuilder)scriptBuilder);
    } else if (scriptBuilder instanceof ParallelScriptBuilder) {
      addSteps((ParallelScriptBuilder)scriptBuilder);
    } else {
      throw new UnsupportedOperationException("Provided an unknown builder type: " + 
                                                scriptBuilder.getClass());
    }
  }
  
  /**
   * Call to check how many threads this script will need to execute at the current build point.  
   * This can give you an idea of how intensely parallel this script is.  Though it should be known 
   * that less threads may be started than this.  If the rate limiting is being used, depending 
   * on how fast test steps are completing not all threads may be used. 
   * 
   * @return Number of threads to run script at it's most parallel point
   */
  public int getMaximumNeededThreadCount() {
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
  
  /**
   * Call to tell builder that a new possible thread maximum has been observed.  If this maximum is 
   * higher than we are already tracking then we will update our tracked value.
   * 
   * @param currentValue New observed needed thread count
   */
  protected void maybeUpdatedMaximumThreads(int currentValue) {
    if (neededThreadCount < currentValue) {
      neededThreadCount = currentValue;
    }
  }
  
  /**
   * Returns this step as an execution item.  This is needed for when combining script chains into 
   * a single start point.
   * 
   * @return The script step as an ExecutionItem
   */
  protected abstract ExecutionItem getStepAsExecutionItem();

  /**
   * Sets a {@link StepStartHandler} on all contained and child steps.  This does NOT set the 
   * handler on any chain runners, only real execution steps.  This should defer to 
   * {@link #setStartHandler(ChildItems, StepStartHandler)}.
   * 
   * @param startHandler Handler to apply to entire step chain
   */
  protected abstract void setStartHandlerOnAllSteps(StepStartHandler startHandler);
  
  /**
   * Static call to traverse all child items tree to set the start handler for all items.  Chain 
   * executors will NOT be set, but their children will be.
   * 
   * @param items Child items to start traversal from
   * @param startHandler Handler to apply to all items
   */
  protected static void setStartHandler(ChildItems items, StepStartHandler startHandler) {
    for (ExecutionItem ei : items) {
      if (ei.isChainExecutor()) {
        setStartHandler(ei.getChildItems(), startHandler);
      } else {
        ei.setStartHandler(startHandler);
      }
    }
  }
  
  /**
   * Marks this builder as replaced.  Once replaced no operations can continue to happen on this 
   * builder.  All further building must be done on the builder which is replacing this one.
   */
  protected void replaced() {
    verifyNotReplaced();
    replacementException = new Exception();
  }
  
  /**
   * Verifies this builder is still valid to build on.  Meaning it has not been finalized or 
   * replaced by another builder.
   */
  protected void verifyValid() {
    verifyNotReplaced();
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
    replaced();
    return new ExecutableScript(neededThreadCount, getStepAsExecutionItem());
  }
  
  /**
   * <p>A simple implementation of {@link ChildItems} which takes in a list of items that it is 
   * holding.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class ChildItemContainer implements ChildItems {
    protected static final ChildItemContainer EMPTY_CHILD_ITEMS_CONTAINER = new ChildItemContainer();
    
    protected final ExecutionItem[] items;
    protected final boolean runSequentially;
    
    private ChildItemContainer() {
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
      return items != null && items.length > 0;
    }

    @Override
    public Iterator<ExecutionItem> iterator() {
      if (hasChildren()) {
        return Arrays.asList(items).iterator();
      } else {
        return Collections.<ExecutionItem>emptyList().iterator();
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
    protected void runItem(ExecutionAssistant assistant) {
      assistant.setStepPerSecondLimit(newRateLimit);
    }

    @Override
    public String toString() {
      return "RateAdjustment:" + newRateLimit;
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
    protected void runItem(ExecutionAssistant assistant) {
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
  protected abstract static class GhostExecutionItem extends AbstractExecutionItem {
    @Override
    public void prepareForRun() {
      // nothing to do here
    }

    @Override
    public void runComplete() {
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
      return ChildItemContainer.EMPTY_CHILD_ITEMS_CONTAINER;
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
  protected abstract static class StepCollectionRunner extends AbstractExecutionItem {
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
      if (steps == null) {
        throw new IllegalStateException("Run has completed");
      }
      
      return steps.length;
    }
    
    /**
     * Returns the backing array of steps which will be ran by this collection.
     * 
     * @return Array of items which will be ran
     */
    public ExecutionItem[] getSteps() {
      if (steps == null) {
        throw new IllegalStateException("Run has completed");
      }
      
      return steps;
    }
    
    protected void setSteps(ExecutionItem[] steps) {
      ArgumentVerifier.assertNotNull(steps, "steps");
      if (this.steps == null) {
        throw new IllegalStateException("Run has completed");
      }
      
      this.steps = steps;
    }

    @Override
    public void prepareForRun() {
      futures.trimToSize();
      
      for (ExecutionItem ei : steps) {
        ei.prepareForRun();
      }
    }

    @Override
    public void runComplete() {
      steps = null;
      // TODO - do we need to cancel here?  In theory these were canceled when each step finished
      FutureUtils.cancelIncompleteFutures(getFutures(), true);
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
      if (steps == null) {
        throw new IllegalStateException("Run has completed");
      }
      
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
      if (steps == null) {
        throw new IllegalStateException("Run has completed");
      }
      
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
      return steps == null ? "CompletedStepCollection" : Arrays.toString(steps);
    }

    @Override
    public boolean manipulatesExecutionAssistant() {
      // TODO - this one does not manipulate it, but do we need to report if any items we contain manipulate?
      return false;
    }

    @Override
    public boolean isChainExecutor() {
      return true;
    }
  }

  /**
   * <p>Implementation for executing a {@link ScriptStep} instance.  This class also represents 
   * the future for the test step execution.  If executed it is guaranteed to provide a 
   * {@link StepResult} (with or without error).</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class ScriptStepRunner extends AbstractExecutionItem {
    protected ScriptStep scriptStep;
    protected SettableListenableFuture<StepResult> future;
    
    public ScriptStepRunner(ScriptStep scriptStep) {
      this.scriptStep = scriptStep;
      future = new SettableListenableFuture<StepResult>(false);
    }

    @Override
    public void prepareForRun() {
      // nothing to do here
    }

    @Override
    public void runComplete() {
      scriptStep = null;
      future.cancel(true);  // should be done anyways
    }
    
    @Override
    public void itemReadyForExecution(ExecutionAssistant assistant) {
      // must override so we can set the running thread to handle the ability to interrupt
      future.setRunningThread(Thread.currentThread());
      super.itemReadyForExecution(assistant);
    }

    @Override
    protected void runItem(ExecutionAssistant assistant) {
      if (scriptStep == null) {
        throw new IllegalStateException("Run has completed");
      }
      
      if (assistant.getMarkedGlobalFailure()) {
        future.cancel(false);
        return;
      }
      
      future.setRunningThread(Thread.currentThread());
      
      switch (scriptStep.getStepType()) {
        case Normal: {
          long startNanos = Clock.accurateTimeNanos();
          try {
            scriptStep.runStep();
            long endNanos = Clock.accurateTimeNanos();
            future.setResult(new PassStepResult(scriptStep.getIdentifier(), 
                                                endNanos - startNanos));
          } catch (Throwable t) {
            long endNanos = Clock.accurateTimeNanos();
            future.setResult(new ErrorStepResult(scriptStep.getIdentifier(), 
                                                 endNanos - startNanos, t));
            // must set result before marking failure
            assistant.markGlobalFailure();
          }
        } break;
        case AsyncMaintenance:
          // set as complete immediately so script can continue
          future.setResult(new MaintenancePassStepResult(scriptStep.getIdentifier()));
          
          assistant.executeAsyncMaintenanceTaskIfStillRunning(new Runnable() {
            @Override
            public void run() {
              try {
                scriptStep.runStep();
              } catch (Throwable t) {
                ExceptionUtils.handleException(t);
              }
            }
          });
          break;
        case Maintenance: {
          try {
            scriptStep.runStep();
            future.setResult(new MaintenancePassStepResult(scriptStep.getIdentifier()));
          } catch (Throwable t) {
            future.setResult(new MaintenanceErrorStepResult(scriptStep.getIdentifier(), t));
            // must set result before marking failure
            assistant.markGlobalFailure();
          }
        } break;
        default:
          throw new UnsupportedOperationException();
      }
    }

    @Override
    public ScriptStepRunner makeCopy() {
      if (scriptStep == null) {
        throw new IllegalStateException("Run has completed");
      }
      
      return new ScriptStepRunner(scriptStep);
    }

    @Override
    public List<SettableListenableFuture<StepResult>> getFutures() {
      return Collections.singletonList(future);
    }

    @Override
    public ChildItems getChildItems() {
      return ChildItemContainer.EMPTY_CHILD_ITEMS_CONTAINER;
    }
    
    @Override
    public String toString() {
      return scriptStep == null ? "CompletedScriptStep" : scriptStep.getIdentifier().toString();
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
  
  /**
   * <p>Abstract implementation of {@link ExecutionItem} for common implementation.  One example 
   * of such common implementation is how the {@link StepStartHandler} behavior is handled.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected abstract static class AbstractExecutionItem implements ExecutionItem {
    private StepStartHandler handler = null;

    @Override
    public void setStartHandler(StepStartHandler handler) {
      if (this.handler != null && handler != null) {
        // TODO - do we need to handle multiple start handlers?  The cost is memory
        ExceptionUtils.handleException(new IllegalStateException("Pre-run condition already set"));
      }
      this.handler = handler;
    }
    
    @Override
    public void itemReadyForExecution(ExecutionAssistant assistant) {
      if (handler != null) {
        handler.readyToRun(this, assistant);
      } else {
        runItem(assistant);
      }
    }
    
    protected abstract void runItem(ExecutionAssistant assistant);
  }
  
  /**
   * <p>Runner to be added as a listener for when an execution item completes.  This runner is 
   * just so that the complete action can be indicated to the original item.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class ExecutionItemCompletionRunner implements Runnable {
    private final ExecutionItem item;
    
    public ExecutionItemCompletionRunner(ExecutionItem item) {
      this.item = item;
    }
    
    @Override
    public void run() {
      item.runComplete();
    }
  }
}
