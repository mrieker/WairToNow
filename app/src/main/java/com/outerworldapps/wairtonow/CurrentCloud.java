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
    public  boolean showGPSInfo    = true;
    private char[] gpsInfoMagHdgStr;
    private char[] gpsInfoSecStr;
    private char[] gpsinfoTrueHdgStr;
    private double gpsInfoAltitude = Double.NaN;
    private double gpsInfoSpeed    = Double.NaN;
    private int gpsInfoSecond;
    private long downOnGPSInfo    = 0;
    private Paint currentBGPaint  = new Paint ();
    private Paint currentTxPaint  = new Paint ();
    private Path gpsInfoPath;
    private Rect gpsInfoBounds    = new Rect ();
    private String gpsInfoAltStr;
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

        gpsInfoMagHdgStr = new char[] { 'x', 'x', 'x', 0xB0, ' ', 'M', 'a', 'g' };
        gpsInfoSecStr = new char[] { 'h', 'h', ':', 'm', 'm', ':', 's', 's', 'z' };
        gpsinfoTrueHdgStr = new char[] { 'x', 'x', 'x', 0xB0, ' ', 'T', 'r', 'u', 'e' };
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

            if (trueOpt) {
                int th = (int) Math.round (heading);
                while (th <=  0) th += 360;
                while (th > 360) th -= 360;
                gpsinfoTrueHdgStr[0] = (char) (th / 100 + '0');
                gpsinfoTrueHdgStr[1] = (char) (th / 10 % 10 + '0');
                gpsinfoTrueHdgStr[2] = (char) (th % 10 + '0');
            } else {
                int mh = (int) Math.round (heading + wairToNow.currentMagVar);
                while (mh <=  0) mh += 360;
                while (mh > 360) mh -= 360;
                gpsInfoMagHdgStr[0] = (char) (mh / 100 + '0');
                gpsInfoMagHdgStr[1] = (char) (mh / 10 % 10 + '0');
                gpsInfoMagHdgStr[2] = (char) (mh % 10 + '0');
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
            Lib.DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 3, trueOpt ? gpsinfoTrueHdgStr : gpsInfoMagHdgStr);
            Lib.DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 4, gpsInfoSpdStr);
        } else {
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
}
