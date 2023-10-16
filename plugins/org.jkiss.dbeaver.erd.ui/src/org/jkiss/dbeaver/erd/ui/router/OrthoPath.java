package org.jkiss.dbeaver.erd.ui.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;

public class OrthoPath {

    /**
     * A Stack of segments.
     */
    private static class SegmentStack extends ArrayList {

        VertexSegment pop() {
            return (VertexSegment) remove(size() - 1);
        }

        VertexRectangle popVertexRectangle() {
            return (VertexRectangle) remove(size() - 1);
        }

        void push(Object obj) {
            add(obj);
        }

    }

    private static final Point CURRENT = new Point();
    private static final double EPSILON = 1.04;
    private static final Point NEXT = new Point();
    private static final double OVAL_CONSTANT = 1.13;

    /**
     * The bendpoint constraints. The path must go through these bendpoints.
     */
    PointList bendpoints;
    /**
     * An arbitrary data field which can be used to map a Path back to some client
     * object.
     */
    public Object data;
    List excludedVertexRectangles;
    List grownSegments;
    /**
     * this field is for internal use only. It is true whenever a property has been
     * changed which requires the solver to resolve this path.
     */
    public boolean isDirty = true;

    boolean isInverted = false;
    boolean isMarked = false;
    PointList points;

    /**
     * The previous cost ratio of the path. The cost ratio is the actual path length
     * divided by the length from the start to the end.
     */
    private double prevCostRatio;
    List segments;

    private SegmentStack stack;
    VertexPoint start, end;
    private OrthoPath subPath;
    double threshold;
    Set visibleVertexRectangles;
    Set visibleVertices;

    /**
     * Constructs a new path.
     * 
     * @since 3.0
     */
    public OrthoPath() {
        segments = new ArrayList();
        grownSegments = new ArrayList();
        points = new PointList();
        visibleVertices = new HashSet();
        stack = new SegmentStack();
        visibleVertexRectangles = new HashSet();
        excludedVertexRectangles = new ArrayList();
    }

    /**
     * Constructs a new path with the given data.
     * 
     * @since 3.0
     * @param data an arbitrary data field
     */
    public OrthoPath(Object data) {
        this();
        this.data = data;
    }

    /**
     * Constructs a new path with the given data, start and end point.
     * 
     * @param start the start point for this path
     * @param end   the end point for this path
     */
    public OrthoPath(Point start, Point end) {
        this(new VertexPoint(start, null), new VertexPoint(end, null));
    }

    /**
     * Creates a path between the given vertices.
     * 
     * @param start start vertex
     * @param end   end vertex
     */
    OrthoPath(VertexPoint start, VertexPoint end) {
        this();
        this.start = start;
        this.end = end;
    }

    /**
     * Attempts to add all segments between the given VertexRectangles to the
     * visibility graph.
     * 
     * @param source the source VertexRectangle
     * @param target the target VertexRectangle
     */
    private void addAllSegmentsBetween(VertexRectangle source, VertexRectangle target) {
        addConnectingSegment(new VertexSegment(source.bottomLeft, target.bottomLeft), source, target, false, false);
        addConnectingSegment(new VertexSegment(source.bottomRight, target.bottomRight), source, target, true, true);
        addConnectingSegment(new VertexSegment(source.topLeft, target.topLeft), source, target, true, true);
        addConnectingSegment(new VertexSegment(source.topRight, target.topRight), source, target, false, false);

        if (source.bottom() == target.bottom()) {
            addConnectingSegment(new VertexSegment(source.bottomLeft, target.bottomRight), source, target, false, true);
            addConnectingSegment(new VertexSegment(source.bottomRight, target.bottomLeft), source, target, true, false);
        }
        if (source.y == target.y) {
            addConnectingSegment(new VertexSegment(source.topLeft, target.topRight), source, target, true, false);
            addConnectingSegment(new VertexSegment(source.topRight, target.topLeft), source, target, false, true);
        }
        if (source.x == target.x) {
            addConnectingSegment(new VertexSegment(source.bottomLeft, target.topLeft), source, target, false, true);
            addConnectingSegment(new VertexSegment(source.topLeft, target.bottomLeft), source, target, true, false);
        }
        if (source.right() == target.right()) {
            addConnectingSegment(new VertexSegment(source.bottomRight, target.topRight), source, target, true, false);
            addConnectingSegment(new VertexSegment(source.topRight, target.bottomRight), source, target, false, true);
        }
    }

