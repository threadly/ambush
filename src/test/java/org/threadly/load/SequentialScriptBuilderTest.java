package org.threadly.load;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.future.ListenableFuture;

@SuppressWarnings("javadoc")
public class SequentialScriptBuilderTest {
  private SequentialScriptBuilder sBuilder;
  
  @Before
  public void setup() {
    sBuilder = new SequentialScriptBuilder();
  }
  
  @After
  public void cleanup() {
    sBuilder = null;
  }
  
  @Test
  public void inSequenceTest() {
    assertTrue(sBuilder == sBuilder.inSequence());
  }
  
  @Test
  public void addParallelStepsTest() throws InterruptedException {
    ParallelScriptBuilder pBuilder = new ParallelScriptBuilder();
    pBuilder.addStep(new TestStep(), 10);
    final Collection<? extends ListenableFuture<?>> futures = pBuilder.currentStep.getFutures();
    assertFalse(futures.isEmpty());
    
    sBuilder.addStep(new TestStep());
    sBuilder.addStep(new TestStep());
    sBuilder.addSteps(pBuilder);
    sBuilder.addStep(new TestStep() {
      @Override
      public void handleRunStart() {
        Iterator<? extends ListenableFuture<?>> it = futures.iterator();
        while (it.hasNext()) {
          assertTrue(it.next().isDone());
        }
      }
    });
    
    assertNull(TestResultCollectionUtils.getFailedResult(sBuilder.startScript()));
  }
  
  @Test
  public void addSequentialStepsTest() throws InterruptedException {
    int stepCountPerBuilder = 10;
    ParallelScriptBuilder secondBuilder = new ParallelScriptBuilder();
    final AtomicReference<Collection<ListenableFuture<?>>> firstHalfFutures;
    firstHalfFutures = new AtomicReference<Collection<ListenableFuture<?>>>();
    for (int i = 0; i < stepCountPerBuilder; i++) {
      sBuilder.addStep(new TestStep());
      if (i == 0) {
        secondBuilder.addStep(new TestStep() {
          @Override
          public void handleRunStart() {
            Iterator<ListenableFuture<?>> it = firstHalfFutures.get().iterator();
            while (it.hasNext()) {
              assertTrue(it.next().isDone());
            }
          }
        });
      } else {
        secondBuilder.addStep(new TestStep());
      }
    }
    
    firstHalfFutures.set(new ArrayList<ListenableFuture<?>>(sBuilder.currentStep.getFutures()));
    sBuilder.addSteps(secondBuilder);
    
    Collection<? extends ListenableFuture<TestResult>> allFutures = sBuilder.startScript();
    assertEquals(stepCountPerBuilder * 2, allFutures.size());
    assertNull(TestResultCollectionUtils.getFailedResult(allFutures));
  }
}
