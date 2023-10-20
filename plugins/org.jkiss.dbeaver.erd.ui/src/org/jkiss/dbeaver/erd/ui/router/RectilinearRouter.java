package org.jkiss.dbeaver.erd.ui.router;

import java.awt.Container;
import java.util.Iterator;
import java.util.Vector;

import org.eclipse.draw2d.AbstractRouter;
import org.eclipse.draw2d.Connection;
import org.eclipse.draw2d.ConnectionAnchor;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.draw2d.geometry.Rectangle;

public class RectilinearRouter extends AbstractRouter {
    private IFigure container;

    public RectilinearRouter(IFigure container) {
        this.container = container;
    }
    //
    // These directional constants are used to describe to the location of a
    // point relative to an IFigure x. They are also used to categorize anchors.
    //
    // NW N NE
    // W x E
    // SW S SE
    //

    /** The point is north of the IFigure (or on its top edge). */
    protected static final int N = 0;

    /** The point is east of the IFigure (or on its top edge). */
    protected static final int E = 1;

    /** The point is south of the IFigure (or on its top edge). */
    protected static final int S = 2;

    /** The point is west of the IFigure (or on its top edge). */
    protected static final int W = 3;

    /** The point is north-east of the IFigure (or on its top right corner). */
    protected static final int NE = 4;

    /** The point is north-west of the IFigure (or on its top left corner). */
    protected static final int NW = 5;

    /**
     * The point is south-east of the IFigure (or on its bottom right corner).
     */
    protected static final int SE = 6;

    /**
     * The point is south-west of the IFigure (or on its bottom left corner).
     */
    protected static final int SW = 7;

    /** The point is north of the IFigure (or on its top edge). */
    protected static final int INTERIOR = 8; // on top of the IFigure x

    /**
     * The default gap AT 1.0 (100%) ZOOM that should be made between an IFigure and
     * the Connector being routed around it--i.e. the distance it "goes out" before
     * turning 90 degrees in one direction (if it does turn).
     */
    protected static final int DEFAULT_GAP = 10;

    /**
     * This is the gap AT 1.0 (100%) ZOOM that should be made between an IFigure and
     * the Connector being routed around it--i.e. the distance it "goes out" before
     * turning 90 degrees in one direction (if it does turn).
     */
    protected int _gap;

    /**
     * This is the zoom ratio (100% == 1.0) of the IFigure we're routing in.
     */
    protected double _zoomFactor;

    /**
     * Construct a new RectilinearRouter.
     */
    public RectilinearRouter() {
        _gap = DEFAULT_GAP;
        _zoomFactor = 1.0;
    }

    /**
     * Get the gap that should be made between an IFigure and the Connector being
     * routed around it. This is the distance it "goes out" before turning 90
     * degrees in one direction.
     * 
     * @return the gap that should be made between an IFigure and the Connector
     *         being routed around it. This is the distance it "goes out" before
     *         turning 90 degrees in one direction.
     */
    public int getGap() {
        return _gap;
    }

    /**
     * Set the gap that should be made between an IFigure and the Connector being
     * routed around it. This is the distance it "goes out" before turning 90
     * degrees in one direction.
     * 
     * @param gap the gap that should be made between an IFigure and the Connector
     *            being routed around it. This is the distance it "goes out" before
     *            turning 90 degrees in one direction.
     */
    public void setGap(int gap) {
        _gap = gap;
    }

    /**
     * Return the zoom ratio (100% == 1.0) of the IFigure we're routing in.
     * 
     * @return the zoom ratio (100% == 1.0) of the IFigure we're routing in
     */
    public double getZoomFactor() {
        return _zoomFactor;
    }

    /**
     * Set the zoom ratio (100% == 1.0) of the IFigure we're routing in.
     * 
     * @param ratio the zoom ratio (100% == 1.0) of the IFigure we're routing in.
     */
    public void setZoomFactor(double ratio) {
        _zoomFactor = ratio;
    }