    /**
     * Attempts to add a segment between the given VertexRectangles to the
     * visibility graph. This method is specifically written for the case where the
     * two VertexRectangles intersect and contains a boolean as to whether to check
     * the diagonal that includes the top right point of the other VertexRectangle.
     * 
     * @param segment        the segment to check
     * @param o1             the first VertexRectangle
     * @param o2             the second VertexRectangle
     * @param checkTopRight1 whether or not to check the diagonal containing top
     *                       right point
     */
    private void addConnectingSegment(VertexSegment segment, VertexRectangle o1, VertexRectangle o2, boolean checkTopRight1,
        boolean checkTopRight2) {
        if (threshold != 0 && (segment.end.getDistance(end) + segment.end.getDistance(start) > threshold
            || segment.start.getDistance(end) + segment.start.getDistance(start) > threshold))
            return;

        if (o2.containsProper(segment.start) || o1.containsProper(segment.end))
            return;

        if (checkTopRight1 && segment.intersects(o1.x, o1.bottom() - 1, o1.right() - 1, o1.y))
            return;
        if (checkTopRight2 && segment.intersects(o2.x, o2.bottom() - 1, o2.right() - 1, o2.y))
            return;
        if (!checkTopRight1 && segment.intersects(o1.x, o1.y, o1.right() - 1, o1.bottom() - 1))
            return;
        if (!checkTopRight2 && segment.intersects(o2.x, o2.y, o2.right() - 1, o2.bottom() - 1))
            return;

        stack.push(o1);
        stack.push(o2);
        stack.push(segment);
    }

    /**
     * Adds an VertexRectangle to the visibility graph and generates new segments
     * 
     * @param newObs the new VertexRectangle, should not be in the graph already
     */
    private void addVertexRectangle(VertexRectangle newObs) {
        visibleVertexRectangles.add(newObs);
        Iterator oItr = new HashSet(visibleVertexRectangles).iterator();
        while (oItr.hasNext()) {
            VertexRectangle currObs = (VertexRectangle) oItr.next();
            if (newObs != currObs)
                addSegmentsFor(newObs, currObs);
        }
        addPerimiterSegments(newObs);
        addSegmentsFor(start, newObs);
        addSegmentsFor(end, newObs);
    }

    /**
     * Adds the segments along the perimiter of an VertexRectangle to the visiblity
     * graph queue.
     * 
     * @param obs the VertexRectangle
     */
    private void addPerimiterSegments(VertexRectangle obs) {
        VertexSegment seg = new VertexSegment(obs.topLeft, obs.topRight);
        stack.push(obs);
        stack.push(null);
        stack.push(seg);
        seg = new VertexSegment(obs.topRight, obs.bottomRight);
        stack.push(obs);
        stack.push(null);
        stack.push(seg);
        seg = new VertexSegment(obs.bottomRight, obs.bottomLeft);
        stack.push(obs);
        stack.push(null);
        stack.push(seg);
        seg = new VertexSegment(obs.bottomLeft, obs.topLeft);
        stack.push(obs);
        stack.push(null);
        stack.push(seg);
    }

    /**
     * Attempts to add a segment to the visibility graph. First checks to see if the
     * segment is outside the threshold oval. Then it compares the segment against
     * all VertexRectangles. If it is clean, the segment is finally added to the
     * graph.
     * 
     * @param segment             the segment
     * @param exclude1            an VertexRectangle to exclude from the search
     * @param exclude2            another VertexRectangle to exclude from the search
     * @param allVertexRectangles the list of all VertexRectangles
     */
    private void addSegment(VertexSegment segment, VertexRectangle exclude1, VertexRectangle exclude2, List allVertexRectangles) {
        if (threshold != 0 && (segment.end.getDistance(end) + segment.end.getDistance(start) > threshold
            || segment.start.getDistance(end) + segment.start.getDistance(start) > threshold))
            return;

        for (int i = 0; i < allVertexRectangles.size(); i++) {
            VertexRectangle obs = (VertexRectangle) allVertexRectangles.get(i);

            if (obs == exclude1 || obs == exclude2 || obs.exclude)
                continue;

            if (segment.intersects(obs.x, obs.y, obs.right() - 1, obs.bottom() - 1)
                || segment.intersects(obs.x, obs.bottom() - 1, obs.right() - 1, obs.y)
                || obs.containsProper(segment.start) || obs.containsProper(segment.end)) {
                if (!visibleVertexRectangles.contains(obs))
                    addVertexRectangle(obs);
                return;
            }
        }

        linkVertices(segment);
    }

