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

import org.eclipse.draw2d.AbstractRouter;
import org.eclipse.draw2d.Bendpoint;
import org.eclipse.draw2d.Connection;
import org.eclipse.draw2d.FigureListener;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.LayoutListener;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.PrecisionPoint;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.draw2d.graph.Path;
import org.eclipse.draw2d.graph.ShortestPathRouter;
import org.jkiss.dbeaver.erd.ui.figures.EntityFigure;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrthogonalShortPathRouting extends AbstractRouter {

    private double indentation = 30.0;
    private static final int RIGHT = 180;
    private static final int LEFT = 0;
    private static final int UP = 90;
    private static final int DOWN = -90;
    private static final int UNDEFINED = -360;
    private final Map<Connection, Object> constraintMap = new HashMap<>();
    private Map<IFigure, Rectangle> figuresToBounds;
    private Map<Connection, Path> connectionToPaths;
    private boolean isDirty;
    private ShortestPathRouter algorithm = new ShortestPathRouter();
    // private OrthogonalShortRouter algorithm = new OrthogonalShortRouter();
    private final IFigure container;
    private final Set<Connection> staleConnections = new HashSet<>();
    private final LayoutListener listener = new LayoutTracker();

    private final FigureListener figureListener = source -> {
        Rectangle newBounds = source.getBounds().getCopy();
        if (algorithm.updateObstacle(figuresToBounds.get(source), newBounds)) {
            queueSomeRouting();
            isDirty = true;
        }

        figuresToBounds.put(source, newBounds);
    };
    private boolean ignoreInvalidate;

    /**
     * Creates a new shortest path router with the given container. The container
     * contains all the figure's which will be treated as obstacles for the
     * connections to avoid. Any time a child of the container moves, one or more
     * connections will be revalidated to process the new obstacle locations. The
     * connections being routed must not be contained within the container.
     */
    public OrthogonalShortPathRouting(IFigure container) {
        isDirty = false;
        this.container = container;
    }

    void addChild(IFigure child) {
        if (connectionToPaths == null) {
            return;
        }
        if (figuresToBounds.containsKey(child)) {
            return;
        }

        Rectangle bounds = child.getBounds().getCopy();
        algorithm.addObstacle(bounds);
        figuresToBounds.put(child, bounds);

        System.out.println(String.format("[v]:Child added ------>>>> \t\t[%s]", bounds));

        child.addFigureListener(figureListener);
        isDirty = true;
    }

    private void hookAll() {
        figuresToBounds = new HashMap<>();
        container.getChildren().forEach(this::addChild);
        container.addLayoutListener(listener);
//        List<? extends IFigure> children = container.getChildren();
//        for (IFigure entity : children) {
//            if (entity instanceof EntityFigure) {
//                for (IFigure attributeList : entity.getChildren()) {
//                    if (attributeList instanceof AttributeListFigure) {
//                        for (IFigure attribute: attributeList.getChildren()) {
//                            if (attribute instanceof AttributeItemFigure) {
//                                System.out.println((AttributeItemFigure) attribute);
//                                addChild(attribute);
//                                container.addLayoutListener(listener);
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }

    private void unhookAll() {
        container.removeLayoutListener(listener);
        if (figuresToBounds != null) {
            Iterator<IFigure> figureItr = figuresToBounds.keySet().iterator();
            while (figureItr.hasNext()) {
                // Must use iterator's remove to avoid concurrent modification
                IFigure child = figureItr.next();
                figureItr.remove();
                removeChild(child);
            }
            figuresToBounds = null;
        }
    }

    /**
     * Gets the constraint for the given {@link Connection}. The constraint is the
     * paths list of bend points for this connection.
     */
    @Override
    public Object getConstraint(Connection connection) {
        return constraintMap.get(connection);
    }

    /**
     * Returns the default spacing maintained on either side of a connection. The
     * default value is 4.
     */
    public int getSpacing() {
        return algorithm.getSpacing();
    }

    /**
     * ConnectionRouter#invalidate(Connection)
     */
    @Override
    public void invalidate(Connection connection) {
        if (ignoreInvalidate) {
            return;
        }
        staleConnections.add(connection);
        isDirty = true;
    }

    private void processStaleConnections() {
        Iterator<Connection> iter = staleConnections.iterator();
        if (iter.hasNext() && connectionToPaths == null) {
            connectionToPaths = new HashMap<>();
            hookAll();
        }

        while (iter.hasNext()) {
            Connection conn = iter.next();

            Path path = connectionToPaths.get(conn);
            if (path == null) {
                path = new Path(conn);
                connectionToPaths.put(conn, path);
                algorithm.addPath(path);
            }
            @SuppressWarnings("unchecked")
            List<Object> constraint = (List<Object>) getConstraint(conn);
            if (constraint == null) {
                constraint = Collections.emptyList();
            }

            Point start = conn.getSourceAnchor().getReferencePoint().getCopy();
            Point end = conn.getTargetAnchor().getReferencePoint().getCopy();

            container.translateToRelative(start);
            container.translateToRelative(end);

            path.setStartPoint(start);
            path.setEndPoint(end);

            if (!constraint.isEmpty()) {
                PointList bends = new PointList(constraint.size());
                for (Object element : constraint) {
                    Bendpoint bp = (Bendpoint) element;
                    bends.addPoint(bp.getLocation());
                }
                path.setBendPoints(bends);
            } else {
                path.setBendPoints(null);
            }

            isDirty |= path.isDirty;
        }
        staleConnections.clear();
    }

    void queueSomeRouting() {
        if (connectionToPaths == null || connectionToPaths.isEmpty()) {
            return;
        }
        try {
            ignoreInvalidate = true;
            connectionToPaths.keySet().iterator().next().revalidate();
        } finally {
            ignoreInvalidate = false;
        }
    }

    /**
     * ConnectionRouter#remove(Connection)
     */
    @Override
    public void remove(Connection connection) {
        staleConnections.remove(connection);
        constraintMap.remove(connection);
        if (connectionToPaths == null) {
            return;
        }
        Path path = connectionToPaths.remove(connection);
        algorithm.removePath(path);
        isDirty = true;
        if (connectionToPaths.isEmpty()) {
            unhookAll();
            connectionToPaths = null;
        } else {
            // Make sure one of the remaining is revalidated so that we can
            // re-route again.
            queueSomeRouting();
        }
    }

    void removeChild(IFigure child) {
        if (connectionToPaths == null) {
            return;
        }
        Rectangle bounds = child.getBounds().getCopy();
        boolean change = algorithm.removeObstacle(bounds);
        figuresToBounds.remove(child);
        child.removeFigureListener(figureListener);
        if (change) {
            isDirty = true;
            queueSomeRouting();
        }
    }

    /**
     * ConnectionRouter#route(Connection)
     */
    @Override
    public void route(Connection conn) {
        if (isDirty) {
            ignoreInvalidate = true;
            processStaleConnections();
            isDirty = false;
            List<?> updated = algorithm.solve();
            Connection connection;
            for (Object element : updated) {
                Path path = (Path) element;
                connection = (Connection) path.data;
                connection.revalidate();

                PointList points = path.getPoints().getCopy();

                Point ref1 = new PrecisionPoint(points.getPoint(1));
                Point ref2 = new PrecisionPoint(points.getPoint(points.size() - 2));
                connection.translateToAbsolute(ref1);
                connection.translateToAbsolute(ref2);
                Point start = connection.getSourceAnchor().getLocation(ref1).getCopy();
                Point end = connection.getTargetAnchor().getLocation(ref2).getCopy();
                connection.translateToRelative(start);
                connection.translateToRelative(end);

                IFigure srcOwner = connection.getSourceAnchor().getOwner();
                Rectangle srcBounds = srcOwner.getBounds().getCopy();

                VertexRectangle vrSource = new VertexRectangle(srcBounds);

                IFigure targetOwner = connection.getTargetAnchor().getOwner();
                Rectangle trgBounds = targetOwner.getBounds().getCopy();
                VertexRectangle vrTarget = new VertexRectangle(trgBounds);
                PointList modifiedPoints = new PointList();

                // check connection to ourselves
                IFigure parentSrc = srcOwner.getParent().getParent();
                IFigure parentTrg = targetOwner.getParent().getParent();

                if (indentation != 0) {
                    // first
                    // modifiedPoints.addPoint(start);
                    // direction1 = 180 - getDirection(bounds, points.getPoint(0).getCopy())
                    int direction = getDirection2(srcBounds, trgBounds);

                    int directionSrcToTrg = 180 - getLRDirection(srcBounds, trgBounds.getTopLeft());
                    int directionTrgToSrc = directionSrcToTrg;

                    if (parentSrc.equals(parentTrg)) {
                        // connection inside entity, to ourself
                        directionSrcToTrg = UNDEFINED;
                        directionTrgToSrc = 180;
                    }
                    switch (direction) {

                        case UP:
                            System.out.println("UP: " + directionSrcToTrg);
                            directionSrcToTrg = RIGHT;
                            directionTrgToSrc = RIGHT;
                            
                            start = vrSource.centerRight;
                            end = vrTarget.centerRight;

                            break;
                        case DOWN:
                            System.out.println("DOWN");
                            directionSrcToTrg = LEFT;
                            directionTrgToSrc = LEFT;

                            start = vrSource.centerLeft;
                            end = vrTarget.centerLeft;

                            break;
                        case LEFT:
                            System.out.println("LEFT TO RIGHT");
                            start = vrSource.centerLeft;
                            end = vrTarget.centerRight;
                            break;
                        case RIGHT:
                            System.out.println("RIGHT TO LEFT");
                            start = vrSource.centerRight;
                            end = vrTarget.centerLeft;
                            break;
                        case UNDEFINED:
                            System.out.println("TO OURSELF");
                            start = vrSource.centerRight;
                            end = vrTarget.centerRight;
                            break;
                        default:
                            System.out.println("?????");
                            directionSrcToTrg = RIGHT;
                            directionTrgToSrc = RIGHT;
                            start = vrSource.centerLeft;
                            end = vrTarget.centerRight;
                            break;
                    }
 

                    // start point
                    modifiedPoints.addPoint(start);

                    // from 1-->>2
                    int dx1 = (int) (Math.cos(Math.toRadians(directionSrcToTrg)) * indentation);
                    int dy1 = (int) (Math.sin(Math.toRadians(directionSrcToTrg)) * indentation);
                    // from 1<<--2
                    int dx2 = (int) (Math.cos(Math.toRadians(directionTrgToSrc)) * indentation);
                    int dy2 = (int) (Math.sin(Math.toRadians(directionTrgToSrc)) * indentation);
                    Point p1 = new Point(start.x + dx1, start.y - dy1);
                    modifiedPoints.addPoint(p1);
                    // add other middle points
                    for (int i = 1; i < points.size() - 1; i++) {
                        modifiedPoints.addPoint(points.getPoint(i));
                    }
                    // before end
                    Point p2 = new Point(end.x - dx2, end.y - dy2);
                    modifiedPoints.addPoint(p2);
                    // end
                    modifiedPoints.addPoint(end);
                    connection.setPoints(modifiedPoints);

                } else {

                    int direction = 180 - getLRDirection(srcBounds, start);
                    if (parentSrc.equals(parentTrg)) {
                        direction = UNDEFINED;

                    }
                    if (direction == -360) {
                        System.out.println("TO OURSELF");
                        start = vrSource.centerRight;
                        end = vrTarget.centerRight;
                    } else if (direction == LEFT) {
                        System.out.println("LEFT TO RIGHT");
                        start = vrSource.centerRight;
                        end = vrTarget.centerLeft;

                    } else {
                        System.out.println("RIGHT TO LEFT");
                        start = vrSource.centerLeft;
                        end = vrTarget.centerRight;

                    }

                    modifiedPoints.addPoint(start);
                    // add other middle points
                    for (int i = 1; i < points.size() - 1; i++) {
                        modifiedPoints.addPoint(points.getPoint(i));
                    }
                    // end
                    modifiedPoints.addPoint(end);
//                    points.removePoint(points.size() - 1);
//                    points.addPoint(lastPoint);
                    connection.setPoints(modifiedPoints);
                }
            }
            ignoreInvalidate = false;
        }
    }

    protected int getLRDirection(Rectangle r, Point p) {
        int i = 0;
        int direction = LEFT;
        int distance = Math.abs(r.x - p.x);
        i = Math.abs(r.right() - p.x);
        if (i < distance) {
            direction = RIGHT;
        }
        if (distance < r.width) {
            direction = UNDEFINED;
        }
        return direction;
    }

    protected int getDirection(Rectangle r, Point p) {
        int i = 0;
        int direction = LEFT;
        int distance = Math.abs(r.x - p.x);
        i = Math.abs(r.y - p.y);
        if (i <= distance) {
            distance = i;
            direction = UP;
        }
        i = Math.abs(r.bottom() - p.y);
        if (i <= distance) {
            distance = i;
            direction = DOWN;
        }
        i = Math.abs(r.right() - p.x);
        if (i < distance) {
            direction = RIGHT;
        }
        return direction;
    }

    protected int getDirection(Rectangle r1, Rectangle r2) {
        int i = 0;
        int direction = LEFT;
        int distance = Math.abs(r1.x - r2.x);
        i = Math.abs(r1.y - r2.y);
        if (i <= distance) {
            distance = i;
            direction = UP;
        }
        i = Math.abs(r1.bottom() - r2.y);
        if (i <= distance) {
            distance = i;
            direction = DOWN;
        }
        i = Math.abs(r1.right() - r2.x);
        if (i < distance) {
            direction = RIGHT;
        }
        return direction;
    }

    protected int getDirection2(Rectangle r1, Rectangle r2) {
        int i = 0;
        int direction = LEFT;
        int distance = Math.abs(r1.x - r2.x);
        i = Math.abs(r1.y - r2.y);
        if (i <= distance) {
            distance = i;
            if (r1.right() > r2.x &&
                r1.right() < r2.right()) {
                direction = UP;
            }
        }
        i = Math.abs(r1.bottom() - r2.y);
        if (i <= distance) {
            distance = i;
            if (r2.x < r1.right() &&
                r2.x > r1.x) {
                direction = DOWN;
            }
        }
        i = Math.abs(r1.right() - r2.x);
        if (i < distance) {
            direction = RIGHT;
        }
        return direction;
    }

    /**
     * All connection paths after routing dirty paths. Some of the paths that were
     * not dirty may change as well, as a consequence of new routings.
     * 
     */
    public List<?> getPathsAfterRouting() {
        if (isDirty) {
            processStaleConnections();
            isDirty = false;
            return algorithm.solve();

        }
        return Collections.emptyList();
    }

    @Override
    public void setConstraint(Connection connection, Object constraint) {
        staleConnections.add(connection);
        constraintMap.put(connection, constraint);
        isDirty = true;
    }

    /**
     * Sets the default space that should be maintained on either side of a
     * connection. This causes the connections to be separated from each other and
     * from the obstacles. The default value is 4.
     */
    public void setSpacing(int spacing) {
        algorithm.setSpacing(spacing);
    }

    /**
     * Checks multiple connections
     */
    public boolean hasMoreConnections() {
        return connectionToPaths != null && !connectionToPaths.isEmpty();
    }

    /**
     * Return a container
     */
    public IFigure getContainer() {
        return container;
    }

    /**
     * Sets the value indicating if connection invalidation should be ignored.
     */
    public void setIgnoreInvalidate(boolean b) {
        ignoreInvalidate = b;
    }

    /**
     * Returns the value indicating if connection invalidation should be ignored.
     */
    public boolean shouldIgnoreInvalidate() {
        return ignoreInvalidate;
    }

    /**
     * Returns the value indicating if the router is dirty, i.e. if there are any
     * outstanding connections that need to be routed
     */
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * Returns true if the given connection is routed by this router, false
     * otherwise
     */
    public boolean containsConnection(Connection conn) {
        return connectionToPaths != null && connectionToPaths.containsKey(conn);
    }

    private class LayoutTracker extends LayoutListener.Stub {
        @Override
        public void postLayout(IFigure container) {
            if (staleConnections.isEmpty()) {
                return;
            }
            staleConnections.iterator().next().revalidate();
        }

        @Override
        public void remove(IFigure child) {
            removeChild(child);
        }

        @Override
        public void setConstraint(IFigure child, Object constraint) {
            addChild(child);
        }
    }

    /**
     * The method returns indentation value
     */
    public double getIndentation() {
        return indentation;
    }

    /**
     * Method design to specify indentation value
     */
    public void setIndentation(double indentation) {
        this.indentation = indentation;
    }

}