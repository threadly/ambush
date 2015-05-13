package org.threadly.load;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;

@SuppressWarnings("javadoc")
public class StepResultCollectionUtilsTest {
  private static final int PROCESSING_TIME_NANOS = 10;
  
  private List<ListenableFuture<StepResult>> passFutures;
  private List<StepResult> failResults;
  private List<ListenableFuture<StepResult>> failFutures;
  private List<ListenableFuture<StepResult>> futures;
  
  @Before
  public void setup() {
    passFutures = new ArrayList<ListenableFuture<StepResult>>(2);
    passFutures.add(FutureUtils.immediateResultFuture(new StepResult("foo", PROCESSING_TIME_NANOS, null)));
    passFutures.add(FutureUtils.immediateResultFuture(new StepResult("foo", PROCESSING_TIME_NANOS, null)));
    
    failResults = new ArrayList<StepResult>(2);
    failResults.add(new StepResult("foo", PROCESSING_TIME_NANOS, new Exception()));
    failResults.add(new StepResult("foo", PROCESSING_TIME_NANOS, new Exception()));
    
    failFutures = new ArrayList<ListenableFuture<StepResult>>(failResults.size());
    Iterator<StepResult> it = failResults.iterator();
    while (it.hasNext()) {
      failFutures.add(FutureUtils.immediateResultFuture(it.next()));
    }
    
    futures = new ArrayList<ListenableFuture<StepResult>>();
    futures.addAll(passFutures);
    futures.addAll(failFutures);
    
    // add canceled and result future mix
    SettableListenableFuture<StepResult> slf1 = new SettableListenableFuture<StepResult>();
    slf1.cancel(false);
    futures.add(slf1);
    futures.add(FutureUtils.immediateResultFuture(new StepResult("foo", PROCESSING_TIME_NANOS, null)));
    StepResult tr1 = new StepResult("foo", PROCESSING_TIME_NANOS, new Exception());
    failResults.add(tr1);
    failFutures.add(FutureUtils.immediateResultFuture(tr1));
    futures.add(failFutures.get(failFutures.size() - 1));
    SettableListenableFuture<StepResult> slf2 = new SettableListenableFuture<StepResult>();
    slf2.cancel(false);
    futures.add(slf2);
    StepResult tr2 = new StepResult("foo", PROCESSING_TIME_NANOS, new Exception());
    failResults.add(tr2);
    failFutures.add(FutureUtils.immediateResultFuture(tr2));
    futures.add(failFutures.get(failFutures.size() - 1));
  }
  
  @After
  public void cleanup() {
    passFutures = null;
    failResults = null;
    failFutures = null;
    futures = null;
  }
  
  @Test
  public void getFailedResultTest() throws InterruptedException {
    assertNull(StepResultCollectionUtils.getFailedResult(passFutures));
    assertTrue(failResults.get(0) == StepResultCollectionUtils.getFailedResult(futures));
  }
  
  @Test
  public void getAllFailedResultsTest() throws InterruptedException {
    assertTrue(StepResultCollectionUtils.getAllFailedResults(passFutures).isEmpty());
    assertEquals(failFutures.size(), StepResultCollectionUtils.getAllFailedResults(futures).size());
    assertTrue(StepResultCollectionUtils.getAllFailedResults(futures).containsAll(failResults));
  }
  
  @Test
    public void getAverageRuntimeTest() throws InterruptedException {
    assertEquals(PROCESSING_TIME_NANOS, 
                 StepResultCollectionUtils.getAverageRuntime(futures, TimeUnit.NANOSECONDS), 0);
  }
  
  @Test
  public void getLongestRuntimeStepPassStepTest() throws InterruptedException {
    StepResult longResult = new StepResult("foo", PROCESSING_TIME_NANOS + 1);
    futures.add(FutureUtils.immediateResultFuture(longResult));
    
    assertTrue(longResult == StepResultCollectionUtils.getLongestRuntimeStep(futures));
  }
  
  @Test
  public void getLongestRuntimeStepFailStepTest() throws InterruptedException {
    StepResult longResult = new StepResult("foo", PROCESSING_TIME_NANOS + 1, new Exception());
    futures.add(FutureUtils.immediateResultFuture(longResult));
    
    assertTrue(longResult == StepResultCollectionUtils.getLongestRuntimeStep(futures));
  }
}
