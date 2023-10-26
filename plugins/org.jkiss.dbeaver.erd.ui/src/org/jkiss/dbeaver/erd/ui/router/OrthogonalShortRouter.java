/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jkiss.dbeaver.erd.ui.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.Rectangle;

public class OrthogonalShortRouter {

    /**
     * A stack of Paths.
     */
    static class PathStack extends ArrayList<Object> {

        private static final long serialVersionUID = 1L;

        OrthoPath pop() {
            return (OrthoPath) remove(size() - 1);
        }

        void push(OrthoPath path) {
            add(path);
        }
    }

    /**
     * The number of times to grow obstacles and test for intersections. This is a
     * tradeoff between performance and quality of output.
     */
    private static final int NUM_GROW_PASSES = 4;

    private int spacing = 10;
    private boolean growPassChangedObstacles;
    private List<OrthoPath> orderedPaths;
    private Map pathsToChildPaths;

    private PathStack stack;
    private List<OrthoPath> subPaths;

    private List<VertexRectangle> userObstacles;
    private List<OrthoPath> userPaths;
    private List<OrthoPath> workingPaths;

    /**
     * Creates a new shortest path routing.
     */
    public OrthogonalShortRouter() {
        userPaths = new ArrayList<>();
        workingPaths = new ArrayList<>();
        pathsToChildPaths = new HashMap<>();
        userObstacles = new ArrayList<>();
    }

    /**
     * Adds an obstacle with the given bounds to the obstacles.
     * 
     * @param rect the bounds of this obstacle
     * @return <code>true</code> if the added obstacle has dirtied one or more paths
     */
    public boolean addObstacle(Rectangle rect) {
        return internalAddObstacle(new VertexRectangle(rect));
    }

    /**
     * Adds a path to the routing.
     * 
     * @param path the path to add.
     */
    public void addPath(OrthoPath path) {
        userPaths.add(path);
        workingPaths.add(path);
    }

    /**
     * Fills the point lists of the Paths to the correct bent points.
     */
    private void bendPaths() {
        for (int i = 0; i < orderedPaths.size(); i++) {
            OrthoPath path = (OrthoPath) orderedPaths.get(i);
            VertexSegment segment = null;
            path.points.addPoint(new Point(path.getStart().x, path.getStart().y));
            for (int v = 0; v < path.getGrownSegments().size(); v++) {
                segment = (VertexSegment) path.getGrownSegments().get(v);
                VertexPoint vertex = segment.end;

                if (vertex != null && v < path.getGrownSegments().size() - 1) {
                    if (vertex.getType() == VertexPoint.INNIE) {
                        vertex.count++;
                        path.points.addPoint(vertex.bend(vertex.count));
                    } else {
                        path.points.addPoint(vertex.bend(vertex.getTotalCount()));
                        vertex.totalCount--;
                    }
                }
            }
            path.points.addPoint(new Point(path.getEnd().x, path.getEnd().y));
        }
    }

