package org.threadly.load.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DragDetectEvent;
import org.eclipse.swt.events.DragDetectListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.threadly.concurrent.PrioritySchedulerInterface;
import org.threadly.util.Clock;

/**
 * <p>Class which handles drawing a window to show a given graph.</p>
 * 
 * @author jent - Mike Jensen
 */
public class AmbushGraph implements DragDetectListener, MouseListener, MouseMoveListener {
  private static final int X_SIZE = 1900;
  private static final int Y_SIZE = 1400;
  private static final int DRAG_TOLLERANCE = 25;
  private static final int REFRESH_DELAY = 500;
  private static final int BACKGROUND_GRAY = 210;
  private static final int GRID_SOFTNESS = 50;
  private static final Random RANDOM = new Random(Clock.lastKnownTimeMillis());

  private final Color backgroundColor;
  private final Shell shell;
  private volatile Map<Node, GuiPoint> guiNodeMap;
  private GuiPoint movingPoint = null;
  
  public AmbushGraph(final PrioritySchedulerInterface scheduler, Display display) {
    guiNodeMap = new HashMap<Node, GuiPoint>();
    backgroundColor = new Color(display, BACKGROUND_GRAY, BACKGROUND_GRAY, BACKGROUND_GRAY);
    
    shell = new Shell(display);
    shell.setText("Ambush execution graph");
    shell.setSize(X_SIZE, Y_SIZE);
    shell.setBackground(backgroundColor);
    
    shell.addListener(SWT.Paint, new Listener() {
      @Override
      public void handleEvent(Event arg0) {
        updateDisplay(arg0.gc);
      }
    });
    
    shell.addDragDetectListener(this);
    shell.addMouseListener(this);
    shell.addMouseMoveListener(this);
    
    guiNodeMap = Collections.emptyMap();
    
    scheduler.scheduleWithFixedDelay(new Runnable() {
      private volatile boolean displayTaskExeced = false;
      
      @Override
      public void run() {
        if (! shell.isDisposed() && ! shell.getDisplay().isDisposed()) {
          if (! displayTaskExeced) {
            displayTaskExeced = true;
            shell.getDisplay().asyncExec(new Runnable() {
              @Override
              public void run() {
                displayTaskExeced = false;
                if (! shell.isDisposed() && ! shell.getDisplay().isDisposed()) {
                  shell.redraw();
                }
              }
            });
          }
        } else {
          scheduler.remove(this);
        }
      }
    }, REFRESH_DELAY, REFRESH_DELAY);
  }

  /**
   * Opens the shell and handles doing the read and dispatch loop for the display.  This call will 
   * block until the shell is closed.
   */
  public void runGuiLoop() {
    shell.open();

    while (! shell.isDisposed()) {
      if (! shell.getDisplay().readAndDispatch()) {
        shell.getDisplay().sleep();
      }
    }
  }
  
  /**
   * Updates the graph representation.  This call will start crawling from the head node provided 
   * to explore all child nodes.
   * 
   * @param headNode Node to start building graph from
   */
  public void updateGraphModel(Node headNode) {
    Map<Node, GuiPoint> buildingMap = new HashMap<Node, GuiPoint>();
    traverseNode(headNode, buildingMap, 1, 1, new HashMap<Integer, Integer>());
    
    guiNodeMap = buildingMap;
  }
  
  private void traverseNode(Node currentNode, Map<Node, GuiPoint> buildingMap, 
                            int xRegion, int yRegion, Map<Integer, Integer> xRegionCountMap) {
    GuiPoint currentPoint = buildingMap.get(currentNode);
    if (currentPoint == null) {
      currentPoint = new GuiPoint(makeRandomColor(), xRegionCountMap, xRegion, yRegion);
      buildingMap.put(currentNode, currentPoint);
      increment(xRegion, xRegionCountMap);
      int childNodeRegion = yRegion - 1;
      Iterator<Node> it = currentNode.getChildNodes().iterator();
      while (it.hasNext()) {
        traverseNode(it.next(), buildingMap, xRegion + 1, ++childNodeRegion, xRegionCountMap);
      }
    } else {
      if (xRegion > currentPoint.xRegion) {
        Set<Node> inspectedNodes = new HashSet<Node>();
        inspectedNodes.add(currentNode);
        shiftLeft(currentNode, currentPoint, buildingMap, 
                  xRegion - currentPoint.xRegion, xRegionCountMap, inspectedNodes);
      }
    }
  }
  
  private static void increment(int key, Map<Integer, Integer> map) {
    Integer currValue = map.get(key);
    if (currValue == null) {
      map.put(key, 1);
    } else {
      map.put(key, currValue + 1);
    }
  }
  
  private static void decrement(int key, Map<Integer, Integer> map) {
    Integer currValue = map.get(key);
    if (currValue == null) {
      throw new IllegalStateException("No value for key: " + key);
    } else {
      map.put(key, currValue - 1);
    }
  }
  
  private void shiftLeft(Node currNode, GuiPoint point, Map<Node, GuiPoint> buildingMap, 
                         int shiftAmount, Map<Integer, Integer> xRegionCountMap, Set<Node> shiftedNodes) {
    decrement(point.xRegion, xRegionCountMap);
    point.xRegion += shiftAmount;
    increment(point.xRegion, xRegionCountMap);
    Iterator<Node> it = currNode.getChildNodes().iterator();
    while (it.hasNext()) {
      Node child = it.next();
      if (shiftedNodes.contains(child)) {
        continue;
      } else {
        shiftedNodes.add(child);
      }
      GuiPoint childPoint = buildingMap.get(child);
      if (childPoint != null) {
        shiftLeft(child, childPoint, buildingMap, shiftAmount, xRegionCountMap, shiftedNodes);
      }
    }
  }
  