    /**
     * Adds the segments between the given VertexRectangles.
     * 
     * @param source source VertexRectangle
     * @param target target VertexRectangle
     */
    private void addSegmentsFor(VertexRectangle source, VertexRectangle target) {
        if (source.intersects(target))
            addAllSegmentsBetween(source, target);
        else if (target.bottom() - 1 < source.y)
            addSegmentsTargetAboveSource(source, target);
        else if (source.bottom() - 1 < target.y)
            addSegmentsTargetAboveSource(target, source);
        else if (target.right() - 1 < source.x)
            addSegmentsTargetBesideSource(source, target);
        else
            addSegmentsTargetBesideSource(target, source);
    }

    /**
     * Adds the segments between the given VertexRectangles.
     * 
     * @param source source VertexRectangle
     * @param target target VertexRectangle
     */
    private void addSegmentsFor(VertexPoint vertex, VertexRectangle obs) {
        VertexSegment seg = null;
        VertexSegment seg2 = null;

        switch (obs.getPosition(vertex)) {
            case PositionConstants.SOUTH_WEST:
            case PositionConstants.NORTH_EAST:
                seg = new VertexSegment(vertex, obs.topLeft);
                seg2 = new VertexSegment(vertex, obs.bottomRight);
                break;
            case PositionConstants.SOUTH_EAST:
            case PositionConstants.NORTH_WEST:
                seg = new VertexSegment(vertex, obs.topRight);
                seg2 = new VertexSegment(vertex, obs.bottomLeft);
                break;
            case PositionConstants.NORTH:
                seg = new VertexSegment(vertex, obs.topLeft);
                seg2 = new VertexSegment(vertex, obs.topRight);
                break;
            case PositionConstants.EAST:
                seg = new VertexSegment(vertex, obs.bottomRight);
                seg2 = new VertexSegment(vertex, obs.topRight);
                break;
            case PositionConstants.SOUTH:
                seg = new VertexSegment(vertex, obs.bottomRight);
                seg2 = new VertexSegment(vertex, obs.bottomLeft);
                break;
            case PositionConstants.WEST:
                seg = new VertexSegment(vertex, obs.topLeft);
                seg2 = new VertexSegment(vertex, obs.bottomLeft);
                break;
            default:
                if (vertex.x == obs.x) {
                    seg = new VertexSegment(vertex, obs.topLeft);
                    seg2 = new VertexSegment(vertex, obs.bottomLeft);
                } else if (vertex.y == obs.y) {
                    seg = new VertexSegment(vertex, obs.topLeft);
                    seg2 = new VertexSegment(vertex, obs.topRight);
                } else if (vertex.y == obs.bottom() - 1) {
                    seg = new VertexSegment(vertex, obs.bottomLeft);
                    seg2 = new VertexSegment(vertex, obs.bottomRight);
                } else if (vertex.x == obs.right() - 1) {
                    seg = new VertexSegment(vertex, obs.topRight);
                    seg2 = new VertexSegment(vertex, obs.bottomRight);
                } else {
                    throw new RuntimeException("Unexpected vertex conditions"); //$NON-NLS-1$
                }
        }

        stack.push(obs);
        stack.push(null);
        stack.push(seg);
        stack.push(obs);
        stack.push(null);
        stack.push(seg2);
    }

    private void addSegmentsTargetAboveSource(VertexRectangle source, VertexRectangle target) {
        // target located above source
        VertexSegment seg = null;
        VertexSegment seg2 = null;
        if (target.x > source.x) {
            seg = new VertexSegment(source.topLeft, target.topLeft);
            if (target.x < source.right() - 1)
                seg2 = new VertexSegment(source.topRight, target.bottomLeft);
            else
                seg2 = new VertexSegment(source.bottomRight, target.topLeft);
        } else if (source.x == target.x) {
            seg = new VertexSegment(source.topLeft, target.bottomLeft);
            seg2 = new VertexSegment(source.topRight, target.bottomLeft);
        } else {
            seg = new VertexSegment(source.bottomLeft, target.bottomLeft);
            seg2 = new VertexSegment(source.topRight, target.bottomLeft);
        }

        stack.push(source);
        stack.push(target);
        stack.push(seg);
        stack.push(source);
        stack.push(target);
        stack.push(seg2);
        seg = null;
        seg2 = null;

        if (target.right() < source.right()) {
            seg = new VertexSegment(source.topRight, target.topRight);
            if (target.right() - 1 > source.x)
                seg2 = new VertexSegment(source.topLeft, target.bottomRight);
            else
                seg2 = new VertexSegment(source.bottomLeft, target.topRight);
        } else if (source.right() == target.right()) {
            seg = new VertexSegment(source.topRight, target.bottomRight);
            seg2 = new VertexSegment(source.topLeft, target.bottomRight);
        } else {
            seg = new VertexSegment(source.bottomRight, target.bottomRight);
            seg2 = new VertexSegment(source.topLeft, target.bottomRight);
        }

        stack.push(source);
        stack.push(target);
        stack.push(seg);
        stack.push(source);
        stack.push(target);
        stack.push(seg2);
    }

