package org.jkiss.dbeaver.erd.ui.router;

import org.eclipse.draw2d.AbstractConnectionAnchor;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.ScalableFreeformLayeredPane;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PrecisionPoint;
import org.eclipse.draw2d.geometry.Rectangle;

public class RelativeConnectionAnchor extends AbstractConnectionAnchor {
    /**
     * The ratio (between 0 and 1) of where the RelativeConnectionAnchor is in the x
     * direction with respect to its owner IFigure.
     */
    private double _xRatio;

    /**
     * The ratio (between 0 and 1) of where the RelativeConnectionAnchor is in the y
     * direction with respect to its owner IFigure.
     */
    private double _yRatio;

    /**
     * Create a new RelativeConnectionAnchor with at the the given xRatio and yRatio
     * inside the given owner.
     * 
     * @param owner  the IFigure that the RelativeConnectionAnchor is in
     * @param xRatio the x location within the IFigure owner [0.0, 1.0]
     * @param yRatio the y location within the IFigure owner [0.0, 1.0]
     */
    public RelativeConnectionAnchor(
        IFigure owner, double xRatio, double yRatio) {
        super(owner);
        _xRatio = xRatio;
        _yRatio = yRatio;
    }

    /**
     * @param figure the ancestor that moved
     * @see org.eclipse.draw2d.AbstractConnectionAnchor#ancestorMoved(IF igure)
     */
    public void ancestorMoved(IFigure figure) {
        if (figure instanceof ScalableFreeformLayeredPane) {
            return;
        }
        super.ancestorMoved(figure);
    }

    /**
     * Get the location of the RelativeConnectionAnchor given the given reference
     * point (which is ignored in this case). This method exists to implement the
     * ConnectionAnchor interface, but its effect is identical to calling
     * getReferencePoint().
     * 
     * @param reference the reference point, which is ignored
     * @return the location of the RelativeConnectionAnchor
     */
    public Point getLocation(Point reference) {
        return getReferencePoint();
    }

    /**
     * Get the getReferencePoint of the RelativeConnectionAnchor, which is actually
     * the location of the RelativeConnectionAnchor.
     * 
     * @return the getReferencePoint of the RelativeConnectionAnchor, which is
     *         actually the location of the RelativeConnectionAnchor.
     */
    public Point getReferencePoint() {
        Rectangle r = getOwner().getBounds();
        Point p = new PrecisionPoint(
            r.x + _xRatio * r.width,
            r.y + _yRatio * r.height);
        getOwner().translateToAbsolute(p);
        return p;
    }
}
