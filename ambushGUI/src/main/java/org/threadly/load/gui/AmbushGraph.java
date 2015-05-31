package org.threadly.load.gui;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.threadly.concurrent.PrioritySchedulerInterface;
import org.threadly.util.ArgumentVerifier;
import org.threadly.util.Clock;

/**
 * <p>Class which handles drawing a window to show a given graph.</p>
 * 
 * @author jent - Mike Jensen
 */
public class AmbushGraph implements DragDetectListener, MouseListener, MouseMoveListener {
  private static final int LARGE_X_SIZE = 1900;
  private static final int LARGE_Y_SIZE = 1400;
  private static final int SMALL_X_SIZE = 1280;
  private static final int SMALL_Y_SIZE = 1024;
  private static final int DRAG_TOLLERANCE = 25;
  private static final int REFRESH_DELAY = 500; // how often to check for new guiNodeMap
  private static final int BACKGROUND_GRAY = 210;
  private static final int GRID_SOFTNESS = 100;  // randomness for point placement
  private static final int DISTANCE_FROM_EDGE = 75;  // dots wont be placed within this distance from the edge
  private static final Random RANDOM = new Random(Clock.lastKnownTimeMillis());

  private final PrioritySchedulerInterface scheduler;
  private final Color backgroundColor;
  private final Shell shell;
  private volatile Map<Node, GuiPoint> guiNodeMap;
  private GuiPoint movingPoint = null;
  
  /**
   * Constructs a new window which will display the graph of nodes.  Nodes will be provided via 
   * {@link #updateGraphModel(Node)}.
   * 
   * @param scheduler Scheduler to schedule and execute tasks on to
   * @param display A non-disposed display to open the shell on
   */
  public AmbushGraph(PrioritySchedulerInterface scheduler, Display display) {
    this(scheduler, display, -1, -1);
  }

  /**
   * Constructs a new window which will display the graph of nodes.  Nodes will be provided via 
   * {@link #updateGraphModel(Node)}.  This constructor allows you to specify the original window 
   * size.
   * 
   * @param scheduler Scheduler to schedule and execute tasks on to
   * @param display A non-disposed display to open the shell on
   * @param xSize Width in pixels for the window
   * @param ySize Height in pixels for the window
   */
  public AmbushGraph(PrioritySchedulerInterface scheduler, Display display, int xSize, int ySize) {
    ArgumentVerifier.assertNotNull(scheduler, "scheduler");
    
    if (xSize < 1 || ySize < 1) {
      Rectangle displayBounds = display.getBounds();
      if (displayBounds.width > LARGE_X_SIZE && displayBounds.height > LARGE_Y_SIZE) {
        xSize = LARGE_X_SIZE;
        ySize = LARGE_Y_SIZE;
      } else {
        xSize = SMALL_X_SIZE;
        ySize = SMALL_Y_SIZE;
      }
    }
    
    this.scheduler = scheduler;
    backgroundColor = new Color(display, BACKGROUND_GRAY, BACKGROUND_GRAY, BACKGROUND_GRAY);
    guiNodeMap = new HashMap<Node, GuiPoint>();
    
    shell = new Shell(display);
    shell.setText("Ambush execution graph");
    shell.setSize(xSize, ySize);
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
  }

  /**
   * Opens the shell and handles doing the read and dispatch loop for the display.  This call will 
   * block until the shell is closed.
   */
  public void runGuiLoop() {
    shell.open();
    
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
    Map<Integer, List<GuiPoint>> xRegionCountMap = new HashMap<Integer, List<GuiPoint>>();
    traverseNode(headNode, buildingMap, 1, 1, xRegionCountMap);
    
    // cleanup xRegionCountMap
    Iterator<List<GuiPoint>> it = xRegionCountMap.values().iterator();
    while (it.hasNext()) {
      List<GuiPoint> xRegion = it.next();
      Collections.sort(xRegion, new Comparator<GuiPoint>() {
        @Override
        public int compare(GuiPoint o1, GuiPoint o2) {
          return o1.yRegion - o2.yRegion;
        }
      });
      Iterator<GuiPoint> points = xRegion.iterator();
      int currentPoint = 0;
      while (points.hasNext()) {
        points.next().yRegion = ++currentPoint;
      }
    }
    
    guiNodeMap = buildingMap;
  }
  
