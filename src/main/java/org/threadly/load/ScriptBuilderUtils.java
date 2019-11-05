package org.threadly.load;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.ExecutableScript.ExecutionItem.ChildItems;
import org.threadly.load.ExecutableScript.ExecutionItem.ExecutionAssistant;
import org.threadly.load.ExecutableScript.ExecutionItem.StepStartHandler;
import org.threadly.util.Pair;

/**
 * Class for helping in construct more sophisticated test and graph structures.
 */
public class ScriptBuilderUtils {
  /**
   * Balance multiple builder chains to attempt to have them complete at a similar point in time.  
   * This attempts to do such by looking at the longest step chain provided, and then make all the 
   * smaller step chains introduce pauses, progressing only as the longest chain progresses.  This 
   * does not mean that the chains will absolutely complete at the same time, depending on the 
   * variations in step invocation time, there will be variations on chain completion time.  
   * <p>
   * For example if a chain has few steps, but long execution times, this will do nothing to 
   * ensure a consistent rate.  
   * <p>
   * This can be very useful if your trying to reproduce production like traffic.  For example you 
   * could create a parallel chain for each API endpoint.  You can have the count of each script 
   * builder (and thus each endpoint) in proportion based off your production usage.  This will 
   * then be reproducing these calls in proportion, rather than just let the short chains end fast.  
   * <p>
   * An alternate method to produce a similar result, that may be more suited for same cases (for 
   * example when step invocation time varies dramatically), would be to allow all chains to 
   * execute freely, timing how long each chain takes.  Then apply a rate limit on chains which 
   * completed too fast via {@link AbstractScriptBuilder#setMaxScriptStepRate(double)} so that the 
   * faster chains end up completing at the same time as the longest chain.  This would have less 
   * overhead, but requires more manual effort, and also wont be able to balance the chains based 
   * off real time delays (which may be unexpected).
   * 
   * @param builders Builders to inspect and apply flow control to attempt similar execution durations
   * @return A parallel script builder to execute the provided, and now replaced script builders
   */
  public static ParallelScriptBuilder balanceBuilders(AbstractScriptBuilder ... builders) {
    if (builders.length == 0) {
      return new ParallelScriptBuilder();
    } else if (builders.length == 1) {
      if (builders[0] instanceof ParallelScriptBuilder) {
        return (ParallelScriptBuilder)builders[0];
      } else {
        ParallelScriptBuilder result = new ParallelScriptBuilder();
        result.addSteps(builders[0]);
        return result;
      }
    }

    int largestBuilderCount = -1;
    AbstractScriptBuilder largestBuilder = null;
    List<Pair<AbstractScriptBuilder, Integer>> flowControlledBuilders = 
        new ArrayList<Pair<AbstractScriptBuilder, Integer>>(builders.length - 1);
    for (AbstractScriptBuilder builder : builders) {
      int builderCount = countScriptSteps(builder.getStepAsExecutionItem().getChildItems());
      if (builderCount == 0) {
        continue;
      }
      if (builderCount > largestBuilderCount) {
        if (largestBuilder != null) {
          flowControlledBuilders.add(new Pair<AbstractScriptBuilder, Integer>(largestBuilder, 
                                                                              largestBuilderCount));
        }
        largestBuilderCount = builderCount;
        largestBuilder = builder;
      } else {
        flowControlledBuilders.add(new Pair<AbstractScriptBuilder, Integer>(builder, builderCount));
      }
    }
    
    if (! flowControlledBuilders.isEmpty()) {
      RunSignalAcceptor[] signalAcceptors = new RunSignalAcceptor[flowControlledBuilders.size()];
      for (int i = 0; i < flowControlledBuilders.size(); i++) {
        Pair<AbstractScriptBuilder, Integer> fcBuilder = flowControlledBuilders.get(i);
        // integer division is necessary to ensure execution
        signalAcceptors[i] = new RunSignalAcceptor(largestBuilderCount / fcBuilder.getRight());
        fcBuilder.getLeft().setStartHandlerOnAllSteps(signalAcceptors[i]);
      }
      
      largestBuilder.setStartHandlerOnAllSteps(new RunSignalSender(signalAcceptors));
    }
    
    ParallelScriptBuilder result = new ParallelScriptBuilder();
    for (AbstractScriptBuilder builder : builders) {
      result.addSteps(builder);
    }
    return result;
  }

  // TODO - should this be moved?
  /**
   * Count how many actual child steps exist in a chain (does not include synthetic steps for 
   * graph structure).
   * 
   * @param items Items to start traversal from
   * @return Total count of script items
   */
  private static int countScriptSteps(ChildItems items) {
    int count = 0;
    for (ExecutionItem item : items) {
      if (item.isChainExecutor()) {
        count += countScriptSteps(item.getChildItems());
      } else {
        count++;
      }
    }
    return count;
  }
  
  /**
   * Signal acceptor that {@link RunSignalSender} can invoke into.  This blocks execution 
   * from happening until enough signals have been accumulated.
   */
  private static class RunSignalAcceptor implements StepStartHandler {
    private final int neededSignalCountPerStep;
    private final AtomicBoolean registeredForFailures;
    private final Semaphore runSemaphore;
    
    public RunSignalAcceptor(int neededSignalCountPerStep) {
      this.neededSignalCountPerStep = neededSignalCountPerStep;
      registeredForFailures = new AtomicBoolean(false);
      runSemaphore = new Semaphore(neededSignalCountPerStep / 2, true);  // half a permit for free to stagger
    }
    
    public void handleRunSignal() {
      runSemaphore.release();
    }

    @Override
    public void readyToRun(ExecutionItem step, ExecutionAssistant assistant) {
      if (! registeredForFailures.get() && registeredForFailures.compareAndSet(false, true)) {
        // register for failure so that we can unblock any waiting steps
        assistant.registerFailureNotification(new Runnable() {
          @Override
          public void run() {
            try {
              while (true) {
                runSemaphore.release(Short.MAX_VALUE / 2);
              }
            } catch (Throwable t) {
              // swallowed, release till error is thrown
            }
          }
        });
      }
      try {
        /* TODO - I wish we could do this without blocking.  The trick here is that if we don't 
         * block we must somehow ensure the future returned from 
         * ExecutableScript.ScriptAssistant#executeIfStillRunning does not complete until we allow 
         * execution here (and complete execution of course).
         */
        runSemaphore.acquire(neededSignalCountPerStep);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      
      step.setStartHandler(null); // unset ourself so that execution can happen naturally
      step.itemReadyForExecution(assistant);
    }
  }
  
  /**
   * Signal sender into {@link RunSignalAcceptor} to indicate that a step has started 
   * execution.  This is used for flow control of steps dependent on {@link RunSignalAcceptor}.
   */
  private static class RunSignalSender implements StepStartHandler {
    private final RunSignalAcceptor[] signalAcceptors;
    
    public RunSignalSender(RunSignalAcceptor[] signalAcceptors) {
      this.signalAcceptors = signalAcceptors;
    }

    @Override
    public void readyToRun(ExecutionItem step, ExecutionAssistant assistant) {
      for (RunSignalAcceptor rsa : signalAcceptors) {
        rsa.handleRunSignal();
      }
      
      step.setStartHandler(null); // unset ourself so that execution can happen naturally
      step.itemReadyForExecution(assistant);
    }
  }
}
