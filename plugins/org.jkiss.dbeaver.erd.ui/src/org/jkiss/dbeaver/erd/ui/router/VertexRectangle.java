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

import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;

public class VertexRectangle extends Rectangle {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    boolean exclude;
    VertexPoint topLeft;
    VertexPoint topRight;
    VertexPoint bottomLeft;
    VertexPoint bottomRight;
    VertexPoint center;
    VertexPoint centerLeft;
    VertexPoint centerRight;

    /**
     * Creates a new obstacle from the given rectangle bounds.
     * 
     * @param rect the bounds
     */
    VertexRectangle(Rectangle rect) {
        init(rect);
    }

    public boolean containsProper(Point p) {
        return p.x > this.x && p.x < this.x + this.width - 1 && p.y > this.y && p.y < this.y + this.height - 1;
    }

//     public int getSpacing() {
//      return router.getSpacing();
//     }

    private void growVertex(VertexPoint vertex) {
        if (vertex.getTotalCount() > 0)
            vertex.grow();
    }

    /**
     * Grows all vertices on this obstacle.
     */
    void growVertices() {
        growVertex(topLeft);
        growVertex(topRight);
        growVertex(bottomLeft);
        growVertex(bottomRight);
    }

    /**
     * Initializes this obstacle to the values of the given rectangle
     * 
     * @param rect bounds of this obstacle
     */
    void init(Rectangle rect) {
        this.x = rect.x;
        this.y = rect.y;
        this.width = rect.width;
        this.height = rect.height;

        exclude = false;

        topLeft = new VertexPoint(x, y, this);
        topLeft.setPositionOnObstacle(PositionConstants.NORTH_WEST);

        topRight = new VertexPoint(x + width - 1, y, this);
        topRight.setPositionOnObstacle(PositionConstants.NORTH_EAST);

        bottomLeft = new VertexPoint(x, y + height - 1, this);
        bottomLeft.setPositionOnObstacle(PositionConstants.SOUTH_WEST);

        bottomRight = new VertexPoint(x + width - 1, y + height - 1, this);
        bottomRight.setPositionOnObstacle(PositionConstants.SOUTH_EAST);

        center = new VertexPoint(getCenter(), this);

        centerLeft = new VertexPoint(x, y + height / 2 - 1, this);
        centerLeft.setPositionOnObstacle(PositionConstants.WEST);

        centerRight = new VertexPoint(x + width - 1, y + height / 2 - 1, this);
        centerRight.setPositionOnObstacle(PositionConstants.EAST);
    }

    /**
     * Requests a full reset on all four vertices of this obstacle.
     */
    void reset() {
        topLeft.fullReset();
        bottomLeft.fullReset();
        bottomRight.fullReset();
        topRight.fullReset();
    }

    private void shrinkVertex(VertexPoint vertex) {
        if (vertex.getTotalCount() > 0)
            vertex.shrink();
    }

    /**
     * Shrinks all four vertices of this obstacle.
     */
    void shrinkVertices() {
        shrinkVertex(topLeft);
        shrinkVertex(topRight);
        shrinkVertex(bottomLeft);
        shrinkVertex(bottomRight);
    }

    /**
     * @see org.eclipse.draw2d.geometry.Rectangle#toString()
     */
    @Override
    public String toString() {
        return "Obstacle(" + x + ", " + y + ", " + width + ", " + height + ")";
    }
}
