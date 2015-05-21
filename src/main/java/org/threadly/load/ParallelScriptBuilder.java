package org.threadly.load;

import java.util.Collection;
import java.util.Iterator;

import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.SequentialScriptBuilder.SequentialStep;

/**
 * <p>A builder which's added steps will all be executed in parallel.</p>
 * 
 * @author jent - Mike Jensen
 */
public class ParallelScriptBuilder extends AbstractScriptBuilder {
  protected final ParallelStep currentStep;
  private int stepsThreadsNeeded = 0;
  
  /**
   * Constructs a new {@link ParallelScriptBuilder}.  This can be used as either the start of a 
   * execution chain, or can later be provided into 
   * {@link AbstractScriptBuilder#addSteps(ParallelScriptBuilder)}.
   */
  public ParallelScriptBuilder() {
    this(null);
  }
  
  protected ParallelScriptBuilder(AbstractScriptBuilder sourceBuilder) {
    super(sourceBuilder);
    currentStep = new ParallelStep();
  }

  @Override
  protected void finalizeStep() {
    if (! currentStep.steps.isEmpty()) {
      stepRunners.add(currentStep);
    }
  }

  /**
   * This is equivalent to calling {@link #inSequence()}.{@link #inParallel()}.  Switching to in 
   * sequence and then back to inParallel result in having any steps added to the return instance 
   * will not execute UNTILL ALL the steps already added to {@code this} instance have finished 
   * (or ended in failure).
   * 
   * So UNLIKE {@link SequentialScriptBuilder#inSequence()}, this will NOT return the same 
   * instance.
   * 
   * @return Builder which you can add additional test steps to run in parallel
   */
  @Override
  public ParallelScriptBuilder inParallel() {
    return inSequence().inParallel();
  }
  
  @Override
  public SequentialScriptBuilder inSequence() {
    return new SequentialScriptBuilder(this);
  }
  
  /**
   * Adds a step which will be run in parallel with other steps on this builder.
   * 
   * @param step Test step to be added
   */
  @Override
  public void addStep(ScriptStepInterface step) {
    addStep(step, 1);
  }
  
  @Override
  protected void addStep(ExecutionItem step) {
    verifyValid();
    currentStep.addItem(step);
  }
  
  /**
   * Adds a step which will be run in parallel with other steps on this builder.  This step will 
   * be executed concurrently the number of times provided in this call.
   * 
   * @param step Test step to be added
   * @param times Quantity of times this step should be ran concurrently
   */
  public void addStep(ScriptStepInterface step, int times) {
    verifyValid();
    incrementThreads(times);
    for (int i = 0; i < times; i++) {
      currentStep.addItem(new ScriptStepWrapper(step));
    }
  }
  
  private void incrementThreads(int value) {
    stepsThreadsNeeded += value;
    maybeUpdatedMaximumThreads(stepsThreadsNeeded);
  }
  
  /**
   * Adds a set of sequential steps which will be executed concurrently with other steps added to 
   * this builder.  The set of sequential steps added will be run in the same way the execution 
   * graph attached to them is.  So for example if there are 10 sequential steps in this, even 
   * though those steps may run in parallel with other steps provided to this builder, those 10 
   * steps will still run one after another.
   * 
   * @param sequentialSteps Sequential steps to add to this builder
   */
  @Override
  public void addSteps(SequentialScriptBuilder sequentialSteps) {
    verifyValid();
    incrementThreads(sequentialSteps.getNeededThreadCount());
    currentStep.addItem(new SequentialTestWrapper(sequentialSteps));
  }
  
  /**
   * Adds a set of parallel steps to this builder.  The execution graph for the provided steps 
   * will be maintained.  These parallel steps will be executed in parallel with all other steps 
   * provided to this builder.
   * 
   * @param parallelSteps Parallel steps to add to this builder
   */
  @Override
  public void addSteps(ParallelScriptBuilder parallelSteps) {
    verifyValid();
    incrementThreads(parallelSteps.getNeededThreadCount());
    Iterator<ExecutionItem> it = parallelSteps.currentStep.steps.iterator();
    while (it.hasNext()) {
      currentStep.addItem(it.next());
    }
  }
  
  /**
   * <p>Wrapper for a test step that will execute on an {#link Executor} in parallel to other 
   * steps.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class ScriptStepWrapper extends AbstractScriptStepWrapper {
    public ScriptStepWrapper(ScriptStepInterface scriptStep) {
      super(scriptStep);
    }
    
    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      assistant.executeAsyncIfStillRunning(scriptStepRunner);
    }

    @Override
    public ExecutionItem makeCopy() {
      return new ScriptStepWrapper(scriptStepRunner.scriptStep);
    }
  }
  
  /**
   * <p>Implementation of {@link ExecutionItem} where multiple test steps will be executed in 
   * sequence, while other steps in this build can run concurrently at the same time.</p>
   * 
   * @author jent - Mike Jensen
   */
  private static class SequentialTestWrapper implements ExecutionItem {
    private final SequentialStep sequentialStep;
    private final Collection<? extends SettableListenableFuture<StepResult>> futures;
    
    public SequentialTestWrapper(SequentialScriptBuilder sequentialScript) {
      sequentialScript.verifyValid();
      this.sequentialStep = sequentialScript.currentStep;
      futures = sequentialStep.getFutures();
    }
    
    private SequentialTestWrapper(SequentialStep sequentialStep) {
      this.sequentialStep = sequentialStep;
      futures = sequentialStep.getFutures();
    }
    
    @Override
    public void runChainItem(final ExecutionAssistant assistant) {
      assistant.executeAsyncIfStillRunning(new Runnable() {
        @Override
        public void run() {
          sequentialStep.runChainItem(assistant);
        }
      });
      // no need to block this thread for these to run
    }

    @Override
    public Collection<? extends SettableListenableFuture<StepResult>> getFutures() {
      return futures;
    }

    @Override
    public ExecutionItem makeCopy() {
      return new SequentialTestWrapper(sequentialStep.makeCopy());
    }
  }
  
  /**
   * <p>Collection of steps which will all be farmed off to the executor as fast as possible.</p>
   * 
   * @author jent - Mike Jensen
   */
  // TODO - can this be put into StepCollectionRunner
  protected static class ParallelStep extends StepCollectionRunner {
    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      Iterator<ExecutionItem> it = steps.iterator();
      while (it.hasNext()) {
        it.next().runChainItem(assistant);
      }
    }

    @Override
    public ParallelStep makeCopy() {
      ParallelStep result = new ParallelStep();
      Iterator<ExecutionItem> it = steps.iterator();
      while (it.hasNext()) {
        result.addItem(it.next().makeCopy());
      }
      return result;
    }
  }
}