    private void addSegmentsTargetBesideSource(VertexRectangle source, VertexRectangle target) {
        // target located above source
        VertexSegment seg = null;
        VertexSegment seg2 = null;
        if (target.y > source.y) {
            seg = new VertexSegment(source.topLeft, target.topLeft);
            if (target.y < source.bottom() - 1)
                seg2 = new VertexSegment(source.bottomLeft, target.topRight);
            else
                seg2 = new VertexSegment(source.bottomRight, target.topLeft);
        } else if (source.y == target.y) {
            // degenerate case
            seg = new VertexSegment(source.topLeft, target.topRight);
            seg2 = new VertexSegment(source.bottomLeft, target.topRight);
        } else {
            seg = new VertexSegment(source.topRight, target.topRight);
            seg2 = new VertexSegment(source.bottomLeft, target.topRight);
        }
        stack.push(source);
        stack.push(target);
        stack.push(seg);
        stack.push(source);
        stack.push(target);
        stack.push(seg2);
        seg = null;
        seg2 = null;

        if (target.bottom() < source.bottom()) {
            seg = new VertexSegment(source.bottomLeft, target.bottomLeft);
            if (target.bottom() - 1 > source.y)
                seg2 = new VertexSegment(source.topLeft, target.bottomRight);
            else
                seg2 = new VertexSegment(source.topRight, target.bottomLeft);
        } else if (source.bottom() == target.bottom()) {
            seg = new VertexSegment(source.bottomLeft, target.bottomRight);
            seg2 = new VertexSegment(source.topLeft, target.bottomRight);
        } else {
            seg = new VertexSegment(source.bottomRight, target.bottomRight);
            seg2 = new VertexSegment(source.topLeft, target.bottomRight);
        }
        stack.push(source);
        stack.push(target);
        stack.push(seg);
        stack.push(source);
        stack.push(target);
        stack.push(seg2);
    }

    /**
    * 
    */
    void cleanup() {
        // segments.clear();
        visibleVertices.clear();
    }

    /**
     * Begins the creation of the visibility graph with the first segment
     * 
     * @param allVertexRectangles list of all VertexRectangles
     */
    private void createVisibilityGraph(List allVertexRectangles) {
        stack.push(null);
        stack.push(null);
        stack.push(new VertexSegment(start, end));

        while (!stack.isEmpty())
            addSegment(stack.pop(), stack.popVertexRectangle(), stack.popVertexRectangle(), allVertexRectangles);
    }

    /**
     * Once the visibility graph is constructed, this is called to label the graph
     * and determine the shortest path. Returns false if no path can be found.
     * 
     * @return true if a path can be found.
     */
    private boolean determineShortestPath() {
        if (!labelGraph())
            return false;
        VertexPoint vertex = end;
        prevCostRatio = end.cost / start.getDistance(end);

        VertexPoint nextVertex;
        while (!vertex.equals(start)) {
            nextVertex = vertex.label;
            if (nextVertex == null)
                return false;
            VertexSegment s = new VertexSegment(nextVertex, vertex);
            segments.add(s);
            vertex = nextVertex;
        }

        Collections.reverse(segments);
        return true;
    }

    /**
     * Resets all necessary fields for a solve.
     */
    void fullReset() {
        visibleVertices.clear();
        segments.clear();
        if (prevCostRatio == 0) {
            double distance = start.getDistance(end);
            threshold = distance * OVAL_CONSTANT;
        } else
            threshold = prevCostRatio * EPSILON * start.getDistance(end);
        visibleVertexRectangles.clear();
        resetPartial();
    }

