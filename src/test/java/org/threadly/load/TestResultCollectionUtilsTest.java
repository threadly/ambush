package org.threadly.load;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;

@SuppressWarnings("javadoc")
public class TestResultCollectionUtilsTest {
  private static final int PROCESSING_TIME_NANOS = 10;
  
  private List<ListenableFuture<TestResult>> passFutures;
  private List<TestResult> failResults;
  private List<ListenableFuture<TestResult>> failFutures;
  private List<ListenableFuture<TestResult>> futures;
  
  @Before
  public void setup() {
    passFutures = new ArrayList<ListenableFuture<TestResult>>(2);
    passFutures.add(FutureUtils.immediateResultFuture(new TestResult("foo", PROCESSING_TIME_NANOS, null)));
    passFutures.add(FutureUtils.immediateResultFuture(new TestResult("foo", PROCESSING_TIME_NANOS, null)));
    
    failResults = new ArrayList<TestResult>(2);
    failResults.add(new TestResult("foo", PROCESSING_TIME_NANOS, new Exception()));
    failResults.add(new TestResult("foo", PROCESSING_TIME_NANOS, new Exception()));
    
    failFutures = new ArrayList<ListenableFuture<TestResult>>(failResults.size());
    Iterator<TestResult> it = failResults.iterator();
    while (it.hasNext()) {
      failFutures.add(FutureUtils.immediateResultFuture(it.next()));
    }
    
    futures = new ArrayList<ListenableFuture<TestResult>>();
    futures.addAll(passFutures);
    futures.addAll(failFutures);
    
    // add canceled and result future mix
    SettableListenableFuture<TestResult> slf1 = new SettableListenableFuture<TestResult>();
    slf1.cancel(false);
    futures.add(slf1);
    futures.add(FutureUtils.immediateResultFuture(new TestResult("foo", PROCESSING_TIME_NANOS, null)));
    TestResult tr1 = new TestResult("foo", PROCESSING_TIME_NANOS, new Exception());
    failResults.add(tr1);
    failFutures.add(FutureUtils.immediateResultFuture(tr1));
    futures.add(failFutures.get(failFutures.size() - 1));
    SettableListenableFuture<TestResult> slf2 = new SettableListenableFuture<TestResult>();
    slf2.cancel(false);
    futures.add(slf2);
    TestResult tr2 = new TestResult("foo", PROCESSING_TIME_NANOS, new Exception());
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
    assertNull(TestResultCollectionUtils.getFailedResult(passFutures));
    assertTrue(failResults.get(0) == TestResultCollectionUtils.getFailedResult(futures));
  }
  
  @Test
  public void getAllFailedResultsTest() throws InterruptedException {
    assertTrue(TestResultCollectionUtils.getAllFailedResults(passFutures).isEmpty());
    assertEquals(failFutures.size(), TestResultCollectionUtils.getAllFailedResults(futures).size());
    assertTrue(TestResultCollectionUtils.getAllFailedResults(futures).containsAll(failResults));
  }
  
  @Test
  public void getAverageRuntimeNanosTest() throws InterruptedException {
    assertEquals(PROCESSING_TIME_NANOS, TestResultCollectionUtils.getAverageRuntimeNanos(futures), 0);
  }
}
