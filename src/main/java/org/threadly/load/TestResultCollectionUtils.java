package org.threadly.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * <p>Some utilities for when dealing with a collection of {@link TestResult}'s.  One of the most 
 * notable is {@link #getFailedResult(Collection)} which is an easy way to know if a set of test 
 * steps completed without error.</p>
 * 
 * @author jent - Mike Jensen
 */
public class TestResultCollectionUtils {
  /**
   * Looks through collection of futures looking for any {@link TestResult}'s that are in a failure 
   * state.  Once it finds the first one it returns it.  If this was a parallel step, it is 
   * possible there are additional failures not returned (it just returns the first failure it 
   * finds).  This call blocks until all the test steps have completed, and if none are in a 
   * failure state, it will return {@code null}.
   * 
   * @param futures Future collection to iterate over and inspect
   * @return Failed TestResult or {@code null} if no failures occurred
   * @throws InterruptedException Thrown if the thread is interrupted while waiting for {@link TestResult}
   */
  public static TestResult getFailedResult(Collection<? extends Future<TestResult>> futures) 
      throws InterruptedException {
    Iterator<? extends Future<TestResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        TestResult tr = it.next().get();
        if (tr.getError() != null) {
          return tr;
        }
      } catch (ExecutionException e) {
        // should not be possible
        throw new RuntimeException(e);
      }
    }
    
    return null;
  }
  
  /**
   * Looks through collection of futures looking for any {@link TestResult}'s that are in a failure 
   * state.  Unlike {@link #getFailedResult(Collection)}, which stops searching after it finds the 
   * first failure, this will find ALL failed results.  Thus ensuring that every test case has 
   * finished before returning.
   * 
   * @param futures Future collection to iterate over and inspect
   * @return Failed List of TestResult's that failed (collection will be empty if no failures occurred)
   * @throws InterruptedException Thrown if the thread is interrupted while waiting for {@link TestResult}
   */
  public static List<TestResult> getAllFailedResults(Collection<? extends Future<TestResult>> futures) 
      throws InterruptedException {
    List<TestResult> result = new ArrayList<TestResult>(2);
    Iterator<? extends Future<TestResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        TestResult tr = it.next().get();
        if (tr.getError() != null) {
          result.add(tr);
        }
      } catch (CancellationException e) {
        // possible if canceled after a failure event
      } catch (ExecutionException e) {
        // should not be possible
        throw new RuntimeException(e);
      }
    }
    
    return result;
  }
  
  /**
   * Calculates the average runtime per test step.  This totals the time spent in processing each 
   * test step, then divides by the total number of test steps to get the average time in 
   * nanoseconds.  If the test is running during this call it will block until all test steps have 
   * completed (or were canceled).
   * 
   * @param futures Future collection to iterate over and inspect
   * @return Average nanoseconds spent per test step 
   * @throws InterruptedException Thrown if the thread is interrupted while waiting for {@link TestResult}
   */
  public static double getAverageRuntimeNanos(Collection<? extends Future<TestResult>> futures) 
      throws InterruptedException {
    double count = 0;
    double totalNanos = 0;
    Iterator<? extends Future<TestResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        TestResult tr = it.next().get();
        count++;
        totalNanos += tr.runTimeInNanos();
      } catch (CancellationException e) {
        // possible if canceled after a failure event
      } catch (ExecutionException e) {
        // should not be possible
        throw new RuntimeException(e);
      }
    }
    
    return totalNanos / count;
  }
}
