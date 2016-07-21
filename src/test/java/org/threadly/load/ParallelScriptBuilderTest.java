package org.threadly.load;

import static org.junit.Assert.*;
import static org.threadly.load.AmbushTestUtils.*;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;

@SuppressWarnings("javadoc")
public class ParallelScriptBuilderTest {
  @Test
  public void hasStepsSimpleStepTest() {
    ParallelScriptBuilder pBuilder = new ParallelScriptBuilder();
    assertFalse(pBuilder.hasSteps());
    pBuilder.addStep(new TestStep());
    assertTrue(pBuilder.hasSteps());
  }
  
  @Test
  public void hasStepsBuilderAddedTest() {
    ParallelScriptBuilder pBuilder = new ParallelScriptBuilder();
    assertFalse(pBuilder.hasSteps());
    pBuilder.addSteps(new ParallelScriptBuilder());
    assertFalse(pBuilder.hasSteps());
    ParallelScriptBuilder secondBuilder = new ParallelScriptBuilder();
    secondBuilder.addStep(new TestStep());
    pBuilder.addSteps(secondBuilder);
    assertTrue(pBuilder.hasSteps());
  }
  
  @Test
  public void addParallelStepsTest() throws InterruptedException, TimeoutException, ExecutionException {
    ParallelScriptBuilder pBuilder = new ParallelScriptBuilder();
    addSteps(makeTestSteps(null, TEST_COMPLEXITY), pBuilder);
    ParallelScriptBuilder pBuilder2 = new ParallelScriptBuilder();
    addSteps(makeTestSteps(null, TEST_COMPLEXITY), pBuilder2);
    
    pBuilder.addSteps(pBuilder2);
    
    assertEquals(TEST_COMPLEXITY * 2, pBuilder.getMaximumNeededThreadCount());
    List<? extends ListenableFuture<StepResult>> futures = pBuilder.build().startScript();
    assertEquals(TEST_COMPLEXITY * 2, futures.size());
    
    FutureUtils.blockTillAllCompleteOrFirstError(futures, 10 * 1000);
  }
  
  @Test
  public void addSequentialStepsTest() throws InterruptedException, TimeoutException, ExecutionException {
    ParallelScriptBuilder pBuilder = new ParallelScriptBuilder();
    addSteps(makeTestSteps(null, TEST_COMPLEXITY), pBuilder);
    SequentialScriptBuilder sBuilder = new SequentialScriptBuilder();
    addSteps(makeTestSteps(null, TEST_COMPLEXITY), sBuilder);
    
    pBuilder.addSteps(sBuilder);
    
    assertEquals(TEST_COMPLEXITY + 2, pBuilder.getMaximumNeededThreadCount());
    List<? extends ListenableFuture<StepResult>> futures = pBuilder.build().startScript();
    assertEquals(TEST_COMPLEXITY * 2, futures.size());
    
    FutureUtils.blockTillAllCompleteOrFirstError(futures, 10 * 1000);
  }
}
