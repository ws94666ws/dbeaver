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

import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;

public class OrthoPathUtils {
    public static final int RIGHT = 180;
    public static final int LEFT = 0;
    public static final int UP = 90;
    public static final int DOWN = -90;
    public static final int TO_OURSELF = -360;

    public static int getLRDirection(Rectangle r, Point p) {
        int i = 0;
        int direction = LEFT;
        int distance = Math.abs(r.x - p.x);
        i = Math.abs(r.right() - p.x);
        if (i < distance) {
            direction = RIGHT;
        }
        if (distance < r.width + 60) {
            direction = DOWN;
        }
        return direction;
    }

    public static int getLRDirection(Rectangle r, Rectangle p) {
        int i = 0;
        int direction = LEFT;
        int distance = Math.abs(r.x - p.x);
        i = Math.abs(r.right() - p.x);
        if (i < distance) {
            direction = RIGHT;
        }
        if (r.left() > p.right()) {
            direction = LEFT;
        }
        if (distance < r.width + 60) {
            direction = DOWN;
        }
        return direction;
    }

}