    /**
     * Creates the visibility graph and returns whether or not a shortest path could
     * be determined.
     * 
     * @param allVertexRectangles the list of all VertexRectangles
     * @return true if a shortest path was found
     */
    boolean generateShortestPath(List allVertexRectangles) {
        createVisibilityGraph(allVertexRectangles);

        if (visibleVertices.size() == 0)
            return false;

        return determineShortestPath();
    }

    /**
     * Returns the list of constrained points through which this path must pass or
     * <code>null</code>.
     * 
     * @see #setBendPoints(PointList)
     * @return list of bend points
     */
    public PointList getBendPoints() {
        return bendpoints;
    }

    /**
     * Returns the end point for this path
     * 
     * @return end point for this path
     */
    public Point getEndPoint() {
        return end;
    }

    /**
     * Returns the solution to this path.
     * 
     * @return the points for this path.
     */
    public PointList getPoints() {
        return points;
    }

    /**
     * Returns the start point for this path
     * 
     * @return start point for this path
     */
    public Point getStartPoint() {
        return start;
    }

    /**
     * Returns a subpath for this path at the given segment
     * 
     * @param currentSegment the segment at which the subpath should be created
     * @return the new path
     */
    OrthoPath getSubPath(VertexSegment currentSegment) {
        // ready new path
        OrthoPath newPath = new OrthoPath(currentSegment.start, end);
        newPath.grownSegments = new ArrayList(
            grownSegments.subList(grownSegments.indexOf(currentSegment), grownSegments.size()));

        // fix old path
        grownSegments = new ArrayList(grownSegments.subList(0, grownSegments.indexOf(currentSegment) + 1));
        end = currentSegment.end;

        subPath = newPath;
        return newPath;
    }

    /**
     * Resets the vertices that this path has traveled prior to this segment. This
     * is called when the path has become inverted and needs to rectify any labeling
     * mistakes it made before it knew it was inverted.
     * 
     * @param currentSegment the segment at which the path found it was inverted
     */
    void invertPriorVertices(VertexSegment currentSegment) {
        int stop = grownSegments.indexOf(currentSegment);
        for (int i = 0; i < stop; i++) {
            VertexPoint vertex = ((VertexSegment) grownSegments.get(i)).end;
            if (vertex.type == VertexPoint.INNIE)
                vertex.type = VertexPoint.OUTIE;
            else
                vertex.type = VertexPoint.INNIE;
        }
    }

    /**
     * Returns true if this VertexRectangle is in the visibility graph
     * 
     * @param obs the VertexRectangle
     * @return true if VertexRectangle is in the visibility graph
     */
    boolean isVertexRectangleVisible(VertexRectangle obs) {
        return visibleVertexRectangles.contains(obs);
    }

    /**
     * Labels the visibility graph to assist in finding the shortest path
     * 
     * @return false if there was a gap in the visibility graph
     */
    private boolean labelGraph() {
        int numPermanentNodes = 1;
        VertexPoint vertex = start;
        VertexPoint neighborVertex = null;
        vertex.isPermanent = true;
        double newCost;
        while (numPermanentNodes != visibleVertices.size()) {
            List neighbors = vertex.neighbors;
            if (neighbors == null)
                return false;
            // label neighbors if they have a new shortest path
            for (int i = 0; i < neighbors.size(); i++) {
                neighborVertex = (VertexPoint) neighbors.get(i);
                if (!neighborVertex.isPermanent) {
                    newCost = vertex.cost + vertex.getDistance(neighborVertex);
                    if (neighborVertex.label == null) {
                        neighborVertex.label = vertex;
                        neighborVertex.cost = newCost;
                    } else if (neighborVertex.cost > newCost) {
                        neighborVertex.label = vertex;
                        neighborVertex.cost = newCost;
                    }
                }
            }
            // find the next none-permanent, labeled vertex with smallest cost
            double smallestCost = 0;
            VertexPoint tempVertex = null;
            Iterator v = visibleVertices.iterator();
            while (v.hasNext()) {
                tempVertex = (VertexPoint) v.next();
                if (!tempVertex.isPermanent && tempVertex.label != null
                    && (tempVertex.cost < smallestCost || smallestCost == 0)) {
                    smallestCost = tempVertex.cost;
                    vertex = tempVertex;
                }
            }
            // set the new vertex to permanent.
            vertex.isPermanent = true;
            numPermanentNodes++;
        }
        return true;
    }

