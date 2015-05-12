package org.threadly.load;

import java.util.Properties;

import org.threadly.util.ArgumentVerifier;

/**
 * <p>Interface for a class which builds an {@link ExecutionScript}, and provides the built 
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
  private Properties properties = null;
  
  protected void initialize(Properties properties) {
    ArgumentVerifier.assertNotNull(properties, "properties");
    
    this.properties = properties;
  }
  
  /**
   * Returns an int value parameter for the given key.  If no value for the given key is found,
   * a {@link IllegalStateException} will be thrown.  If you the parameter may or may not exist use 
   * {@link #getIntValue(String, int)}.  If unable to parse the integer for the key a 
   * {@link NumberFormatException} will be thrown.
   * 
   * @param key Key to lookup the integer value from
   * @return Integer parameter value
   */
  public int getIntValue(String key) {
    return Integer.parseInt(getStringValue(key));
  }
  
  /**
   * Returns a long value parameter for the given key.  If no value for the given key is found,
   * a {@link IllegalStateException} will be thrown.  If you the parameter may or may not exist use 
   * {@link #getLongValue(String, long)}.  If unable to parse the long for the key a 
   * {@link NumberFormatException} will be thrown.
   * 
   * @param key Key to lookup the long value from 
   * @return Long parameter value
   */
  public long getLongValue(String key) {
    return Long.parseLong(getStringValue(key));
  }
  
  /**
   * Returns a double value parameter for the given key.  If no value for the given key is found,
   * a {@link IllegalStateException} will be thrown.  If you the parameter may or may not exist use 
   * {@link #getDoubleValue(String, double)}.  If unable to parse the double for the key a 
   * {@link NumberFormatException} will be thrown.
   * 
   * @param key Key to lookup the long value from 
   * @return Long parameter value
   */
  public double getDoubleValue(String key) {
    return Double.parseDouble(getStringValue(key));
  }
  
  /**
   * Returns a string value parameter for the given key.  If no value for the given key is found,
   * a {@link IllegalStateException} will be thrown.  If you the parameter may or may not exist use 
   * {@link #getStringValue(String, String)}.
   * 
   * @param key Key to lookup the String value from 
   * @return String parameter value
   */
  public String getStringValue(String key) {
    String result = properties.getProperty(key);
    if (result == null) {
      throw new IllegalStateException("No property for key: " + key);
    }
    return result;
  }
  
  /**
   * Returns an int value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the integer for the key a 
   * {@link NumberFormatException} will be thrown.
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
    return Integer.parseInt(result);
  }
  
  /**
   * Returns a long value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the long for the key a 
   * {@link NumberFormatException} will be thrown.
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
    return Long.parseLong(result);
  }
  
  /**
   * Returns a double value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the long for the key a 
   * {@link NumberFormatException} will be thrown.
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
    return Double.parseDouble(result);
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
  public abstract ExecutionScript buildScript();
}
