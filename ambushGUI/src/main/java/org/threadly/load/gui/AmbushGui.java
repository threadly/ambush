package org.threadly.load.gui;

import org.eclipse.swt.widgets.Display;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.load.ScriptGraphBuilder;

/**
 * <p>Class which is responsible for starting up the gui to display ambush scripts.</p>
 * 
 * @author jent - Mike Jensen
 */
public class AmbushGui {
  /**
   * Main function for starting execution of the gui.
   * 
   * @param args String array representing arguments for execution
   */
  public static void main(String[] args) {
    Display display = null;
    try {
      display = new Display();
    } catch (Throwable t) {
      System.err.println("Exception starting gui: " + t.getMessage());
      t.printStackTrace();
      System.exit(1);
    }
    int cpus = Runtime.getRuntime().availableProcessors();
    PriorityScheduler scheduler = new PriorityScheduler(cpus, false);
    try {
      //Instantiate the GUI part
      try {
        AmbushGraph gui = new AmbushGraph(scheduler, display);
        
        /*Node head = new Node("head");
        Node chain1s1 = new Node("chain1s1");
        head.addChildNode(chain1s1);
        Node chain2s1 = new Node("chain2s1");
        head.addChildNode(chain2s1);
        Node chain2s2 = new Node("chain2s2");
        chain2s1.addChildNode(chain2s2);
        Node chain2p1 = new Node("chain2p1");
        Node chain2p2 = new Node("chain2p2");
        chain2s2.addChildNode(chain2p1);
        chain2s2.addChildNode(chain2p2);
        Node merge = new Node("merge");
        chain1s1.addChildNode(merge);
        chain2p1.addChildNode(merge);
        chain2p2.addChildNode(merge);
        
        Node p1 = new Node("p1");
        Node p1s1 = new Node("p1s1");
        Node p1s2 = new Node("p1s2");
        p1.addChildNode(p1s1);
        p1.addChildNode(p1s2);
        Node p2 = new Node("p2");
        Node p3 = new Node("p3");
        Node p4 = new Node("p4");
        
        merge.addChildNode(p1);
        merge.addChildNode(p2);
        merge.addChildNode(p3);
        merge.addChildNode(p4);
        
        Node end = new Node("tail");
        p1s1.addChildNode(end);
        p1s2.addChildNode(end);
        p2.addChildNode(end);
        p3.addChildNode(end);
        p4.addChildNode(end);
        p4.addChildNode(new Node("fake tail"));
        
        gui.updateGraphModel(head);*/
        
        gui.updateGraphModel(ScriptGraphBuilder.buildGraph(args));
        
        gui.runGuiLoop();
      } finally {
        if (! display.isDisposed()) {
          display.dispose();
        }
      }
    } finally {
      scheduler.shutdown();
    }
  }
}
