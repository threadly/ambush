package org.threadly.load;

import java.util.concurrent.ExecutionException;

import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.ExecutableScript.ExecutionItem.StepStartHandler;
import org.threadly.util.ExceptionUtils;

/**
 * A builder which's added steps will all be executed in sequence.  Typically this is constructed 
 * with {@link #SequentialScriptBuilder()}, and then steps are added using 
 * {@link #addStep(ScriptStep)}.  You can also add other builders, for example to add a step which 
 * is compromised of a bunch of parallel steps you can pass in an already constructed 
 * {@link ParallelScriptBuilder} to {@link #addSteps(ParallelScriptBuilder)}.
 */
public class SequentialScriptBuilder extends AbstractScriptBuilder {
  protected final SequentialStep currentStep;

  /**
   * Constructs a new {@link SequentialScriptBuilder}.  This can be used as either the start of a 
   * execution chain, or can later be provided into 
   * {@link AbstractScriptBuilder#addSteps(SequentialScriptBuilder)}.
   */
  public SequentialScriptBuilder() {
    currentStep = new SequentialStep();
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
  public SequentialScriptBuilder makeCopy() {
    SequentialScriptBuilder result = new SequentialScriptBuilder();
    
    for (ExecutionItem item : currentStep.getSteps()) {
      result.addStep(item.makeCopy());
    }
    
    return result;
  }
  
  @Override
  public int getMaximumNeededThreadCount() {
    // we must add one for the thread that is controlling the sequential execution
    return super.getMaximumNeededThreadCount() + 1;
  }
  
  /**
   * Adds a step to be run sequentially.  This step will be ran after previously added steps have 
   * completed.
   * 
   * @param step Test step to be added
   */
  @Override
  public void addStep(ScriptStep step) {
    addStep(new ScriptStepRunner(step));
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
    maybeUpdatedMaximumThreads(sequentialSteps.getMaximumNeededThreadCount());
    currentStep.addItems(sequentialSteps.currentStep.getSteps());
  }
  
  /**
   * Adds a set of steps which will run in parallel.  The execution graph of the provided steps 
   * will be maintained.  So the provided parallel steps will run in parallel as built, and 
   * future steps added to this builder will not be executed till this provided builder has 
   * finished running.
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
    maybeUpdatedMaximumThreads(parallelSteps.getMaximumNeededThreadCount());
    addStep(parallelSteps.currentStep);
  }
  
  /**
   * Collection of steps which will all be executed on this thread, one after another.
   */
  protected static class SequentialStep extends StepCollectionRunner {
    @Override
    protected void runItem(ExecutionAssistant assistant) {
      for (ExecutionItem chainItem : getSteps()) {
        if (chainItem.manipulatesExecutionAssistant()) {
          assistant = assistant.makeCopy();
        }
        ListenableFuture<?> f = assistant.executeIfStillRunning(chainItem, false);
        f.listener(new ExecutionItemCompletionRunner(chainItem));
        // block till execution is done (and also allow thread to do execution)
        try {
          f.get();
        } catch (InterruptedException e) {
          // reset status and let thread exit
          Thread.currentThread().interrupt();
          return;
        } catch (ExecutionException e) {
          throw ExceptionUtils.makeRuntime(e.getCause());
        }
        // block till all child executions finish, thus making us wait to run the next chain item
        try {
          if (StepResultCollectionUtils.getFailedResult(chainItem.getFutures()) != null) {
            // failure occurred, cancel other steps
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
      for (ExecutionItem chainItem : getSteps()) {
        ExecutionItem copy = chainItem.makeCopy();
        if (copy != null) {
          result.addItem(copy);
        }
      }
      return result;
    }

    @Override
    public ChildItems getChildItems() {
      return new ChildItemContainer(getSteps(), true);
    }
  }
}
