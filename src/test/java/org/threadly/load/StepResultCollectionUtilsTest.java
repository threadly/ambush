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
  
  private List<ListenableFuture<PassStepResult>> passFutures;
  private List<ErrorStepResult> failResults;
  private List<ListenableFuture<ErrorStepResult>> failFutures;
  private List<ListenableFuture<? extends StepResult>> futures;
  
  @Before
  public void setup() {
    passFutures = new ArrayList<ListenableFuture<PassStepResult>>(2);
    passFutures.add(FutureUtils.immediateResultFuture(new PassStepResult("foo", PROCESSING_TIME_NANOS)));
    passFutures.add(FutureUtils.immediateResultFuture(new PassStepResult("foo", PROCESSING_TIME_NANOS)));
    
    failResults = new ArrayList<ErrorStepResult>(2);
    failResults.add(new ErrorStepResult("foo", PROCESSING_TIME_NANOS, new Exception()));
    failResults.add(new ErrorStepResult("foo", PROCESSING_TIME_NANOS, new Exception()));
    
    failFutures = new ArrayList<ListenableFuture<ErrorStepResult>>(failResults.size());
    Iterator<ErrorStepResult> it = failResults.iterator();
    while (it.hasNext()) {
      failFutures.add(FutureUtils.immediateResultFuture(it.next()));
    }
    
    futures = new ArrayList<ListenableFuture<? extends StepResult>>();
    futures.addAll(passFutures);
    futures.addAll(failFutures);
    
    // add canceled and result future mix
    SettableListenableFuture<StepResult> slf1 = new SettableListenableFuture<StepResult>();
    slf1.cancel(false);
    futures.add(slf1);
    futures.add(FutureUtils.immediateResultFuture(new PassStepResult("foo", PROCESSING_TIME_NANOS)));
    ErrorStepResult tr1 = new ErrorStepResult("foo", PROCESSING_TIME_NANOS, new Exception());
    failResults.add(tr1);
    failFutures.add(FutureUtils.immediateResultFuture(tr1));
    futures.add(failFutures.get(failFutures.size() - 1));
    SettableListenableFuture<StepResult> slf2 = new SettableListenableFuture<StepResult>();
    slf2.cancel(false);
    futures.add(slf2);
    ErrorStepResult tr2 = new ErrorStepResult("foo", PROCESSING_TIME_NANOS, new Exception());
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
                 StepResultCollectionUtils.getRunTimeAverage(futures, TimeUnit.NANOSECONDS), 0);
  }
  
  // TODO - add unit tests for percentiles
  
  @Test
  public void getLongestRuntimeStepPassStepTest() throws InterruptedException {
    StepResult longResult = new PassStepResult("foo", PROCESSING_TIME_NANOS + 1);
    futures.add(FutureUtils.immediateResultFuture(longResult));
    
    assertTrue(longResult == StepResultCollectionUtils.getRunTimePercentiles(futures, 100).get(100.));
  }
  
  @Test
  public void getLongestRuntimeStepFailStepTest() throws InterruptedException {
    StepResult longResult = new ErrorStepResult("foo", PROCESSING_TIME_NANOS + 1, new Exception());
    futures.add(FutureUtils.immediateResultFuture(longResult));
    
    assertTrue(longResult == StepResultCollectionUtils.getRunTimePercentiles(futures, 100).get(100.));
  }
}
