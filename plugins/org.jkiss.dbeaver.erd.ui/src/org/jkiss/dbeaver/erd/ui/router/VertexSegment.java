package org.jkiss.dbeaver.erd.ui.router;

import org.eclipse.draw2d.geometry.Geometry;
import org.eclipse.draw2d.geometry.Point;

public class VertexSegment {
    VertexPoint start, end;

    /**
     * Creates a segment between the given start and end points.
     * 
     * @param start the start vertex
     * @param end   the end vertex
     */
    VertexSegment(VertexPoint start, VertexPoint end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Returns the cosine of the made between this segment and the given segment
     * 
     * @param otherSegment the other segment
     * @return cosine value (not arc-cos)
     */
    double cosine(VertexSegment otherSegment) {
        double cos = (((start.x - end.x) * (otherSegment.end.x - otherSegment.start.x))
            + ((start.y - end.y) * (otherSegment.end.y - otherSegment.start.y)))
            / (getLength() * otherSegment.getLength());
        double sin = (((start.x - end.x) * (otherSegment.end.y - otherSegment.start.y))
            - ((start.y - end.y) * (otherSegment.end.x - otherSegment.start.x)));
        if (sin < 0.0)
            return (1 + cos);

        return -(1 + cos);
    }

    /**
     * Returns the cross product of this segment and the given segment
     * 
     * @param otherSegment the other segment
     * @return the cross product
     */
    long crossProduct(VertexSegment otherSegment) {
        return (((start.x - end.x) * (otherSegment.end.y - end.y))
            - ((start.y - end.y) * (otherSegment.end.x - end.x)));
    }

    private double getLength() {
        return (end.getDistance(start));
    }

    /**
     * Returns a number that represents the sign of the slope of this segment. It
     * does not return the actual slope.
     * 
     * @return number representing sign of the slope
     */
    double getSlope() {
        if (end.x - start.x >= 0)
            return (end.y - start.y);
        else
            return -(end.y - start.y);
    }

    /**
     * Returns true if the given segment intersects this segment.
     * 
     * @param sx start x
     * @param sy start y
     * @param tx end x
     * @param ty end y
     * @return true if the segments intersect
     */
    boolean intersects(int sx, int sy, int tx, int ty) {
        return Geometry.linesIntersect(start.x, start.y, end.x, end.y, sx, sy, tx, ty);
    }

    /**
     * Return true if the segment represented by the points intersects this segment.
     * 
     * @param s start point
     * @param t end point
     * @return true if the segments intersect
     */
    boolean intersects(Point s, Point t) {
        return intersects(s.x, s.y, t.x, t.y);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return start + "---" + end; //$NON-NLS-1$
    }

}
