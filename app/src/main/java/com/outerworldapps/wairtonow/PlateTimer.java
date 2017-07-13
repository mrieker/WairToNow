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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * Manage timer display on the IAP plates.
 */
public class PlateTimer {
    private boolean   timerQueued;
    private float     timrCharWidth, timrTextAscent, timrTextHeight;
    private long      timerStarted;
    private Paint     timrBGPaint      = new Paint ();
    private Paint     timrTxPaint      = new Paint ();
    private Path      timrButtonPath   = new Path  ();
    private RectF     timrButtonBounds = new RectF ();
    private Runnable  timerUpdate;
    private View      plateView;
    private WairToNow wairToNow;

    public PlateTimer (WairToNow wtn, View pv)
    {
        wairToNow = wtn;
        plateView = pv;

        timrBGPaint.setColor (Color.BLACK);
        timrBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        timrBGPaint.setStrokeWidth (wairToNow.thickLine);

        timrTxPaint.setColor (Color.YELLOW);
        timrTxPaint.setStyle (Paint.Style.FILL);
        timrTxPaint.setStrokeWidth (3);
        timrTxPaint.setTextSize (wairToNow.textSize * 1.125F);
        timrTxPaint.setTextAlign (Paint.Align.LEFT);
        timrTxPaint.setTypeface (Typeface.MONOSPACE);

        float[] charWidths = new float[1];
        Rect textBounds = new Rect ();
        timrTxPaint.getTextBounds ("M", 0, 1, textBounds);
        timrTxPaint.getTextWidths ("M", charWidths);
        timrCharWidth = charWidths[0];
        timrTextAscent = textBounds.height ();
        timrTextHeight = timrTxPaint.getTextSize ();

        timerUpdate = new Runnable () {
            public void run ()
            {
                timerQueued = false;
                plateView.invalidate ();
            }
        };
    }

    public void GotMouseDown (float x, float y)
    {
        if (timrButtonBounds.contains (x, y)) {
            TimerButtonClicked ();
        }
    }

    /**
     * Draw timer in upper right corner.
     */
    public void DrawTimer (Canvas canvas)
    {
        int canvasWidth = plateView.getWidth ();

        // see if timer running
        if (timerStarted > 0) {
            int timrx = canvasWidth - (int) Mathf.ceil (timrCharWidth * 6.5F);
            int timry = (int) Mathf.ceil (timrTextHeight * 1.25F);

            timrButtonBounds.top    = timry - (int) Math.ceil (timrTextAscent);
            timrButtonBounds.bottom = timry;
            timrButtonBounds.left   = timrx;
            timrButtonBounds.right  = canvasWidth - timrButtonBounds.top;

            // draw black background rectangle where text goes
            canvas.drawRect (timrButtonBounds, timrBGPaint);

            // draw timer string
            StringBuilder sb = new StringBuilder (6);

            long elapsed = SystemClock.elapsedRealtime () - timerStarted;
            int seconds = (int) (elapsed / 1000);
            boolean inrange = seconds < 60000;
            if (inrange) {
                sb.append (seconds / 60);
                while (sb.length () < 3) sb.insert (0, ' ');
                sb.append (':');
                sb.append (seconds % 60);
                while (sb.length () < 6) sb.insert (4, '0');
            } else {
                sb.append ("---:--");
            }

            timrx -= (int) Mathf.ceil (timrCharWidth / 5.0F);
            canvas.drawText (sb.toString (), timrx, timry, timrTxPaint);

            // come back in a second for update
            if (!timerQueued && inrange) {
                timerQueued = true;
                long delay = 1000 - elapsed % 1000;
                WairToNow.wtnHandler.runDelayed (delay, timerUpdate);
            }
        } else {

            // user doesn't want timer shown, draw a little button instead
            float ts = wairToNow.textSize;
            timrButtonBounds.left   = canvasWidth - (int) (ts * 2);
            timrButtonBounds.right  = canvasWidth;
            timrButtonBounds.top    = 0;
            timrButtonBounds.bottom = (int) (ts * 2);

            timrButtonPath.rewind ();
            timrButtonPath.moveTo (canvasWidth - ts,     ts * 2);
            timrButtonPath.lineTo (canvasWidth - ts * 2, ts * 2);
            timrButtonPath.lineTo (canvasWidth - ts * 2, ts);

            canvas.drawPath (timrButtonPath, timrBGPaint);
            canvas.drawPath (timrButtonPath, timrTxPaint);
        }
    }

    /**
     * Upper right corner of plate view was clicked and so flip timer.
     */
    private void TimerButtonClicked ()
    {
        if (timerStarted > 0) {
            timerStarted = 0;
        } else {
            timerStarted = SystemClock.elapsedRealtime ();
        }
        plateView.invalidate ();
    }
}
