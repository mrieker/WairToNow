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
import android.graphics.Point;
import android.graphics.Rect;
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
    private static final int undrawnColor = Color.MAGENTA;

    public  boolean showCenterInfo = true;
    public  boolean showCourseInfo = true;

    private boolean centerInfoMphOption, centerInfoTrueOption;
    private boolean courseInfoMphOpt, courseInfoTrueOpt;
    private ChartView chartView;
    private DecimalFormat scalingFormat = new DecimalFormat ("#.##");
    private float centerInfoAltitude;
    private float centerInfoArrowLat, centerInfoArrowLon;
    private float centerInfoCanvasHdgRads;
    private float centerInfoCenterLat, centerInfoCenterLon;
    private float centerInfoScaling;
    private float courseInfoArrowLat, courseInfoArrowLon;
    private float courseInfoDstLat, courseInfoDstLon;
    private int canvasWidth, canvasHeight;
    private int centerInfoCanvasHeight;
    private int centerInfoLLOption;
    private int courseInfoCanvasWidth;
    private long downOnCenterInfo   = 0;
    private long downOnCourseInfo   = 0;
    private Paint centerBGPaint     = new Paint ();
    private Paint centerTxPaint     = new Paint ();
    private Paint courseBGPaint     = new Paint ();
    private Paint courseTxPaint     = new Paint ();
    private Paint trafficBGPaint    = new Paint ();
    private Paint trafficTxPaint    = new Paint ();
    private Paint undrawnTxPaint    = new Paint ();
    private Path centerInfoPath     = new Path ();
    private Path courseInfoPath     = new Path ();
    private Rect centerInfoBounds   = new Rect ();
    private Rect courseInfoBounds   = new Rect ();
    private String centerInfoDistStr, centerInfoMCToStr;
    private String centerInfoLatStr, centerInfoLonStr;
    private String centerInfoRotStr, centerInfoScaStr;
    private String courseInfoDistStr, courseInfoMCToStr, courseInfoEteStr;
    private Traffic[] trafficArray;
    private WairToNow wairToNow;

    public StateView (ChartView cv)
    {
        super (cv.wairToNow);

        chartView = cv;
        wairToNow = cv.wairToNow;
        float ts = wairToNow.textSize;

        centerBGPaint.setColor (Color.WHITE);
        centerBGPaint.setStyle (Paint.Style.STROKE);
        centerBGPaint.setStrokeWidth (30);
        centerBGPaint.setTextSize (ts);
        centerBGPaint.setTextAlign (Paint.Align.CENTER);
        centerTxPaint.setColor (centerColor);
        centerTxPaint.setStyle (Paint.Style.FILL);
        centerTxPaint.setStrokeWidth (2);
        centerTxPaint.setTextSize (ts);
        centerTxPaint.setTextAlign (Paint.Align.CENTER);

        courseBGPaint.setColor (Color.WHITE);
        courseBGPaint.setStyle (Paint.Style.STROKE);
        courseBGPaint.setStrokeWidth (30);
        courseBGPaint.setTextSize (ts);
        courseBGPaint.setTextAlign (Paint.Align.CENTER);
        courseTxPaint.setColor (courseColor);
        courseTxPaint.setStyle (Paint.Style.FILL);
        courseTxPaint.setStrokeWidth (2);
        courseTxPaint.setTextSize (ts);
        courseTxPaint.setTextAlign (Paint.Align.CENTER);

        trafficBGPaint.setColor (Color.BLACK);
        trafficBGPaint.setStyle (Paint.Style.STROKE);
        trafficBGPaint.setStrokeWidth (20);
        trafficBGPaint.setTextSize (ts * 0.75F);
        trafficTxPaint.setColor (Color.YELLOW);
        trafficTxPaint.setStyle (Paint.Style.FILL);
        trafficTxPaint.setStrokeWidth (2);
        trafficTxPaint.setTextSize (ts * 0.75F);

        undrawnTxPaint.setColor (undrawnColor);
        undrawnTxPaint.setStyle (Paint.Style.FILL);
        undrawnTxPaint.setStrokeWidth (9);
        undrawnTxPaint.setTextSize (ts * 1.5F);
        undrawnTxPaint.setTextAlign (Paint.Align.RIGHT);
    }

    /**
     * Callback for mouse events on the image.
     * We use this to detect button clicks.
     */
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
                    invalidate ();
                    return true;
                }

                if (courseInfoBounds.contains (x, y)) {
                    showCourseInfo ^= (now - downOnCourseInfo < 500);
                    invalidate ();
                    return true;
                }

                if (wairToNow.currentCloud.MouseUp (x, y, now)) {
                    invalidate ();
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
        canvasWidth  = getWidth ();
        canvasHeight = getHeight ();

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
            DrawCourseInfo (canvas, courseBGPaint);
            DrawCourseInfo (canvas, courseTxPaint);
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
            DrawCenterInfo (canvas, centerBGPaint);
            DrawCenterInfo (canvas, centerTxPaint);
        }

        /*
         * Number of undrawn 3D tiles in lower-right corner.
         */
        if (chartView.undrawn != 0) {
            float x = chartView.pmap.canvasWidth  - 20;
            float y = chartView.pmap.canvasHeight - 20;
            canvas.drawText (Integer.toString (chartView.undrawn),
                    x, y, undrawnTxPaint);
        }

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
    private void DrawCenterInfo (Canvas canvas, Paint paint)
    {
        boolean holdPosition = chartView.holdPosition;
        float centerLat = chartView.centerLat;
        float centerLon = chartView.centerLon;
        float scaling = chartView.scaling;

        if (showCenterInfo) {
            int cx = (int)(paint.getTextSize () * 3.125);
            int dy = (int)paint.getFontSpacing ();
            int by = canvasHeight - 10;

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

            centerInfoBounds.setEmpty ();

            if (holdPosition) {

                // display heading/distance from airplane to center iff airplane not centered on screen
                float altitude  = wairToNow.currentGPSAlt;
                float latitude  = wairToNow.currentGPSLat;
                float longitude = wairToNow.currentGPSLon;
                if ((centerInfoArrowLat != latitude) ||
                    (centerInfoArrowLon != longitude) ||
                    centerLLChanged ||
                    optionsChanged) {
                    centerInfoArrowLat = latitude;
                    centerInfoArrowLon = longitude;
                    float distFromGPS = Lib.LatLonDist (latitude, longitude, centerLat, centerLon);
                    float tcorFromGPS = Lib.LatLonTC (latitude, longitude, centerLat, centerLon);
                    centerInfoDistStr = Lib.DistString (distFromGPS, mphOption);
                    centerInfoMCToStr = wairToNow.optionsView.HdgString (tcorFromGPS, latitude, longitude, altitude);
                }
                Lib.DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 6, centerInfoDistStr);
                Lib.DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 5, centerInfoMCToStr);
            }
            if (holdPosition || !wairToNow.currentCloud.showGPSInfo) {

                // display center lat/lon iff different than airplane lat/lon or airplane lat/lon not being shown
                if (centerLLChanged) {
                    centerInfoLatStr = wairToNow.optionsView.LatLonString (centerLat, 'N', 'S');
                    centerInfoLonStr = wairToNow.optionsView.LatLonString (centerLon, 'E', 'W');
                }
                Lib.DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 4, centerInfoLatStr);
                Lib.DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 3, centerInfoLonStr);
            }

            // always display scaling and rotation
            float altitude = wairToNow.currentGPSAlt;
            float chr = wairToNow.chartView.backing.GetCanvasHdgRads ();
            if (centerLLChanged || optionsChanged ||
                    (centerInfoScaling       != scaling) ||
                    (centerInfoCanvasHdgRads != chr)     ||
                    (centerInfoAltitude      != altitude)) {
                centerInfoScaling       = scaling;
                centerInfoCanvasHdgRads = chr;
                centerInfoAltitude      = altitude;
                centerInfoScaStr = "x " + scalingFormat.format (scaling);
                centerInfoRotStr = wairToNow.optionsView.HdgString (Mathf.toDegrees (chr), centerLat, centerLon, altitude);
            }
            Lib.DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 2, centerInfoScaStr);
            Lib.DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy, centerInfoRotStr);
        } else {

            // user doesn't want any info shown, draw a little button instead
            float ts = wairToNow.textSize;
            int   ch = canvasHeight;
            centerInfoBounds.left   = 0;
            centerInfoBounds.right  = (int) (ts * 2);
            centerInfoBounds.top    = (int) (ch - ts * 2);
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
    }

    /**
     * Draw Course info text in upper right corner of canvas.
     */
    private void DrawCourseInfo (Canvas canvas, Paint paint)
    {
        Waypoint clDest = chartView.clDest;

        if (showCourseInfo) {
            float altitude  = wairToNow.currentGPSAlt;
            float latitude  = wairToNow.currentGPSLat;
            float longitude = wairToNow.currentGPSLon;
            float speed     = wairToNow.currentGPSSpd;
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

                float dist = Lib.LatLonDist (latitude, longitude, courseInfoDstLat, courseInfoDstLon);
                courseInfoDistStr = Lib.DistString (dist, mphOpt);

                float tcto = Lib.LatLonTC (latitude, longitude, courseInfoDstLat, courseInfoDstLon);
                courseInfoMCToStr = wairToNow.optionsView.HdgString (tcto, latitude, longitude, altitude);

                courseInfoEteStr = "";
                if (speed >= WairToNow.gpsMinSpeedMPS) {
                    int etesec = Math.round (dist * Lib.MPerNM / speed);
                    int etemin = etesec / 60;
                    int etehrs = etemin / 60;
                    etesec %= 60;
                    etemin %= 60;
                    courseInfoEteStr = Integer.toString (etehrs) + ":" + Integer.toString (etemin + 100).substring (1)
                            + ":" + Integer.toString (etesec + 100).substring (1);
                }
            }

            int cx = canvasWidth - (int)(paint.getTextSize () * 3);
            int dy = (int)paint.getFontSpacing ();

            courseInfoBounds.setEmpty ();
            Lib.DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy, clDest.ident);
            Lib.DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 2, courseInfoDistStr);
            Lib.DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 3, courseInfoMCToStr);
            Lib.DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 4, courseInfoEteStr);
        } else {
            float ts = wairToNow.textSize;
            int   cw = canvasWidth;

            courseInfoBounds.left   = (int) (cw - ts * 2);
            courseInfoBounds.right  = cw;
            courseInfoBounds.top    = 0;
            courseInfoBounds.bottom = (int) (ts * 2);

            if (courseInfoCanvasWidth != cw) {
                courseInfoCanvasWidth = cw;
                courseInfoPath.rewind ();
                courseInfoPath.moveTo (cw - ts * 2, ts);
                courseInfoPath.lineTo (cw - ts * 2, ts * 2);
                courseInfoPath.lineTo (cw - ts, ts * 2);
            }
            canvas.drawPath (courseInfoPath, paint);
        }
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
                float distaway = Lib.LatLonDist (traffic.latitude, traffic.longitude,
                        wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                if (distaway > TRAFDISTLIMNM) continue;

                // it must map to a pixel within bounds of the screen
                if (traffic.canpix == null) traffic.canpix = new Point ();
                float trafmsl = traffic.taltitude;
                if (!backing.LatLonAlt2CanPixExact (
                        traffic.latitude, traffic.longitude, trafmsl, traffic.canpix)) continue;

                // make sure it is a minimum AGL so we don't get a bunch
                // of airplanes sitting on the ground (eg, as at KBOS)
                if (!Float.isNaN (trafmsl)) {
                    float trafagl = trafmsl - Topography.getElevMetres (traffic.latitude, traffic.longitude);
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
            float canvasUp = Mathf.toDegrees (backing.GetCanvasHdgRads ());
            while (-- ntraf >= 0) {
                Traffic traffic = trafficArray[ntraf];

                for (int pass = 0; pass < 2; pass ++) {
                    Paint paint = (pass == 0) ? trafficBGPaint : trafficTxPaint;

                    float x = traffic.canpix.x;
                    float y = traffic.canpix.y;

                    // draw circle at that location
                    float radius = textSize * 0.25F;
                    canvas.drawCircle (x, y, radius, paint);
                    x += radius;
                    y += radius;

                    // draw descriptive text
                    String[] texts = traffic.getText (wairToNow, canvasUp);
                    for (String text : texts) {
                        y += textSize;
                        canvas.drawText (text, x, y, paint);
                    }
                }
            }
        }
    }

    private final static Comparator<Traffic> trafficComparator = new Comparator<Traffic> () {
        @Override
        public int compare (Traffic a, Traffic b)
        {
            if (a.distaway < b.distaway) return -1;
            if (a.distaway > b.distaway) return  1;
            return 0;
        }
    };
}
