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

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.PolylineConnection;
import org.eclipse.draw2d.geometry.Geometry;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.PointList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.jkiss.dbeaver.erd.ui.ERDUIConstants;
import org.jkiss.dbeaver.ui.UIUtils;

public class ERDiagramConnection extends PolylineConnection {

    private static final double DELTA = 0.15;
    private static final int TOLERANCE = 2;
    private boolean isLineSelected;
    private boolean bezier;
    private PointList overlappingLinesByX = new PointList();
    private PointList overlappingLinesByY = new PointList();
    private PointList crossRouters = new PointList();

    public ERDiagramConnection(boolean bezier) {
        this.bezier = bezier;
    }

    public void setSelected(boolean selected) {
        this.isLineSelected = selected;
    }

    public void setBezierLine(boolean bezier) {
        this.bezier = bezier;
    }

    @Override
    protected void outlineShape(Graphics g) {
        g.setAntialias(SWT.ON);
        if (isLineSelected) {
            if (bezier) {
                g.setForegroundColor(ColorConstants.gray);
                final PointList points = getPoints();
                g.drawPolyline(points);
            }
            g.setLineWidth(4);
        }
        final PointList points = getOrthoPoints();
        int width = g.getLineWidth();
        Color color = g.getForegroundColor();

        final int lineRed = color.getRed();
        final int lineGreen = color.getGreen();
        final int lineBlue = color.getBlue();

        final int deltaRed = (255 - lineRed) * 2 / width;
        final int deltaGreen = (255 - lineGreen) * 2 / width;
        final int deltaBlue = (255 - lineBlue) * 2 / width;

        int red = 255;
        int green = 255;
        int blue = 255;

        while (width > 0) {
            red -= deltaRed;
            green -= deltaGreen;
            blue -= deltaBlue;

            if (red < lineRed) {
                red = lineRed;
            }
            if (green < lineGreen) {
                green = lineGreen;
            }
            if (blue < lineBlue) {
                blue = lineBlue;
            }

            color = new Color(Display.getCurrent(), red, green, blue);

            g.setLineWidth(width);
            g.setForegroundColor(color);
            g.drawPolyline(points);

            width -= 2;
        }
        g.setForegroundColor(UIUtils.getColorRegistry().get(ERDUIConstants.COLOR_ERD_LINES_FOREGROUND));
        g.setBackgroundColor(UIUtils.getColorRegistry().get(ERDUIConstants.COLOR_ERD_LINES_FOREGROUND));
        if (isLineSelected) {
            g.setForegroundColor(ColorConstants.blue);
            g.setBackgroundColor(ColorConstants.red);
        }

        for (int i = 0; i < crossRouters.size(); i++) {
            g.setBackgroundColor(ColorConstants.white);
            Point p1 = crossRouters.getPoint(i);
            g.fillArc(p1.x - 5, p1.y - 5, 10, 12, 0, 180);
            g.setForegroundColor(UIUtils.getColorRegistry().get(ERDUIConstants.COLOR_ERD_LINES_FOREGROUND));
            g.setBackgroundColor(UIUtils.getColorRegistry().get(ERDUIConstants.COLOR_ERD_LINES_FOREGROUND));
            g.drawArc(p1.x - 5, p1.y - 5, 10, 10, 0, 180);
        }
    }

    private PointList getOrthoPoints() {
        return getPoints();
    }

    public PointList getBezierPoints() {
        final PointList controlPoints = getPoints();
        if (bezier && controlPoints.size() >= 3) {
            int index = 0;
            final PointList pointList = new PointList();
            Point p0 = controlPoints.getPoint(index++);
            Point p1 = controlPoints.getPoint(index++);
            Point p2 = null;
            Point nextPoint = controlPoints.getPoint(index++);

            while (true) {
                if (index != controlPoints.size()) {
                    p2 = new Point((p1.x + nextPoint.x) / 2, (p1.y + nextPoint.y) / 2);
                } else {
                    p2 = nextPoint;
                }
                for (double t = 0.0; t <= 1.0; t = t + DELTA) {
                    final Point point = new Point();
                    point.x = (int) (p0.x * (1 - t) * (1 - t) + 2 * p1.x * t * (1 - t) + p2.x * t * t);
                    point.y = (int) (p0.y * (1 - t) * (1 - t) + 2 * p1.y * t * (1 - t) + p2.y * t * t);
                    pointList.addPoint(point);
                }
                pointList.addPoint(p2);
                if (index == controlPoints.size()) {
                    break;
                }
                p0 = p2;
                p1 = nextPoint;
                nextPoint = controlPoints.getPoint(index++);
            }
            return pointList;
        }
        return controlPoints;
    }

    @Override
    protected boolean shapeContainsPoint(int x, int y) {
        return Geometry.polylineContainsPoint(getBezierPoints(), x, y, TOLERANCE);
    }

    public void hightLightRoutePointsByX(Point p) {
        if (p != null) {
            this.overlappingLinesByX.addPoint(p);
        }
    }

    public void hightLightRoutePointsByY(Point p) {
        if (p != null) {
            this.overlappingLinesByY.addPoint(p);
        }
    }

    public void cleanupOverlappedPoints() {
        this.overlappingLinesByY.removeAllPoints();
        this.overlappingLinesByX.removeAllPoints();
        this.crossRouters.removeAllPoints();
    }

    @Override
    public void revalidate() {
        super.revalidate();
    }

    public void cleanupCrossRouters() {
        this.crossRouters.removeAllPoints();
    }

    public void setCrossRouters(PointList crossRouters) {
        this.crossRouters.removeAllPoints();
        this.crossRouters.addAll(crossRouters);
    }

}
