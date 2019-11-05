package org.threadly.load;

/**
 * Interface for a class which builds an {@link ExecutableScript}, and provides the built 
 * result, ready to run.
 * <p>
 * Any implementing class must provide a default (empty) constructor.  Properties will be 
 * provided after construction and can be accessed via {@link #getIntValue(String)}, 
 * {@link #getIntValue(String, int)}, {@link #getLongValue(String)}, 
 * {@link #getLongValue(String, long)}, {@link #getDoubleValue(String)}, 
 * {@link #getDoubleValue(String, double)}, {@link #getStringValue(String)} and 
 * {@link #getStringValue(String, String)}.
 */
public abstract class ScriptFactory extends ParameterStore {
  /**
   * Call to have the factory build the respective execution script.  This may be simply adding 
   * items on to a {@link SequentialScriptBuilder} or a {@link ParallelScriptBuilder}.  Or it may 
   * be doing a more complicated combination as such.  In the end, 
   * {@link AbstractScriptBuilder#build()} should be invoked, and that result returned here.  
   * <p>
   * During script construction parameters can be accessed via {@link #getIntValue(String)}, 
   * {@link #getIntValue(String, int)}, {@link #getLongValue(String)}, 
   * {@link #getLongValue(String, long)}, {@link #getDoubleValue(String)}, 
   * {@link #getDoubleValue(String, double)}, {@link #getStringValue(String)} and 
   * {@link #getStringValue(String, String)}.
   * 
   * @return A constructed script, ready to be ran
   */
  public abstract ExecutableScript buildScript();
}
