package org.threadly.load;

import org.threadly.concurrent.future.FutureUtils;
import org.threadly.load.ExecutableScript.ExecutionItem;

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
    if (currentStep.getStepCount() > 0) {
      stepRunners.add(currentStep);
    }
  }
  
  @Override
  public ParallelScriptBuilder makeCopy() {
    ParallelScriptBuilder result = new ParallelScriptBuilder();
    
    for (ExecutionItem item : currentStep.getSteps()) {
      result.addStep(item.makeCopy());
    }
    
    return result;
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
      currentStep.addItem(new ScriptStepRunner(step));
    }
  }
  
  @Override
  protected void addStep(ExecutionItem step) {
    verifyValid();
    currentStep.addItem(step);
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
   * The provided builder can not be modified after being provided.  It also can not be provided 
   * as steps again.  If needing to provide again, please use {@link #makeCopy()}.
   * 
   * @param sequentialSteps Sequential steps to add to this builder
   */
  // TODO - what about previous chain items from the current step provided, this only gets future steps
  @Override
  public void addSteps(SequentialScriptBuilder sequentialSteps) {
    verifyValid();
    sequentialSteps.replaced();
    if (sequentialSteps.currentStep.getStepCount() == 0) {
      return;
    }
    incrementThreads(sequentialSteps.getNeededThreadCount() + 1);
    currentStep.addItem(sequentialSteps.currentStep);
  }
  
  /**
   * Adds a set of parallel steps to this builder.  The execution graph for the provided steps 
   * will be maintained.  These parallel steps will be executed in parallel with all other steps 
   * provided to this builder.
   * 
   * The provided builder can not be modified after being provided.  It also can not be provided 
   * as steps again.  If needing to provide again, please use {@link #makeCopy()}.
   * 
   * @param parallelSteps Parallel steps to add to this builder
   */
  // TODO - what about previous chain items from the current step provided, this only gets future steps
  @Override
  public void addSteps(ParallelScriptBuilder parallelSteps) {
    verifyValid();
    parallelSteps.replaced();
    if (parallelSteps.currentStep.getStepCount() == 0) {
      return;
    }
    incrementThreads(parallelSteps.getNeededThreadCount());
    currentStep.addItems(parallelSteps.currentStep.getSteps());
  }
  
  /**
   * <p>Collection of steps which will all be farmed off to the executor as fast as possible.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class ParallelStep extends StepCollectionRunner {
    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      for (ExecutionItem chainItem : getSteps()) {
        assistant.executeIfStillRunning(chainItem, true);
      }
      // block till all parallel steps finish, or first error
      try {
        if (StepResultCollectionUtils.getFailedResult(getFutures()) != null) {
          FutureUtils.cancelIncompleteFutures(getFutures(), true);
          return;
        }
      } catch (InterruptedException e) {
        // let thread exit
        return;
      }
    }

    @Override
    public ParallelStep makeCopy() {
      ParallelStep result = new ParallelStep();
      for (ExecutionItem step : getSteps()) {
        ExecutionItem ei = step.makeCopy();
        if (ei != null) {
          result.addItem(ei);
        }
      }
      return result;
    }

    @Override
    public ChildItems getChildItems() {
      return new ChildItemContainer(getSteps(), false);
    }
  }
}
