package org.threadly.load;

import static org.junit.Assert.*;

import org.junit.Test;
import org.threadly.load.AbstractScriptBuilder.ChildItemContainer;
import org.threadly.load.ExecutableScript.ExecutionItem;

@SuppressWarnings("javadoc")
public class AbstractScriptBuilderChildItemContainerTest {
  @Test
  public void emptyConstructorTest() {
    assertNull(new ChildItemContainer().items);
    assertTrue(new ChildItemContainer().itemsRunSequential());
  }
  
  @Test
  public void constructorTest() {
    ExecutionItem[] items = new ExecutionItem[0];
    ChildItemContainer cic = new ChildItemContainer(items, false);
    assertTrue(cic.items == items);
    assertFalse(cic.itemsRunSequential());
  }
  
  @Test
  public void hasChildrenTest() {
    assertTrue(new ChildItemContainer(new ExecutionItem[0], false).hasChildren());
    assertFalse(new ChildItemContainer().hasChildren());
  }
  
  @Test
  public void iteratorTest() {
    assertFalse(new ChildItemContainer().iterator().hasNext());
    assertTrue(new ChildItemContainer(new ExecutionItem[1], false).iterator().hasNext());
  }
}
