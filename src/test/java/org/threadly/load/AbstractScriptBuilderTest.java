package org.threadly.load;

import static org.junit.Assert.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.junit.Test;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.test.concurrent.AsyncVerifier;
import org.threadly.util.Clock;

@SuppressWarnings("javadoc")
public class AbstractScriptBuilderTest {
  @Test
  public void setMaxScriptStepRateTest() throws InterruptedException, ExecutionException {
    int testSteps = 5;
    SequentialScriptBuilder builder = new SequentialScriptBuilder();
    builder.setMaxScriptStepRate(testSteps * 10);
    for (int i = 0; i < testSteps + 1; i++) {
      builder.addStep(new TestStep());
    }
    ExecutableScript script = builder.build();
    
    long start = Clock.accurateForwardProgressingMillis();
    FutureUtils.blockTillAllCompleteOrFirstError(script.startScript());
    long end = Clock.accurateForwardProgressingMillis();
    
    assertTrue(end - start >= 100);
  }
  
  @Test
  public void addProgressFutureTest() throws InterruptedException, TimeoutException {
    SequentialScriptBuilder builder = new SequentialScriptBuilder();
    builder.addStep(new TestStep());
    builder.addStep(new TestStep());
    ListenableFuture<Double> future = builder.addProgressFuture();
    builder.addStep(new TestStep());
    builder.addStep(new TestStep());
    
    builder.build().startScript();
    
    final AsyncVerifier av = new AsyncVerifier();
    future.callback(new FutureCallback<Double>() {
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
