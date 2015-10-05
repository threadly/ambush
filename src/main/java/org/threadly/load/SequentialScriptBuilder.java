package org.threadly.load;

import java.util.concurrent.ExecutionException;

import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.util.ExceptionUtils;

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
    if (currentStep.getStepCount() > 0) {
      stepRunners.add(currentStep);
    }
  }
  
  @Override
  public SequentialScriptBuilder makeCopy() {
    SequentialScriptBuilder result = new SequentialScriptBuilder();
    
    for (ExecutionItem item : currentStep.getSteps()) {
      result.addStep(item.makeCopy());
    }
    
    return result;
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
    maybeUpdatedMaximumThreads(sequentialSteps.getNeededThreadCount());
    currentStep.addItems(sequentialSteps.currentStep.getSteps());
  }
  
  /**
   * Adds a set of steps which will run in parallel.  The execution graph of the provided steps 
   * will be maintained.  So the provided parallel steps will run in parallel as built, and 
   * future steps added to this builder will not be executed till this provided builder has 
   * finished running.
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
    maybeUpdatedMaximumThreads(parallelSteps.getNeededThreadCount() + 1);
    addStep(parallelSteps.currentStep);
  }
  
  /**
   * <p>Collection of steps which will all be executed on this thread, one after another.</p>
   * 
   * @author jent - Mike Jensen
   */
  protected static class SequentialStep extends StepCollectionRunner {
    @Override
    public void runChainItem(ExecutionAssistant assistant) {
      for (ExecutionItem chainItem : getSteps()) {
        if (chainItem.manipulatesExecutionAssistant()) {
          assistant = assistant.makeCopy();
        }
        final ExecutionItem fChainItem = chainItem;
        ListenableFuture<?> f = assistant.executeIfStillRunning(chainItem, false);
        f.addListener(new Runnable() {
          @Override
          public void run() {
            FutureUtils.cancelIncompleteFutures(fChainItem.getFutures(), true);
          }
        });
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
