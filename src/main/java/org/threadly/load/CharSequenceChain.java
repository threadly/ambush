package org.threadly.load;

/**
 * <p>Char sequence which is built from a chain of sequences.</p>
 * 
 * @author jent - Mike Jensen
 */
public class CharSequenceChain implements CharSequence {
  private static final int HASH_START_PRIME = 31;
  
  private final CharSequence[] chain;
  
  /**
   * Construct a new char sequence from several char sequences.
   * 
   * @param chain Chain of sequences that produces this sequence
   */
  public CharSequenceChain(CharSequence ... chain) {
    int index = 0;
    CharSequence[] fooChain = new CharSequence[chain.length];
    for (CharSequence cs : chain) {
      if (cs.length() > 0) {
        if (cs instanceof String) {
          cs = new LightCharSequence(((String)cs).toCharArray());
        }
        fooChain[index++] = cs;
      }
    }
    
    if (index == chain.length) {
      this.chain = fooChain;
    } else {
      CharSequence[] newChain = new CharSequence[index];
      System.arraycopy(fooChain, 0, newChain, 0, index);
      this.chain = newChain;
    }
  }

  @Override
  public char charAt(int index) {
    int currIndex = 0;
    for (CharSequence cs : chain) {
      if (currIndex + cs.length() > index) {
        return cs.charAt(index - currIndex);
      } else {
        currIndex += cs.length();
      }
    }
    
    throw new IndexOutOfBoundsException();
  }

  @Override
  public int length() {
    int result = 0;
    for (CharSequence cs : chain) {
      result += cs.length();
    }
    
    return result;
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    // TODO - improve performance dramatically by not using charAt to build result
    
    char[] resultArray = new char[end - start];
    for (int i = 0; i < resultArray.length; i++) {
      resultArray[i] = charAt(start + i);
    }
    
    return new LightCharSequence(resultArray);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (CharSequence cs : chain) {
      sb.append(cs);
    }
    return sb.toString();
  }
  
  @Override
  public int hashCode() {
    int result = HASH_START_PRIME;
    for (CharSequence cs : chain) {
      result ^= cs.hashCode();
    }
    return result;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof CharSequence) {
      return this.toString().equals(o.toString());
    } else {
      return false;
    }
  }
}