  private void updateDisplay(GC gc) {
    //gc.setBackground(new Color(shell.getDisplay(), 230, 230, 230));
    //gc.fillRectangle(0, 0, XSIZE, YSIZE);
    Iterator<Entry<Node, GuiPoint>> it = guiNodeMap.entrySet().iterator();
    while (it.hasNext()) {
      Entry<Node, GuiPoint> entry = it.next();
      // draw a dot to indicate node point
      gc.setForeground(entry.getValue().color);
      gc.drawOval(entry.getValue().getX(), entry.getValue().getY(), 5, 5);
      gc.setBackground(entry.getValue().color);
      gc.fillOval(entry.getValue().getX(), entry.getValue().getY(), 5, 5);
      gc.setBackground(backgroundColor);
      
      // draw lines to peer nodes (which may or may not be drawn yet)
      Iterator<Node> it2 = entry.getKey().getChildNodes().iterator();
      while (it2.hasNext()) {
        Node child = it2.next();
        GuiPoint childPoint = guiNodeMap.get(child);
        if (childPoint == null) {
          System.err.println("***** " + entry.getKey().getName() + " is connected to an unknown node: " + child.getName() + " *****");
          continue;
        }
        
        gc.drawLine(entry.getValue().getX(), entry.getValue().getY(), childPoint.getX(), childPoint.getY());
      }
      
      // Draw the label last
      gc.setForeground(new Color(shell.getDisplay(), 0, 0, 0));
      gc.setBackground(backgroundColor);
      gc.drawText(entry.getKey().getName(), entry.getValue().getX() + 10, entry.getValue().getY() - 5);
    }
  }

  @Override
  public void dragDetected(DragDetectEvent dde) {
    synchronized (guiNodeMap) {
      Iterator<GuiPoint> it = guiNodeMap.values().iterator();
      GuiPoint minEntry = null;
      double minDistance = Double.MAX_VALUE;
      while (it.hasNext()) {
        GuiPoint point = it.next();
        if (Math.abs(point.getX() - dde.x) <= DRAG_TOLLERANCE &&
            Math.abs(point.getY() - dde.y) <= DRAG_TOLLERANCE) {
          double distance = Math.sqrt(Math.pow(Math.abs(point.getX() - dde.x), 2) + 
                                      Math.pow(Math.abs(point.getY() - dde.y), 2));
          if (distance < minDistance) {
            minDistance = distance;
            minEntry = point;
          }
        }
      }
      if (minEntry != null) {
        movingPoint = minEntry;
      }
    }
  }

  @Override
  public void mouseDoubleClick(MouseEvent me) {
    // ignored
  }

  @Override
  public void mouseDown(MouseEvent me) {
    // ignored
  }

  @Override
  public void mouseUp(MouseEvent me) {
    if (movingPoint != null) {
      movingPoint.setPosition(Math.max(Math.min(me.x, X_SIZE - 25), 10), 
                              Math.max(Math.min(me.y, Y_SIZE - 45), 10));

      shell.redraw();
    }
    movingPoint = null;
  }

  @Override
  public void mouseMove(MouseEvent me) {
    if (movingPoint != null) {
      movingPoint.setPosition(Math.max(Math.min(me.x, X_SIZE - 25), 10), 
                              Math.max(Math.min(me.y, Y_SIZE - 45), 10));
      
      shell.redraw();
    }
  }
  
  private Color makeRandomColor() {
    final int maxValue = 150;
    int r = RANDOM.nextInt(maxValue);
    int g = RANDOM.nextInt(maxValue);
    int b = RANDOM.nextInt(maxValue);
    return new Color(shell.getDisplay(), r, g, b);
  }
  
  private static int getSoftGridPoint(int region, int totalRegions, int maxDimension) {
    if (region < 1) {
      throw new IllegalArgumentException("Region must be >= 1: " + region);
    } else if (region > totalRegions) {
      throw new IllegalArgumentException("Region can not be beyond total regions: " + region + " / " + totalRegions);
    }
    int spacePerRegion = maxDimension / totalRegions;
    int pos = spacePerRegion / 2;
    pos += (region - 1) * spacePerRegion;
    int softness = RANDOM.nextInt(GRID_SOFTNESS);
    if (RANDOM.nextBoolean()) {
      pos += softness;
    } else {
      pos -= softness;
    }
    return pos;
  }
  
  private static class GuiPoint {
    private final Color color;
    private final Map<Integer, Integer> xRegionCountMap;
    private int xRegion;
    private int yRegion;
    private boolean coordiantesSet;
    private int x;
    private int y;
    
    public GuiPoint(Color color, Map<Integer, Integer> xRegionCountMap, int xRegion, int yRegion) {
      this.color = color;
      this.xRegionCountMap = xRegionCountMap;
      this.xRegion = xRegion;
      this.yRegion = yRegion;
    }
    
    private void ensureCoordinatesSet() {
      if (! coordiantesSet) {
        coordiantesSet = true;
        x = getSoftGridPoint(xRegion, xRegionCountMap.size(), X_SIZE);
        int totalY = xRegionCountMap.get(xRegion);
        if (totalY < yRegion) {
          totalY = yRegion;
          xRegionCountMap.put(xRegion, yRegion);
        }
        y = getSoftGridPoint(yRegion, totalY, Y_SIZE);
      }
    }
    
    public int getX() {
      ensureCoordinatesSet();
      return x;
    }
    
    public int getY() {
      ensureCoordinatesSet();
      return y;
    }
    
    public void setPosition(int x, int y) {
      coordiantesSet = true;
      this.x = x;
      this.y = y;
    }
  }
}
