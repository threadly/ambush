package org.threadly.load;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class SimpleExecutionGraphTest {
  private static final int TEST_COMPLEXITY = 10;
  
  @Test
  public void inSequenceOnlyTest() throws InterruptedException, ExecutionException {
    String identifier1 = StringUtils.randomString(5);
    String identifier2 = StringUtils.randomString(5);
    int runTime = 2;
    final TestStep step1 = new TestStep(identifier1, runTime);
    TestStep step2 = new TestStep(identifier2, runTime) {
      @Override
      public void handleRunStart() {
        assertEquals(1, step1.getRunCount());
      }
    };
    SequentialScriptBuilder builder = new SequentialScriptBuilder();
    builder.addStep(step1);
    builder.addStep(step2);
    
    List<? extends ListenableFuture<StepResult>> futures = builder.build().startScript();
    assertEquals(2, futures.size());
    
    StepResult tr1 = futures.get(0).get();
    assertNull(tr1.getError());
    assertEquals(identifier1, tr1.getDescription());
    assertTrue(tr1.getRunTime(TimeUnit.MILLISECONDS) >= runTime);
    
    StepResult tr2 = futures.get(0).get();
    assertNull(tr2.getError());
    assertEquals(identifier1, tr2.getDescription());
    assertTrue(tr1.getRunTime(TimeUnit.MILLISECONDS) >= runTime);
  }
  
  @Test
  public void inSequenceOnlyWithFailureTest() throws InterruptedException, ExecutionException {
    int failreRunTime = 2;
    SequentialScriptBuilder builder = new SequentialScriptBuilder();
    List<TestStep> steps1 = makeTestSteps(null, TEST_COMPLEXITY);
    addSteps(steps1, builder);
    
    builder.addStep(new FailureTestStep(failreRunTime));
    
    List<TestStep> steps2 = makeTestSteps(null, TEST_COMPLEXITY);
    addSteps(steps2, builder);
    
    List<? extends ListenableFuture<StepResult>> futures = builder.build().startScript();
    assertEquals(TEST_COMPLEXITY * 2 + 1, futures.size());
    
    for (int i = 0; i < futures.size(); i++) {
      if (i < steps1.size()) {
        assertNull(futures.get(i).get().getError());
      } else if (i == steps1.size()) {
        StepResult tr = futures.get(i).get();
        assertNotNull(tr.getError());
        assertTrue(tr.getRunTime(TimeUnit.MILLISECONDS) >= failreRunTime);
      } else {
        try {
          futures.get(i).get();
          fail("Exception should have thrown");
        } catch (CancellationException e) {
          // expected
        }
      }
    }
  }
  
  @Test
  public void inParallelOnlyTest() throws InterruptedException, ExecutionException {
    String identifier = StringUtils.randomString(5);
    int runTime = 2;
    int runCount = 10;
    TestStep step = new TestStep(identifier, runTime);
    ParallelScriptBuilder builder = new ParallelScriptBuilder();
    builder.addStep(step, runCount);
    
    List<? extends ListenableFuture<StepResult>> futures = builder.build().startScript();
    assertEquals(runCount, futures.size());
    
    StepResult tr = futures.get(0).get();
    
    assertNull(tr.getError());
    assertEquals(identifier, tr.getDescription());
    assertTrue(tr.getRunTime(TimeUnit.MILLISECONDS) >= runTime);
  }
  
  private static List<TestStep> makeTestSteps(final Runnable startRunnable, int count) {
    List<TestStep> result = new ArrayList<TestStep>(count);
    for (int i = 0; i < count; i++) {
      result.add(new TestStep() {
        @Override
        public void handleRunStart() {
          if (startRunnable != null) {
            startRunnable.run();
          }
        }
      });
    }
    
    return result;
  }
  
  private static void addSteps(List<TestStep> steps, AbstractScriptBuilder builder) {
    Iterator<TestStep> it = steps.iterator();
    while (it.hasNext()) {
      builder.addStep(it.next());
    }
  }
  
  @Test
  public void inSequenceSectionsOfParallelTest() throws InterruptedException {
    final List<TestStep> parallelSteps1 = makeTestSteps(null, TEST_COMPLEXITY);
    ParallelScriptBuilder builder = new ParallelScriptBuilder();
    addSteps(parallelSteps1, builder);
    builder = builder.inParallel();
    List<TestStep> parallelSteps2 = makeTestSteps(new Runnable() {
      @Override
      public void run() {
        Iterator<TestStep> it = parallelSteps1.iterator();
        while (it.hasNext()) {
          assertEquals(1, it.next().getRunCount());
        }
      }
    }, TEST_COMPLEXITY);
    addSteps(parallelSteps2, builder);
    
    List<? extends ListenableFuture<StepResult>> futures = builder.build().startScript();
    assertEquals(TEST_COMPLEXITY * 2, futures.size());
    
    FutureUtils.blockTillAllComplete(futures);
  }
  
  @Test
  public void inSequenceSectionsOfParallelWithFailureTest() throws InterruptedException, ExecutionException {
    final List<TestStep> parallelSteps1 = makeTestSteps(null, TEST_COMPLEXITY);
    AbstractScriptBuilder builder = new ParallelScriptBuilder();
    addSteps(parallelSteps1, builder);
    
    builder = builder.inSequence();
    builder.addStep(new FailureTestStep());
    builder = builder.inParallel();
    
    List<TestStep> parallelSteps2 = makeTestSteps(new Runnable() {
      @Override
      public void run() {
        Iterator<TestStep> it = parallelSteps1.iterator();
        while (it.hasNext()) {
          assertEquals(1, it.next().getRunCount());
        }
      }
    }, TEST_COMPLEXITY);
    addSteps(parallelSteps2, builder);
    
    List<? extends ListenableFuture<StepResult>> futures = builder.build().startScript();
    assertEquals(TEST_COMPLEXITY * 2 + 1, futures.size());
    
    for (int i = 0; i < futures.size(); i++) {
      if (i < parallelSteps1.size()) {
        assertNull(futures.get(i).get().getError());
      } else if (i == parallelSteps1.size()) {
        StepResult tr = futures.get(i).get();
        assertNotNull(tr.getError());
      } else {
        try {
          futures.get(i).get();
          fail("Exception should have thrown");
        } catch (CancellationException e) {
          // expected
        }
      }
    }
  }
}
