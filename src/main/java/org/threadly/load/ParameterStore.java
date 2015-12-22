package org.threadly.load;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.threadly.util.ArgumentVerifier;

/**
 * <p>Storage for parameters, providing a common interface for retriving them.  Properties will be 
 * provided after construction and can be accessed via {@link #getIntValue(String)}, 
 * {@link #getIntValue(String, int)}, {@link #getLongValue(String)}, 
 * {@link #getLongValue(String, long)}, {@link #getDoubleValue(String)}, 
 * {@link #getDoubleValue(String, double)}, {@link #getStringValue(String)} and 
 * {@link #getStringValue(String, String)}.</p>
 *  
 * @author jent - Mike Jensen
 */
public class ParameterStore {
  /**
   * Parse the parameters from an array of String arguments.  It is expected that each String be 
   * in the format of {@code key=value}.  Any '=' signs in the keys or values are unexpected and 
   * may cause issues.
   *  
   * @param args Array of arguments to parse through
   * @param offset Offset into array to start parsing from
   * @param length Number of elements which should be inspected
   * @return New properties reference which has the parsed out key/values
   */
  public static Properties parseProperties(String[] args, int offset, int length) {
    if (offset < 0 || offset > args.length) {
      throw new IndexOutOfBoundsException("Offset out of range: " + 
                                            offset + ", " + length + " - " + args.length);
    } else if (length < 0 || offset + length > args.length) {
      throw new IndexOutOfBoundsException("Length out of range: " + 
                                            offset + ", " + length + " - " + args.length);
    }
    Properties properties = new Properties();
    for (int i = 0; i < length; i++) {
      int delimIndex = args[offset + i].indexOf('=');
      if (delimIndex < 1) {
        System.err.println("Ignoring unknown key/value argument: " + args[offset + i]);
      } else {
        properties.put(args[offset + i].substring(0, delimIndex), 
                       args[offset + i].substring(delimIndex + 1));
      }
    }

    return properties;
  }
  
  protected Properties properties = null;
  
  /**
   * Construct a {@link ParameterStore} with the parameters to be provided later from 
   * {@link #initialize(Properties)}.
   */
  public ParameterStore() {
    this(null);
  }
  
  /**
   * Constructs a new {@link ParameterStore} with the parameters used for backing, or {@code null} 
   * if it is desired to set the parameters later using {@link #initialize(Properties)}.
   * 
   * @param properties Properties to load parameters from
   */
  public ParameterStore(Properties properties) {
    this.properties = properties;
  }
  
  /**
   * Initializes the {@link ParameterStore} with the properties to load values used in the 
   * {@link #buildScript()}.  For that reason this should be called right after construction, and 
   * before {@link #buildScript()} is invoked.  This is not done in the constructor because 
   * extending classes should all have an empty (default) constructor.
   * 
   * @param properties Parameters to load values from
   */
  protected void initialize(Properties properties) {
    ArgumentVerifier.assertNotNull(properties, "properties");
    if (this.properties != null) {
      throw new IllegalStateException("Alread initialized");
    }
    
    this.properties = properties;
  }
  
  /**
   * This is an optional function to override.  It is highly recommended it is overridden if using 
   * {@link ScriptRunner}.  This provides documentation for what parameters are used by this 
   * builder.  If someone fails to provide a parameter this will be displayed to the CLI so that 
   * it makes it easier to know what was missing.  This is triggered by throwing 
   * {@link ParameterException}.  
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
   * Returns a boolean value parameter for the given key.  If no value for the given key is found, 
   * a {@link ParameterException} will be thrown.  If the parameter may or may not exist use 
   * {@link #getBoolValue(String, boolean)}.  If unable to parse the boolean for the key a 
   * {@link ParameterException}.  It is expected that the string value to represent the 
   * boolean will be either {@code "true"} or {@code "false"}.
   * 
   * @param key Key to lookup the boolean value from 
   * @return Boolean parameter value or the provided default if none is found
   */
  public boolean getBoolValue(String key) {
    String str = getStringValue(key);
    if (str.equalsIgnoreCase("true")) {
      return true;
    } else if (str.equalsIgnoreCase("false")) {
      return false;
    } else {
      throw new ParameterException("Can not parse boolean from: " + str);
    }
  }
  
  /**
   * Returns an int value parameter for the given key.  If no value for the given key is found,
   * a {@link ParameterException} will be thrown.  If the parameter may or may not exist use 
   * {@link #getIntValue(String, int)}.  If unable to parse the integer for the key a 
   * {@link ParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the integer value from
   * @return Integer parameter value
   */
  public int getIntValue(String key) {
    try {
      return Integer.parseInt(getStringValue(key));
    } catch (NumberFormatException e) {
      throw new ParameterException(e);
    }
  }
  
