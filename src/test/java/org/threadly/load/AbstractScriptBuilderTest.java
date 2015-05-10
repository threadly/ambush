package org.threadly.load;

import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.test.concurrent.AsyncVerifier;

@SuppressWarnings("javadoc")
public class AbstractScriptBuilderTest {
  @Test
  public void addProgressFutureTest() throws InterruptedException, TimeoutException {
    SequentialScriptBuilder builder = new SequentialScriptBuilder();
    builder.addStep(new TestStep());
    builder.addStep(new TestStep());
    ListenableFuture<Double> future = builder.addProgressFuture();
    builder.addStep(new TestStep());
    builder.addStep(new TestStep());
    
    builder.startScript();
    
    final AsyncVerifier av = new AsyncVerifier();
    future.addCallback(new FutureCallback<Double>() {
      @Override
      public void handleResult(Double result) {
        av.assertEquals(50., result);
        av.signalComplete();
      }

      @Override
      public void handleFailure(Throwable t) {
        av.fail(t);
      }
    });
    av.waitForTest();
  }
}