    /**
     * Checks a vertex to see if its offset should shrink
     * 
     * @param vertex the vertex to check
     */
    private void checkVertexForIntersections(VertexPoint vertex) {
        if (vertex.getNearestObstacle() != 0 || vertex.isNearestObstacleChecked())
            return;
        int sideLength, x, y;

        sideLength = 2 * (vertex.totalCount * getSpacing()) + 1;

        if ((vertex.getPositionOnObstacle() & PositionConstants.NORTH) > 0) {
        } else if ((vertex.getPositionOnObstacle() & PositionConstants.SOUTH) > 0) {
        } else if ((vertex.getPositionOnObstacle() & PositionConstants.EAST) > 0) {
        } else if ((vertex.getPositionOnObstacle() & PositionConstants.WEST) > 0) {
        }

        if ((vertex.getPositionOnObstacle() & PositionConstants.NORTH) > 0)
            y = vertex.y - sideLength;
        else
            y = vertex.y;
        if ((vertex.getPositionOnObstacle() & PositionConstants.EAST) > 0)
            x = vertex.x;
        else
            x = vertex.x - sideLength;

        Rectangle r = new Rectangle(x, y, sideLength, sideLength);

        int xDist, yDist;

        for (int o = 0; o < userObstacles.size(); o++) {
            VertexRectangle obs = (VertexRectangle) userObstacles.get(o);
            if (obs != vertex.getObs() && r.intersects(obs)) {
                int pos = obs.getPosition(vertex);
                if (pos == 0)
                    continue;

                if ((pos & PositionConstants.NORTH) > 0)
                    // use top
                    yDist = obs.y - vertex.y;
                else
                    // use bottom
                    yDist = vertex.y - obs.bottom() + 1;
                if ((pos & PositionConstants.EAST) > 0)
                    // use right
                    xDist = vertex.x - obs.right() + 1;
                else
                    // use left
                    xDist = obs.x - vertex.x;

                if (Math.max(xDist, yDist) < vertex.getNearestObstacle() || vertex.getNearestObstacle() == 0) {
                    vertex.setNearestObstacle(Math.max(xDist, yDist));
                    vertex.updateOffset();
                }

            }
        }

        vertex.setNearestObstacleChecked(true);
    }

    /**
     * Checks all vertices along paths for intersections
     */
    private void checkVertexIntersections() {
        for (int i = 0; i < workingPaths.size(); i++) {
            OrthoPath path = (OrthoPath) workingPaths.get(i);

            for (int s = 0; s < path.getSegments().size() - 1; s++) {
                VertexPoint vertex = ((VertexSegment) path.getSegments().get(s)).end;
                checkVertexForIntersections(vertex);
            }
        }
    }

    /**
     * Frees up fields which aren't needed between invocations.
     */
    private void cleanup() {
        for (int i = 0; i < workingPaths.size(); i++) {
            OrthoPath path = (OrthoPath) workingPaths.get(i);
            path.cleanup();
        }
    }

    /**
     * Counts how many paths are on given vertices in order to increment their total
     * count.
     */
    private void countVertices() {
        for (int i = 0; i < workingPaths.size(); i++) {
            OrthoPath path = (OrthoPath) workingPaths.get(i);
            for (int v = 0; v < path.getSegments().size() - 1; v++)
                ((VertexSegment) path.getSegments().get(v)).end.totalCount++;
        }
    }

    /**
     * Dirties the paths that are on the given vertex
     * 
     * @param vertex the vertex that has the paths
     */
    private boolean dirtyPathsOn(VertexPoint vertex) {
        List<OrthoPath> paths = vertex.getPaths();
        if (paths != null && paths.size() != 0) {
            for (int i = 0; i < paths.size(); i++)
                ((OrthoPath) paths.get(i)).isDirty = true;
            return true;
        }
        return false;
    }

    /**
     * Returns the closest vertex to the given segment.
     * 
     * @param v1      the first vertex
     * @param v2      the second vertex
     * @param segment the segment
     * @return v1, or v2 whichever is closest to the segment
     */
    private VertexPoint getNearestVertex(VertexPoint v1, VertexPoint v2, VertexSegment segment) {
        if (segment.start.getDistance(v1) + segment.end.getDistance(v1) > segment.start.getDistance(v2)
            + segment.end.getDistance(v2))
            return v2;
        else
            return v1;
    }

    /**
     * Returns the spacing maintained between paths.
     * 
     * @return the default path spacing
     * @see #setSpacing(int)
     * @since 3.2
     */
    public int getSpacing() {
        return spacing;
    }

    /**
     * Returns the subpath for a split on the given path at the given segment.
     * 
     * @param path    the path
     * @param segment the segment
     * @return the new subpath
     */
    private OrthoPath getSubpathForSplit(OrthoPath path, VertexSegment segment) {
        OrthoPath newPath = path.getSubPath(segment);
        workingPaths.add(newPath);
        subPaths.add(newPath);
        return newPath;
    }

