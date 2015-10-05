package org.threadly.load;

/**
 * <p>A light weight char sequence implementation.  This implementation only holds the char[], and 
 * calculates everything lazily.  Thus saving heap usage by giving up possibly more computation 
 * complexity.</p>
 * 
 * @author jent - Mike Jensen
 */
public class LightCharSequence implements CharSequence {
  private static final int HASH_START_PRIME = 31;
  
  protected final char[] chars;
  
  /**
   * Construct a new light char sequence with the following array to back it.  This array is not 
   * copied.
   * 
   * @param chars non-null reference to characters
   */
  public LightCharSequence(char[] chars) {
    this.chars = chars;
  }

  @Override
  public char charAt(int i) {
    return chars[i];
  }

  @Override
  public int length() {
    return chars.length;
  }

  @Override
  public CharSequence subSequence(int arg0, int arg1) {
    int size = arg1 - arg0;
    if (size < 0) {
      throw new IndexOutOfBoundsException();
    } else if (size == 0) {
      return new LightCharSequence(new char[0]);
    }
    
    char[] resultChars = new char[size];
    System.arraycopy(chars, arg0, resultChars, 0, size);
    
    return new LightCharSequence(resultChars);
  }
  
  @Override
  public String toString() {
    // we intern these strings since this class is focused on saving heap
    return new String(chars).intern();
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof CharSequence) {
      CharSequence cs = (CharSequence)o;
      if (chars.length == cs.length()) {
        for (int i = 0; i < chars.length; i++) {
          if (chars[i] != cs.charAt(i)) {
            return false;
          }
        }
        
        return true;
      }
    }
    return false;
  }
  
  @Override
  public int hashCode() {
    int result = HASH_START_PRIME;
    for (char c : chars) {
      result ^= c;
    }
    return result;
  }
}
