package org.threadly.load;

import static org.junit.Assert.*;
import static org.threadly.load.AmbushTestUtils.*;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class SimpleExecutionGraphTest {
  @Test
  public void inSequenceOnlyTest() throws InterruptedException, ExecutionException {
    String identifier1 = StringUtils.makeRandomString(5);
    String identifier2 = StringUtils.makeRandomString(5);
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
    String identifier = StringUtils.makeRandomString(5);
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
  
  @Test
  public void inSequenceSectionsOfParallelTest() throws InterruptedException, TimeoutException, ExecutionException {
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

    assertEquals(TEST_COMPLEXITY, builder.getNeededThreadCount());
    List<? extends ListenableFuture<StepResult>> futures = builder.build().startScript();
    assertEquals(TEST_COMPLEXITY * 2, futures.size());
    
    FutureUtils.blockTillAllCompleteOrFirstError(futures, 10 * 1000);
  }
  
  @Test
  public void inSequenceSectionsOfParallelByAddingBuildersTest() throws InterruptedException, TimeoutException, ExecutionException {
    final List<TestStep> parallelSteps1 = makeTestSteps(null, TEST_COMPLEXITY);
    SequentialScriptBuilder sBuilder = new SequentialScriptBuilder();
    ParallelScriptBuilder pBuilder1 = new ParallelScriptBuilder();
    addSteps(parallelSteps1, pBuilder1);
    ParallelScriptBuilder pBuilder2 = new ParallelScriptBuilder();
    List<TestStep> parallelSteps2 = makeTestSteps(new Runnable() {
      @Override
      public void run() {
        Iterator<TestStep> it = parallelSteps1.iterator();
        while (it.hasNext()) {
          assertEquals(1, it.next().getRunCount());
        }
      }
    }, TEST_COMPLEXITY);
    addSteps(parallelSteps2, pBuilder2);
    
    sBuilder.addSteps(pBuilder1);
    sBuilder.addSteps(pBuilder2);

    assertEquals(TEST_COMPLEXITY + 1, sBuilder.getNeededThreadCount());
    List<? extends ListenableFuture<StepResult>> futures = sBuilder.build().startScript();
    assertEquals(TEST_COMPLEXITY * 2, futures.size());
    
    FutureUtils.blockTillAllCompleteOrFirstError(futures, 10 * 1000);
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
  
  @Test
  public void inParallelSequenceChainsTest() throws InterruptedException, TimeoutException {
    final List<TestStep> steps1 = makeTestSteps(null, TEST_COMPLEXITY);
    final List<TestStep> steps2 = makeTestSteps(null, TEST_COMPLEXITY);
    ParallelScriptBuilder pBuilder = new ParallelScriptBuilder();
    SequentialScriptBuilder sBuilder1 = new SequentialScriptBuilder();
    addSteps(steps1, sBuilder1);
    SequentialScriptBuilder sBuilder2 = new SequentialScriptBuilder();
    addSteps(steps2, sBuilder2);
    pBuilder.addSteps(sBuilder1);
    pBuilder.addSteps(sBuilder2);
    
    assertEquals(4, pBuilder.getNeededThreadCount());
    List<? extends ListenableFuture<StepResult>> futures = pBuilder.build().startScript();
    assertEquals(TEST_COMPLEXITY * 2, futures.size());
    
    FutureUtils.blockTillAllComplete(futures, 10 * 1000);
  }
}
