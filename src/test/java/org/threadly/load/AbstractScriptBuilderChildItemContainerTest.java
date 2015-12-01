package org.threadly.load;

import static org.junit.Assert.*;

import org.junit.Test;
import org.threadly.load.AbstractScriptBuilder.ChildItemContainer;
import org.threadly.load.ExecutableScript.ExecutionItem;

@SuppressWarnings("javadoc")
public class AbstractScriptBuilderChildItemContainerTest {
  @Test
  public void emptyConstructorTest() {
    assertNull(ChildItemContainer.EMPTY_CHILD_ITEMS_CONTAINER.items);
    assertTrue(ChildItemContainer.EMPTY_CHILD_ITEMS_CONTAINER.itemsRunSequential());
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
    assertFalse(new ChildItemContainer(new ExecutionItem[0], false).hasChildren());
    assertFalse(ChildItemContainer.EMPTY_CHILD_ITEMS_CONTAINER.hasChildren());
    assertTrue(new ChildItemContainer(new ExecutionItem[] { null }, false).hasChildren());
  }
  
  @Test
  public void iteratorTest() {
    assertFalse(ChildItemContainer.EMPTY_CHILD_ITEMS_CONTAINER.iterator().hasNext());
    assertTrue(new ChildItemContainer(new ExecutionItem[1], false).iterator().hasNext());
  }
}