  private void traverseNode(Node currentNode, Map<Node, GuiPoint> buildingMap, 
                            int xRegion, int yRegion, Map<Integer, List<GuiPoint>> xRegionCountMap) {
    GuiPoint currentPoint = buildingMap.get(currentNode);
    if (currentPoint == null) {
      currentPoint = new GuiPoint(makeRandomColor(), 
                                  shell.getBounds().width, shell.getBounds().height, 
                                  xRegionCountMap, xRegion, yRegion);
      buildingMap.put(currentNode, currentPoint);
      add(currentPoint, xRegionCountMap);
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
  
  private static void add(GuiPoint point, Map<Integer, List<GuiPoint>> map) {
    List<GuiPoint> currList = map.get(point.xRegion);
    if (currList == null) {
      currList = new LinkedList<GuiPoint>();
      map.put(point.xRegion, currList);
    }
    if (! currList.contains(point)) {
      currList.add(point);
    }
  }
  
  private static void remove(GuiPoint point, Map<Integer, List<GuiPoint>> map) {
    List<GuiPoint> currList = map.get(point.xRegion);
    if (currList != null) {
      currList.remove(point);
    }
  }
  
  private void shiftLeft(Node currNode, GuiPoint point, 
                         Map<Node, GuiPoint> buildingMap, int shiftAmount, 
                         Map<Integer, List<GuiPoint>> xRegionCountMap, Set<Node> shiftedNodes) {
    remove(point, xRegionCountMap);
    point.xRegion += shiftAmount;
    add(point, xRegionCountMap);
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
          System.err.println("***** " + entry.getKey().getName() + 
                               " is connected to an unknown node: " + child.getName() + " *****");
          continue;
        }
        
        gc.drawLine(entry.getValue().getX(), entry.getValue().getY(), childPoint.getX(), childPoint.getY());
      }
      
      // Draw the label last
      gc.setForeground(new Color(shell.getDisplay(), 0, 0, 0));
      gc.setBackground(backgroundColor);
      gc.drawText(entry.getValue().xRegion + "/" + entry.getValue().yRegion + "-" + entry.getKey().getName(), entry.getValue().getX() + 10, entry.getValue().getY() - 5);
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
      movingPoint.setPosition(Math.max(Math.min(me.x, shell.getBounds().width - 25), 10), 
                              Math.max(Math.min(me.y, shell.getBounds().height - 45), 10));

      shell.redraw();
    }
    movingPoint = null;
  }

  @Override
  public void mouseMove(MouseEvent me) {
    if (movingPoint != null) {
      movingPoint.setPosition(Math.max(Math.min(me.x, shell.getBounds().width - 25), 10), 
                              Math.max(Math.min(me.y, shell.getBounds().height - 45), 10));
      
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
    if (pos < DISTANCE_FROM_EDGE || (pos < maxDimension - DISTANCE_FROM_EDGE && RANDOM.nextBoolean())) {
      pos += softness;
    } else {
      pos -= softness;
    }
    if (pos < DISTANCE_FROM_EDGE) {
      pos = DISTANCE_FROM_EDGE;
    } else if (pos > maxDimension - DISTANCE_FROM_EDGE) {
      pos = maxDimension - DISTANCE_FROM_EDGE;
    }
    return pos;
  }
  
  private static class GuiPoint {
    private final Color color;
    private final int xSize;
    private final int ySize;
    private Map<Integer, List<GuiPoint>> xRegionCountMap;
    private int xRegion;
    private int yRegion;
    private boolean coordiantesSet;
    private int x;
    private int y;
    
    public GuiPoint(Color color, int xSize, int ySize, 
                    Map<Integer, List<GuiPoint>> xRegionCountMap, int xRegion, int yRegion) {
      this.color = color;
      this.xSize = xSize;
      this.ySize = ySize;
      this.xRegionCountMap = xRegionCountMap;
      this.xRegion = xRegion;
      this.yRegion = yRegion;
    }
    
    private void ensureCoordinatesSet() {
      if (! coordiantesSet) {
        coordiantesSet = true;
        if (xRegion == 1) {
          x = DISTANCE_FROM_EDGE;
        } else {
          x = getSoftGridPoint(xRegion, xRegionCountMap.size(), xSize);
        }
        y = getSoftGridPoint(yRegion, xRegionCountMap.get(xRegion).size(), ySize);
        xRegionCountMap = null; // no longer needed, allow GC
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
