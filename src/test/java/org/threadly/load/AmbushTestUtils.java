package org.threadly.load;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("javadoc")
public class AmbushTestUtils {
  public static final int TEST_COMPLEXITY = 10;
  
  public static List<TestStep> makeTestSteps(final Runnable startRunnable, int count) {
    List<TestStep> result = new ArrayList<TestStep>(count);
    for (int i = 0; i < count; i++) {
      result.add(new TestStep() {
        @Override
        public void handleRunStart() {
          if (startRunnable != null) {
            startRunnable.run();
          }
        }
      });
    }
    
    return result;
  }
  
  public static void addSteps(List<TestStep> steps, AbstractScriptBuilder builder) {
    Iterator<TestStep> it = steps.iterator();
    while (it.hasNext()) {
      builder.addStep(it.next());
    }
  }
}
