//    Copyright (C) 2016, Mike Rieker, Beverly, MA USA
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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Display chart and current position and a specified course line.
 * Used as a overlay of a Chart2DView or Chart3DView.
 */
@SuppressLint("ViewConstructor")
public class StateView extends View {

    private static final int coursecleartime = 1000;
    private static final int TRAFDISTLIMNM = 30;
    private static final int TRAFTIMELIMMS = 60 * 1000;

    private static final int centerColor  = Color.BLUE;
    public  static final int courseColor  = Color.rgb (170, 0, 170);

    public  boolean showCenterInfo = true;
    public  boolean showCourseInfo = true;
    public  boolean showCourseLine = true;

    private boolean centerInfoMphOption, centerInfoTrueOption;
    private boolean courseInfoMphOpt, courseInfoTrueOpt;
    private char[] courseInfoCrsStr;
    private char[] courseInfoEteStr;
    private ChartView chartView;
    private DecimalFormat scalingFormat = new DecimalFormat ("x #.##");
    private double centerInfoAltitude;
    private double centerInfoArrowLat, centerInfoArrowLon;
    private double centerInfoCanvasHdgRads;
    private double centerInfoCenterLat, centerInfoCenterLon;
    private double centerInfoScaling;
    private double courseInfoArrowLat, courseInfoArrowLon;
    private double courseInfoDstLat, courseInfoDstLon;
    private int canvasWidth, canvasHeight;
    private int centerInfoCanvasHeight;
    private int centerInfoLLOption;
    private int courseInfoCanvasWidth;
    private long downOnCenterInfo   = 0;
    private long downOnCourseInfo   = 0;
    private Paint centerBGPaint     = new Paint ();
    private Paint centerTuPaint     = new Paint ();
    private Paint centerTvPaint     = new Paint ();
    private Paint centerTwPaint     = new Paint ();
    private Paint cloudPaint        = new Paint ();
    private Paint courseBGPaint     = new Paint ();
    private Paint courseTcPaint     = new Paint ();
    private Paint courseTuPaint     = new Paint ();
    private Paint courseTvPaint     = new Paint ();
    private Paint trafficBGPaint    = new Paint ();
    private Paint trafficTxPaint    = new Paint ();
    private Path centerCloudPath;
    private Path centerInfoPath     = new Path ();
    private Path courseCloudPath;
    private Path courseInfoPath     = new Path ();
    private Rect centerInfoBounds   = new Rect ();
    private Rect courseInfoBounds   = new Rect ();
    private String centerInfoDistStr = "", centerInfoMCToStr = "";
    private String centerInfoLatStr = "", centerInfoLonStr = "";
    private String centerInfoRotStr = "", centerInfoScaStr = "";
    private String courseInfoDistStr = "";
    private Traffic[] trafficArray;
    private WairToNow wairToNow;

