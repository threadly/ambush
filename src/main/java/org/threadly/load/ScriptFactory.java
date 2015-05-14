package org.threadly.load;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.threadly.util.ArgumentVerifier;

/**
 * <p>Interface for a class which builds an {@link ExecutableScript}, and provides the built 
 * result, ready to run.</p>
 * 
 * <p>Any implementing class must provide a default (empty) constructor.  Properties will be 
 * provided after construction and can be accessed via {@link #getIntValue(String)}, 
 * {@link #getIntValue(String, int)}, {@link #getLongValue(String)}, 
 * {@link #getLongValue(String, long)}, {@link #getDoubleValue(String)}, 
 * {@link #getDoubleValue(String, double)}, {@link #getStringValue(String)} and 
 * {@link #getStringValue(String, String)}.</p>
 *  
 * @author jent - Mike Jensen
 */
public abstract class ScriptFactory {
  protected Properties properties = null;
  
  protected void initialize(Properties properties) {
    ArgumentVerifier.assertNotNull(properties, "properties");
    
    this.properties = properties;
  }
  
  /**
   * This is an optional function to override.  It is highly recommended it is overridden if using 
   * {@link ScriptRunner}.  This provides documentation for what parameters are used by this 
   * builder.  If someone fails to provide a parameter this will be displayed to the CLI so that 
   * it makes it easier to know what was missing.  This is triggered by throwing 
   * {@link TestParameterException}.  
   * 
   * This map is structured such that the key represents the parameter key.  The value is an 
   * optional description to describe what the key represents.
   * 
   * @return Map with keys that indicate the parameters used, and values representing their description
   */
  public Map<String, String> getPossibleParameters() {
    return Collections.emptyMap();
  }
  
  /**
   * Returns an int value parameter for the given key.  If no value for the given key is found,
   * a {@link TestParameterException} will be thrown.  If you the parameter may or may not exist use 
   * {@link #getIntValue(String, int)}.  If unable to parse the integer for the key a 
   * {@link TestParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the integer value from
   * @return Integer parameter value
   */
  public int getIntValue(String key) {
    try {
      return Integer.parseInt(getStringValue(key));
    } catch (NumberFormatException e) {
      throw new TestParameterException(e);
    }
  }
  
  /**
   * Returns a long value parameter for the given key.  If no value for the given key is found,
   * a {@link TestParameterException} will be thrown.  If you the parameter may or may not exist use 
   * {@link #getLongValue(String, long)}.  If unable to parse the long for the key a 
   * {@link TestParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the long value from 
   * @return Long parameter value
   */
  public long getLongValue(String key) {
    try {
      return Long.parseLong(getStringValue(key));
    } catch (NumberFormatException e) {
      throw new TestParameterException(e);
    }
  }
  
  /**
   * Returns a double value parameter for the given key.  If no value for the given key is found,
   * a {@link TestParameterException} will be thrown.  If you the parameter may or may not exist use 
   * {@link #getDoubleValue(String, double)}.  If unable to parse the double for the key a 
   * {@link TestParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the long value from 
   * @return Long parameter value
   */
  public double getDoubleValue(String key) {
    try {
      return Double.parseDouble(getStringValue(key));
    } catch (NumberFormatException e) {
      throw new TestParameterException(e);
    }
  }
  
  /**
   * Returns a string value parameter for the given key.  If no value for the given key is found,
   * a {@link TestParameterException} will be thrown.  If you the parameter may or may not exist use 
   * {@link #getStringValue(String, String)}.
   * 
   * @param key Key to lookup the String value from 
   * @return String parameter value
   */
  public String getStringValue(String key) {
    String result = properties.getProperty(key);
    if (result == null) {
      throw new TestParameterException("No property for key: " + key);
    }
    return result;
  }
  
  /**
   * Returns an int value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the integer for the key a 
   * {@link TestParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the integer value from 
   * @param defaultVal Value to be returned if there is no value for the key
   * @return Integer parameter value or the provided default if none is found
   */
  public int getIntValue(String key, int defaultVal) {
    String result = properties.getProperty(key);
    if (result == null) {
      return defaultVal;
    }
    try {
      return Integer.parseInt(result);
    } catch (NumberFormatException e) {
      throw new TestParameterException(e);
    }
  }
  
  /**
   * Returns a long value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the long for the key a 
   * {@link TestParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the long value from 
   * @param defaultVal Value to be returned if there is no value for the key
   * @return Long parameter value or the provided default if none is found
   */
  public long getLongValue(String key, long defaultVal) {
    String result = properties.getProperty(key);
    if (result == null) {
      return defaultVal;
    }
    try {
      return Long.parseLong(result);
    } catch (NumberFormatException e) {
      throw new TestParameterException(e);
    }
  }
  
  /**
   * Returns a double value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the double for the key a 
   * {@link TestParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the double value from 
   * @param defaultVal Value to be returned if there is no value for the key
   * @return Double parameter value or the provided default if none is found
   */
  public double getDoubleValue(String key, double defaultVal) {
    String result = properties.getProperty(key);
    if (result == null) {
      return defaultVal;
    }
    try {
      return Double.parseDouble(result);
    } catch (NumberFormatException e) {
      throw new TestParameterException(e);
    }
  }
  
  /**
   * Returns a String value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.
   * 
   * @param key Key to lookup the string value from 
   * @param defaultVal Value to be returned if there is no value for the key
   * @return String parameter value or the provided default if none is found
   */
  public String getStringValue(String key, String defaultVal) {
    String result = properties.getProperty(key);
    if (result == null) {
      return defaultVal;
    }
    return result;
  }
  
  /**
   * Call to have the factory build the respective execution script.  This may be simply adding 
   * items on to a {@link SequentialScriptBuilder} or a {@link ParallelScriptBuilder}.  Or it may 
   * be doing a more complicated combination as such.  In the end, 
   * {@link AbstractScriptBuilder#build()} should be invoked, and that result returned here.  
   * 
   * During script construction parameters can be accessed via {@link #getIntValue(String)}, 
   * {@link #getIntValue(String, int)}, {@link #getLongValue(String)}, 
   * {@link #getLongValue(String, long)}, {@link #getDoubleValue(String)}, 
   * {@link #getDoubleValue(String, double)}, {@link #getStringValue(String)} and 
   * {@link #getStringValue(String, String)}.
   * 
   * @return A constructed script, ready to be ran
   */
  public abstract ExecutableScript buildScript();
  
  /**
   * <p>Exception type that indicates an error with one of the parameters.  This most commonly 
   * would be a missing required value, but it may also represent invalid data provided.</p>
   * 
   * <p>When used inside {@link ScriptRunner} it will cause a description of set parameters 
   * provided by {@link #getPossibleParameters()} to be printed to the user.</p>
   * 
   * @author jent - Mike Jensen
   */
  public static class TestParameterException extends RuntimeException {
    private static final long serialVersionUID = -1913265153770464976L;

    public TestParameterException() {
      super();
    }
    
    public TestParameterException(String msg) {
      super(msg);
    }
    
    public TestParameterException(Throwable cause) {
      super(cause);
    }
    
    public TestParameterException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }
}
