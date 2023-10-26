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
import org.eclipse.draw2d.geometry.Geometry;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.PrecisionPoint;
import org.eclipse.draw2d.geometry.Rectangle;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class OrthogonalShortPathRouting extends AbstractRouter {

    private double indentation = 30.0;
    private final Map<Connection, Object> constraintMap = new HashMap<>();
    private Map<IFigure, Rectangle> figuresToBounds;
    private Map<Connection, OrthoPath> connectionToPaths;
    private Map<Connection, PointList> connection2points = new HashMap<>();

    private boolean isDirty;
    private OrthogonalShortRouter algorithm = new OrthogonalShortRouter();
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
        child.addFigureListener(figureListener);
        isDirty = true;
    }

    private void hookAll() {
        figuresToBounds = new HashMap<>();
        container.getChildren().forEach(this::addChild);
        container.addLayoutListener(listener);
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

            OrthoPath path = connectionToPaths.get(conn);
            if (path == null) {
                path = new OrthoPath(conn);
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
        OrthoPath path = connectionToPaths.remove(connection);
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

            List<OrthoPath> routePaths = algorithm.solve();
            ERDiagramConnection connection;

            for (OrthoPath path : routePaths) {
                connection = (ERDiagramConnection) path.data;
                connection.revalidate();
                connection.cleanupOverlappedPoints();

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
                if (srcOwner == null) {
                    continue;
                }
                Rectangle srcBounds = srcOwner.getBounds().getCopy();
                VertexRectangle vrSource = new VertexRectangle(srcBounds);
                IFigure targetOwner = connection.getTargetAnchor().getOwner();
                if (targetOwner == null) {
                    continue;
                }
                Rectangle trgBounds = targetOwner.getBounds().getCopy();
                VertexRectangle vrTarget = new VertexRectangle(trgBounds);
                PointList modifiedPoints = new PointList();

                // check connection to ourselves
                IFigure parentSrc = srcOwner.getParent().getParent();
                IFigure parentTrg = targetOwner.getParent().getParent();

                if (indentation == 0) {
                    indentation = 20;
                }

                // first
                int direction = OrthoPathUtils.getLRDirection(srcBounds, trgBounds);
                int directionSrcToTrg = 0;
                int directionTrgToSrc = 0;

                if (parentSrc.equals(parentTrg)) {
                    // connection inside entity, to ourself
                    directionSrcToTrg = OrthoPathUtils.TO_OURSELF;
                    directionTrgToSrc = OrthoPathUtils.RIGHT;
                }
                switch (direction) {
                    case OrthoPathUtils.UP:
                        directionSrcToTrg = OrthoPathUtils.RIGHT;
                        directionTrgToSrc = OrthoPathUtils.RIGHT;
                        start = vrSource.centerRight;
                        end = vrTarget.centerRight;
                        break;
                    case OrthoPathUtils.DOWN:
                        directionSrcToTrg = OrthoPathUtils.LEFT;
                        directionTrgToSrc = OrthoPathUtils.RIGHT;
                        start = vrSource.centerRight;
                        end = vrTarget.centerRight;
                        break;
                    case OrthoPathUtils.LEFT:
                        directionSrcToTrg = 180 - OrthoPathUtils.LEFT;
                        directionTrgToSrc = 180 - OrthoPathUtils.LEFT;
                        start = vrSource.centerLeft;
                        end = vrTarget.centerRight;
                        break;
                    case OrthoPathUtils.RIGHT:
                        start = vrSource.centerRight;
                        end = vrTarget.centerLeft;
                        break;
                    case OrthoPathUtils.TO_OURSELF:
                        directionSrcToTrg = 180 - OrthoPathUtils.LEFT;
                        directionTrgToSrc = OrthoPathUtils.LEFT;
                        start = vrSource.centerLeft;
                        end = vrTarget.centerLeft;
                        break;
                    default:
                        directionSrcToTrg = OrthoPathUtils.RIGHT;
                        directionTrgToSrc = OrthoPathUtils.RIGHT;
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
                for (int i = 1; i < points.size() - 1; i++) {
                    modifiedPoints.addPoint(points.getPoint(i));
                }
                // !!!
                // before end
                Point p2 = new Point(end.x - dx2, end.y - dy2);
                modifiedPoints.addPoint(p2);
                modifiedPoints.addPoint(end);
                // !!!!

                PointList intermediate = new PointList();
                for (int i = 1; i < modifiedPoints.size() - 1; i++) {
                    intermediate.addPoint(modifiedPoints.getPoint(i));
                }
                PointList routePoints = calcOrthoRoutePoints(intermediate, directionSrcToTrg);
                List<VertexRectangle> userObstacle = algorithm.getUserObstacle();
                PointList resolvedRoutePoints = resolveEntityIntersections(routePoints, userObstacle);
                resolvedRoutePoints = resolveExtraRoutePoints(resolvedRoutePoints);
                resolvedRoutePoints = resolveOverlappingSegments(resolvedRoutePoints);
                resolvedRoutePoints = combineRoutePoints(start, end, resolvedRoutePoints);
                connection2points.put(connection, resolvedRoutePoints);
                connection.setPoints(resolvedRoutePoints);

                // bridges
                detectBridgesPoints();

            }
            ignoreInvalidate = false;
        }
    }

    private PointList combineRoutePoints(Point start, Point end, PointList resolvedRoutePoints) {
        PointList resultPointList = new PointList();
        resultPointList.addPoint(start);
        resultPointList.addAll(resolvedRoutePoints);
        resultPointList.addPoint(end);
        return resultPointList;
    }

    private PointList resolveOverlappingSegments(PointList routePoints) {
        // Y - vertical lines
        PointList conflictingPointsByY = new PointList();
        for (Entry<Connection, PointList> entry : connection2points.entrySet()) {
            conflictingPointsByY.addAll(detectOverlappingRoutePointsByAxisY(routePoints, entry.getValue()));
        }
        int isLeftToRight = getLeftRightDirection(routePoints);
        for (int i = 0; i < routePoints.size(); i++) {
            Point resolvedPoint = routePoints.getPoint(i);
            for (int j = 0; j < conflictingPointsByY.size(); j++) {
                if (isLeftToRight == 1) {
                    resolvedPoint.setX(resolvedPoint.x - 5);
                } else {
                    resolvedPoint.setX(resolvedPoint.x - 5);
                }
                routePoints.setPoint(resolvedPoint, i);
            }
        }

        // X - horizontal lines
        PointList conflictingPointsByX = new PointList();
        for (Entry<Connection, PointList> entry : connection2points.entrySet()) {
            conflictingPointsByX.addAll(detectOverlappingRoutePointsByAxisX(routePoints, entry.getValue()));
        }
        if (conflictingPointsByX != null) {
            for (int i = 0; i < routePoints.size(); i++) {
                Point ortho = routePoints.getPoint(i);
                for (int j = 0; j < conflictingPointsByX.size(); j++) {
                    Point medium = conflictingPointsByX.getPoint(j);
                    if (ortho.equals(medium)) {
                        ortho.setX(ortho.x - 5);
                        routePoints.setPoint(ortho, i);
                    }
                }
            }
        }
        return routePoints;
    }

    private PointList resolveEntityIntersections(PointList routePoints, List<VertexRectangle> userObstacle) {
        PointList copyRoutePoints = routePoints.getCopy();
        PointList resolvedRoutePoints = detectIntersectionWithEntities(userObstacle, copyRoutePoints);
        if (resolvedRoutePoints.size() != routePoints.size()) {
            copyRoutePoints = resolvedRoutePoints.getCopy();
            resolvedRoutePoints = detectIntersectionWithEntities(userObstacle, copyRoutePoints);
            if (resolvedRoutePoints.size() != routePoints.size()) {
                copyRoutePoints = resolvedRoutePoints.getCopy();
                resolvedRoutePoints = detectIntersectionWithEntities(userObstacle, copyRoutePoints);
            }
        }
        return resolvedRoutePoints;
    }

    private PointList detectIntersectionWithEntities(List<VertexRectangle> userObstacles, PointList routePointList) {
        Map<Integer, PointList> intersection = new HashMap<>();
        for (VertexRectangle entity : userObstacles) {
            VertexRectangle vRect = getDeformedRectangle(entity, 20);
            for (int j = 1; j < routePointList.size(); j++) {
                Point pointA = routePointList.getPoint(j - 1);
                Point pointB = routePointList.getPoint(j);
                PointList line = new PointList();
                line.addPoint(pointA);
                line.addPoint(pointB);

                if (line.intersects(entity)) {

                    PointList entityTopLine = new PointList();
                    entityTopLine.addPoint(entity.getTopLeft());
                    entityTopLine.addPoint(entity.getTopRight());
                    PointList intesections = detectIntesections(line, entityTopLine);
                    if (intesections.size() > 0) {
                        // vertical
                        // to expensive route for now
                    } else {
                        // horizontal
                        PointList aroundEntity = new PointList();
                        Point p1 = new Point(pointA.x, vRect.bottomLeft.y);
                        Point p2 = new Point(pointB.x, vRect.bottomLeft.y);
                        aroundEntity.addPoint(p1);
                        aroundEntity.addPoint(p2);
                        intersection.put(j, aroundEntity);
                    }
                }
            }
        }
        for (Entry<Integer, PointList> entry : intersection.entrySet()) {
            int position = entry.getKey();
            PointList points = entry.getValue();
            for (int j = 0; j < points.size(); j++) {
                Point p = points.getPoint(j);
                routePointList.insertPoint(p, position++);
            }
        }
        return routePointList;
    }

    // 1 - left to right
    private int getLeftRightDirection(PointList pointList) {
        Point firstPoint = pointList.getFirstPoint();
        Point lastPoint = pointList.getLastPoint();
        if (firstPoint.x < lastPoint.x) {
            return 1;
        } else {
            return 0;
        }

    }

    private PointList resolveExtraRoutePoints(PointList routePointList) {
        if (routePointList.size() < 2) {
            return routePointList;
        }
        for (int j = 2; j < routePointList.size(); j++) {
            Point pointA = routePointList.getPoint(j - 2);
            Point pointB = routePointList.getPoint(j - 1);
            Point pointC = routePointList.getPoint(j);
            if (pointA.x == pointB.x && pointA.x == pointC.x) {
                if (pointB.y < pointA.y && pointB.y < pointC.y) {
                    routePointList.removePoint(j - 1);
                }
            }
        }
        return routePointList;
    }

    private void detectBridgesPoints() {
        for (Entry<Connection, PointList> entry : connection2points.entrySet()) {
            ERDiagramConnection erdConnection = (ERDiagramConnection) entry.getKey();
            erdConnection.cleanupCrossRouters();
            PointList crossPoints = new PointList();
            for (Entry<Connection, PointList> targetEntry : connection2points.entrySet()) {
                if (targetEntry.equals(entry)) {
                    continue;
                }
                crossPoints.addAll(detectIntesections(entry.getValue(), targetEntry.getValue()));
                if (crossPoints.size() > 0) {
                    erdConnection.setCrossRouters(crossPoints);
                }
            }
        }
    }

    private PointList detectIntesections(PointList routePointList, PointList existingRoutePoints) {
        PointList list = new PointList();
        Set<Point> setOfPoints = new HashSet<>();
        for (int j = 1; j < routePointList.size(); j++) {
            Point pointA = routePointList.getPoint(j);
            Point pointB = routePointList.getPoint(j - 1);
            for (int k = 1; k < existingRoutePoints.size(); k++) {
                Point pointC = existingRoutePoints.getPoint(k);
                Point pointD = existingRoutePoints.getPoint(k - 1);
                if ((pointA.x == pointB.x && pointA.x == pointC.x) ||
                    (pointA.x == pointB.x && pointA.x == pointD.x) ||
                    (pointA.y == pointB.y && pointA.y == pointC.y) ||
                    (pointA.y == pointB.y && pointA.y == pointD.y) ||
                    (pointC.x == pointD.x && pointC.x == pointA.x) ||
                    (pointC.y == pointD.y && pointC.y == pointA.y) ||
                    (pointC.x == pointD.x && pointC.x == pointB.x) ||
                    (pointC.y == pointD.y && pointC.y == pointB.y)) {
                    // just a corner
                    continue;
                }
                PointList lineAB = new PointList();
                lineAB.addPoint(pointA);
                lineAB.addPoint(pointB);
                PointList lineCD = new PointList();
                lineCD.addPoint(pointC);
                lineCD.addPoint(pointD);

                Point intersectionPoint = calculateInterceptionPoint(pointA, pointB, pointC, pointD);
                if (intersectionPoint != null &&
                    Geometry.polylineContainsPoint(lineAB, intersectionPoint.x, intersectionPoint.y, 1) &&
                    Geometry.polylineContainsPoint(lineCD, intersectionPoint.x, intersectionPoint.y, 1)) {
                    setOfPoints.add(intersectionPoint);
                }
            }
        }
        if (!setOfPoints.isEmpty()) {
            for (Point p : setOfPoints) {
                list.addPoint(p);
            }
        }
        return list;
    }

    public static float angleBetween2Lines(Point A1, Point A2, Point B1, Point B2) {
        float angle1 = (float) Math.atan2(A2.y - A1.y, A1.x - A2.x);
        float angle2 = (float) Math.atan2(B2.y - B1.y, B1.x - B2.x);
        float calculatedAngle = (float) Math.toDegrees(angle1 - angle2);
        if (calculatedAngle < 0)
            calculatedAngle += 360;
        return calculatedAngle;
    }

    private PointList detectOverlappingRoutePointsByAxisX(PointList orthoPoints, PointList connectionPoints) {
        PointList list = new PointList();
        for (int j = 1; j < orthoPoints.size(); j++) {
            Point pointA = orthoPoints.getPoint(j);
            Point pointB = orthoPoints.getPoint(j - 1);
            for (int i = 1; i < connectionPoints.size(); i++) {
                Point pointC = connectionPoints.getPoint(i);
                Point pointD = connectionPoints.getPoint(i - 1);
                list.addAll(getIntersectionPointsByX(
                    pointA.x, pointA.y,
                    pointB.x, pointB.y,
                    pointC.x, pointC.y,
                    pointD.x, pointD.y));
            }
        }
        return list;

    }

    private PointList detectOverlappingRoutePointsByAxisY(PointList orthoPoints, PointList connectionPoints) {
        PointList list = new PointList();
        for (int j = 1; j < orthoPoints.size(); j++) {
            Point pointA = orthoPoints.getPoint(j);
            Point pointB = orthoPoints.getPoint(j - 1);
            for (int i = 1; i < connectionPoints.size(); i++) {
                Point pointC = connectionPoints.getPoint(i);
                Point pointD = connectionPoints.getPoint(i - 1);
                list.addAll(getIntersectionPointsByAxisY(
                    pointA.x, pointA.y,
                    pointB.x, pointB.y,
                    pointC.x, pointC.y,
                    pointD.x, pointD.y));
            }
        }
        return list;

    }

    private PointList getIntersectionPointsByAxisY(int x, int y, int x2, int y2, int x3, int y3, int x4, int y4) {
        if (Math.abs(x - x2) < 5 &&
            Math.abs(x - x3) < 5 &&
            Math.abs(x - x4) < 5) {

            int lineOneBottomY = 0;
            int lineOneTopY = 0;
            int lineTwoBottomY = 0;
            int lineTwoTopY = 0;

            if (y > y2) {
                lineOneBottomY = y;
                lineOneTopY = y2;
            } else {
                lineOneBottomY = y2;
                lineOneTopY = y;
            }
            if (y3 > y4) {
                lineTwoTopY = y4;
                lineTwoBottomY = y3;
            } else {
                lineTwoTopY = y3;
                lineTwoBottomY = y4;
            }

            // by x
            if (lineOneTopY < lineTwoTopY && lineOneBottomY > lineTwoBottomY) {
                PointList pointList = new PointList();
                pointList.addPoint(new Point(x, y));
                pointList.addPoint(new Point(x2, y2));
                return pointList;
            }
            if (lineTwoTopY < lineOneTopY && lineTwoBottomY > lineOneBottomY) {
                PointList pointList = new PointList();
                pointList.addPoint(new Point(x, y));
                pointList.addPoint(new Point(x2, y2));
                return pointList;
            }
            if (lineTwoTopY > lineOneTopY && lineOneBottomY > lineTwoTopY && lineOneBottomY > lineTwoBottomY) {
                PointList pointList = new PointList();
                pointList.addPoint(new Point(x, y));
                pointList.addPoint(new Point(x2, y2));
                return pointList;
            }

            if (lineTwoTopY > lineOneTopY && lineOneBottomY > lineTwoTopY && lineOneBottomY < lineTwoBottomY) {
                PointList pointList = new PointList();
                pointList.addPoint(new Point(x, y));
                pointList.addPoint(new Point(x2, y2));
                return pointList;
            }
        }
        return new PointList();
    }

    private PointList getIntersectionPointsByX(int x, int y, int x2, int y2, int x3, int y3, int x4, int y4) {
        if (Math.abs(y - y2) < 5 &&
            Math.abs(y - y3) < 5 &&
            Math.abs(y - y4) < 5) {
            // by y
            int lineOneRightX = 0;
            int lineOneLeftX = 0;
            int lineTwoRightX = 0;
            int lineTwoLeftX = 0;
            if (x > x2) {
                lineOneRightX = x;
                lineOneLeftX = x2;
            } else {
                lineOneRightX = x2;
                lineOneLeftX = x;
            }
            if (x3 > x4) {
                lineTwoRightX = x3;
                lineTwoLeftX = x4;
            } else {
                lineTwoRightX = x4;
                lineTwoLeftX = x3;
            }
            // contains
            if (lineTwoRightX > lineOneRightX && lineOneLeftX > lineTwoLeftX) {
                System.out.println("Intersection-1: y:" + y + " from  x:" + lineOneLeftX + "-" + lineOneRightX);
                PointList pointList = new PointList();
                pointList.addPoint(new Point(x, lineOneLeftX));
                pointList.addPoint(new Point(x, lineOneRightX));
                return pointList;
            }

            if (lineOneRightX > lineTwoLeftX && lineOneRightX < lineTwoRightX) {
                System.out.println("Intersection-2: y:" + y + " from  x:" + lineTwoLeftX + "-" + lineOneRightX);
                PointList pointList = new PointList();
                pointList.addPoint(new Point(x, lineTwoLeftX));
                pointList.addPoint(new Point(x, lineOneRightX));
                return pointList;
            }
            if (lineOneLeftX > lineTwoLeftX && lineOneLeftX < lineTwoRightX) {
                System.out.println("Intersection-3: y:" + y + " from  x:" + lineOneLeftX + "-" + lineTwoRightX);
                PointList pointList = new PointList();
                pointList.addPoint(new Point(x, lineOneLeftX));
                pointList.addPoint(new Point(x, lineTwoRightX));
                return pointList;
            }
        }

        return new PointList();
    }

//    protected int getDirection(Rectangle r, Point p) {
//
//        int direction = LEFT;
//        int dX = Math.abs(r.x - p.x);
//        int dY = Math.abs(r.y - p.y);
//
//        if (dY <= dX) {
//            dX = dY;
//            direction = UP;
//        }
//        dY = Math.abs(r.bottom() - p.y);
//        if (dY <= dX) {
//            dX = dY;
//            direction = DOWN;
//        }
//        dY = Math.abs(r.right() - p.x);
//        if (dY < dX) {
//            direction = RIGHT;
//        }
//        return direction;
//    }

//    protected int getDirection(Rectangle r1, Rectangle r2) {
//        int direction = LEFT;
//        int dX = Math.abs(r1.x - r2.x);
//        int dY = Math.abs(r1.y - r2.y);
//        if (dY <= dX) {
//            dX = dY;
//            direction = UP;
//        }
//        dY = Math.abs(r1.bottom() - r2.y);
//        if (dY <= dX) {
//            dX = dY;
//            direction = DOWN;
//        }
//        dY = Math.abs(r1.right() - r2.x);
//        if (dY < dX) {
//            direction = RIGHT;
//        }
//        return direction;
//    }

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

    public static Point calculateInterceptionPoint(Point A, Point B, Point C, Point D) {

        // Line AB represented as a1x + b1y = c1
        double a1 = B.y - A.y;
        double b1 = A.x - B.x;
        double c1 = a1 * (A.x) + b1 * (A.y);

        // Line CD represented as a2x + b2y = c2
        double a2 = D.y - C.y;
        double b2 = C.x - D.x;
        double c2 = a2 * (C.x) + b2 * (C.y);

        double determinant = a1 * b2 - a2 * b1;

        if (determinant == 0) {
            // The lines are parallel. This is simplified
            // by returning a pair of FLT_MAX
            return null;
        } else {
            int x = (int) ((b2 * c1 - b1 * c2) / determinant);
            int y = (int) ((a1 * c2 - a2 * c1) / determinant);

            return new Point(x, y);
        }
    }

    public PointList calcOrthoRoutePoints(PointList points, int direction) {

        Point[] retval = new Point[2 * points.size() - 1];
        // assign even points to the corner points passed in
        for (int i = 0; i < points.size(); i++) {
            retval[2 * i] = points.getPoint(i);
        }
        // generate the side points
        for (int i = 0; i < points.size() - 1; i++) {
            Point pointCurrent = points.getPoint(i);
            Point nextPoint = points.getPoint(i + 1);
            int dx = Math.abs(pointCurrent.x - nextPoint.x);
            int dy = Math.abs(pointCurrent.y - nextPoint.y);

            if (pointCurrent.x == nextPoint.x || pointCurrent.y == nextPoint.y) {
                retval[2 * i + 1] = new Point(pointCurrent.x, pointCurrent.y); //
            } else {
                // do ortho
                int dxTransformed = (int) (Math.cos(Math.toRadians(direction)) * dx);
                int dyTransformed = (int) (Math.sin(Math.toRadians(direction)) * dy);
                retval[2 * i + 1] = new Point(pointCurrent.x + dxTransformed, pointCurrent.y - dyTransformed);
            }
        }
        PointList arrayToPointList = arrayToPointList(retval);
        PointList alignedPointList = new PointList();
        // validate

        for (int i = 0; i < arrayToPointList.size() - 1; i++) {
            Point pointCurrent = arrayToPointList.getPoint(i);
            Point nextPoint = arrayToPointList.getPoint(i + 1);
            if (pointCurrent.x == nextPoint.x || pointCurrent.y == nextPoint.y) {
                alignedPointList.addPoint(pointCurrent);
            } else {
                Point beforePoint = arrayToPointList.getPoint(i - 1);
                alignedPointList.addPoint(new Point(beforePoint.x, nextPoint.y));
            }

        }
        alignedPointList.addPoint(arrayToPointList.getLastPoint());
        return alignedPointList;
    }

    private static PointList arrayToPointList(Point[] array) {
        PointList list = new PointList();
        for (Point p : array) {
            list.addPoint(p);
        }
        return list;
    }

    private VertexRectangle getDeformedRectangle(VertexRectangle r, int extraOffset) {
        Rectangle rect = new Rectangle(r.x - extraOffset, r.y - extraOffset,
            r.width + extraOffset * 2, r.height + extraOffset * 2);
        return new VertexRectangle(rect);
    }
}