    /**
     * Links two vertices together in the visibility graph
     * 
     * @param segment the segment to add
     */
    private void linkVertices(VertexSegment segment) {
        if (segment.start.neighbors == null)
            segment.start.neighbors = new ArrayList();
        if (segment.end.neighbors == null)
            segment.end.neighbors = new ArrayList();

        if (!segment.start.neighbors.contains(segment.end)) {
            segment.start.neighbors.add(segment.end);
            segment.end.neighbors.add(segment.start);
        }

        visibleVertices.add(segment.start);
        visibleVertices.add(segment.end);
    }

    /**
     * Called to reconnect a subpath back onto this path. Does a depth-first search
     * to reconnect all paths. Should be called after sorting.
     */
    void reconnectSubPaths() {
        if (subPath != null) {
            subPath.reconnectSubPaths();

            VertexSegment changedSegment = (VertexSegment) subPath.grownSegments.remove(0);
            VertexSegment oldSegment = (VertexSegment) grownSegments.get(grownSegments.size() - 1);

            oldSegment.end = changedSegment.end;
            
            grownSegments.addAll(subPath.grownSegments);

            subPath.points.removePoint(0);
            points.removePoint(points.size() - 1);
            points.addAll(subPath.points);

            visibleVertexRectangles.addAll(subPath.visibleVertexRectangles);

            end = subPath.end;
            subPath = null;
        }
    }

    /**
     * Refreshes the exclude field on the VertexRectangles in the list. Excludes all
     * VertexRectangles that contain the start or end point for this path.
     * 
     * @param allVertexRectangles list of all VertexRectangles
     */
    void refreshExcludedVertexRectangles(List allVertexRectangles) {
        excludedVertexRectangles.clear();

        for (int i = 0; i < allVertexRectangles.size(); i++) {
            VertexRectangle o = (VertexRectangle) allVertexRectangles.get(i);
            o.exclude = false;

            if (o.contains(start)) {
                if (o.containsProper(start))
                    o.exclude = true;
                else {
                    /*
                     * $TODO Check for corners. If the path begins exactly at the corner of an
                     * VertexRectangle, the exclude should also be true.
                     * 
                     * Or, change segment intersection so that two segments that share an endpoint
                     * do not intersect.
                     */
                }
            }

            if (o.contains(end)) {
                if (o.containsProper(end))
                    o.exclude = true;
                else {
                    // check for corners. See above statement.
                }
            }

            if (o.exclude && !excludedVertexRectangles.contains(o))
                excludedVertexRectangles.add(o);
        }
    }

    /**
     * Resets the fields for everything in the solve after the visibility graph
     * steps.
     */
    void resetPartial() {
        isMarked = false;
        isInverted = false;
        subPath = null;
        isDirty = false;
        grownSegments.clear();
        points.removeAllPoints();
    }

    /**
     * Sets the list of bend points to the given list and dirties the path.
     * 
     * @param bendPoints the list of bend points
     */
    public void setBendPoints(PointList bendPoints) {
        this.bendpoints = bendPoints;
        isDirty = true;
    }

    /**
     * Sets the end point for this path to the given point.
     * 
     * @param end the new end point for this path
     */
    public void setEndPoint(Point end) {
        if (end.equals(this.end))
            return;
        this.end = new VertexPoint(end, null);
        isDirty = true;
    }

    /**
     * Sets the start point for this path to the given point.
     * 
     * @param start the new start point for this path
     */
    public void setStartPoint(Point start) {
        if (start.equals(this.start))
            return;
        this.start = new VertexPoint(start, null);
        isDirty = true;
    }

    /**
     * Returns <code>true</code> if the path is clean and intersects the given
     * VertexRectangle. Also dirties the path in the process.
     * 
     * @since 3.0
     * @param obs the VertexRectangle
     * @return <code>true</code> if a clean path touches the VertexRectangle
     */
    boolean testAndSet(VertexRectangle obs) {
        if (isDirty)
            return false;
        // This will never actually happen because VertexRectangles are not stored by
        // identity
        if (excludedVertexRectangles.contains(obs))
            return false;

        VertexSegment seg1 = new VertexSegment(obs.topLeft, obs.bottomRight);
        VertexSegment seg2 = new VertexSegment(obs.topRight, obs.bottomLeft);

        for (int s = 0; s < points.size() - 1; s++) {
            points.getPoint(CURRENT, s);
            points.getPoint(NEXT, s + 1);

            if (seg1.intersects(CURRENT, NEXT) || seg2.intersects(CURRENT, NEXT) || obs.contains(CURRENT)
                || obs.contains(NEXT)) {
                isDirty = true;
                return true;
            }
        }
        return false;
    }

}
