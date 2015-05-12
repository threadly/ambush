package org.threadly.load;

import java.util.Properties;

/**
 * <p>Interface for a class which builds an {@link ExecutionScript}, and provides the built 
 * result, ready to run.</p>
 * 
 * <p>This class must have an empty constructor, arguments will be provided via the properties in 
 * {@link #buildScript(Properties)}.</p>
 *  
 * @author jent - Mike Jensen
 */
public interface ScriptFactory {
  /**
   * Call to have the factory build the respective execution script.  This may be simply adding 
   * items on to a {@link SequentialScriptBuilder} or a {@link ParallelScriptBuilder}.  Or it may 
   * be doing a more complicated combination as such.  In the end, 
   * {@link AbstractScriptBuilder#build()} should be invoked, and that result returned here.
   * 
   * @param properties A collection of properties to help configure and build the script from
   * @return A constructed script, ready to be ran
   */
  public ExecutionScript buildScript(Properties properties);
}
