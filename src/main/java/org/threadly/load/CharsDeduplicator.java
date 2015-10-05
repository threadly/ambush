package org.threadly.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Class used for de duplicating strings into a more minimal storage form.  This does a more 
 * aggressive form of reference aggregation than {@link String#intern()}.  And in fact the cache 
 * continues to grow infinitely until {@link CharsDeduplicator#clearCache()} is invoked.</p>
 * 
 * <p>The returned references are not Strings, but rather a reference which only holds the 
 * {@code char[]}, all invocations then calculate the result at the time of invocation.  Trading 
 * memory for extra CPU cycles.</p>
 * 
 * @author jent - Mike Jensen
 */
public class CharsDeduplicator {
  private static final Object CACHE_LOCK = new Object();
  // TODO - should we make this hold soft references??
  private static Map<Integer, ArrayList<LightCharSequence>> cache = null;
  
  /**
   * De-duplicate the provided string into a lighter memory form.  Because this form is a new 
   * reference entirely, memory will only be saved if the original reference is allowed to be 
   * garbage collected.
   * 
   * @param str String to search for
   * @return Immutable CharSequence which represents the strings contents
   */
  public static LightCharSequence deDuplicate(String str) {
    return deDuplicate(str.toCharArray());
  }
  
  private static LightCharSequence deDuplicate(char[] chars) {
    if (chars == null) {
      return null;
    }
    
    ArrayList<LightCharSequence> deDupList;
    synchronized (CACHE_LOCK) {
      if (cache == null) {
        cache = new HashMap<Integer, ArrayList<LightCharSequence>>();
      }
      deDupList = cache.get(chars.length);
      if (deDupList == null) {
        deDupList = new ArrayList<LightCharSequence>(1);
        cache.put(chars.length, deDupList);
      }
    }
    
    synchronized (deDupList) {
      for (LightCharSequence c : deDupList) {
        if (Arrays.equals(c.chars, chars)) {
          return c;
        }
      }
      
      LightCharSequence result = new LightCharSequence(chars);
      deDupList.add(result);
      return result;
    }
  }
  
  /**
   * Clears the stored cache, freeing up all stored memory here.
   */
  public static void clearCache() {
    cache = null;
  }
}
