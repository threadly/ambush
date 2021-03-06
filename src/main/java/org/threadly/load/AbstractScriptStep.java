package org.threadly.load;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.threadly.util.ArgumentVerifier;
import org.threadly.util.StringUtils;

/**
 * The basic foundation for a {@link ScriptStep} implementation.  This provides a 
 * handler for {@link #getIdentifier()}.  It also adds additional functionality though 
 * {@link #setGlobalParam(String, String)} and {@link #getGlobalParam(String)}.
 */
public abstract class AbstractScriptStep implements ScriptStep {
  private static final Map<String, String> PARAMS = new ConcurrentHashMap<String, String>();
  
  /**
   * Sets a global parameter.  This parameter will be shared among tests.  If tests are setting 
   * this concurrently it is important to know that only the last set will be maintained.
   * <p>
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
   * Unset or remove a given global parameter.  If using the global parameters map heavily, you may 
   * find you need to remove data as you are done with it so that the heap does not grow without 
   * bounds.
   * 
   * @param key Key to be removed
   * @return Value previously associated with the key
   */
  public static String removeGlobalParam(String key) {
    return PARAMS.remove(key);
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
  
  protected final CharSequence identifier;
  protected final ScriptStepType stepType;
  
  protected AbstractScriptStep(CharSequence identifier) {
    this(identifier, ScriptStepType.Normal);
  }
  
  protected AbstractScriptStep(CharSequence identifier, ScriptStepType stepType) {
    ArgumentVerifier.assertNotNull(stepType, "stepType");
    
    if (identifier instanceof String) {
      this.identifier = CharsDeduplicator.deDuplicate((String)identifier);
    } else if (identifier == null) {
      this.identifier = "";
    } else {
      this.identifier = identifier;
    }
    
    this.stepType = stepType;
  }

  @Override
  public ScriptStepType getStepType() {
    return stepType;
  }
  
  @Override
  public CharSequence getIdentifier() {
    return identifier;
  }
}
