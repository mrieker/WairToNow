//    Copyright (C) 2015, Mike Rieker, Beverly, MA USA
//    www.outerworldapps.com
//
//    This program is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; version 2 of the License.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    EXPECT it to FAIL when someone's HeALTh or PROpeRTy is at RISk.
//
//    You should have received a copy of the GNU General Public License
//    along with this program; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//    http://www.gnu.org/licenses/gpl-2.0.html

package com.outerworldapps.wairtonow;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * Handle panning and zooming a display.
 * Call OnTouchEvent() with incoming mouse events.
 * Implement MouseDown(), MouseUp(), Panning() and Scaling().
 */
public abstract class PanAndZoom implements ScaleGestureDetector.OnScaleGestureListener {
    private final static String TAG = "WairToNow";
    public  static final int panningblocktime = 200;

    private Context ctx;
    private double mouse0LastX;
    private double mouse0LastY;
    private long blockPanUntil;
    private ScaleGestureDetector scaleGestureDetector;

    public PanAndZoom (Context ctx)
    {
        this.ctx = ctx;
        scaleGestureDetector = new ScaleGestureDetector (ctx, this);
    }

    // called when mouse pressed
    //  x,y = absolute mouse position
    public abstract void MouseDown (double x, double y);

    // called when mouse released
    //  x,y = absolute mouse position
    public abstract void MouseUp (double x, double y);

    // called when panning
    //  x,y = absolute mouse position
    //  dx,dy = delta position
    public abstract void Panning (double x, double y, double dx, double dy);

    // called when scaling
    //  fx,fy = absolute position of center of scaling
    //  sf = delta scaling factor
    public abstract void Scaling (double fx, double fy, double sf);

    /**
     * Callback for mouse events on the image.
     */
    public boolean OnTouchEvent (MotionEvent event)
    {
        /*
         * Maybe scaling is being performed.
         * Note: scaleGestureDetector.onTouchEvent() always return true,
         *       so scaleGestureDetector.isInProgress() must be called.
         */
        try {
            scaleGestureDetector.onTouchEvent (event);
            if (scaleGestureDetector.isInProgress ()) {
                // block panning for a little time after done scaling
                // to eliminate surious jumps at end of scaling
                blockPanUntil  = System.currentTimeMillis () + panningblocktime;
                return true;
            }
        } catch (ArrayIndexOutOfBoundsException aioobe) {
            Log.w (TAG, "onTouchEvent: scaleGestureDetector.onTouchEvent()", aioobe);
            scaleGestureDetector = new ScaleGestureDetector (ctx, this);
        }

        /*
         * Maybe translation is being performed.
         */
        switch (event.getAction ()) {
            case MotionEvent.ACTION_DOWN: {
                mouse0LastX = event.getX ();
                mouse0LastY = event.getY ();
                MouseDown (mouse0LastX, mouse0LastY);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                double x = event.getX ();
                double y = event.getY ();

                // scaling blocks panning for a few milliseconds
                // to eliminate spurious mouse panning events when
                // exiting scaling mode
                if (System.currentTimeMillis () >= blockPanUntil) {
                    Panning (x, y, x - mouse0LastX, y - mouse0LastY);
                }

                mouse0LastX = x;
                mouse0LastY = y;
                break;
            }
            case MotionEvent.ACTION_UP: {
                double x = event.getX ();
                double y = event.getY ();
                MouseUp (x, y);
                break;
            }
        }
        return true;
    }

    /**
     * ScaleGestoreDetector.OnScaleGestureListener implementation methods.
     */
    @Override
    public boolean onScaleBegin (ScaleGestureDetector detector)
    {
        return true;
    }

    @Override
    public boolean onScale (ScaleGestureDetector detector)
    {
        Scaling (detector.getFocusX (), detector.getFocusY (), detector.getScaleFactor ());
        return true;
    }

    @Override
    public void onScaleEnd (ScaleGestureDetector detector)
    { }
}
