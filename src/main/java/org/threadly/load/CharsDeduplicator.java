package org.threadly.load;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
  // TODO - should we make this hold soft references??
  private static final ConcurrentMap<Integer, ArrayList<LightCharSequence>> CACHE = 
      new ConcurrentHashMap<Integer, ArrayList<LightCharSequence>>();
  
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
    
    ArrayList<LightCharSequence> deDupList = CACHE.get(chars.length);
    if (deDupList == null) {
      deDupList = new ArrayList<LightCharSequence>(1);
      ArrayList<LightCharSequence> replacedList = CACHE.putIfAbsent(chars.length, deDupList);
      if (replacedList != null) {
        deDupList = replacedList;
      }
    }
    
    int lastIndex = 0;
    try {
      Iterator<LightCharSequence> it = deDupList.iterator();
      while (it.hasNext()) {
        LightCharSequence c = it.next();
        if (Arrays.equals(c.chars, chars)) {
          return c;
        }
        lastIndex++;
      }
    } catch (ConcurrentModificationException e) {
      // oh well, retry in lock
    }
    synchronized (deDupList) {
      Iterator<LightCharSequence> it = deDupList.listIterator(lastIndex);
      while (it.hasNext()) {
        LightCharSequence c = it.next();
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
    CACHE.clear();
  }
}