    /**
     * Grows all obstacles in in routing and tests for new intersections
     */
    private void growObstacles() {
        growPassChangedObstacles = false;
        for (int i = 0; i < NUM_GROW_PASSES; i++) {
            if (i == 0 || growPassChangedObstacles)
                growObstaclesPass();
        }
    }

    /**
     * Performs a single pass of the grow obstacles step, this can be repeated as
     * desired. Grows obstacles, then tests paths against the grown obstacles.
     */
    private void growObstaclesPass() {
        // grow obstacles
        for (int i = 0; i < userObstacles.size(); i++)
            ((VertexRectangle) userObstacles.get(i)).growVertices();

        // go through paths and test segments
        for (int i = 0; i < workingPaths.size(); i++) {
            OrthoPath path = (OrthoPath) workingPaths.get(i);

            for (int e = 0; e < path.getExcludedVertexRectangles().size(); e++)
                ((VertexRectangle) path.getExcludedVertexRectangles().get(e)).exclude = true;

            if (path.getGrownSegments().size() == 0) {
                for (int s = 0; s < path.getSegments().size(); s++)
                    testOffsetSegmentForIntersections((VertexSegment) path.getSegments().get(s), -1, path);
            } else {
                int counter = 0;
                List<Object> currentSegments = new ArrayList<>(path.getGrownSegments());
                for (int s = 0; s < currentSegments.size(); s++)
                    counter += testOffsetSegmentForIntersections((VertexSegment) currentSegments.get(s), s + counter, path);
            }

            for (int e = 0; e < path.getExcludedVertexRectangles().size(); e++)
                ((VertexRectangle) path.getExcludedVertexRectangles().get(e)).exclude = false;

        }

        // revert obstacles
        for (int i = 0; i < userObstacles.size(); i++)
            ((VertexRectangle) userObstacles.get(i)).shrinkVertices();
    }

    /**
     * Adds an obstacle to the routing
     * 
     * @param obs the obstacle
     */
    private boolean internalAddObstacle(VertexRectangle obs) {
        userObstacles.add(obs);
        return testAndDirtyPaths(obs);
    }

    /**
     * Removes an obstacle from the routing.
     * 
     * @param rect the bounds of the obstacle
     * @return the obstacle removed
     */
    private boolean internalRemoveObstacle(Rectangle rect) {
        VertexRectangle obs = null;
        int index = -1;
        for (int i = 0; i < userObstacles.size(); i++) {
            obs = (VertexRectangle) userObstacles.get(i);
            if (obs.equals(rect)) {
                index = i;
                break;
            }
        }

        userObstacles.remove(index);

        boolean result = false;
        result |= dirtyPathsOn(obs.bottomLeft);
        result |= dirtyPathsOn(obs.topLeft);
        result |= dirtyPathsOn(obs.bottomRight);
        result |= dirtyPathsOn(obs.topRight);

        for (int p = 0; p < workingPaths.size(); p++) {
            OrthoPath path = (OrthoPath) workingPaths.get(p);
            if (path.isDirty)
                continue;
            if (path.isVertexRectangleVisible(obs))
                path.isDirty = result = true;
        }

        return result;
    }

