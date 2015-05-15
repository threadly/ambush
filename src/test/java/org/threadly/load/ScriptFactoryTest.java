package org.threadly.load;

import static org.junit.Assert.*;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.load.ScriptFactory.TestParameterException;
import org.threadly.util.StringUtils;

@SuppressWarnings("javadoc")
public class ScriptFactoryTest {
  private Properties properties;
  private TestScriptFactory factory;
  
  @Before
  public void setup() {
    properties = new Properties();
    factory = new TestScriptFactory();
    factory.initialize(properties);
  }
  
  @After
  public void cleanup() {
    factory = null;
    properties = null;
  }
  
  @Test
  public void initializeTest() {
    Properties properties = new Properties();
    factory.initialize(properties);
    
    assertTrue(factory.properties == properties);
  }
  
  @Test
  public void getPossibleParametersTest() {
    assertEquals(0, factory.getPossibleParameters().size());
  }
  
  @Test
  public void getBoolTest() {
    String key = StringUtils.randomString(5);
    properties.put(key, "true");
    
    assertTrue(factory.getBoolValue(key));
  }
  
  @Test
  public void getBoolParseFail() {
    String key = StringUtils.randomString(5);
    properties.put(key, StringUtils.randomString(5));
    
    try {
      factory.getBoolValue(key);
      fail("Exception should have thrown");
    } catch (TestParameterException e) {
      // expected
    }
  }
  
  @Test (expected = TestParameterException.class)
  public void getBoolMissingFail() {
    factory.getBoolValue("foo");
    fail("Exception should have thrown");
  }
  
  @Test
  public void getIntTest() {
    String key = StringUtils.randomString(5);
    int val = 10;
    properties.put(key, Integer.toString(val));
    
    assertEquals(val, factory.getIntValue(key));
  }
  
  @Test
  public void getIntParseFail() {
    String key = StringUtils.randomString(5);
    properties.put(key, "s" + StringUtils.randomString(5));
    
    try {
      factory.getIntValue(key);
      fail("Exception should have thrown");
    } catch (TestParameterException e) {
      // expected
      assertTrue(e.getCause() instanceof NumberFormatException);
    }
  }
  
  @Test (expected = TestParameterException.class)
  public void getIntMissingFail() {
    factory.getIntValue("foo");
    fail("Exception should have thrown");
  }
  
  @Test
  public void getLongTest() {
    String key = StringUtils.randomString(5);
    int val = 10;
    properties.put(key, Integer.toString(val));
    
    assertEquals(val, factory.getLongValue(key));
  }
  
  @Test
  public void getLongParseFail() {
    String key = StringUtils.randomString(5);
    properties.put(key, "s" + StringUtils.randomString(5));
    
    try {
      factory.getLongValue(key);
      fail("Exception should have thrown");
    } catch (TestParameterException e) {
      // expected
      assertTrue(e.getCause() instanceof NumberFormatException);
    }
  }
  
  @Test (expected = TestParameterException.class)
  public void getLongMissingFail() {
    factory.getLongValue("foo");
    fail("Exception should have thrown");
  }
  
  @Test
  public void getDoubleTest() {
    String key = StringUtils.randomString(5);
    double val = 8.8;
    properties.put(key, Double.toString(val));
    
    assertEquals(val, factory.getDoubleValue(key), 0);
  }
  
  @Test
  public void getDoubleParseFail() {
    String key = StringUtils.randomString(5);
    properties.put(key, "s" + StringUtils.randomString(5));
    
    try {
      factory.getDoubleValue(key);
      fail("Exception should have thrown");
    } catch (TestParameterException e) {
      // expected
      assertTrue(e.getCause() instanceof NumberFormatException);
    }
  }
  
  @Test (expected = TestParameterException.class)
  public void getDoubleMissingFail() {
    factory.getDoubleValue("foo");
    fail("Exception should have thrown");
  }
  
  @Test
  public void getStringTest() {
    String key = StringUtils.randomString(5);
    String val = StringUtils.randomString(5);
    properties.put(key, val);
    
    assertEquals(val, factory.getStringValue(key));
  }
  
  @Test (expected = TestParameterException.class)
  public void getStringMissingFail() {
    factory.getStringValue("foo");
    fail("Exception should have thrown");
  }
  
  @Test
  public void getBoolDefaultTest() {
    assertTrue(factory.getBoolValue(StringUtils.randomString(5), true));
  }
  
  @Test
  public void getBoolDefaultParseFail() {
    String key = StringUtils.randomString(5);
    properties.put(key, StringUtils.randomString(5));
    
    try {
      factory.getBoolValue(key, true);
      fail("Exception should have thrown");
    } catch (TestParameterException e) {
      // expected
    }
  }
  
  @Test
  public void getIntDefaultTest() {
    String key = StringUtils.randomString(5);
    int val = 10;
    assertEquals(val, factory.getIntValue(key, val));
  }
  
  @Test
  public void getIntDefaultParseFail() {
    String key = StringUtils.randomString(5);
    properties.put(key, "s" + StringUtils.randomString(5));
    
    try {
      factory.getIntValue(key, 10);
      fail("Exception should have thrown");
    } catch (TestParameterException e) {
      // expected
      assertTrue(e.getCause() instanceof NumberFormatException);
    }
  }
  
  @Test
  public void getLongDefaultTest() {
    String key = StringUtils.randomString(5);
    int val = 10;
    
    assertEquals(val, factory.getLongValue(key, val));
  }
  
  @Test
  public void getLongDefaultParseFail() {
    String key = StringUtils.randomString(5);
    properties.put(key, "s" + StringUtils.randomString(5));
    
    try {
      factory.getLongValue(key, 10);
      fail("Exception should have thrown");
    } catch (TestParameterException e) {
      // expected
      assertTrue(e.getCause() instanceof NumberFormatException);
    }
  }
  
  @Test
  public void getDoubleDefaultTest() {
    String key = StringUtils.randomString(5);
    double val = 8.8;
    
    assertEquals(val, factory.getDoubleValue(key, val), 0);
  }
  
  @Test
  public void getDoubleDefaultParseFail() {
    String key = StringUtils.randomString(5);
    properties.put(key, "s" + StringUtils.randomString(5));
    
    try {
      factory.getDoubleValue(key, 8.8);
      fail("Exception should have thrown");
    } catch (TestParameterException e) {
      // expected
      assertTrue(e.getCause() instanceof NumberFormatException);
    }
  }
  
  @Test
  public void getStringDefaultTest() {
    String key = StringUtils.randomString(5);
    String val = StringUtils.randomString(5);
    
    assertEquals(val, factory.getStringValue(key, val));
  }
  
  protected static class TestScriptFactory extends ScriptFactory {
    @Override
    public ExecutableScript buildScript() {
      throw new TestParameterException();
    }
  }
}