    public StateView (ChartView cv)
    {
        super (cv.wairToNow);

        chartView = cv;
        wairToNow = cv.wairToNow;
        float ts = wairToNow.textSize;

        cloudPaint.setColor (Color.WHITE);
        cloudPaint.setStyle (Paint.Style.FILL_AND_STROKE);

        centerBGPaint.setColor (Color.WHITE);
        centerBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        centerBGPaint.setStrokeWidth (wairToNow.thickLine);
        centerTuPaint.setColor (centerColor);
        centerTuPaint.setStyle (Paint.Style.FILL);
        centerTuPaint.setStrokeWidth (2);
        centerTuPaint.setTextSize (ts);
        centerTuPaint.setTextAlign (Paint.Align.LEFT);
        centerTvPaint.setColor (centerColor);
        centerTvPaint.setStyle (Paint.Style.FILL);
        centerTvPaint.setStrokeWidth (2);
        centerTvPaint.setTextSize (ts);
        centerTvPaint.setTextAlign (Paint.Align.RIGHT);
        centerTvPaint.setTypeface (Typeface.create (centerTvPaint.getTypeface (), Typeface.BOLD));
        centerTwPaint.setColor (centerColor);
        centerTwPaint.setStyle (Paint.Style.FILL);
        centerTwPaint.setStrokeWidth (2);
        centerTwPaint.setTextSize (ts);
        centerTwPaint.setTextAlign (Paint.Align.LEFT);
        centerTwPaint.setTypeface (Typeface.create (centerTvPaint.getTypeface (), Typeface.BOLD));

        courseBGPaint.setColor (Color.WHITE);
        courseBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        courseBGPaint.setStrokeWidth (wairToNow.thickLine);
        courseTuPaint.setColor (courseColor);
        courseTuPaint.setStyle (Paint.Style.FILL);
        courseTuPaint.setStrokeWidth (2);
        courseTuPaint.setTextSize (ts);
        courseTuPaint.setTextAlign (Paint.Align.LEFT);
        courseTvPaint.setColor (courseColor);
        courseTvPaint.setStyle (Paint.Style.FILL);
        courseTvPaint.setStrokeWidth (2);
        courseTvPaint.setTextSize (ts);
        courseTvPaint.setTextAlign (Paint.Align.RIGHT);
        courseTvPaint.setTypeface (Typeface.create (courseTvPaint.getTypeface (), Typeface.BOLD));
        courseTcPaint.setColor (courseColor);
        courseTcPaint.setStyle (Paint.Style.FILL);
        courseTcPaint.setStrokeWidth (2);
        courseTcPaint.setTextSize (ts);
        courseTcPaint.setTextAlign (Paint.Align.CENTER);
        courseTcPaint.setTypeface (Typeface.create (courseTcPaint.getTypeface (), Typeface.BOLD));

        trafficBGPaint.setColor (Color.BLACK);
        trafficBGPaint.setStyle (Paint.Style.STROKE);
        trafficBGPaint.setStrokeWidth (wairToNow.thickLine * 2.0F / 3.0F);
        trafficBGPaint.setTextSize (ts * 0.75F);
        trafficTxPaint.setColor (Color.YELLOW);
        trafficTxPaint.setStyle (Paint.Style.FILL);
        trafficTxPaint.setStrokeWidth (2);
        trafficTxPaint.setTextSize (ts * 0.75F);

        courseInfoCrsStr = new char[] { 'd', 'd', 'd', '\u00B0' };
        courseInfoEteStr = new char[] { 'h', 'h', ':', 'm', 'm', ':', 's', 's' };
    }