  /**
   * Returns a long value parameter for the given key.  If no value for the given key is found,
   * a {@link ParameterException} will be thrown.  If the parameter may or may not exist use 
   * {@link #getLongValue(String, long)}.  If unable to parse the long for the key a 
   * {@link ParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the long value from 
   * @return Long parameter value
   */
  public long getLongValue(String key) {
    try {
      return Long.parseLong(getStringValue(key));
    } catch (NumberFormatException e) {
      throw new ParameterException(e);
    }
  }
  
  /**
   * Returns a double value parameter for the given key.  If no value for the given key is found,
   * a {@link ParameterException} will be thrown.  If the parameter may or may not exist use 
   * {@link #getDoubleValue(String, double)}.  If unable to parse the double for the key a 
   * {@link ParameterException} will be thrown with a {@link NumberFormatException} being the 
   * cause.
   * 
   * @param key Key to lookup the long value from 
   * @return Long parameter value
   */
  public double getDoubleValue(String key) {
    try {
      return Double.parseDouble(getStringValue(key));
    } catch (NumberFormatException e) {
      throw new ParameterException(e);
    }
  }
  
  /**
   * Returns a string value parameter for the given key.  If no value for the given key is found,
   * a {@link ParameterException} will be thrown.  If the parameter may or may not exist use 
   * {@link #getStringValue(String, String)}.
   * 
   * @param key Key to lookup the String value from 
   * @return String parameter value
   */
  public String getStringValue(String key) {
    String result = properties.getProperty(key);
    if (result == null) {
      throw new ParameterException("No property for key: " + key);
    }
    return result;
  }
  
  /**
   * Returns a boolean value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the boolean for the key a 
   * {@link ParameterException}.  It is expected that the string value to represent the 
   * boolean will be either {@code "true"} or {@code "false"}.
   * 
   * @param key Key to lookup the boolean value from 
   * @param defaultVal Value to be returned if there is no value for the key
   * @return Boolean parameter value or the provided default if none is found
   */
  public boolean getBoolValue(String key, boolean defaultVal) {
    String result = properties.getProperty(key);
    if (result == null) {
      return defaultVal;
    } else if (result.equalsIgnoreCase("true")) {
      return true;
    } else if (result.equalsIgnoreCase("false")) {
      return false;
    } else {
      throw new ParameterException("Can not parse boolean from: " + result);
    }
  }
  
  /**
   * Returns an int value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the integer for the key a 
   * {@link ParameterException} will be thrown with a {@link NumberFormatException} being the 
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
      throw new ParameterException(e);
    }
  }
  
  /**
   * Returns a long value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the long for the key a 
   * {@link ParameterException} will be thrown with a {@link NumberFormatException} being the 
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
      throw new ParameterException(e);
    }
  }
  
  /**
   * Returns a double value parameter for the given key.  If no value for the given key is found 
   * then the provided default will be returned.  If unable to parse the double for the key a 
   * {@link ParameterException} will be thrown with a {@link NumberFormatException} being the 
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
      throw new ParameterException(e);
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
   * <p>Exception type that indicates an error with one of the parameters.  This most commonly 
   * would be a missing required value, but it may also represent invalid data provided.</p>
   * 
   * <p>When used inside {@link ScriptRunner} it will cause a description of set parameters 
   * provided by {@link #getPossibleParameters()} to be printed to the user.</p>
   * 
   * @author jent - Mike Jensen
   */
  public static class ParameterException extends RuntimeException {
    private static final long serialVersionUID = -1913265153770464976L;
    
    /**
     * Constructs a new {@link ParameterException} with a provided message to indicate the 
     * specific parameter violation.
     * 
     * @param msg Message to describe the parameter error
     */
    public ParameterException(String msg) {
      super(msg);
    }
    
    /**
     * Constructs a new {@link ParameterException} with a cause exception which represents 
     * the failure.
     * 
     * @param cause Exception which represents the initial failure from a provided parameter
     */
    public ParameterException(Throwable cause) {
      super(cause);
    }
    
    /**
     * Constructs a new {@link ParameterException} with a provided message to indicate the 
     * specific parameter violation, as well as an exception which represents the failure.
     * 
     * @param msg Message to describe the parameter error
     * @param cause Exception which represents the failure
     */
    public ParameterException(String msg, Throwable cause) {
      super(msg, cause);
    }
  }
}
