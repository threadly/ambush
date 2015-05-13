package org.threadly.load;

import java.util.Iterator;
import java.util.concurrent.Executor;

import org.threadly.concurrent.future.FutureUtils;
import org.threadly.load.ExecutableScript.ExecutionItem;

/**
 * <p>A builder which's added steps will all be executed in sequence.</p>
 * 
 * @author jent - Mike Jensen
 */
public class SequentialScriptBuilder extends AbstractScriptBuilder {
  protected final SequentialStep currentStep;

  /**
   * Constructs a new {@link SequentialScriptBuilder}.  This can be used as either the start of a 
   * execution chain, or can later be provided into 
   * {@link AbstractScriptBuilder#addSteps(SequentialScriptBuilder)}.
   */
  public SequentialScriptBuilder() {
    this(null);
  }
  
  protected SequentialScriptBuilder(AbstractScriptBuilder sourceBuilder) {
    super(sourceBuilder);
    currentStep = new SequentialStep();
  }

  @Override
  protected void finalizeStep() {
    if (! currentStep.steps.isEmpty()) {
      stepRunners.add(currentStep);
    }
  }

  /**
   * The call to get a {@link SequentialScriptBuilder} from within an existing 
   * {@link SequentialScriptBuilder} is a no-op.  So this will just return the reference to itself.
   *   
   * @return The same reference to this instance.
   */
  @Override
  public SequentialScriptBuilder inSequence() {
    return this;
  }
  
  @Override
  public ParallelScriptBuilder inParallel() {
    return new ParallelScriptBuilder(this);
  }
  
  /**
   * Adds a step to be run sequentially.  This step will be ran after previously added steps have 
   * completed.
   * 
   * @param step Test step to be added
   */
  @Override
  public void addStep(ScriptStepInterface step) {
    addStep(new ScriptStepWrapper(step));
  }
  
  @Override
  protected void addStep(ExecutionItem step) {
    verifyValid();
    currentStep.addItem(step);
  }
  
  /**
   * Adds a set of steps to be run sequentially.  These sequential steps wont be started until all 
   * previously added steps have completed.  The execution graph of the provided steps will be 
   * maintained.  Future steps wont be executed till all provided steps are complete.
   * 
   * @param sequentialSteps Sequential steps to add to this builder
   */
  @Override
  public void addSteps(SequentialScriptBuilder sequentialSteps) {
    verifyValid();
    maybeUpdatedMaximumThreads(sequentialSteps.getMaximumThreadsNeeded() + 1);
    Iterator<ExecutionItem> it = sequentialSteps.currentStep.steps.iterator();
    while (it.hasNext()) {
      currentStep.addItem(it.next());
    }
  }
  
  /**
   * Adds a set of steps which will run in parallel.  The execution graph of the provided steps 
   * will be maintained.  So the provided parallel steps will run in parallel as built, and 
   * future steps added to this builder will not be executed till this provided builder has 
   * finished running.
   * 
   * @param parallelSteps Parallel steps to add to this builder
   */
  @Override
  public void addSteps(ParallelScriptBuilder parallelSteps) {
    maybeUpdatedMaximumThreads(parallelSteps.getMaximumThreadsNeeded() + 1);
    addStep(parallelSteps.currentStep);
  }
  
  /**
   * <p>Implementation where the test will be ran in-thread (rather than farmed out to the 
   * executor).  This will result in {@link #runChainItem(Executor)} to block till the test step 
   * has completed.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class ScriptStepWrapper extends AbstractScriptStepWrapper {
    public ScriptStepWrapper(ScriptStepInterface scriptStep) {
      super(scriptStep);
    }
    
    @Override
    public void runChainItem(ExecutableScript runningScriptBuilder, Executor executor) {
      scriptStepRunner.run();
    }

    @Override
    public ExecutionItem makeCopy() {
      return new ScriptStepWrapper(scriptStepRunner.scriptStep);
    }
  }
  
  /**
   * <p>Collection of steps which will all be executed on this thread, one after another.</p>
   * 
   * @author jent - Mike Jensen
   */
  // TODO - can this be put into StepCollectionRunner
  protected static class SequentialStep extends StepCollectionRunner {
    @Override
    public void runChainItem(ExecutableScript runningScriptBuilder, final Executor executor) {
      Iterator<ExecutionItem> it = steps.iterator();
      while (it.hasNext()) {
        ExecutionItem chainItem = it.next();
        chainItem.runChainItem(runningScriptBuilder, executor);
        // this call will block till execution is done, thus making us wait to run the next chain item
        try {
          if (StepResultCollectionUtils.getFailedResult(chainItem.getFutures()) != null) {
            FutureUtils.cancelIncompleteFutures(getFutures(), true);
            return;
          }
        } catch (InterruptedException e) {
          // let thread exit
          return;
        }
      }
    }

    @Override
    public SequentialStep makeCopy() {
      SequentialStep result = new SequentialStep();
      Iterator<ExecutionItem> it = steps.iterator();
      while (it.hasNext()) {
        result.addItem(it.next().makeCopy());
      }
      return result;
    }
  }
}