    /**
     * Callback for mouse events on the image.
     * We use this to detect button clicks.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent (@NonNull MotionEvent event)
    {
        // we might get divide-by-zero if we don't know canvas size yet
        if ((canvasWidth == 0) || (canvasHeight == 0)) return false;

        switch (event.getActionMasked ()) {

            // handle mouse-down for purpose of tapping buttons on the chart surface.
            case MotionEvent.ACTION_DOWN: {
                int x = Math.round (event.getX ());
                int y = Math.round (event.getY ());
                long now = System.currentTimeMillis ();

                if (centerInfoBounds.contains (x, y)) {
                    downOnCenterInfo = now;
                    return true;
                }

                if (courseInfoBounds.contains (x, y)) {
                    downOnCourseInfo = now;
                    WairToNow.wtnHandler.runDelayed (coursecleartime, new Runnable () {
                        @Override
                        public void run ()
                        {
                            if (downOnCourseInfo > 0) {
                                downOnCourseInfo = 0;
                                ConfirmClearCourse ();
                            }
                        }
                    });
                    return true;
                }

                if (wairToNow.currentCloud.MouseDown (x, y, now)) {
                    return true;
                }

                break;
            }

            // handle mouse-up
            case MotionEvent.ACTION_UP: {
                int x = Math.round (event.getX ());
                int y = Math.round (event.getY ());
                long now = System.currentTimeMillis ();

                if (centerInfoBounds.contains (x, y)) {
                    showCenterInfo ^= (now - downOnCenterInfo < 500);
                    downOnCenterInfo = 0;
                    invalidate ();  // redraw button vs text cloud
                    chartView.backing.getView ().invalidate ();  // redraw with or without cross at center
                    return true;
                }

                if (courseInfoBounds.contains (x, y)) {
                    if (now - downOnCourseInfo < 500) {
                        if (showCourseInfo) showCourseInfo = false;
                        else if (showCourseLine) showCourseLine = false;
                        else showCourseInfo = showCourseLine = true;
                    }
                    downOnCourseInfo = 0;
                    invalidate ();
                    chartView.backing.getView ().invalidate ();
                    return true;
                }

                if (wairToNow.currentCloud.MouseUp (x, y, now)) {
                    invalidate ();
                    chartView.backing.getView ().invalidate ();
                    return true;
                }

                break;
            }
        }
        return false;
    }

    /**
     * Long click on course info clears destination fix and course line.
     */
    private void ConfirmClearCourse ()
    {
        if (chartView.clDest != null) {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Confirm clear destination");
            adb.setMessage (chartView.clDest.ident);
            adb.setPositiveButton ("Clear it", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    wairToNow.routeView.ShutTrackingOff ();
                    chartView.clDest = null;
                    invalidate ();
                }
            });
            adb.setNegativeButton ("Keep it", null);
            adb.show ();
        }
    }

    /**
     * Callback to draw the clouds and info (or corresponding buttons).
     */
    @Override
    public void onDraw (Canvas canvas)
    {
        int cw = getWidth ();
        int ch = getHeight ();
        if ((canvasWidth != cw) || (canvasHeight != ch)) {
            canvasWidth  = cw;
            canvasHeight = ch;
            centerCloudPath = null;
            courseCloudPath = null;
        }

        /*
         * Plot the ADS-B traffic.
         */
        if (!wairToNow.trafficRepo.amEmpty && wairToNow.optionsView.showTraffic.checkBox.isChecked ()) {
            DrawTrafficInfo (canvas);
        }

        /*
         * Draw course destination text.
         */
        if (chartView.clDest != null) {
            if (showCourseInfo) {
                DrawCourseInfo (canvas);
            } else {
                DrawCourseTriangle (canvas, courseBGPaint);
                DrawCourseTriangle (canvas, courseTuPaint);
            }
        }

        /*
         * Draw GPS (current position) info text.
         */
        wairToNow.currentCloud.DrawIt (canvas);

        /*
         * Draw center position info.
         * Only if chart is selected or it is meaningless
         * and don't block the splash screen startup text.
         */
        if (chartView.selectedChart != null) {
            if (showCenterInfo) {
                DrawCenterInfo (canvas);
            } else {
                DrawCenterTriangle (canvas, centerBGPaint);
                DrawCenterTriangle (canvas, centerTuPaint);
            }
        }

        /*
         * Chart{2D,3D}-specific info.
         */
        chartView.backing.drawOverlay (canvas);

        /*
         * Draw box around perimeter warning that GPS is not available.
         */
        if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
            wairToNow.drawGPSAvailable (canvas, this);
        }
    }

    /**
     * Draw Center Point info in lower left corner of canvas.
     */
    private void DrawCenterInfo (Canvas canvas)
    {
        boolean holdPosition = chartView.holdPosition;
        double centerLat = chartView.centerLat;
        double centerLon = chartView.centerLon;
        double scaling = chartView.scaling;

        float dx = centerTuPaint.getTextSize ();
        float dy = centerTuPaint.getFontSpacing ();
        float by = canvasHeight - dy / 2.0F;

        if (centerCloudPath == null) {
            centerCloudPath = new Path ();
            RectF oval = new RectF (0, by - 6.5F * dy, dx * 8.0F, canvasHeight);
            centerCloudPath.addRect (oval, Path.Direction.CCW);
            for (int iy = 0; iy < 7; iy ++) {
                oval.set (7.5F * dx, by - 6.5F * dy + iy * dy, 8.5F * dx, by - 6.5F * dy + iy * dy + dy);
                centerCloudPath.addArc (oval, 90, -180);
            }
            for (int ix = 0; ix < 8; ix ++) {
                oval.set (ix * dx, by - 7 * dy, ix * dx + dx, by - 6 * dy);
                centerCloudPath.addArc (oval, 0, -180);
            }
            centerCloudPath.close ();
        }

        boolean centerLLChanged = false;
        if ((centerInfoCenterLat != centerLat) ||
            (centerInfoCenterLon != centerLon)) {
            centerInfoCenterLat = centerLat;
            centerInfoCenterLon = centerLon;
            centerLLChanged = true;
        }

        boolean optionsChanged = false;
        int     llOption   = wairToNow.optionsView.latLonOption.getVal ();
        boolean mphOption  = wairToNow.optionsView.ktsMphOption.getAlt ();
        boolean trueOption = wairToNow.optionsView.magTrueOption.getAlt ();
        if ((centerInfoLLOption   != llOption) ||
            (centerInfoMphOption  != mphOption) ||
            (centerInfoTrueOption != trueOption)) {
            centerInfoLLOption   = llOption;
            centerInfoMphOption  = mphOption;
            centerInfoTrueOption = trueOption;
            optionsChanged = true;
        }

        float nlines = 2;
        if (holdPosition) nlines += 2.5F;
        if (holdPosition || !wairToNow.currentCloud.showGPSInfo) nlines += 2;

        centerInfoBounds.set (0, Math.round (by - nlines * dy), Math.round (dx * 8.0F), canvasHeight);

        if (! holdPosition) canvas.translate (0, 4.5F * dy);
        canvas.drawPath (centerCloudPath, cloudPaint);
        if (! holdPosition) canvas.translate (0, -4.5F * dy);

        int i, j;

        if (holdPosition) {

            // display heading/distance from airplane to center iff airplane not centered on screen
            double latitude  = wairToNow.currentGPSLat;
            double longitude = wairToNow.currentGPSLon;
            if ((centerInfoArrowLat != latitude) ||
                (centerInfoArrowLon != longitude) ||
                centerLLChanged ||
                optionsChanged) {
                centerInfoArrowLat = latitude;
                centerInfoArrowLon = longitude;
                double distFromGPS = Lib.LatLonDist (latitude, longitude, centerLat, centerLon);
                double tcorFromGPS = Lib.LatLonTC (latitude, longitude, centerLat, centerLon);
                centerInfoDistStr = Lib.DistString (distFromGPS, mphOption);
                centerInfoMCToStr = wairToNow.optionsView.HdgString (tcorFromGPS,
                        wairToNow.currentMagVar);
            }
            i = centerInfoDistStr.indexOf (' ');
            j = centerInfoDistStr.length ();
            canvas.drawText (centerInfoDistStr, 0, i, dx * 4.5F, by - dy * 5.5F, centerTvPaint);
            canvas.drawText (centerInfoDistStr, i, j, dx * 4.5F, by - dy * 5.5F, centerTuPaint);
            i = centerInfoMCToStr.indexOf (' ');
            j = centerInfoMCToStr.length ();
            canvas.drawText (centerInfoMCToStr, 0, i, dx * 4.5F, by - dy * 4.5F, centerTvPaint);
            canvas.drawText (centerInfoMCToStr, i, j, dx * 4.5F, by - dy * 4.5F, centerTuPaint);
        }
        if (holdPosition || !wairToNow.currentCloud.showGPSInfo) {

            // display center lat/lon iff different than airplane lat/lon or airplane lat/lon not being shown
            if (centerLLChanged) {
                centerInfoLatStr = wairToNow.optionsView.LatLonString (centerLat, 'N', 'S');
                centerInfoLonStr = wairToNow.optionsView.LatLonString (centerLon, 'E', 'W');
            }
            i = llCenter (centerInfoLatStr);
            j = llCenter (centerInfoLonStr);
            canvas.drawText (centerInfoLatStr.substring (0, i), dx * 2.5F, by - dy * 3.5F, centerTvPaint);
            canvas.drawText (centerInfoLonStr.substring (0, j), dx * 2.5F, by - dy * 2.5F, centerTvPaint);
            canvas.drawText (centerInfoLatStr.substring (i),    dx * 2.5F, by - dy * 3.5F, centerTwPaint);
            canvas.drawText (centerInfoLonStr.substring (j),    dx * 2.5F, by - dy * 2.5F, centerTwPaint);
        }

        // always display scaling and rotation
        double altitude = wairToNow.currentGPSAlt;
        double chr = wairToNow.chartView.backing.GetCanvasHdgRads ();
        if (centerLLChanged || optionsChanged ||
                (centerInfoScaling       != scaling) ||
                (centerInfoCanvasHdgRads != chr)     ||
                (centerInfoAltitude      != altitude)) {
            centerInfoScaling       = scaling;
            centerInfoCanvasHdgRads = chr;
            centerInfoAltitude      = altitude;
            centerInfoScaStr = scalingFormat.format (scaling);
            centerInfoRotStr = wairToNow.optionsView.HdgString (Math.toDegrees (chr),
                    wairToNow.currentMagVar) + " up";
        }
        canvas.drawText (centerInfoScaStr, dx * 3.5F, by - dy, centerTvPaint);
        canvas.drawText (" scale", dx * 3.5F, by - dy, centerTuPaint);
        i = centerInfoRotStr.indexOf (' ');
        j = centerInfoRotStr.length ();
        canvas.drawText (centerInfoRotStr, 0, i, dx * 3.5F, by, centerTvPaint);
        canvas.drawText (centerInfoRotStr, i, j, dx * 3.5F, by, centerTuPaint);
    }

    private static int llCenter (String llstr)
    {
        int i = llstr.indexOf ('\u00B0') + 1;
        int j = llstr.indexOf ('.');
        if (i <= 0) i = llstr.length ();
        if (j <  0) j = llstr.length ();
        return Math.min (i, j);
    }

    // user doesn't want any info shown, draw a little button instead
    private void DrawCenterTriangle (Canvas canvas, Paint paint)
    {
        float ts = wairToNow.textSize;
        int   ch = canvasHeight;
        centerInfoBounds.left   = 0;
        centerInfoBounds.right  = Math.round (ts * 4);
        centerInfoBounds.top    = Math.round (ch - ts * 4);
        centerInfoBounds.bottom = ch;

        if (centerInfoCanvasHeight != ch) {
            centerInfoCanvasHeight = ch;
            centerInfoPath.rewind ();
            centerInfoPath.moveTo (ts, ch - ts * 2);
            centerInfoPath.lineTo (ts * 2, ch - ts * 2);
            centerInfoPath.lineTo (ts * 2, ch - ts);
        }
        canvas.drawPath (centerInfoPath, paint);

    }

    /**
     * Draw Course info text in upper right corner of canvas.
     */
    private void DrawCourseInfo (Canvas canvas)
    {
        Waypoint clDest  = chartView.clDest;
        double latitude  = wairToNow.currentGPSLat;
        double longitude = wairToNow.currentGPSLon;
        double speed     = wairToNow.currentGPSSpd;
        boolean mphOpt  = wairToNow.optionsView.ktsMphOption.getAlt ();
        boolean trueOpt = wairToNow.optionsView.magTrueOption.getAlt ();
        if ((courseInfoArrowLat != latitude)   ||
            (courseInfoArrowLon != longitude)  ||
            (courseInfoDstLat   != clDest.lat) ||
            (courseInfoDstLon   != clDest.lon) ||
            (courseInfoMphOpt   != mphOpt)     ||
            (courseInfoTrueOpt  != trueOpt)) {
            courseInfoArrowLat = latitude;
            courseInfoArrowLon = longitude;
            courseInfoDstLat   = clDest.lat;
            courseInfoDstLon   = clDest.lon;
            courseInfoMphOpt   = mphOpt;
            courseInfoTrueOpt  = trueOpt;

            double dist = Lib.LatLonDist (latitude, longitude, courseInfoDstLat, courseInfoDstLon);
            courseInfoDistStr = Lib.DistString (dist, mphOpt);

            double tcto = Lib.LatLonTC (latitude, longitude, courseInfoDstLat, courseInfoDstLon);
            int crsto = (int) Math.round (trueOpt ? tcto : (tcto + wairToNow.currentMagVar));
            while (crsto <=  0) crsto += 360;
            while (crsto > 360) crsto -= 360;
            courseInfoCrsStr[0] = (char) (crsto / 100 + '0');
            courseInfoCrsStr[1] = (char) (crsto / 10 % 10 + '0');
            courseInfoCrsStr[2] = (char) (crsto % 10 + '0');

            int etesec = (int) Math.round (dist * Lib.MPerNM / speed);
            if ((speed < WairToNow.gpsMinSpeedMPS) || (etesec > 99*3600+59*60+59)) {
                courseInfoEteStr[0] = '\u2012';
                courseInfoEteStr[1] = '\u2012';
                courseInfoEteStr[3] = '\u2012';
                courseInfoEteStr[4] = '\u2012';
                courseInfoEteStr[6] = '\u2012';
                courseInfoEteStr[7] = '\u2012';
            } else {
                int etemin = etesec / 60;
                int etehrs = etemin / 60;
                etesec %= 60;
                etemin %= 60;
                courseInfoEteStr[0] = (char) (etehrs / 10 + '0');
                courseInfoEteStr[1] = (char) (etehrs % 10 + '0');
                courseInfoEteStr[3] = (char) (etemin / 10 + '0');
                courseInfoEteStr[4] = (char) (etemin % 10 + '0');
                courseInfoEteStr[6] = (char) (etesec / 10 + '0');
                courseInfoEteStr[7] = (char) (etesec % 10 + '0');
            }
        }

        float dx = courseTuPaint.getTextSize ();
        float dy = courseTuPaint.getFontSpacing ();
        float cx = canvasWidth - 3 * dx;
        int i, j;

        courseInfoBounds.set (Math.round (canvasWidth - 6 * dx), 0, canvasWidth, Math.round (dy * 4));

        if (courseCloudPath == null) {
            courseCloudPath = new Path ();
            RectF oval = new RectF (canvasWidth - 6 * dx, 0, canvasWidth, dy * 4);
            courseCloudPath.addRect (oval, Path.Direction.CCW);
            for (int iy = 0; iy < 4; iy ++) {
                oval.set (canvasWidth - 6.5F * dx, iy * dy, canvasWidth - 5.5F * dx, iy * dy + dy);
                courseCloudPath.addArc (oval, 90, 180);
            }
            for (int ix = 6; ix > 0; -- ix) {
                oval.set (canvasWidth - ix * dx, dy * 3.5F, canvasWidth - ix * dx + dx, dy * 4.5F);
                courseCloudPath.addArc (oval, 0, 180);
            }
            courseCloudPath.close ();
        }
        canvas.drawPath (courseCloudPath, cloudPaint);

        canvas.drawText (clDest.ident, cx, dy, courseTcPaint);
        i = courseInfoDistStr.indexOf (' ');
        j = courseInfoDistStr.length ();
        canvas.drawText (courseInfoDistStr, 0, i, cx, dy * 2, courseTvPaint);
        canvas.drawText (courseInfoDistStr, i, j, cx, dy * 2, courseTuPaint);
        canvas.drawText (courseInfoCrsStr,  0, 4, cx, dy * 3, courseTvPaint);
        canvas.drawText (trueOpt ? " true" : " mag", cx, dy * 3, courseTuPaint);
        canvas.drawText (courseInfoEteStr,  0, 8, cx, dy * 4, courseTcPaint);
    }

    private void DrawCourseTriangle (Canvas canvas, Paint paint)
    {
        float ts = wairToNow.textSize;
        int   cw = canvasWidth;

        courseInfoBounds.left   = Math.round (cw - ts * 4);
        courseInfoBounds.right  = cw;
        courseInfoBounds.top    = 0;
        courseInfoBounds.bottom = Math.round (ts * 4);

        if (courseInfoCanvasWidth != cw) {
            courseInfoCanvasWidth = cw;
            courseInfoPath.rewind ();
            courseInfoPath.moveTo (cw - ts * 2, ts);
            courseInfoPath.lineTo (cw - ts * 2, ts * 2);
            courseInfoPath.lineTo (cw - ts, ts * 2);
        }
        canvas.drawPath (courseInfoPath, paint);
    }

    /**
     * Draw traffic information.
     */
    private void DrawTrafficInfo (Canvas canvas)
    {
        ChartView.Backing backing = wairToNow.chartView.backing;

        // lock out changes by ADS-B receiver threads
        synchronized (wairToNow.trafficRepo) {
            TrafficRepo trafficRepo = wairToNow.trafficRepo;

            // make array of traffic we will paint
            int mtraf = trafficRepo.trafficAges.size ();
            if ((trafficArray == null) || (trafficArray.length < mtraf)) {
                trafficArray = new Traffic[mtraf];
            }
            int ntraf = 0;
            for (Iterator<Traffic> it = trafficRepo.trafficAges.values ().iterator (); it.hasNext ();) {
                Traffic traffic = it.next ();

                // it must not be too old
                if (traffic.time < wairToNow.currentGPSTime - TRAFTIMELIMMS) {
                    it.remove ();
                    trafficRepo.trafficAddr.remove (traffic.address);
                    continue;
                }

                // it must not be too far away
                double distaway = Lib.LatLonDist (traffic.latitude, traffic.longitude,
                        wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                if (distaway > TRAFDISTLIMNM) continue;

                // it must map to a pixel within bounds of the screen
                if (traffic.canpix == null) traffic.canpix = new PointD ();
                double trafmsl = traffic.taltitude;
                if (!backing.LatLonAlt2CanPixExact (
                        traffic.latitude, traffic.longitude, trafmsl, traffic.canpix)) continue;

                // make sure it is a minimum AGL so we don't get a bunch
                // of airplanes sitting on the ground (eg, as at KBOS)
                if (!Double.isNaN (trafmsl)) {
                    double trafagl = trafmsl - Topography.getElevMetresZ (traffic.latitude, traffic.longitude);
                    if (trafagl < Traffic.MINTRAFAGLM) continue;
                }

                // ok save its distance from our airplane and put in array
                traffic.distaway = distaway;
                trafficArray[ntraf++] = traffic;
            }
            trafficRepo.amEmpty = trafficRepo.trafficAges.isEmpty ();

            // sort by ascending distance from our airplane
            Arrays.sort (trafficArray, 0, ntraf, trafficComparator);

            // draw traffic starting with farthest away so nearby don't get overdrawn by far away
            float textSize = trafficTxPaint.getTextSize ();
            double canvasUp = Math.toDegrees (backing.GetCanvasHdgRads ());
            while (-- ntraf >= 0) {
                Traffic traffic = trafficArray[ntraf];

                for (int pass = 0; pass < 2; pass ++) {
                    Paint paint = (pass == 0) ? trafficBGPaint : trafficTxPaint;

                    double x = traffic.canpix.x;
                    double y = traffic.canpix.y;

                    // draw circle at that location
                    float radius = textSize * 0.25F;
                    canvas.drawCircle ((float) x, (float) y, radius, paint);
                    x += radius;
                    y += radius;

                    // draw descriptive text
                    String[] texts = traffic.getText (wairToNow, canvasUp);
                    for (String text : texts) {
                        y += textSize;
                        canvas.drawText (text, (float) x, (float) y, paint);
                    }
                }
            }
        }
    }

    private final static Comparator<Traffic> trafficComparator = new Comparator<Traffic> () {
        @Override
        public int compare (Traffic a, Traffic b)
        {
            return Double.compare (a.distaway, b.distaway);
        }
    };
}
