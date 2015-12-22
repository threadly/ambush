package org.threadly.load;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * <p>Some utilities for when dealing with a collection of {@link StepResult}'s.  One of the most 
 * notable is {@link #getFailedResult(Collection)} which is an easy way to know if a set of test 
 * steps completed without error.</p>
 * 
 * @author jent - Mike Jensen
 */
public class StepResultCollectionUtils {
  /**
   * Looks through collection of futures looking for any {@link StepResult}'s that are in a failure 
   * state.  Once it finds the first one it returns it.  If this was a parallel step, it is 
   * possible there are additional failures not returned (it just returns the first failure it 
   * finds).  This call blocks until all the test steps have completed, and if none are in a 
   * failure state, it will return {@code null}.
   * 
   * @param futures Future collection to iterate over and inspect
   * @return Failed TestResult or {@code null} if no failures occurred
   * @throws InterruptedException Thrown if the thread is interrupted while waiting for {@link StepResult}
   */
  public static StepResult getFailedResult(Collection<? extends Future<? extends StepResult>> futures) 
      throws InterruptedException {
    Iterator<? extends Future<? extends StepResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        StepResult tr = it.next().get();
        if (tr.getError() != null) {
          return tr;
        }
      } catch (CancellationException e) {
        // likely was canceled already from another thread
      } catch (ExecutionException e) {
        // should not be possible
        throw new RuntimeException(e);
      }
    }
    
    return null;
  }
  
  /**
   * Looks through collection of futures looking for any {@link StepResult}'s that are in a failure 
   * state.  Unlike {@link #getFailedResult(Collection)}, which stops searching after it finds the 
   * first failure, this will find ALL failed results.  Thus ensuring that every test case has 
   * finished before returning.
   * 
   * @param futures Future collection to iterate over and inspect
   * @return Failed List of TestResult's that failed (collection will be empty if no failures occurred)
   * @throws InterruptedException Thrown if the thread is interrupted while waiting for {@link StepResult}
   */
  public static List<StepResult> getAllFailedResults(Collection<? extends Future<? extends StepResult>> futures) 
      throws InterruptedException {
    List<StepResult> result = new ArrayList<StepResult>(2);
    Iterator<? extends Future<? extends StepResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        StepResult tr = it.next().get();
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
   * @param timeUnit Time unit that the resulting time should be returned in
   * @return Average nanoseconds spent per test step 
   * @throws InterruptedException Thrown if the thread is interrupted while waiting for {@link StepResult}
   */
  public static double getRunTimeAverage(Collection<? extends Future<? extends StepResult>> futures, TimeUnit timeUnit) 
      throws InterruptedException {
    double count = 0;
    double totalNanos = 0;
    Iterator<? extends Future<? extends StepResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        StepResult tr = it.next().get();
        count++;
        totalNanos += tr.getRunTime(timeUnit);
      } catch (CancellationException e) {
        // possible if canceled after a failure event
      } catch (ExecutionException e) {
        // should not be possible
        throw new RuntimeException(e);
      }
    }
    
    return totalNanos / count;
  }
  
  /**
   * Sort through results to get step run time percentiles.
   * 
   * @param futures Future collection to iterate over and inspect
   * @param timeUnit Time unit that the resulting percentile run times should be returned in
   * @param percentiles Percentile points requested, must be provided, must be between 0 and 100 (inclusive)
   * @return Map which key maps to percentile, and value maps to the result
   * @throws InterruptedException Thrown if thread is interrupted while waiting for results to complete
   */
  public static Map<Double, Long> 
      getRunTimePercentiles(Collection<? extends Future<? extends StepResult>> futures, 
                            TimeUnit timeUnit, double ... percentiles) throws InterruptedException {
    if (percentiles.length == 0) {
      throw new IllegalArgumentException("No percentiles requested");
    }
    
    List<Long> runTimes = new ArrayList<Long>(futures.size());
    Iterator<? extends Future<? extends StepResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        runTimes.add(it.next().get().getRunTime(timeUnit));
      } catch (CancellationException e) {
        // possible if canceled after a failure event
      } catch (ExecutionException e) {
        // should not be possible
        throw new RuntimeException(e);
      }
    }
    
    Collections.sort(runTimes);
    
    Map<Double, Long> result = new LinkedHashMap<Double, Long>();
    for (double p : percentiles) {
      if (p > 100 || p < 0) {
        throw new IllegalArgumentException("Percentile not in range of 0 to 100: " + p);
      }
      
      int index = (int)((p / 100.) * runTimes.size());
      result.put(p, runTimes.get(index));
    }
    return result;
  }
  
  /**
   * Searched through the test results to find which test step took the longest to execute.
   * 
   * @param futures Future collection to iterate over and inspect
   * @return TestResult which took the longest to execute in the set
   * @throws InterruptedException Thrown if the thread is interrupted while waiting for {@link StepResult}
   */
  public static StepResult getLongestRunTimeStep(Collection<? extends Future<? extends StepResult>> futures) 
      throws InterruptedException {
    StepResult longestStep = null;
    Iterator<? extends Future<? extends StepResult>> it = futures.iterator();
    while (it.hasNext()) {
      try {
        StepResult tr = it.next().get();
        if (longestStep == null || 
            longestStep.getRunTime(TimeUnit.NANOSECONDS) < tr.getRunTime(TimeUnit.NANOSECONDS)) {
          longestStep = tr;
        }
      } catch (CancellationException e) {
        // possible if canceled after a failure event
      } catch (ExecutionException e) {
        // should not be possible
        throw new RuntimeException(e);
      }
    }
    
    return longestStep;
  }
}