    /**
     * Route the {@link org.eclipse.draw2d.Connection} in a rectilinear way that
     * avoids other IFigures as much as possible to do <i>quickly</i> and without
     * making the Connection look like it walked through a maze.
     *
     * Since the destination point is an anchor point, we need to have the line
     * which goes from the source point to the destination point not only avoid the
     * source IFigure, but also avoid the destination IFigure that the destination
     * anchor is part of. But (and this is where it gets tricky), we don't just want
     * to avoid the destination IFigure; we want to do so with the least number of
     * possible bends in our line. This means that we can't just code a generic
     * algorithm for getting around a IFigure, and then apply it to both the source
     * and destination IFigures--the avoiding code needs to take into account where
     * the other IFigure and anchor point is.
     *
     * Note: To get to a IFigure on the opposite side of the anchor we're starting
     * from, we will go around the side of the source IFigure that we are closest
     * to.
     *
     * @param con the Connection being routed
     * @see org.eclipse.draw2d.ConnectionRouter#route(org.eclipse.draw2d
     *      .Connection)
     */
    public void route(Connection con) {
        ConnectionAnchor sourceAnchor = con.getSourceAnchor();
        ConnectionAnchor targetAnchor = con.getTargetAnchor();
        if (sourceAnchor == null || targetAnchor == null) {
            return;
        }

        IFigure sourceFigure = sourceAnchor.getOwner();
        IFigure targetFigure = targetAnchor.getOwner();
        if (sourceFigure == null || targetFigure == null) {
            return;
        }

        IFigure commonAncestor = getNearestCommonAncestor(sourceFigure, targetFigure);

        Point sourceAnchorLoc = sourceAnchor.getLocation(sourceAnchor.getReferencePoint());
        Point targetAnchorLoc = targetAnchor.getLocation(targetAnchor.getReferencePoint());

        // this includes source, which is the FIRST point of the returned array
        Point[] sourcePts = getRequiredSourcePoints(
            sourceAnchorLoc,
            sourceAnchor,
            sourceFigure,
            targetAnchorLoc);

        // this includes dest, which is the LAST point of the returned array
        Point[] targetPts = getRequiredDestPoints(
            targetAnchorLoc,
            targetAnchor,
            targetFigure,
            sourcePts[sourcePts.length - 1]);

        Point lastSourcePt = sourcePts[sourcePts.length - 1];
        Point lastDestPt = targetPts[0];
        Point bridgeCandidateOne = new Point(lastSourcePt.x, lastDestPt.y);
        Point bridgeCandidateTwo = new Point(lastDestPt.x, lastSourcePt.y);
        int numPoints = sourcePts.length + 1 + targetPts.length;

        Point[] boundsCheckPointsOne = new Point[] {
            new Point(lastSourcePt),
            bridgeCandidateOne,
            new Point(lastDestPt) };
        int numIntersectedShapesOne = getNumIntersectedFigures(boundsCheckPointsOne, commonAncestor);

        Point[] boundsCheckPointsTwo = new Point[] {
            new Point(lastSourcePt),
            bridgeCandidateTwo,
            new Point(lastDestPt) };
        int numIntersectedShapesTwo = getNumIntersectedFigures(boundsCheckPointsTwo, commonAncestor);

        Point[] newPointArray = new Point[numPoints];
        System.arraycopy(sourcePts, 0, newPointArray, 0, sourcePts.length);
        if (numIntersectedShapesOne <= numIntersectedShapesTwo) {
            newPointArray[sourcePts.length] = bridgeCandidateOne;
        } else {
            newPointArray[sourcePts.length] = bridgeCandidateTwo;
        }
        System.arraycopy(
            targetPts,
            0,
            newPointArray,
            sourcePts.length + 1,
            targetPts.length);
        newPointArray = addSidePoints(stripRedundantCornerPoints(newPointArray));

        PointList newPts = new PointList(newPointArray.length);
        for (int i = 0; i < newPointArray.length; i++) {
            newPts.addPoint(newPointArray[i]);
        }

        PointList points = con.getPoints();
        points.removeAllPoints();

        con.setPoints(newPts);
    }

    /**
     * @param points an array OF CORNER POINTS ONLY
     * @return the array of non-redundant corner points
     */
    protected static Point[] stripRedundantCornerPoints(Point[] points) {
        Vector keepPts = new Vector();
        Point prevAddedPoint = points[0];
        keepPts.add(points[0]); // always add start point
        boolean xLine, yLine;
        for (int i = 1; i < points.length - 1; i++) {
            xLine = sameX(prevAddedPoint, points[i])
                && sameX(points[i], points[i + 1]);
            yLine = sameY(prevAddedPoint, points[i])
                && sameY(points[i], points[i + 1]);
            if (!(xLine || yLine)) {
                keepPts.add(points[i]);
                prevAddedPoint = points[i];
            }
        }
        keepPts.add(points[points.length - 1]); // always add end point
        return (Point[]) (keepPts.toArray(new Point[] {}));
    }
    
