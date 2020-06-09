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

/**
 * Manage current position cloud shown in upper-left corner of some screens.
 */
public class CurrentCloud {
    public  static final int currentColor = Color.RED;

    private boolean gpsInfoMphOpt;
    private boolean gpsInfoTrueOpt;
    public  boolean showGPSInfo   = true;
    private double gpsInfoAltitude = Double.NaN;
    private double gpsInfoHeading  = Double.NaN;
    private double gpsInfoSpeed    = Double.NaN;
    private int gpsInfoSecond;
    private long downOnGPSInfo    = 0;
    private Paint currentBGPaint  = new Paint ();
    private Paint currentTxPaint  = new Paint ();
    private Path gpsInfoPath;
    private Rect gpsInfoBounds    = new Rect ();
    private String gpsInfoAltStr;
    private String gpsInfoHdgStr;
    private String gpsInfoSecStr;
    private String gpsInfoSpdStr;
    private WairToNow wairToNow;

    public CurrentCloud (WairToNow wtn)
    {
        wairToNow = wtn;
        float ts = wairToNow.textSize;

        currentBGPaint.setColor (Color.WHITE);
        currentBGPaint.setStyle (Paint.Style.STROKE);
        currentBGPaint.setStrokeWidth (wairToNow.thickLine);
        currentBGPaint.setTextSize (ts);
        currentBGPaint.setTextAlign (Paint.Align.CENTER);
        currentTxPaint.setColor (CurrentCloud.currentColor);
        currentTxPaint.setStyle (Paint.Style.FILL);
        currentTxPaint.setStrokeWidth (2);
        currentTxPaint.setTextSize (ts);
        currentTxPaint.setTextAlign (Paint.Align.CENTER);
    }

    /**
     * Mouse touched down somewhere on screen.
     */
    public boolean MouseDown (int x, int y, long now)
    {
        if (gpsInfoBounds.contains (x, y)) {
            downOnGPSInfo = now;
            return true;
        }
        return false;
    }

    /**
     * Mouse released somewhere on screen.
     */
    public boolean MouseUp (int x, int y, long now)
    {
        if (gpsInfoBounds.contains (x, y)) {
            showGPSInfo ^= (now - downOnGPSInfo < 500);
            return true;
        }
        return false;
    }

    /**
     * Draw GPS info text in upper left corner of canvas.
     */
    public void DrawIt (Canvas canvas)
    {
        if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
            DrawIt (canvas, currentBGPaint);
            DrawIt (canvas, currentTxPaint);
        }
    }

    private void DrawIt (Canvas canvas, Paint paint)
    {
        if (showGPSInfo) {
            double altitude  = wairToNow.currentGPSAlt;
            double heading   = wairToNow.currentGPSHdg;
            double latitude  = wairToNow.currentGPSLat;
            double longitude = wairToNow.currentGPSLon;
            double speed     = wairToNow.currentGPSSpd;
            long  time      = wairToNow.currentGPSTime;

            int second = (int) (time / 1000 % 86400);

            boolean mphOpt  = wairToNow.optionsView.ktsMphOption.getAlt ();
            boolean trueOpt = wairToNow.optionsView.magTrueOption.getAlt ();

            if (gpsInfoSecond != second) {
                gpsInfoSecond = second;
                int hh = second / 3600;
                int mm = second / 60 % 60;
                int ss = second % 60;
                gpsInfoSecStr = Integer.toString (hh + 100).substring (1) + ":" +
                        Integer.toString (mm + 100).substring (1) + ":" +
                        Integer.toString (ss + 100).substring (1) + "z";
            }

            if ((gpsInfoHeading != heading) || (gpsInfoTrueOpt != trueOpt)) {
                gpsInfoHeading = heading;
                gpsInfoTrueOpt = trueOpt;
                gpsInfoHdgStr  = wairToNow.optionsView.HdgString (heading, latitude, longitude, altitude);
            }

            if (gpsInfoAltitude != altitude) {
                gpsInfoAltitude = altitude;
                gpsInfoAltStr   = Math.round (altitude * Lib.FtPerM) + " ft";
            }

            if ((gpsInfoMphOpt != mphOpt) || (gpsInfoSpeed != speed)) {
                gpsInfoMphOpt = mphOpt;
                gpsInfoSpeed  = speed;
                gpsInfoSpdStr = Lib.SpeedString (speed * Lib.KtPerMPS, mphOpt);
            }

            int cx = (int)(paint.getTextSize () * 3.125);
            int dy = (int)paint.getFontSpacing ();

            gpsInfoBounds.setEmpty ();
            Lib.DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy, gpsInfoSecStr);
            Lib.DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 2, gpsInfoAltStr);
            Lib.DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 3, gpsInfoHdgStr);
            Lib.DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 4, gpsInfoSpdStr);
        } else {
            float ts = wairToNow.textSize;

            gpsInfoBounds.left   = 0;
            gpsInfoBounds.right  = (int) (ts * 2);
            gpsInfoBounds.top    = 0;
            gpsInfoBounds.bottom = (int) (ts * 2);

            if (gpsInfoPath == null) {
                gpsInfoPath = new Path ();
                gpsInfoPath.moveTo (ts * 2, ts);
                gpsInfoPath.lineTo (ts * 2, ts * 2);
                gpsInfoPath.lineTo (ts, ts * 2);
            }
            canvas.drawPath (gpsInfoPath, paint);
        }
    }
}
