package org.threadly.load;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.threadly.load.ExecutableScript.ExecutionItem;
import org.threadly.load.ExecutableScript.ExecutionItem.ChildItems;
import org.threadly.load.gui.Node;

/**
 * <p>Class which builds a graph of {@link Node}'s based off a script produced by a 
 * {@link ScriptFactory}.  This builds a script and then traverses the executable items to produce 
 * a graph of {@link Node} objects.</p>
 * 
 * @author jent - Mike Jensen
 */
public class ScriptGraphBuilder extends AbstractScriptFactoryInitializer {
  /**
   * Builds a graph from an array of arguments.  It is expected that the first argument is the 
   * {@link ScriptFactory} class.  The following arguments should be parameters for that factory 
   * in the form of key=value.
   * 
   * @param args Arguments to construct {@link ScriptFactory} with
   * @return The head node for the graph provided from the script
   */
  public static Node buildGraph(String[] args) {
    return new ScriptGraphBuilder(args).makeGraph();
  }
  
  /**
   * Makes a {@link Node} graph from the list of provided steps.  It is expected that these 
   * initial steps are ran sequentially between each other, but it can handle steps which fork 
   * out into parallel channels.
   * 
   * @param steps Collection of sequential steps to start graph production from
   * @return Head node of a graph which matches the steps execution
   */
  public static Node makeGraph(ExecutionItem[] steps) {
    Node head = new Node("start");
    Node current = head;
    for (ExecutionItem step : steps) {
      current = expandNode(current, step, new AtomicInteger());
    }
    
    head.cleanGraph();
    
    return head;
  }
  
  private static Node expandNode(Node previousNode, ExecutionItem item, AtomicInteger chainLength) {
    ChildItems childItems = item.getChildItems();
    if (! childItems.hasChildren()) {
      Node result = new Node(item.toString());
      previousNode.addChildNode(result);
      chainLength.incrementAndGet();
      return result;
    } else {
      int maxLength = -1;
      List<Node> childNodes = new LinkedList<Node>();
      Node longestNode = previousNode;
      Iterator<ExecutionItem> it = childItems.iterator();
      if (! childItems.itemsRunSequential()) {
        Node branchPoint = new Node();
        previousNode.addChildNode(branchPoint);
        previousNode = branchPoint;
      }
      while (it.hasNext()) {
        ExecutionItem childItem = it.next();
        AtomicInteger length = new AtomicInteger();
        Node endNode = expandNode(previousNode, childItem, length);
        if (childItems.itemsRunSequential()) {
          previousNode = endNode;
        }
        if (length.get() >= maxLength) {
          maxLength = length.get();
          longestNode = endNode;
        }
        childNodes.add(endNode);
      }
      if (childItems.itemsRunSequential() || maxLength < 1) {
        return longestNode;
      } else {
        Node joinPoint = new Node();
        for(Node n : childNodes) {
          n.addChildNode(joinPoint);
        }
        return joinPoint;
      }
    }
  }

  protected ScriptGraphBuilder(String[] args) {
    super(args);
  }
  
  protected Node makeGraph() {
    return ScriptGraphBuilder.makeGraph(script.steps);
  }
}
