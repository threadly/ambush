package org.threadly.load;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.threadly.concurrent.collections.ConcurrentArrayList;

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
  private static final ConcurrentMap<Integer, ConcurrentArrayList<LightCharSequence>> CACHE = 
      new ConcurrentHashMap<Integer, ConcurrentArrayList<LightCharSequence>>();
  
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
  
  protected static LightCharSequence deDuplicate(char[] chars) {
    if (chars == null) {
      return null;
    }
    
    ConcurrentArrayList<LightCharSequence> deDupList = CACHE.get(chars.length);
    if (deDupList == null) {
      deDupList = new ConcurrentArrayList<LightCharSequence>();
      ConcurrentArrayList<LightCharSequence> existing = CACHE.putIfAbsent(chars.length, deDupList);
      if (existing != null) {
        deDupList = existing;
      }
    }
    
    // more likely to see updates from .get() calls than iterator, and RandomAccessList anyways
    int lastIndex = 0;
    for (; lastIndex < deDupList.size(); lastIndex++) {
      LightCharSequence c = deDupList.get(lastIndex);
      if (Arrays.equals(c.chars, chars)) {
        return c;
      }
    }
    
    // not found, so lock, verify absolutely not in list, then add if still not found
    synchronized (deDupList.getModificationLock()) {
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