    /**
     * @param points an array OF CORNER POINTS ONLY
     * @return the array of non-redundant corner points
     */
    protected static PointList stripRedundantCornerPoints(PointList points) {
        Vector keepPts = new Vector();
        Point prevAddedPoint = points.getFirstPoint();
        keepPts.add(points.getFirstPoint()); // always add start point
        boolean xLine, yLine;
        for (int i = 1; i < points.size() - 1; i++) {
            xLine = sameX(prevAddedPoint, points.getPoint(i))
                && sameX(points.getPoint(i), points.getPoint(i+1));
            yLine = sameY(prevAddedPoint, points.getPoint(i))
                && sameY(points.getPoint(i), points.getPoint(i+1));
            if (!(xLine || yLine)) {
                keepPts.add(points.getPoint(i));
                prevAddedPoint = points.getPoint(i);
            }
        }
        keepPts.add(points.getPoint(points.size() - 1)); // always add end point
       
        // to 
       
       
        
        //return (Point[]) (keepPts.toArray(new Point[] {}));
        return  arrayToPointList((Point[]) keepPts.toArray(new Point[] {})); 
    }

    private static PointList arrayToPointList(Point[] array) {
        
        PointList list = new PointList(); 
        for (Point p : array) {
            list.addPoint(p);
        }
        return list;
    }

    /**
     * Return true if the two Points have the same x, false otherwise.
     * 
     * @param a the first point
     * @param b the second point
     * @return true if the two Points have the same x, false otherwise.
     */
    protected static boolean sameX(Point a, Point b) {
        return (a.x == b.x);
    }

    /**
     * Return true if the two Points have the same y, false otherwise.
     * 
     * @param a the first point
     * @param b the second point
     * @return true if the two Points have the same y, false otherwise.
     */
    protected static boolean sameY(Point a, Point b) {
        return (a.y == b.y);
    }

    /**
     * Create the side handles.
     * 
     * @param points an array of non-redundant corner points
     * @return an array which includes all the non-redundant corner points, now
     *         separated by side handles
     */
    public static Point[] addSidePoints(Point[] points) {
        Point[] retval = new Point[2 * points.length - 1];
        // assign even points to the corner points passed in
        for (int i = 0; i < points.length; i++) {
            retval[2 * i] = points[i];
        }
        // generate the side points
        for (int i = 0; i < points.length - 1; i++) {
            if (points[i].x == points[i + 1].x) { // vertical
                retval[2 * i + 1] = new Point(points[i].x, (points[i].y + points[i + 1].y) / 2);
            } else { // horizontal
                retval[2 * i + 1] = new Point((points[i].x + points[i + 1].x) / 2, points[i].y);
            }
        }
        return retval;
    }
 
