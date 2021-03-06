package org.threadly.load;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.threadly.concurrent.future.FutureUtils;
import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.ExecutableScript.ExecutionItem.StepStartHandler;
import org.threadly.util.Clock;

/**
 * A builder which's added steps will all be executed in parallel.  Typically this is constructed 
 * with {@link #ParallelScriptBuilder()}, and then steps are added using 
 * {@link #addStep(ScriptStep)}.  You can also add other builders, please see 
 * {@link #addSteps(SequentialScriptBuilder)}.
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
    currentStep = new ParallelStep();
  }
  
  @Override
  public boolean hasSteps() {
    return currentStep.getStepCount() > 0;
  }

  @Override
  protected ExecutionItem getStepAsExecutionItem() {
    return currentStep;
  }

  @Override
  protected void setStartHandlerOnAllSteps(StepStartHandler startHandler) {
    setStartHandler(currentStep.getChildItems(), startHandler);
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
   * Adds a step which will be run in parallel with other steps on this builder.
   * 
   * @param step Test step to be added
   */
  @Override
  public void addStep(ScriptStep step) {
    addStep(step, 1);
  }
  
  /**
   * Adds a step which will be run in parallel with other steps on this builder.  This step will 
   * be executed concurrently the number of times provided in this call.
   * 
   * @param step Test step to be added
   * @param times Quantity of times this step should be ran concurrently
   */
  public void addStep(ScriptStep step, int times) {
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
   * <p>
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
    if (! sequentialSteps.hasSteps()) {
      return;
    }
    incrementThreads(sequentialSteps.getMaximumNeededThreadCount());
    currentStep.addItem(sequentialSteps.currentStep);
  }
  
  /**
   * Adds a set of parallel steps to this builder.  The execution graph for the provided steps 
   * will be maintained.  These parallel steps will be executed in parallel with all other steps 
   * provided to this builder.
   * <p>
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
    if (! parallelSteps.hasSteps()) {
      return;
    }
    incrementThreads(parallelSteps.getMaximumNeededThreadCount());
    currentStep.addItems(parallelSteps.currentStep.getSteps());
  }
  
  /**
   * Collection of steps which will all be farmed off to the executor as fast as possible.
   */
  protected static class ParallelStep extends StepCollectionRunner {
    @Override
    protected void runItem(ExecutionAssistant assistant) {
      for (ExecutionItem chainItem : getSteps()) {
        assistant.executeIfStillRunning(chainItem, true)
                 .listener(new ExecutionItemCompletionRunner(chainItem));
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
    public void prepareForRun() {
      // shuffle steps so that the execution order is not biased
      ExecutionItem[] steps = getSteps();
      List<ExecutionItem> shuffledList = Arrays.asList(steps);
      Collections.shuffle(shuffledList, new Random(Clock.accurateTimeNanos()));
      setSteps(shuffledList.toArray(steps));
      
      super.prepareForRun();
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
