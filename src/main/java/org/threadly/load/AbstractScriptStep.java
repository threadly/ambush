package org.threadly.load;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.threadly.util.StringUtils;

/**
 * <p>The basic foundation for a {@link ScriptStepInterface} implementation.  This provides a 
 * handler for {@link #getIdentifier()}.  It also adds additional functionality though 
 * {@link #setGlobalParam(String, String)} and {@link #getGlobalParam(String)}.</p>
 * 
 * @author jent - Mike Jensen
 */
public abstract class AbstractScriptStep implements ScriptStepInterface {
  private static final Map<String, String> PARAMS = new ConcurrentHashMap<String, String>();
  
  private final String identifier;
  
  protected AbstractScriptStep(String identifier) {
    this.identifier = identifier;
  }
  
  /**
   * Sets a global parameter.  This parameter will be shared among tests.  If tests are setting 
   * this concurrently it is important to know that only the last set will be maintained.
   * 
   * This provides a mechanism to share parameters between tests.  For example if one test needs 
   * to pass the result of something to be consumed by a future test, it can be set here, then 
   * retrieved by a future test using {@link #getGlobalParam(String)}.
   * 
   * @param key Key used for retrieval later
   * @param value Value to be set for the given key
   */
  public void setGlobalParam(String key, String value) {
    PARAMS.put(key, value);
  }
  
  /**
   * Requests to get the global param that has been set via 
   * {@link #setGlobalParam(String, String)}.  This is NOT specific to this test instance, but 
   * rather parameters which are shared among all tests.
   * 
   * @param key Key to use for parameter lookup
   * @return Returns the set parameter, will never return {@code null}, but an empty string is possible
   */
  public String getGlobalParam(String key) {
    return StringUtils.makeNonNull(PARAMS.get(key));
  }
  
  @Override
  public String getIdentifier() {
    return identifier;
  }
}
