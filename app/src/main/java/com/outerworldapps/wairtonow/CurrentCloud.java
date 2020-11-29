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

/**
 * Manage current position cloud shown in upper-left corner of some screens.
 */
public class CurrentCloud {
    public  static final int currentColor = Color.RED;

    private boolean gpsInfoMphOpt;
    public  boolean showGPSInfo    = true;
    private char[] gpsInfoHdgStr;
    private char[] gpsInfoSecStr;
    private double gpsInfoAltitude = Double.NaN;
    private double gpsInfoSpeed    = Double.NaN;
    private int gpsInfoSecond;
    private long downOnGPSInfo    = 0;
    private Paint cloudPaint      = new Paint ();
    private Paint currentBGPaint  = new Paint ();
    private Paint currentTuPaint  = new Paint ();
    private Paint currentTvPaint  = new Paint ();
    private Path cloudPath;
    private Path gpsInfoPath;
    private Rect gpsInfoBounds    = new Rect ();
    private String gpsInfoAltStr  = "";
    private String gpsInfoSpdStr  = "";
    private WairToNow wairToNow;

    public CurrentCloud (WairToNow wtn)
    {
        wairToNow = wtn;
        float ts = wairToNow.textSize;

        cloudPaint.setColor (Color.WHITE);
        cloudPaint.setStyle (Paint.Style.FILL_AND_STROKE);

        currentBGPaint.setColor (Color.WHITE);
        currentBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        currentBGPaint.setStrokeWidth (wairToNow.thickLine);
        currentTuPaint.setColor (CurrentCloud.currentColor);
        currentTuPaint.setStyle (Paint.Style.FILL);
        currentTuPaint.setStrokeWidth (2);
        currentTuPaint.setTextSize (ts);
        currentTuPaint.setTextAlign (Paint.Align.LEFT);
        currentTvPaint.setColor (CurrentCloud.currentColor);
        currentTvPaint.setStyle (Paint.Style.FILL);
        currentTvPaint.setStrokeWidth (2);
        currentTvPaint.setTextSize (ts);
        currentTvPaint.setTextAlign (Paint.Align.RIGHT);
        currentTvPaint.setTypeface (Typeface.create (currentTvPaint.getTypeface (), Typeface.BOLD));

        gpsInfoHdgStr = new char[] { 'x', 'x', 'x', 0xB0 };
        gpsInfoSecStr = new char[] { 'h', 'h', ':', 'm',  'm', ':', 's', 's' };
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
            if (showGPSInfo) {
                DrawGPSInfo (canvas);
            } else {
                DrawTriangle (canvas, currentBGPaint);
                DrawTriangle (canvas, currentTuPaint);
            }
        }
    }

    private void DrawGPSInfo (Canvas canvas)
    {
        double altitude = wairToNow.currentGPSAlt;
        double heading  = wairToNow.currentGPSHdg;
        double speed    = wairToNow.currentGPSSpd;
        long   time     = wairToNow.currentGPSTime;
        boolean mphOpt  = wairToNow.optionsView.ktsMphOption.getAlt ();
        boolean trueOpt = wairToNow.optionsView.magTrueOption.getAlt ();

        int second = (int) ((time + 500) / 1000 % 86400);
        if (gpsInfoSecond != second) {
            gpsInfoSecond = second;
            int hh = second / 3600;
            int mm = second / 60 % 60;
            int ss = second % 60;
            gpsInfoSecStr[0] = (char) (hh / 10 + '0');
            gpsInfoSecStr[1] = (char) (hh % 10 + '0');
            gpsInfoSecStr[3] = (char) (mm / 10 + '0');
            gpsInfoSecStr[4] = (char) (mm % 10 + '0');
            gpsInfoSecStr[6] = (char) (ss / 10 + '0');
            gpsInfoSecStr[7] = (char) (ss % 10 + '0');
        }

        if (speed < WairToNow.gpsMinSpeedMPS) {
            gpsInfoHdgStr[0] = '\u2012';
            gpsInfoHdgStr[1] = '\u2012';
            gpsInfoHdgStr[2] = '\u2012';
        } else {
            int hdg = (int) Math.round (trueOpt ? heading : (heading + wairToNow.currentMagVar));
            while (hdg <=  0) hdg += 360;
            while (hdg > 360) hdg -= 360;
            gpsInfoHdgStr[0] = (char) (hdg / 100 + '0');
            gpsInfoHdgStr[1] = (char) (hdg / 10 % 10 + '0');
            gpsInfoHdgStr[2] = (char) (hdg % 10 + '0');
        }

        if (gpsInfoAltitude != altitude) {
            gpsInfoAltitude = altitude;
            gpsInfoAltStr   = Long.toString (Math.round (altitude * Lib.FtPerM));
        }

        if ((gpsInfoMphOpt != mphOpt) || (gpsInfoSpeed != speed)) {
            gpsInfoMphOpt = mphOpt;
            gpsInfoSpeed  = speed;
            gpsInfoSpdStr = Lib.SpeedString (speed * Lib.KtPerMPS, mphOpt);
        }

        float dx = currentTuPaint.getTextSize ();
        float dy = currentTuPaint.getFontSpacing ();

        gpsInfoBounds.set (0, 0, Math.round (dx * 6), Math.round (dy * 4));

        if (cloudPath == null) {
            cloudPath = new Path ();
            RectF oval = new RectF (0, 0, dx * 6, dy * 4);
            cloudPath.addRect (oval, Path.Direction.CCW);
            for (int iy = 0; iy < 4; iy ++) {
                oval.set (dx * 5.5F, iy * dy, dx * 6.5F, iy * dy + dy);
                cloudPath.addArc (oval, 90, -180);
            }
            for (int ix = 0; ix < 6; ix ++) {
                oval.set (ix * dx, dy * 3.5F, ix * dx + dx, dy * 4.5F);
                cloudPath.addArc (oval, 0, 180);
            }
            cloudPath.close ();
        }
        canvas.drawPath (cloudPath, cloudPaint);

        canvas.drawText (gpsInfoSecStr, 0, 8,                       5.0F * dx, dy,     currentTvPaint);
        canvas.drawText ("z",                                       5.0F * dx, dy,     currentTuPaint);
        canvas.drawText (gpsInfoAltStr, 0, gpsInfoAltStr.length (), 3.5F * dx, dy * 2, currentTvPaint);
        canvas.drawText (" ft",                                     3.5F * dx, dy * 2, currentTuPaint);
        canvas.drawText (gpsInfoHdgStr, 0, 4,                       3.5F * dx, dy * 3, currentTvPaint);
        canvas.drawText (trueOpt ? " true" : " mag",                3.5F * dx, dy * 3, currentTuPaint);
        int i = gpsInfoSpdStr.indexOf (' ');
        canvas.drawText (gpsInfoSpdStr, 0, i,                       3.5F * dx, dy * 4, currentTvPaint);
        canvas.drawText (gpsInfoSpdStr, i, gpsInfoSpdStr.length (), 3.5F * dx, dy * 4, currentTuPaint);
    }

    private void DrawTriangle (Canvas canvas, Paint paint)
    {
        float ts = wairToNow.textSize;

        gpsInfoBounds.left   = 0;
        gpsInfoBounds.right  = Math.round (ts * 4);
        gpsInfoBounds.top    = 0;
        gpsInfoBounds.bottom = Math.round (ts * 4);

        if (gpsInfoPath == null) {
            gpsInfoPath = new Path ();
            gpsInfoPath.moveTo (ts * 2, ts);
            gpsInfoPath.lineTo (ts * 2, ts * 2);
            gpsInfoPath.lineTo (ts, ts * 2);
        }
        canvas.drawPath (gpsInfoPath, paint);
    }
}