    /**
     * Labels the given path's vertices as innies, or outies, as well as determining
     * if this path is inverted.
     * 
     * @param path the path
     */
    private void labelPath(OrthoPath path) {
        VertexSegment segment = null;
        VertexSegment nextSegment = null;
        VertexPoint vertex = null;
        boolean agree = false;
        for (int v = 0; v < path.getGrownSegments().size() - 1; v++) {
            segment = (VertexSegment) path.getGrownSegments().get(v);
            nextSegment = (VertexSegment) path.getGrownSegments().get(v + 1);
            vertex = segment.end;
            long crossProduct = segment.crossProduct(new VertexSegment(vertex, vertex.getObs().center));

            if (vertex.getType() == VertexPoint.NOT_SET) {
                labelVertex(segment, crossProduct, path);
            } else if (!path.isInverted && ((crossProduct > 0 && vertex.getType() == VertexPoint.OUTIE)
                || (crossProduct < 0 && vertex.getType() == VertexPoint.INNIE))) {
                if (agree) {
                    // split detected.
                    stack.push(getSubpathForSplit(path, segment));
                    return;
                } else {
                    path.isInverted = true;
                    path.invertPriorVertices(segment);
                }
            } else if (path.isInverted && ((crossProduct < 0 && vertex.getType() == VertexPoint.OUTIE)
                || (crossProduct > 0 && vertex.getType() == VertexPoint.INNIE))) {
                // split detected.
                stack.push(getSubpathForSplit(path, segment));
                return;
            } else
                agree = true;

            if (vertex.getPaths() != null) {
                for (int i = 0; i < vertex.getPaths().size(); i++) {
                    OrthoPath nextPath = (OrthoPath) vertex.getPaths().get(i);
                    if (!nextPath.isMarked) {
                        nextPath.isMarked = true;
                        stack.push(nextPath);
                    }
                }
            }

            vertex.addPath(path, segment, nextSegment);
        }
    }

    /**
     * Labels all path's vertices in the routing.
     */
    private void labelPaths() {
        OrthoPath path = null;
        for (int i = 0; i < workingPaths.size(); i++) {
            path = (OrthoPath) workingPaths.get(i);
            stack.push(path);
        }

        while (!stack.isEmpty()) {
            path = stack.pop();
            if (!path.isMarked) {
                path.isMarked = true;
                labelPath(path);
            }
        }

        // revert is marked so we can use it again in ordering.
        for (int i = 0; i < workingPaths.size(); i++) {
            path = (OrthoPath) workingPaths.get(i);
            path.isMarked = false;
        }
    }

    /**
     * Labels the vertex at the end of the semgent based on the cross product.
     * 
     * @param segment      the segment to this vertex
     * @param crossProduct the cross product of this segment and a segment to the
     *                     obstacles center
     * @param path         the path
     */
    private void labelVertex(VertexSegment segment, long crossProduct, OrthoPath path) {
        // assumes vertex in question is segment.end
        if (crossProduct > 0) {
            if (path.isInverted) {
                segment.end.setType(VertexPoint.OUTIE);
            } else {
                segment.end.setType(VertexPoint.INNIE);
            }
        } else if (crossProduct < 0) {
            if (path.isInverted) {
                segment.end.setType(VertexPoint.INNIE);
            } else {
                segment.end.setType(VertexPoint.OUTIE);
            }
        } else if (segment.start.getType() != VertexPoint.NOT_SET) {
            segment.end.setType(segment.start.getType());
        } else {
            segment.end.setType(VertexPoint.INNIE);
        }
    }

    /**
     * Orders the path by comparing its angle at shared vertices with other paths.
     * 
     * @param path the path
     */
    private void orderPath(OrthoPath path) {
        if (path.isMarked)
            return;
        path.isMarked = true;
        VertexSegment segment = null;
        VertexPoint vertex = null;
        for (int v = 0; v < path.getGrownSegments().size() - 1; v++) {
            segment = (VertexSegment) path.getGrownSegments().get(v);
            vertex = segment.end;
            double thisAngle = ((Double) vertex.getCachedCosines().get(path)).doubleValue();
            if (path.isInverted)
                thisAngle = -thisAngle;

            for (int i = 0; i < vertex.getPaths().size(); i++) {
                OrthoPath vPath = (OrthoPath) vertex.getPaths().get(i);
                if (!vPath.isMarked) {
                    double otherAngle = ((Double) vertex.getCachedCosines().get(vPath)).doubleValue();

                    if (vPath.isInverted)
                        otherAngle = -otherAngle;

                    if (otherAngle < thisAngle)
                        orderPath(vPath);
                }
            }
        }

        orderedPaths.add(path);
    }

    /**
     * Orders all paths in the graph.
     */
    private void orderPaths() {
        for (int i = 0; i < workingPaths.size(); i++) {
            OrthoPath path = (OrthoPath) workingPaths.get(i);
            orderPath(path);
        }
    }

