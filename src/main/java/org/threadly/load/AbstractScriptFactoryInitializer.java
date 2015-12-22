package org.threadly.load;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.threadly.util.StringUtils;

/**
 * <p>Abstract implementation for a class which initializes a factory.  Usually a main class which 
 * is accepting arguments for what and how to construct a {@link ScriptFactory}.</p>
 * 
 * @author jent - Mike Jensen
 */
//extends ParameterStore so extending classes can get parameters easily
abstract class AbstractScriptFactoryInitializer extends ParameterStore {
  protected final ExecutableScript script;
  
  protected AbstractScriptFactoryInitializer(String[] args) {
    this(args.length > 0 ? args[0] : null, 
         args.length > 1 ? ParameterStore.parseProperties(args, 1, args.length - 1) : new Properties());
  }
  
  protected AbstractScriptFactoryInitializer(String classStr, Properties properties) {
    super(properties);

    if (StringUtils.isNullOrEmpty(classStr)) {
      System.err.println("ScriptFactory class not provided");
      handleInitializationFailure(null);
      // in test situations usageAndExit may not exit
      script = null;
      return;
    }
    
    ScriptFactory factory = null;
    try {
      Class<?> factoryClass = Class.forName(classStr);
      try {
        factory = (ScriptFactory)factoryClass.newInstance();
      } catch (ClassCastException e) {
        System.err.println("Class does not seem to be an instance of " + 
                             ScriptFactory.class.getSimpleName() + ": " + classStr);
        handleInitializationFailure(null);
      } catch (InstantiationException e) {
        System.err.println("Failed to call empty cosntructor on: " + classStr);
        e.printStackTrace();
        handleInitializationFailure(null);
      } catch (IllegalAccessException e) {
        System.err.println("Failed to call empty cosntructor on: " + classStr);
        e.printStackTrace();
        handleInitializationFailure(null);
      }
    } catch (ClassNotFoundException e) {
      System.err.println("Could not find class in classpath: " + classStr);
      handleInitializationFailure(null);
    }
    
    // may be null in test situations, not a normal case
    if (factory == null) {
      script = null;
      return;
    }
    factory.initialize(properties);
    try {
      script = factory.buildScript();
    } catch (ParameterException e) {
      Map<String, String> paramDocs = new HashMap<String, String>();
      Map<String, String> factoryParams = factory.getPossibleParameters();
      if (factoryParams != null) {
        paramDocs.putAll(factoryParams);
      }
      Map<String, String> runnerParams = this.getPossibleParameters();
      if (runnerParams != null) {
        paramDocs.putAll(runnerParams);
      }
      if (paramDocs.isEmpty()) {
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
      
      handleInitializationFailure(factory.getClass().getName());
      throw e;  // should not really throw unless someone has overridden {@link usageAndExit}
    }
  }

  /**
   * Invoked when there was an error in initializing the script.  Most definitions will likely 
   * provide some usage help and possibly exit.
   * 
   * @param runningScript Possible script that is loaded, or {@code null} if unknown
   */
  protected abstract void handleInitializationFailure(String buildingScript);
}
