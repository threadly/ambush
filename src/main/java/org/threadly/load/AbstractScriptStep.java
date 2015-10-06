package org.threadly.load;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.threadly.util.StringUtils;

/**
 * <p>The basic foundation for a {@link ScriptStep} implementation.  This provides a 
 * handler for {@link #getIdentifier()}.  It also adds additional functionality though 
 * {@link #setGlobalParam(String, String)} and {@link #getGlobalParam(String)}.</p>
 * 
 * @author jent - Mike Jensen
 */
public abstract class AbstractScriptStep implements ScriptStep {
  private static final Map<String, String> PARAMS = new ConcurrentHashMap<String, String>();
  
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
  public static void setGlobalParam(String key, String value) {
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
  public static String getGlobalParam(String key) {
    return StringUtils.nullToEmpty(PARAMS.get(key));
  }
  
  private final CharSequence identifier;
  
  protected AbstractScriptStep(CharSequence identifier) {
    if (identifier instanceof String) {
      this.identifier = CharsDeduplicator.deDuplicate((String)identifier);
    } else if (identifier == null) {
      this.identifier = "";
    } else {
      this.identifier = identifier;
    }
  }
  
  protected AbstractScriptStep(String identifier) {
    this.identifier = CharsDeduplicator.deDuplicate(StringUtils.nullToEmpty(identifier));
  }
  
  @Override
  public CharSequence getIdentifier() {
    return identifier;
  }
}
