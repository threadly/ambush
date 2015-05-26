package org.threadly.load;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.threadly.load.ScriptFactory.TestParameterException;
import org.threadly.util.StringUtils;

/**
 * <p>Abstract implementation for a class which initializes a factory.  Usually a main class which 
 * is accepting arguments for what and how to construct a {@link ScriptFactory}.</p>
 * 
 * @author jent - Mike Jensen
 */
abstract class AbstractScriptFactoryInitializer {
  protected final ExecutableScript script;
  
  protected AbstractScriptFactoryInitializer(String[] args) {
    if (args.length == 0) {
      System.err.println("No arguments provided, need ScriptFactory class");
      usageAndExit(null);
      // in test situations usageAndExit may not exit
      script = null;
      return;
    }
    
    String classStr = args[0];
    ScriptFactory factory = null;
    try {
      Class<?> factoryClass = Class.forName(classStr);
      try {
        factory = (ScriptFactory)factoryClass.newInstance();
      } catch (ClassCastException e) {
        System.err.println("Class does not seem to be an instance of " + 
                             ScriptFactory.class.getSimpleName() + ": " + classStr);
        usageAndExit(null);
      } catch (InstantiationException e) {
        System.err.println("Failed to call empty cosntructor on: " + classStr);
        e.printStackTrace();
        usageAndExit(null);
      } catch (IllegalAccessException e) {
        System.err.println("Failed to call empty cosntructor on: " + classStr);
        e.printStackTrace();
        usageAndExit(null);
      }
    } catch (ClassNotFoundException e) {
      System.err.println("Could not find class in classpath: " + classStr);
      usageAndExit(null);
    }

    Properties props = new Properties();
    for (int i = 1; i < args.length; i++) {
      int delimIndex = args[i].indexOf('=');
      if (delimIndex < 1) {
        System.err.println("Ignoring unknown key/value argument: " + args[i]);
      } else {
        props.put(args[i].substring(0, delimIndex), args[i].substring(delimIndex + 1));
      }
    }
    
    // may be null in test situations, not a normal case
    if (factory == null) {
      script = null;
      return;
    }
    factory.initialize(props);
    try {
      script = factory.buildScript();
    } catch (TestParameterException e) {
      Map<String, String> paramDocs = factory.getPossibleParameters();
      if (paramDocs == null || paramDocs.isEmpty()) {
        throw e;
      }

      System.err.println("Failure with script parameters..." + e.getMessage());
      if (e.getCause() != null) {
        System.err.println("Cause exception: ");
        e.getCause().printStackTrace();
      }
      System.err.println("Possible parameters:");
      System.err.println();
      Iterator<Map.Entry<String, String>> it = paramDocs.entrySet().iterator();
      StringBuilder paramDefs = new StringBuilder();
      while (it.hasNext()) {
        Map.Entry<String, String> param = it.next();
        paramDefs.append(param.getKey());
        if (param.getValue() != null && ! param.getValue().isEmpty()) {
          for (int i = param.getKey().length(); i < 10; i++) {
            paramDefs.append(" ");
          }
          paramDefs.append(" - ").append(param.getValue());
        }
        paramDefs.append(StringUtils.NEW_LINE)
                 .append(StringUtils.NEW_LINE);
      }
      System.err.print(paramDefs.toString());
      
      usageAndExit(factory.getClass().getName());
      throw e;  // should not really throw unless someone has overridden {@link usageAndExit}
    }
  }
  
  protected void usageAndExit(String runningScript) {
    if (runningScript == null || runningScript.isEmpty()) {
      runningScript = "script.factory.to.call";
    }
    System.err.println("java " + this.getClass().getName() + 
                         " " + runningScript + " key1=value1 key2=value2....");
    System.exit(1);
  }
}
