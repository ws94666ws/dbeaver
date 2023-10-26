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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;

public class VertexPoint extends Point {

    private static final long serialVersionUID = 1L;
    // constants for the vertex type
    static final int NOT_SET = 0;
    static final int INNIE = 1;
    static final int OUTIE = 2;

    // for shortest path
    private List<Object> neighbors;
    private boolean isPermanent = false;
    private VertexPoint label;
    private double cost = 0;

    // for routing
    private int nearestObstacle = 0;
    private double offset = 0;
    private int type = NOT_SET;
    int count = 0;
    int totalCount = 0;
    private VertexRectangle obs;
    private List<OrthoPath> paths;
    private boolean nearestObstacleChecked = false;
    private Map<OrthoPath, Double> cachedCosines;

    private int positionOnObstacle = -1;

    private int origX, origY;

    /**
     * Creates a new Vertex with the given x, y position and on the given obstacle.
     * 
     * @param x   x point
     * @param y   y point
     * @param obs obstacle - can be null
     */
    VertexPoint(int x, int y, VertexRectangle obs) {
        super(x, y);
        origX = x;
        origY = y;
        this.obs = obs;
    }

    /**
     * Creates a new Vertex with the given point position and on the given obstacle.
     * 
     * @param p   the point
     * @param obs obstacle - can be null
     */
    VertexPoint(Point p, VertexRectangle obs) {
        this(p.x, p.y, obs);
    }

    /**
     * Adds a path to this vertex, calculates angle between two segments and caches
     * it.
     * 
     * @param path  the path
     * @param start the segment to this vertex
     * @param end   the segment away from this vertex
     */
    public void addPath(OrthoPath path, VertexSegment start, VertexSegment end) {
        if (paths == null) {
            paths = new ArrayList<>();
            cachedCosines = new HashMap<>();
        }
        if (!paths.contains(path))
            paths.add(path);
        cachedCosines.put(path, Double.valueOf(start.cosine(end)));
    }

    /**
     * Creates a point that represents this vertex offset by the given amount times
     * the offset.
     * 
     * @param modifier the offset
     * @return a Point that has been bent around this vertex
     */
    public Point bend(int modifier) {
        Point point = new Point(x, y);
        if ((positionOnObstacle & PositionConstants.NORTH) > 0)
            point.y -= modifier * offset;
        else
            point.y += modifier * offset;
        if ((positionOnObstacle & PositionConstants.EAST) > 0)
            point.x += modifier * offset;
        else
            point.x -= modifier * offset;
        return point;
    }

    /**
     * Resets all fields on this Vertex.
     */
    public void fullReset() {
        totalCount = 0;
        type = NOT_SET;
        count = 0;
        cost = 0;
        offset = getSpacing();
        nearestObstacle = 0;
        setLabel(null);
        nearestObstacleChecked = false;
        setPermanent(false);
        if (neighbors != null)
            neighbors.clear();
        if (cachedCosines != null)
            cachedCosines.clear();
        if (paths != null)
            paths.clear();
    }

    /**
     * Returns a Rectangle that represents the region around this vertex that paths
     * will be traveling in.
     * 
     * @param extraOffset a buffer to add to the region.
     * @return the rectangle
     */
    public Rectangle getDeformedRectangle(int extraOffset) {
        Rectangle rect = new Rectangle(0, 0, 0, 0);

        if ((positionOnObstacle & PositionConstants.NORTH) > 0) {
            rect.y = y - extraOffset;
            rect.height = origY - y + extraOffset;
        } else {
            rect.y = origY;
            rect.height = y - origY + extraOffset;
        }
        if ((positionOnObstacle & PositionConstants.EAST) > 0) {
            rect.x = origX;
            rect.width = x - origX + extraOffset;
        } else {
            rect.x = x - extraOffset;
            rect.width = origX - x + extraOffset;
        }

        return rect;
    }

    private int getSpacing() {
        if (obs == null) {
            return 0;
        }
        return 1;
    }

    /**
     * Grows this vertex by its offset to its maximum size.
     */
    public void grow() {
        int modifier;

        if (nearestObstacle == 0)
            modifier = totalCount * getSpacing();
        else
            modifier = (nearestObstacle / 2) - 1;

        if ((positionOnObstacle & PositionConstants.NORTH) > 0)
            y -= modifier;
        else
            y += modifier;
        if ((positionOnObstacle & PositionConstants.EAST) > 0)
            x += modifier;
        else
            x -= modifier;
    }

    /**
     * Shrinks this vertex to its original size.
     */
    public void shrink() {
        x = origX;
        y = origY;
    }

    /**
     * Updates the offset of this vertex based on its shortest distance.
     */
    public void updateOffset() {
        if (nearestObstacle != 0)
            offset = ((nearestObstacle / 2) - 1) / totalCount;
    }

    /**
     * @see org.eclipse.draw2d.geometry.Point#toString()
     */
    @Override
    public String toString() {
        return "V(" + origX + ", " + origY + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public boolean isPermanent() {
        return isPermanent;
    }

    public void setPermanent(boolean isPermanent) {
        this.isPermanent = isPermanent;
    }

    public VertexPoint getLabel() {
        return label;
    }

    public void setLabel(VertexPoint label) {
        this.label = label;
    }

    public double getCost() {
        return cost;
    }

    public boolean isNearestObstacleChecked() {
        return nearestObstacleChecked;
    }

    public void setNearestObstacleChecked(boolean flag) {
        nearestObstacleChecked = flag;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getPositionOnObstacle() {
        return positionOnObstacle;
    }

    public void setPositionOnObstacle(int position) {
        this.positionOnObstacle = position;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public List<Object> getNeighbors() {
        return neighbors;
    }

    public void setNeighbors(ArrayList<Object> arrayList) {
        neighbors = arrayList;
    }

    public VertexRectangle getObs() {
        return obs;
    }

    public int getNearestObstacle() {
        return nearestObstacle;
    }

    public void setNearestObstacle(int nearestObstacle) {
        this.nearestObstacle = nearestObstacle;
    }

    public Map<OrthoPath, Double> getCachedCosines() {
        return cachedCosines;
    }

    public void setCachedCosines(Map<OrthoPath, Double> cachedCosines) {
        this.cachedCosines = cachedCosines;
    }

    public List<OrthoPath> getPaths() {
        return paths;
    }

    public void setPaths(List<OrthoPath> paths) {
        this.paths = paths;
    }

}