    /**
     * Populates the parent paths with all the child paths that were created to
     * represent bendpoints.
     */
    private void recombineChildrenPaths() {
        // only populate those paths with children paths.
        Iterator keyItr = pathsToChildPaths.keySet().iterator();
        while (keyItr.hasNext()) {
            OrthoPath path = (OrthoPath) keyItr.next();

            path.fullReset();

            List childPaths = (List) pathsToChildPaths.get(path);
            OrthoPath childPath = null;

            for (int i = 0; i < childPaths.size(); i++) {
                childPath = (OrthoPath) childPaths.get(i);
                System.out.println("i:" + i + "  " + childPath.getStartPoint().toString());
                System.out.println("i:" + i + "  " + childPath.getEndPoint().toString());

                path.points.addAll(childPath.getPoints());
                // path will overlap
                path.points.removePoint(path.points.size() - 1);
                // path.grownSegments.addAll(childPath.grownSegments);
                path.getSegments().addAll(childPath.getSegments());
                path.getVisibleVertexRectangles().addAll(childPath.getVisibleVertexRectangles());
            }

            // add last point.
            path.points.addPoint(childPath.points.getLastPoint());
        }
    }

    /**
     * Reconnects all subpaths.
     */
    private void recombineSubpaths() {
        for (int p = 0; p < orderedPaths.size(); p++) {
            OrthoPath path = (OrthoPath) orderedPaths.get(p);
            path.reconnectSubPaths();
        }

        orderedPaths.removeAll(subPaths);
        workingPaths.removeAll(subPaths);
        subPaths = null;
    }

    /**
     * Removes the obstacle with the rectangle's bounds from the routing.
     * 
     * @param rect the bounds of the obstacle to remove
     * @return <code>true</code> if the removal has dirtied one or more paths
     */
    public boolean removeObstacle(Rectangle rect) {
        return internalRemoveObstacle(rect);
    }

    /**
     * Removes the given path from the routing.
     * 
     * @param path the path to remove.
     * @return <code>true</code> if the removal may have affected one of the
     *         remaining paths
     */
    public boolean removePath(OrthoPath path) {
        userPaths.remove(path);
        List children = (List) pathsToChildPaths.get(path);
        if (children == null)
            workingPaths.remove(path);
        else
            workingPaths.removeAll(children);
        return true;
    }

    /**
     * Resets exclude field on all obstacles
     */
    private void resetObstacleExclusions() {
        for (int i = 0; i < userObstacles.size(); i++)
            ((VertexRectangle) userObstacles.get(i)).exclude = false;
    }

    /**
     * Resets all vertices found on paths and obstacles.
     */
    private void resetVertices() {
        for (int i = 0; i < userObstacles.size(); i++) {
            VertexRectangle obs = (VertexRectangle) userObstacles.get(i);
            obs.reset();
        }
        for (int i = 0; i < workingPaths.size(); i++) {
            OrthoPath path = (OrthoPath) workingPaths.get(i);
            path.getStart().fullReset();
            path.getEnd().fullReset();
        }
    }