    public static PointList addSidePoints(PointList points) {
        
       
        
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
            int dy = pointCurrent.y - nextPoint.y; 
            
            
            if (pointCurrent.x == nextPoint.x || pointCurrent.y == nextPoint.y  ) { 
                retval[2 * i + 1] = new Point(pointCurrent.x,  pointCurrent.y ); // 
            } else { 
                // do ortho
                retval[2 * i + 1] = new Point(pointCurrent.x, pointCurrent.y-dy);
            }
        }
        return arrayToPointList(retval); 
    }
    /**
     * Return the required points coming out of the source IFigure.
     * 
     * @param source       the source point
     * @param sourceAnchor the source anchor
     * @param sourceFigure the source shape
     * @param dest         the dest point
     * @return the required points coming out of the source IFigure
     */
    protected Point[] getRequiredSourcePoints(
        Point source,
        ConnectionAnchor sourceAnchor,
        IFigure sourceFigure,
        Point dest) {
        Vector pts = new Vector();
        pts.add(source);

        Rectangle sb = sourceFigure.getBounds();
        int zoomedGap = (int) (_gap * _zoomFactor);
        Rectangle sbg = new Rectangle(
            sb.x - zoomedGap,
            sb.y - zoomedGap,
            sb.width + 2 * zoomedGap,
            sb.height + 2 * zoomedGap);

        int relToSrcShapeLoc = getRelativeLocation(sbg, dest);

        if (relToSrcShapeLoc == INTERIOR) {
            return new Point[] { source };
        }

        int anchorType = getRelativeLocation(
            sourceAnchor.getOwner().getBounds(),
            sourceAnchor.getLocation(sourceAnchor.getReferencePoint()));

        switch (anchorType) {
            case N:
                pts.add(new Point(source.x, sbg.y));
                switch (relToSrcShapeLoc) {
                    case N:
                    case NW:
                    case NE:
                        // don't add any more
                        break;
                    case W:
                    case SW:
                        pts.add(new Point(sbg.x, sbg.y));
                        break;
                    case E:
                    case SE:
                        pts.add(new Point(sbg.x + sbg.width, sbg.y));
                        break;
                    case S:
                        if (dest.x < sb.x + sb.width / 2) { // go around left
                            pts.add(new Point(sbg.x, sbg.y));
                            pts.add(new Point(sbg.x, sbg.y + sbg.height));
                        } else { // go around right
                            pts.add(new Point(sbg.x + sbg.width, sbg.y));
                            pts.add(
                                new Point(
                                    sbg.x + sbg.width,
                                    sbg.y + sbg.height));
                        }
                        break;
                    default: // unreachable
                }
                break;
            case S:
                pts.add(new Point(source.x, sbg.y + sbg.height));
                switch (relToSrcShapeLoc) {
                    case S:
                    case SW:
                    case SE:
                        // don't add any more
                        break;
                    case NW:
                    case W:
                        pts.add(new Point(sbg.x, sbg.y + sbg.height));
                        break;
                    case NE:
                    case E:
                        pts.add(
                            new Point(sbg.x + sbg.width, sbg.y + sbg.height));
                        break;
                    case N:
                        if (dest.x < sb.x + sb.width / 2) { // go around left
                            pts.add(new Point(sbg.x, sbg.y + sbg.height));
                            pts.add(new Point(sbg.x, sbg.y));
                        } else { // go around right
                            pts.add(
                                new Point(
                                    sbg.x + sbg.width,
                                    sbg.y + sbg.height));
                            pts.add(new Point(sbg.x + sbg.width, sbg.y));
                        }
                        break;
                    default: // unreachable
                }
                break;
            case E:
                pts.add(new Point(sbg.x + sbg.width, source.y));
                switch (relToSrcShapeLoc) {
                    case E:
                    case NE:
                    case SE:
                        // don't add any more
                        break;
                    case S:
                    case SW:
                        pts.add(
                            new Point(sbg.x + sbg.width, sbg.y + sbg.height));
                        break;
                    case N:
                    case NW:
                        pts.add(new Point(sbg.x + sbg.width, sbg.y));
                        break;
                    case W:
                        if (dest.y < sb.y + sb.height / 2) { // go around top
                            pts.add(new Point(sbg.x + sbg.width, sbg.y));
                            pts.add(new Point(sbg.x, sbg.y));
                        } else { // go around bottom
                            pts.add(
                                new Point(
                                    sbg.x + sbg.width,
                                    sbg.y + sbg.height));
                            pts.add(new Point(sbg.x, sbg.y + sbg.height));
                        }
                        break;
                    default: // unreachable
                }
                break;
            case W:
                pts.add(new Point(sbg.x, source.y));
                switch (relToSrcShapeLoc) {
                    case W:
                    case NW:
                    case SW:
                        // don't add any more
                        break;
                    case S:
                    case SE:
                        pts.add(new Point(sbg.x, sbg.y + sbg.height));
                        break;
                    case N:
                    case NE:
                        pts.add(new Point(sbg.x, sbg.y));
                        break;
                    case E:
                        if (dest.y < sb.y + sb.height / 2) { // go around top
                            pts.add(new Point(sbg.x, sbg.y));
                            pts.add(new Point(sbg.x + sbg.width, sbg.y));
                        } else { // go around bottom
                            pts.add(new Point(sbg.x, sbg.y + sbg.height));
                            pts.add(
                                new Point(
                                    sbg.x + sbg.width,
                                    sbg.y + sbg.height));
                        }
                        break;
                    default: // unreachable
                }
                break;
            case NE:
                switch (relToSrcShapeLoc) {
                    case NE:
                        // could work either way; don't append any points
                        break;
                    case N:
                    case NW:
                        pts.add(new Point(source.x, sbg.y));
                        break;
                    case E:
                    case SE:
                        pts.add(new Point(sbg.x + sbg.width, source.y));
                        break;
                    case S:
                    case SW:
                        pts.add(new Point(sbg.x + sbg.width, source.y));
                        pts.add(
                            new Point(sbg.x + sbg.width, sbg.y + sbg.height));
                        break;
                    case W:
                        pts.add(new Point(source.x, sbg.y));
                        pts.add(new Point(sbg.x, sbg.y));
                        break;
                    default: // unreachable
                }
                break;
            case NW:
                switch (relToSrcShapeLoc) {
                    case NW:
                        // could work either way; don't append any points
                        break;
                    case N:
                    case NE:
                        pts.add(new Point(source.x, sbg.y));
                        break;
                    case W:
                    case SW:
                        pts.add(new Point(sbg.x, source.y));
                        break;
                    case S:
                    case SE:
                        pts.add(new Point(sbg.x, source.y));
                        pts.add(new Point(sbg.x, sbg.y + sbg.height));
                        break;
                    case E:
                        pts.add(new Point(source.x, sbg.y));
                        pts.add(new Point(sbg.x + sbg.width, sbg.y));
                        break;
                    default: // unreachable
                }
                break; // to here
            case SE:
                switch (relToSrcShapeLoc) {
                    case SE:
                        // could work either way; don't append any points
                        break;
                    case S:
                    case SW:
                        pts.add(new Point(source.x, sbg.y + sbg.height));
                        break;
                    case E:
                    case NE:
                        pts.add(new Point(sbg.x + sbg.width, source.y));
                        break;
                    case N:
                    case NW:
                        pts.add(new Point(sbg.x + sbg.width, source.y));
                        pts.add(new Point(sbg.x + sbg.width, sbg.y));
                        break;
                    case W:
                        pts.add(new Point(source.x, sbg.y + sbg.height));
                        pts.add(new Point(sbg.x, sbg.y + sbg.height));
                        break;
                    default: // unreachable
                }
                break;
            case SW:
                switch (relToSrcShapeLoc) {
                    case SW:
                        // could work either way; don't append any points
                        break;
                    case S:
                    case SE:
                        pts.add(new Point(source.x, sbg.y + sbg.height));
                        break;
                    case W:
                    case NW:
                        pts.add(new Point(sbg.x, source.y));
                        break;
                    case N:
                    case NE:
                        pts.add(new Point(sbg.x, source.y));
                        pts.add(new Point(sbg.x, sbg.y));
                        break;
                    case E:
                        pts.add(new Point(source.x, sbg.y + sbg.height));
                        pts.add(
                            new Point(sbg.x + sbg.width, sbg.y + sbg.height));
                        break;
                    default: // unreachable
                }
                break;
            default: // INTERIOR
        }
        return (Point[]) (pts.toArray(new Point[] {}));
    }

    /**
     * Return the location of the Point p relative to the Rectangle r (one of the
     * directional constants)
     * 
     * @param r the Rectangle the Point is relative to
     * @param p the Point whose location relative to the Rectangle we are getting
     * @return the location of the Point p relative to the Rectangle r (one of the
     *         directional constants)
     */
    protected int getRelativeLocation(Rectangle r, Point p) {
        if (p.y <= r.y) { // top
            if (p.x <= r.x) { // left
                return NW;
            } else if (p.x >= r.right()) { // right
                return NE;
            } else { // middle
                return N;
            }
        } else if (p.y >= r.bottom()) { // bottom
            if (p.x <= r.x) { // left
                return SW;
            } else if (p.x >= r.right()) { // right
                return SE;
            } else { // middle
                return S;
            }
        } else {
            if (p.x <= r.x) { // left
                return W;
            } else if (p.x >= r.right()) { // right
                return E;
            } else { // middle
                return INTERIOR;
            }
        }
    }

    /**
     * Return the required points coming out of the source IFigure.
     * 
     * @param dest       the dest point
     * @param destAnchor the dest anchor
     * @param destFigure the dest shape
     * @param source     the source point
     * @return the required points coming out of the source IFigure
     */
    protected Point[] getRequiredDestPoints(
        Point dest,
        ConnectionAnchor destAnchor,
        IFigure destFigure,
        Point source) {
        if (destAnchor != null && destFigure != null) {
            Point[] backwardsPts = getRequiredSourcePoints(dest, destAnchor, destFigure, source);
            Point[] retval = new Point[backwardsPts.length];
            for (int i = 0; i < retval.length; i++) {
                retval[i] = backwardsPts[backwardsPts.length - i - 1];
            }
            return retval;
        } else {
            return new Point[] { dest };
        }
    }

    /**
     * Return the number of IFigures that are children of the given IFigure that the
     * given polyline intersects (not including Connections).
     * 
     * @param polylinePoints the points of the polyline
     * @param parent         the IFigure whose children we are checking to see if
     *                       they intersect
     * @return the number of IFigures that are children of the given IFigure that
     *         the given polyline intersects (not including Connections)
     */
    protected int getNumIntersectedFigures(
        Point[] polylinePoints,
        IFigure parent) {
        java.awt.Polygon bounds = createBounds(polylinePoints);
        int numShapesIntersected = 0;
        Iterator shapes = parent.getChildren().iterator();
        IFigure shape;
        while (shapes.hasNext()) {
            shape = (IFigure) shapes.next();
            Rectangle sb = shape.getBounds();
            if (!(shape instanceof Connection)
                && bounds.intersects(sb.x, sb.y, sb.width, sb.height)) {
                numShapesIntersected++;
            }
        }
        return numShapesIntersected;
    }

    /**
     * I have to return a java.awt.Polygon here, since the intersects method of
     * org.eclipse.draw2d.Polygon does not work the way I need it to: it does not
     * test if the org.eclipse.draw2d.Polygon intersects, but rather if the bounding
     * rectangle of the org.eclipse.draw2d.Polygon does (it merely inherits its
     * behavior from Figure, which does this. So I need to do this unless the
     * behavior of org.eclipse.draw2d.Polygon is changed.
     *
     * This method creates the bounds polygons which are the bounds of the
     * connectors. Each handle results in two bounds points being created, at the
     * ith and (n - 1 - i)th positions in the array (since we want the polygon lines
     * not to criss-cross). The bounds polygon treats the connector as the median in
     * a divided highway.
     *
     * @param points the points in the Connector
     * @return a Polygon which surrounds the points
     */
    protected static java.awt.Polygon createBounds(Point[] points) {
        // Half of the width of the bounds polygon we're creating.
        int bwidth2 = 1;

        int n = 2 * points.length; // number of points
        int[] xpoints = new int[n];
        int[] ypoints = new int[n];

        Point h1, h2, h3; // the three handle locations we're considering at
        // once to get the angle

        //
        // Process the special case of the first handle.
        //
        h1 = points[0];
        h2 = points[1];
        if (h1.x == h2.x) {
            // it's a vertical line
            ypoints[0] = h1.y;
            ypoints[n - 1] = h1.y;
            if (h1.y < h2.y) { // going down
                xpoints[0] = h1.x - bwidth2;
                xpoints[n - 1] = h1.x + bwidth2;
            } else { // going up
                xpoints[0] = h1.x + bwidth2;
                xpoints[n - 1] = h1.x - bwidth2;
            }
        } else {
            // it's a horizontal line
            xpoints[0] = h1.x;
            xpoints[n - 1] = h1.x;
            if (h1.x < h2.x) { // going right
                ypoints[0] = h1.y + bwidth2;
                ypoints[n - 1] = h1.y - bwidth2;
            } else { // going left
                ypoints[0] = h1.y - bwidth2;
                ypoints[n - 1] = h1.y + bwidth2;
            }
        }

        //
        // Process the second to the second last handles (we're assigning the
        // bounds points for h2).
        //
        for (int i = 0; i < points.length - 2; i++) {
            h1 = points[i];
            h2 = points[i + 1];
            h3 = points[i + 2];
            if (h1.x == h2.x) {
                // first line is a vertical line, so the second line will be
                // a horizontal line
                if (h1.y < h2.y) { // first line going down
                    xpoints[i + 1] = h2.x - bwidth2;
                    xpoints[n - 2 - i] = h2.x + bwidth2;
                    if (h2.x < h3.x) {
                        // second line left to right
                        ypoints[i + 1] = h2.y + bwidth2;
                        ypoints[n - 2 - i] = h2.y - bwidth2;
                    } else {
                        // second line right to left
                        ypoints[i + 1] = h2.y - bwidth2;
                        ypoints[n - 2 - i] = h2.y + bwidth2;
                    }
                } else { // first line going up
                    xpoints[i + 1] = h2.x + bwidth2;
                    xpoints[n - 2 - i] = h2.x - bwidth2;
                    if (h2.x < h3.x) {
                        // second line left to right
                        ypoints[i + 1] = h2.y + bwidth2;
                        ypoints[n - 2 - i] = h2.y - bwidth2;
                    } else {
                        // second line right to left
                        ypoints[i + 1] = h2.y - bwidth2;
                        ypoints[n - 2 - i] = h2.y + bwidth2;
                    }
                }
            } else {
                // first line is a horizontal line, so the second line will be
                // vertical
                if (h1.x < h2.x) { // first line going right
                    ypoints[i + 1] = h2.y + bwidth2;
                    ypoints[n - 2 - i] = h2.y - bwidth2;
                    if (h2.y < h3.y) {
                        // second line up to down
                        xpoints[i + 1] = h2.x - bwidth2;
                        xpoints[n - 2 - i] = h2.x + bwidth2;
                    } else {
                        // second line down to up
                        xpoints[i + 1] = h2.x + bwidth2;
                        xpoints[n - 2 - i] = h2.x - bwidth2;
                    }
                } else { // first line going left
                    ypoints[i + 1] = h2.y - bwidth2;
                    ypoints[n - 2 - i] = h2.y + bwidth2;
                    if (h2.y < h3.y) {
                        // second line up to down
                        xpoints[i + 1] = h2.x - bwidth2;
                        xpoints[n - 2 - i] = h2.x + bwidth2;
                    } else {
                        // second line down to up
                        xpoints[i + 1] = h2.x + bwidth2;
                        xpoints[n - 2 - i] = h2.x - bwidth2;
                    }
                }
            }
        }

        //
        // Process the special case of the last handle.
        //
        h1 = points[points.length - 2];
        h2 = points[points.length - 1]; // last handle
        if (h1.x == h2.x) {
            // last line is vertical
            ypoints[points.length - 1] = h2.y;
            ypoints[points.length] = h2.y;
            if (h1.y < h2.y) { // going down
                xpoints[points.length - 1] = h2.x - bwidth2;
                xpoints[points.length] = h2.x + bwidth2;
            } else { // going up
                xpoints[points.length - 1] = h2.x + bwidth2;
                xpoints[points.length] = h2.x - bwidth2;
            }
        } else {
            // last line is horizontal
            xpoints[points.length - 1] = h2.x;
            xpoints[points.length] = h2.x;
            if (h1.x < h2.x) { // going right
                ypoints[points.length - 1] = h2.y + bwidth2;
                ypoints[points.length] = h2.y - bwidth2;
            } else { // going left
                ypoints[points.length - 1] = h2.y - bwidth2;
                ypoints[points.length] = h2.y + bwidth2;
            }
        }

        // finally build and return the new bounds polygon
        return new java.awt.Polygon(xpoints, ypoints, n);
    }

    /**
     * Get the IFigure that contains fig1 and fig2 the most directly.
     * 
     * @param fig1 the first IFigure to check
     * @param fig2 the second IFigure to check
     * @return the IFigure that contains fig1 and fig2 the most directly.
     */
    protected IFigure getNearestCommonAncestor(
        IFigure fig1, IFigure fig2) {
        IFigure fig1Parent = fig1.getParent();
        IFigure fig2Parent = fig2.getParent();
        if (fig1Parent == null || fig2Parent == null) {
            return null;
        } else if (fig1Parent == fig2Parent) {
            return fig1Parent;
        } else {
            // If we get here, both fig1parent and fig2parent are non-null and
            // not equal to each other. So, figure out whether either is the
            // ancestor of each other (and if so, return it) or else if they have
            // a common ancestor (and return that). If neither of them are, then
            // check their parents...
            // TODO - refactor/rewrite?
            if (isAncestor(fig1Parent, fig2Parent)) {
                return fig1Parent;
            }
            if (isAncestor(fig2Parent, fig1Parent)) {
                return fig2Parent;
            }
            return getNearestCommonAncestor(fig1Parent, fig2Parent);
        }
    }

    /**
     * Return true if fig1 is an ancestor of fig2, false otherwise.
     * 
     * @param fig1 the IFigure ancestor candidate
     * @param fig2 the IFigure which we're checking ancestors of
     * @return true if fig1 is an ancestor of fig2, false otherwise
     */
    protected boolean isAncestor(IFigure fig1, IFigure fig2) {
        IFigure fig2parent = fig2.getParent();
        if (fig2parent == null) {
            return false;
        }
        if (fig2parent == fig1) {
            return true;
        }
        return isAncestor(fig1, fig2parent);
    }
}
