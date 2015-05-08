package org.threadly.load;

import java.util.Collection;
import java.util.Iterator;
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
}