    /**
     * Sets the default spacing between paths. The spacing is the minimum distance
     * that path should be offset from other paths or obstacles. The default value
     * is 4. When this value can not be satisfied, paths will be squeezed together
     * uniformly.
     * 
     * @param spacing the path spacing
     * @since 3.2
     */
    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }

    /**
     * Updates the points in the paths in order to represent the current solution
     * with the given paths and obstacles.
     * 
     * @return returns the list of paths which were updated.
     */
    public List solve() {

        solveDirtyPaths();

        countVertices();
        checkVertexIntersections();
        growObstacles();

        subPaths = new ArrayList();
        stack = new PathStack();
        labelPaths();
        stack = null;

        orderedPaths = new ArrayList();
        orderPaths();
        bendPaths();

        recombineSubpaths();
        orderedPaths = null;
        subPaths = null;

        recombineChildrenPaths();
        cleanup();

        return Collections.unmodifiableList(userPaths);
    }

    /**
     * Solves paths that are dirty.
     * 
     * @return number of dirty paths
     */
    private int solveDirtyPaths() {
        int numSolved = 0;

        for (int i = 0; i < userPaths.size(); i++) {
            OrthoPath path = (OrthoPath) userPaths.get(i);
            if (!path.isDirty)
                continue;
            List children = (List) pathsToChildPaths.get(path);
            int prevCount = 1, newCount = 1;
            if (children == null)
                children = Collections.EMPTY_LIST;
            else
                prevCount = children.size();

            if (path.getBendPoints() != null)
                newCount = path.getBendPoints().size() + 1;

            if (prevCount != newCount)
                children = regenerateChildPaths(path, children, prevCount, newCount);
            refreshChildrenEndpoints(path, children);
        }

        for (int i = 0; i < workingPaths.size(); i++) {
            OrthoPath path = (OrthoPath) workingPaths.get(i);
            path.refreshExcludedVertexRectangles(userObstacles);
            if (!path.isDirty) {
                path.resetPartial();
                continue;
            }

            numSolved++;
            path.fullReset();

            boolean pathFoundCheck = path.generateShortestPath(userObstacles);
            if (!pathFoundCheck || path.getEnd().getCost() > path.getThreshold()) {
                // path not found, or path found was too long
                resetVertices();
                path.fullReset();
                path.setThreshold(0);
                pathFoundCheck = path.generateShortestPath(userObstacles);
            }

            resetVertices();
        }

        resetObstacleExclusions();

        if (numSolved == 0)
            resetVertices();

        return numSolved;
    }

    /**
     * @since 3.0
     * @param path
     * @param children
     */
    private void refreshChildrenEndpoints(OrthoPath path, List children) {
        Point previous = path.getStartPoint();
        Point next;
        PointList bendpoints = path.getBendPoints();
        OrthoPath child;

        for (int i = 0; i < children.size(); i++) {
            if (i < bendpoints.size())
                next = bendpoints.getPoint(i);
            else
                next = path.getEndPoint();
            child = (OrthoPath) children.get(i);
            child.setStartPoint(previous);
            child.setEndPoint(next);
            previous = next;
        }
    }

    /**
     * @since 3.0
     * @param path
     * @param children
     */
    private List regenerateChildPaths(OrthoPath path, List children, int currentSize, int newSize) {
        // OrthoPath used to be simple but now is compound, children is EMPTY.
        if (currentSize == 1) {
            workingPaths.remove(path);
            currentSize = 0;
            children = new ArrayList(newSize);
            pathsToChildPaths.put(path, children);
        } else
        // OrthoPath is becoming simple but was compound. children becomes empty.
        if (newSize == 1) {
            workingPaths.removeAll(children);
            workingPaths.add(path);
            pathsToChildPaths.remove(path);
            return Collections.EMPTY_LIST;
        }

        // Add new working paths until the sizes are the same
        while (currentSize < newSize) {
            OrthoPath child = new OrthoPath();
            workingPaths.add(child);
            children.add(child);
            currentSize++;
        }

        while (currentSize > newSize) {
            OrthoPath child = (OrthoPath) children.remove(children.size() - 1);
            workingPaths.remove(child);
            currentSize--;
        }

        return children;
    }

    /**
     * Tests a segment that has been offset for new intersections
     * 
     * @param segment the segment
     * @param index   the index of the segment along the path
     * @param path    the path
     * @return 1 if new segments have been inserted
     */
    private int testOffsetSegmentForIntersections(VertexSegment segment, int index, OrthoPath path) {
        return 0;
//        for (int i = 0; i < userObstacles.size(); i++) {
//            VertexRectangle obs = (VertexRectangle) userObstacles.get(i);
//
//            if (segment.end.obs == obs || segment.start.obs == obs || obs.exclude)
//                continue;
//            VertexPoint vertex = null;
//
//            int offset = getSpacing();
//            if (segment.getSlope() < 0) {
//                if (segment.intersects(obs.topLeft.x - offset, obs.topLeft.y - offset, obs.bottomRight.x + offset,
//                    obs.bottomRight.y + offset)) {
//                    vertex = getNearestVertex(obs.topLeft, obs.bottomRight, segment);
//                  
//                }
//                else if (segment.intersects(obs.bottomLeft.x - offset, obs.bottomLeft.y + offset,
//                    obs.topRight.x + offset, obs.topRight.y - offset)) {
//                    vertex = getNearestVertex(obs.bottomLeft, obs.topRight, segment);
//                }
//            } else {
//                if (segment.intersects(obs.bottomLeft.x - offset, obs.bottomLeft.y + offset, obs.topRight.x + offset,
//                    obs.topRight.y - offset)) {
//                    vertex = getNearestVertex(obs.bottomLeft, obs.topRight, segment);
//                }
//                else if (segment.intersects(obs.topLeft.x - offset, obs.topLeft.y - offset, obs.bottomRight.x + offset,
//                    obs.bottomRight.y + offset)) {
//                    vertex = getNearestVertex(obs.topLeft, obs.bottomRight, segment);
//                }
//            }
//
//            if (vertex != null) {
//                Rectangle vRect = vertex.getDeformedRectangle(offset);
//                if (segment.end.obs != null) {
//                    Rectangle endRect = segment.end.getDeformedRectangle(offset);
//                    if (vRect.intersects(endRect))
//                        vertex=null;
//                        continue;
//                }
//                if (segment.start.obs != null) {
//                    Rectangle startRect = segment.start.getDeformedRectangle(offset);
//                    if (vRect.intersects(startRect))
//                        vertex=null;
//                        continue;
//                }
//              
//                VertexRectangle vr = new VertexRectangle(vRect);
//                VertexSegment newSegmentStart = new VertexSegment(segment.start, vr.topRight);
//                VertexSegment newSegmentEnd = new VertexSegment(vr.bottomRight, segment.end);
//
//               //    VertexSegment newSegmentStart = new VertexSegment(segment.start, vertex);
//               //    VertexSegment newSegmentEnd = new VertexSegment(vertex, segment.end);
//                
//                vertex.totalCount++;
//                vertex.nearestObstacleChecked = false;
//                vertex.shrink();
//                checkVertexForIntersections(vertex);
//                vertex.grow();
//                if (vertex.nearestObstacle != 0)
//                    vertex.updateOffset();
//
//                growPassChangedObstacles = true;
//
//                if (index != -1) {
//                    path.grownSegments.remove(segment);
//                    path.grownSegments.add(index, newSegmentStart);
//                    path.grownSegments.add(index + 1, newSegmentEnd);
//                } else {
//                    path.grownSegments.add(newSegmentStart);
//                    path.grownSegments.add(newSegmentEnd);
//                }
//                return 1;
//            }
//        }
//        if (index == -1) {
//            
//            path.grownSegments.add(segment);
//        }
//         
//        return 0;
    }

    /**
     * Tests all paths against the given obstacle
     * 
     * @param obs the obstacle
     */
    private boolean testAndDirtyPaths(VertexRectangle obs) {
        boolean result = false;
        for (int i = 0; i < workingPaths.size(); i++) {
            OrthoPath path = (OrthoPath) workingPaths.get(i);
            result |= path.testAndSet(obs);
        }
        return result;
    }

    /**
     * Updates the position of an existing obstacle.
     * 
     * @param oldBounds the old bounds(used to find the obstacle)
     * @param newBounds the new bounds
     * @return <code>true</code> if the change the current results to become stale
     */
    public boolean updateObstacle(Rectangle oldBounds, Rectangle newBounds) {
        boolean result = internalRemoveObstacle(oldBounds);
        result |= addObstacle(newBounds);
        return result;
    }

    public List<VertexRectangle> getUserObstacle() {

        return userObstacles;
    }

}
