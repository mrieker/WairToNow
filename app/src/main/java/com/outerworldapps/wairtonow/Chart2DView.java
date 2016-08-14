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

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Display chart and current position and a specified course line.
 */
@SuppressLint("ViewConstructor")
public class Chart2DView extends View
        implements ChartView.Backing, DisplayableChart.Invalidatable {
    public final static String TAG = "WairToNow";

    private final static float nearbynm = 5.0F;
    private final static int waypointopentime = 1000;
    private final static float rotatestep = Mathf.toRadians (5.0F);  // manual rotation stepping
    private final static float[] scalesteps = new float[] {       // manual scale stepping
        0.10F, 0.15F, 0.20F, 0.25F, 0.30F, 0.40F, 0.50F, 0.60F, 0.80F, 1.00F,
        1.25F, 1.50F, 2.00F, 2.50F, 3.00F, 4.00F, 5.00F, 6.00F, 8.00F, 10.0F,
        12.5F, 15.0F, 20.0F, 25.0F, 30.0F, 40.0F, 50.0F, 60.0F, 80.0F, 100.0F
    };
    private final static boolean enablesteps = true;   // enables discrete steps
    private final static boolean blinknexrad = false;

    private static final float scalebase  = 500000;    // chart scale factor (when scaling=1.0, using 500000
                                                       // makes sectionals appear actual size theoretically)

    private static final int capGridColor = Color.DKGRAY;
    private static final int centerColor  = Color.BLUE;
    public  static final int courseColor  = Color.rgb (170, 0, 170);
    private static final int currentColor = Color.RED;

    private static class Pointer {
        public int id;        // event pointer id
        public float lx, ly;  // latest x,y on the canvas
        public float sx, sy;  // starting x,y on the canvas
    }

    private float userRotationRad;            // user applied rotation (only in finger-rotation mode)
    private float pixelHeightM;               // how many chart metres high pixels are
    private float pixelWidthM;                // how many chart metres wide pixels are

    private ArrayList<DrawWaypoint> allDrawWaypoints = new ArrayList<> ();
    @SuppressWarnings("FieldCanBeLocal")
    private boolean reDrawQueued;
    private ChartView chartView;
    private float canvasHdgRadOverride;
    private float latStart, lonStart;
    private float mappingCanvasHdgRads;
    private float mappingCenterLat;
    private float mappingCenterLon;
    private float mappingPixelHeightM;
    private float mappingPixelWidthM;
    private float rotStart, scaStart;
    private float mouseDownPosX, mouseDownPosY;
    private float[] poly2polyFloats = new float[16];
    private LatLon drawCourseLineISect = new LatLon ();
    private LatLon newCtrLL = new LatLon ();
    private LinkedList<CapGrid> capgrids;
    private int mappingCanvasHeight;
    private int mappingCanvasWidth;
    private long waypointOpenAt     = Long.MAX_VALUE;
    private Matrix poly2polyMatrix  = new Matrix ();
    private Paint capGridBGPaint    = new Paint ();
    private Paint capGridLnPaint    = new Paint ();
    private Paint capGridTxPaint    = new Paint ();
    private Paint centerLnPaint     = new Paint ();
    private Paint courseLnPaint     = new Paint ();
    private Paint courseTxPaint     = new Paint ();
    private Paint currentBGPaint    = new Paint ();
    private Paint currentLnPaint    = new Paint ();
    private Paint currentTxPaint    = new Paint ();
    private Paint trailPaint        = new Paint ();
    private Paint wayptBGPaint      = new Paint ();
    private Paint[] faaWPPaints     = new Paint[] { new Paint (), new Paint () };
    private Paint[] userWPPaints    = new Paint[] { new Paint (), new Paint () };
    private Path trailPath          = new Path ();
    private Point onDrawPt          = new Point ();
    private Point canpix1           = new Point ();
    private Point canpix2           = new Point ();
    private Point canpix3           = new Point ();
    private Point canpix4           = new Point ();
    private Point drawCourseFilletCom = new Point ();
    private Point drawCourseFilletFr  = new Point ();
    private Point drawCourseFilletTo  = new Point ();
    private Pointer firstPointer;
    private Pointer secondPointer;
    private Pointer transPointer    = new Pointer ();
    private PointF drawCourseFilletCp = new PointF ();
    private RectF drawCourseFilletOval = new RectF ();
    public  WairToNow wairToNow;
    private Waypoint.Within waypointsWithin = new Waypoint.Within ();

    public Chart2DView (ChartView cv)
    {
        super (cv.wairToNow);

        chartView = cv;
        wairToNow = cv.wairToNow;
        float ts = wairToNow.textSize;

        UnSetCanvasHdgRad ();

        capGridBGPaint.setColor (Color.argb (170, 255, 255, 255));
        capGridBGPaint.setStyle (Paint.Style.STROKE);
        capGridBGPaint.setStrokeWidth (30);
        capGridBGPaint.setTextSize (ts);
        capGridLnPaint.setColor (capGridColor);
        capGridLnPaint.setStyle (Paint.Style.FILL);
        capGridLnPaint.setStrokeWidth (6);
        capGridTxPaint.setColor (capGridColor);
        capGridTxPaint.setStyle (Paint.Style.FILL);
        capGridTxPaint.setStrokeWidth (2);
        capGridTxPaint.setTextSize (ts);

        centerLnPaint.setColor (centerColor);
        centerLnPaint.setStyle (Paint.Style.STROKE);
        centerLnPaint.setStrokeWidth (10);

        courseLnPaint.setColor (courseColor);
        courseLnPaint.setStyle (Paint.Style.FILL);
        courseLnPaint.setStrokeWidth (15);
        courseLnPaint.setStyle (Paint.Style.STROKE);
        courseLnPaint.setTextAlign (Paint.Align.CENTER);
        courseTxPaint.setColor (courseColor);
        courseTxPaint.setStyle (Paint.Style.FILL);
        courseTxPaint.setStrokeWidth (2);
        courseTxPaint.setTextSize (ts);
        courseTxPaint.setTextAlign (Paint.Align.CENTER);

        currentBGPaint.setColor (Color.WHITE);
        currentBGPaint.setStyle (Paint.Style.STROKE);
        currentBGPaint.setStrokeWidth (30);
        currentBGPaint.setTextSize (ts);
        currentBGPaint.setTextAlign (Paint.Align.CENTER);
        currentLnPaint.setColor (currentColor);
        currentLnPaint.setStyle (Paint.Style.STROKE);
        currentLnPaint.setStrokeWidth (10);
        currentTxPaint.setColor (currentColor);
        currentTxPaint.setStyle (Paint.Style.FILL);
        currentTxPaint.setStrokeWidth (2);
        currentTxPaint.setTextSize (ts);
        currentTxPaint.setTextAlign (Paint.Align.CENTER);

        trailPaint.setColor (Color.argb (255, 100, 75, 75));
        trailPaint.setStyle (Paint.Style.STROKE);
        trailPaint.setStrokeWidth (5);

        wayptBGPaint.setColor (Color.BLACK);
        wayptBGPaint.setStyle (Paint.Style.STROKE);
        wayptBGPaint.setStrokeWidth (15);
        wayptBGPaint.setTextSize (ts);
        wayptBGPaint.setTextAlign (Paint.Align.LEFT);

        for (Paint p : faaWPPaints) {
            p.setStyle (Paint.Style.FILL);
            p.setStrokeWidth (2);
            p.setTextSize (ts);
            p.setTextAlign (Paint.Align.LEFT);
        }
        faaWPPaints[0].setColor (Color.YELLOW);
        faaWPPaints[1].setColor (Color.argb (255, 250, 180, 0));

        for (Paint p : userWPPaints) {
            p.setStyle (Paint.Style.FILL);
            p.setStrokeWidth (2);
            p.setTextSize (ts);
            p.setTextAlign (Paint.Align.LEFT);
        }
        userWPPaints[0].setColor (Color.CYAN);
        userWPPaints[1].setColor (Color.argb (255, 180, 180, 250));
    }

    @Override  // Backing
    public void Activate ()
    {
        chartView.ReCenter ();
    }

    @Override  // Backing
    public View getView ()
    {
        return this;
    }

    /**
     * Selected chart was just changed.
     */
    @Override  // Backing
    public void ChartSelected ()
    {
        invalidate ();
    }

    /**
     * Just received a new GPS lat/lon.
     */
    @Override  // Backing
    public void SetGPSLocation ()
    {
        invalidate ();
    }

    /**
     * User just clicked 'Re-center' from the menu.
     */
    @Override  // Backing
    public void ReCenter ()
    {
        invalidate ();
    }

    /**
     * See how much to rotate chart by.
     */
    @Override  // Backing
    public float GetCanvasHdgRads ()
    {
        if (canvasHdgRadOverride != -99999.0F) return canvasHdgRadOverride;

        switch (wairToNow.optionsView.chartTrackOption.getVal ()) {

            // Course Up : rotate charts counter-clockwise by the heading along the route
            // that is adjacent to the current position.  If no route, use track-up mode.
            case OptionsView.CTO_COURSEUP: {
                if (chartView.clDest != null) {
                    float courseUpRad = Mathf.toRadians (Lib.GCOnCourseHdg (
                            chartView.orgLat, chartView.orgLon,
                            chartView.clDest.lat, chartView.clDest.lon,
                            wairToNow.currentGPSLat, wairToNow.currentGPSLon));
                    userRotationRad = Math.round (courseUpRad / rotatestep) * rotatestep;
                    return courseUpRad;
                }
                // fall through
            }

            // Track up : rotate charts counter-clockwise by the current heading
            case OptionsView.CTO_TRACKUP: {
                float trackUpRad = Mathf.toRadians (wairToNow.currentGPSHdg);
                userRotationRad = Math.round (trackUpRad / rotatestep) * rotatestep;
                return trackUpRad;
            }

            // North Up : north is always up
            case OptionsView.CTO_NORTHUP: {
                userRotationRad = 0.0F;
                return 0.0F;
            }

            // Finger Rotation : leave chart rotated as per user's manual rotation
            case OptionsView.CTO_FINGEROT: {
                return userRotationRad;
            }

            // who knows what?
            default: throw new RuntimeException ();
        }
    }

    /**
     * Set "UP" direction for next call to DrawChart().
     * @param canvasHdg = new "UP" direction for canvas (radians)
     */
    public void SetCanvasHdgRad (float canvasHdg)
    {
        canvasHdgRadOverride = canvasHdg;
    }
    public void UnSetCanvasHdgRad ()
    {
        canvasHdgRadOverride = -99999.0F;
    }

    /**
     * This screen is no longer current so close bitmaps to conserve memory.
     */
    @Override  // Backing
    public void recycle ()
    {
        for (Iterator<AirChart> it = wairToNow.maintView.GetAirChartIterator (); it.hasNext ();) {
            it.next ().CloseBitmaps ();
        }
    }

    /**
     * Callback for mouse events on the image.
     * We use this for scrolling the map around.
     */
    @Override  // View
    public boolean onTouchEvent (@NonNull MotionEvent event)
    {
        // we might get divide-by-zero if we don't know canvas size yet
        if ((chartView.pmap.canvasWidth == 0) || (chartView.pmap.canvasHeight == 0)) return false;

        // don't bother if no chart selected so we don't do weird things
        if (chartView.selectedChart == null) return false;

        switch (event.getActionMasked ()) {
            case MotionEvent.ACTION_DOWN: {
                MouseDown (event.getX (), event.getY ());
                // fall through
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int   i = event.getActionIndex ();
                int  id = event.getPointerId (i);
                float x = event.getX (i);
                float y = event.getY (i);
                if (firstPointer == null) {
                    firstPointer = new Pointer ();
                    firstPointer.id = id;
                    firstPointer.lx = x;
                    firstPointer.ly = y;
                } else if (secondPointer == null) {
                    secondPointer = new Pointer ();
                    secondPointer.id = id;
                    secondPointer.lx = x;
                    secondPointer.ly = y;
                }
                StartFingerPainting ();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int  i = event.getActionIndex ();
                int id = event.getPointerId (i);
                if ((firstPointer  != null) && (firstPointer.id  == id)) firstPointer  = null;
                if ((secondPointer != null) && (secondPointer.id == id)) secondPointer = null;

                // maybe lifted one finger but still have another one down
                // so pretend we are starting a translate from this point on
                StartFingerPainting ();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                Pointer thisptr = null;
                Pointer thatptr = null;
                int n = event.getPointerCount ();
                for (int i = 0; i < n; i ++) {
                    int  id = event.getPointerId (i);
                    float x = event.getX (i);
                    float y = event.getY (i);
                    Pointer p = null;
                    if ((firstPointer  != null) && (firstPointer.id  == id)) p = firstPointer;
                    if ((secondPointer != null) && (secondPointer.id == id)) p = secondPointer;
                    if (p != null) {
                        if (thisptr == null) {

                            // one finger found, save latest x,y
                            thisptr = p;
                            p.lx = x;
                            p.ly = y;

                            // if moved by this much, don't open nearby waypoint menu
                            if (Mathf.hypot (p.lx - p.sx, p.ly - p.sy) > 50.0) {
                                waypointOpenAt = Long.MAX_VALUE;
                            }
                        } else if (thatptr == null) {

                            // another finger found, save latest x,y
                            thatptr = p;
                            p.lx = x;
                            p.ly = y;

                            // two fingers down disables opening nearby waypoint menu
                            waypointOpenAt = Long.MAX_VALUE;
                        }
                    }
                }
                if (thisptr != null) {

                    // if we only have one finger down, it is a simple translate
                    // so make up a another finger that is parallelling it
                    if (thatptr == null) {
                        thatptr = transPointer;
                        thatptr.sx = thisptr.sx + 100;
                        thatptr.sy = thisptr.sy + 100;
                        thatptr.lx = thisptr.lx + 100;
                        thatptr.ly = thisptr.ly + 100;
                    }

                    // transform view such that point at thisold->thisnew
                    //                      and point at thatold->thatnew
                    TransformView (
                            thisptr.sx, thisptr.sy, thisptr.lx, thisptr.ly,
                            thatptr.sx, thatptr.sy, thatptr.lx, thatptr.ly);

                    // user has manually positioned screen, don't re-position on GPS updates
                    chartView.holdPosition = true;

                    // re-draw screen with new transform
                    invalidate ();
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                firstPointer   = null;
                secondPointer  = null;
                waypointOpenAt = Long.MAX_VALUE;
                break;
            }
        }
        return true;
    }

    /**
     * One or two fingers were either just touched or lifted.
     * Restart transform at this point in case transitioning
     * between full translate/rotate/scale and simple translate.
     */
    private void StartFingerPainting ()
    {
        /*
         * Save transform parameters at this point and all movements from
         * now on will be based on these values, ie, the values saved in
         * {lat,lon,rot,sca}Start were the values in effect when {first,second}
         * Pointer.s{x,y} were captured.
         */
        latStart = chartView.centerLat;
        lonStart = chartView.centerLon;
        rotStart = GetCanvasHdgRads ();
        scaStart = chartView.scaling;

        /*
         * Save the current pointer values as the starting values as they
         * now correspond to the values just saved in {lat,lon,rot,sca}Start.
         */
        if (firstPointer != null) {
            firstPointer.sx = firstPointer.lx;
            firstPointer.sy = firstPointer.ly;
        }
        if (secondPointer != null) {
            secondPointer.sx = secondPointer.lx;
            secondPointer.sy = secondPointer.ly;
        }
    }

    /**
     * Update canvas <-> latlon transformation given user manual scaling/rotation/translation
     */
    private void TransformView (float oldx1, float oldy1, float newx1, float newy1,
                                float oldx2, float oldy2, float newx2, float newy2)
    {
        // canvasHdgRads = what true heading is up on display
        // scaling = scaling factor, < 1: zoomed out; > 1: zoomed in
        // center{Lat,Lon} = what lat/lon is in center of display
        // canvas{Width,Height} = width/height of display in pixels

        // we should be able to set up a matrix that transforms points from new -> old
        // in the form:
        //   [ f * c   f * s   tx ]     [ newx ]     [ oldx ]
        //   [ f *-s   f * c   ty ]  *  [ newy ]  =  [ oldy ]
        //   [   0       0      1 ]     [   1  ]     [   1  ]

        // where:
        //    f = scale factor from new to old
        //    c = cosine of rotation from new to old
        //    s = sine of rotation
        //    t = translation in x and y

        float fold = Mathf.hypot (oldx2 - oldx1, oldy2 - oldy1);  // length of old line between 2 touch points
        float fnew = Mathf.hypot (newx2 - newx1, newy2 - newy1);  // length of new line between 2 touch points
        float f    = fold / fnew;                                // ratio of lengths
        float rold = Mathf.atan2 (oldx2 - oldx1, oldy2 - oldy1);  // angle of old line, cw from up
        float rnew = Mathf.atan2 (newx2 - newx1, newy2 - newy1);  // angle of new line, cw from up
        float r    = rold - rnew;                                // difference of angles

        float fc = f * Mathf.cos (r);  // scale/rotation cosine
        float fs = f * Mathf.sin (r);  // scale/rotation sine

        //  fc * newx + fs * newy + tx = oldx  =>  tx = oldx - fc * newx - fs * newy
        // -fs * newx + fc * newy + ty = oldy  =>  ty = oldy + fs * newx - fc * newy

        float tx = oldx1 - fc * newx1 - fs * newy1;  // translation needed for old point
        float ty = oldy1 - fc * newy1 + fs * newx1;

        // tx,ty are in the old co-ordinate system
        // so find new center lat/lon by looking tx,ty pixels away from old center

        // set up canvas pixel <-> lat/lon mapping for old points
        chartView.centerLat = latStart;
        chartView.centerLon = lonStart;
        userRotationRad     = rotStart;
        chartView.scaling   = scaStart;
        MapLatLonsToCanvasPixels (chartView.pmap.canvasWidth, chartView.pmap.canvasHeight);

        // figure out where new center point is in the old canvas
        float newCtrX = chartView.pmap.canvasWidth  / 2;
        float newCtrY = chartView.pmap.canvasHeight / 2;
        float oldCtrX = fc * newCtrX + fs * newCtrY + tx;
        float oldCtrY = fc * newCtrY - fs * newCtrX + ty;

        // compute the lat/lon for that point using the old canvas transform
        chartView.pmap.CanPix2LatLonAprox (oldCtrX, oldCtrY, newCtrLL);

        // that lat/lon will be at center of new canvas
        chartView.centerLat = newCtrLL.lat;
        chartView.centerLon = newCtrLL.lon;

        // rotate chart by this much counterclockwise
        float rotInt = rotStart - r;
        while (rotInt < -Mathf.PI) rotInt += Mathf.PI * 2.0F;
        while (rotInt >= Mathf.PI) rotInt -= Mathf.PI * 2.0F;
        if (enablesteps) rotInt = Math.round (rotInt / rotatestep) * rotatestep;
        userRotationRad = rotInt;

        // zoom out on chart by this much
        chartView.scaling = scaStart / f;
        int m = scalesteps.length - 1;
        if (enablesteps) {
            while (m > 0) {
                float a = scalesteps[m-1];
                float b = scalesteps[m];
                float e = chartView.scaling / a;
                float g = b / chartView.scaling;
                if (e > g) break;  // scaling closer to scalesteps[m] than scalesteps[m-1]
                -- m;
            }
            chartView.scaling = scalesteps[m];
        } else {
            if (chartView.scaling < scalesteps[0]) chartView.scaling = scalesteps[0];
            if (chartView.scaling > scalesteps[m]) chartView.scaling = scalesteps[m];
        }
    }

    /**
     * Handle mouse-down for purpose of tapping buttons on the chart surface.
     * @param x = canvas pixel tapped on
     * @param y = canvas pixel tapped on
     */
    private void MouseDown (float x, float y)
    {
        long now = System.currentTimeMillis ();
        mouseDownPosX  = x;
        mouseDownPosY  = y;

        // set up to detect long click on a nearby waypoint
        waypointOpenAt = now + waypointopentime;
        WairToNow.wtnHandler.runDelayed (waypointopentime, new Runnable () {
            @Override
            public void run ()
            {
                if (System.currentTimeMillis () >= waypointOpenAt) {
                    waypointOpenAt = Long.MAX_VALUE;
                    LatLon ll = new LatLon ();
                    CanPix2LatLonExact (mouseDownPosX, mouseDownPosY, ll);
                    wairToNow.waypointView1.OpenWaypointAtLatLon (ll.lat, ll.lon, nearbynm / chartView.scaling);
                }
            }
        });
    }

    /**
     * Callback to draw the tiles in position to fill the screen.
     */
    @Override  // View
    public void onDraw (Canvas canvas)
    {
        DrawChart (canvas, getWidth (), getHeight ());

        /*
         * Draw circles around spot where screen is being touched.
         */
        if (firstPointer != null) {
            float r = wairToNow.dotsPerInch * 0.25F;
            canvas.drawCircle (firstPointer.lx, firstPointer.ly, r, currentLnPaint);
        }
        if (secondPointer != null) {
            float r = wairToNow.dotsPerInch * 0.25F;
            canvas.drawCircle (secondPointer.lx, secondPointer.ly, r, currentLnPaint);
        }
    }

    /**
     * Draw chart (or splash image if no chart currently selected).
     * @param canvas = canvas to draw it on
     * @param cw = canvas width
     * @param ch = canvas height
     */
    public void DrawChart (Canvas canvas, int cw, int ch)
    {
        /*
         * Set up latlon <-> canvas pixel mapping.
         */
        MapLatLonsToCanvasPixels (cw, ch);

        if (chartView.selectedChart != null) {

            /*
             * Draw whatever chart was selected by user.
             */
            DrawSelectedChart (canvas);
        } else {

            /*
             * No chart selected, draw splash image centered on screen.
             */
            Bitmap bm = BitmapFactory.decodeResource (wairToNow.getResources (), R.drawable.splash);
            Rect src = new Rect (0, 0, bm.getWidth (), bm.getHeight ());
            int  rad = Math.min (cw, ch) * 3 / 8;
            Rect dst = new Rect (cw / 2 - rad, ch / 2 - rad,
                    cw / 2 + rad, ch / 2 + rad);
            canvas.drawBitmap (bm, src, dst, null);

            /*
             * Draw airplane icon centered on screen.
             */
            canvas.save ();
            canvas.translate (cw / 2, ch / 2);      // anything drawn below will be translated this much
            wairToNow.DrawAirplaneSymbol (canvas);  // draw airplane symbol unrotated
            canvas.restore ();                      // remove translation/scaling/rotation

            /*
             * Draw startup text below splash image.
             */
            DrawStartupStrings (canvas, currentBGPaint);
            DrawStartupStrings (canvas, currentTxPaint);
        }
    }

    private void DrawStartupStrings (Canvas canvas, Paint paint)
    {
        int cx = chartView.pmap.canvasWidth / 2;
        int dy = (int) paint.getFontSpacing ();
        int by = chartView.pmap.canvasHeight - 10;

        canvas.drawText ("Maint to download charts", cx, by - dy * 2, paint);
        canvas.drawText ("Chart to display chart", cx, by - dy, paint);
    }

    /**
     * Draw chart last selected by user and any related points on the chart.
     * @param canvas = canvas to draw it on
     */
    private void DrawSelectedChart (Canvas canvas)
    {
        /*
         * Draw map image as background.
         */
        try {
            chartView.selectedChart.DrawOnCanvas (chartView.pmap, canvas, this, GetCanvasHdgRads ());
        } catch (OutOfMemoryError oome) {
            // sometimes fast panning gives bitmap oom errors
            // force all bitmaps closed, garbage collect and give up for now
            recycle ();
            System.gc ();
            canvas.drawText ("out of memory error",
                    chartView.pmap.canvasWidth / 2, chartView.pmap.canvasHeight * 3/4, courseTxPaint);
        }

        /*
         * Draw Nexrads.
         */
        if (wairToNow.optionsView.showNexrad.checkBox.isChecked ()) {
            DrawNexrads (canvas);
        }

        /*
         * Maybe draw CAP grid lines and numbers.
         */
        if (wairToNow.optionsView.capGridOption.checkBox.isChecked ()) {
            DrawCapGrid (canvas);
        }

        /*
         * Maybe draw crumbs trail.
         */
        Point pt = onDrawPt;
        LinkedList<Position> shownTrail = wairToNow.crumbsView.GetShownTrail ();
        if (shownTrail != null) {
            // set up arrow size when heading north
            // - go down 1mm, go right 1.5mm
            float dy = wairToNow.dotsPerInch / Lib.MMPerIn;
            float dx = dy * 1.5F;
            // stroke width 0.3mm
            float sw = dy * 0.3F;
            // loop through each point and try to convert to canvas pixel
            for (Position pos : shownTrail) {
                if (LatLon2CanPixExact (pos.latitude, pos.longitude, pt)) {
                    trailPath.rewind ();
                    // if standing still, draw a circle with our thinnest line
                    float mps = pos.speed;
                    if (mps < WairToNow.gpsMinSpeedMPS) {
                        trailPaint.setStrokeWidth (sw);
                        trailPath.addCircle (pt.x, pt.y, dx, Path.Direction.CCW);
                    } else {
                        // if moving, draw arrow with thickness proportional to speed
                        trailPaint.setStrokeWidth ((Mathf.log10 (mps) + 1.0F) * sw);
                        float a = Mathf.toRadians (pos.heading) - GetCanvasHdgRads ();
                        float s = Mathf.sin (a);
                        float c = Mathf.cos (a);
                        float dxr = dx * c - dy * s;
                        float dyr = dy * c + dx * s;
                        trailPath.moveTo (pt.x + dxr, pt.y + dyr);
                        trailPath.rLineTo (- dxr, - dyr);
                        dxr = - dx * c - dy * s;
                        dyr =   dy * c - dx * s;
                        trailPath.rLineTo (dxr, dyr);
                    }
                    canvas.drawPath (trailPath, trailPaint);
                }
            }
        }

        /*
         * Draw any visible FAA waypoints.
         */
        allDrawWaypoints.clear ();
        if (wairToNow.optionsView.faaWPOption.checkBox.isChecked ()) {
            for (Waypoint faaWP : waypointsWithin.Get (
                    chartView.pmap.canvasSouthLat, chartView.pmap.canvasNorthLat,
                    chartView.pmap.canvasWestLon, chartView.pmap.canvasEastLon)) {
                if (LatLon2CanPixExact (faaWP.lat, faaWP.lon, pt)) {
                    allDrawWaypoints.add (new DrawWaypoint (faaWP.ident, pt, faaWPPaints));
                }
            }
        }

        /*
         * Draw any visible user waypoints.
         */
        if (wairToNow.optionsView.userWPOption.checkBox.isChecked ()) {
            Collection<UserWPView.UserWP> userWPs = wairToNow.userWPView.GetUserWPs ();
            for (UserWPView.UserWP userWP : userWPs) {
                if (LatLon2CanPixExact (userWP.lat, userWP.lon, pt)) {
                    allDrawWaypoints.add (new DrawWaypoint (userWP.ident, pt, userWPPaints));
                }
            }
        }
        DrawAllWaypoints (canvas);

        /*
         * Draw center cross if enabled.
         * But do not draw it if at same location as airplane.
         */
        if (wairToNow.chartView.stateView.showCenterInfo && chartView.holdPosition) {
            LatLon2CanPixExact (chartView.centerLat, chartView.centerLon, pt);
            float len = wairToNow.textSize * 1.5F;
            canvas.drawLine (pt.x - len, pt.y, pt.x + len, pt.y, centerLnPaint);
            canvas.drawLine (pt.x, pt.y - len, pt.x, pt.y + len, centerLnPaint);
        }

        /*
         * Draw course line if defined and enabled.
         */
        if ((chartView.clDest != null) && wairToNow.chartView.stateView.showCourseInfo) {
            DrawCourseLine (canvas);
        }

        /*
         * Draw an airplane icon where the GPS (current) lat/lon point is.
         */
        if (!wairToNow.optionsView.typeBOption.checkBox.isChecked () &&
                LatLon2CanPixExact (wairToNow.currentGPSLat, wairToNow.currentGPSLon, pt)) {
            wairToNow.DrawLocationArrow (canvas, pt, GetCanvasHdgRads ());
        }
    }

    /**
     * An user or FAA waypoint to be drawn.
     */
    private class DrawWaypoint {
        public final static int STOFF = 5;

        public int color;         // what color to draw it (index in faaWPPaints[] or userWPPaints[])
        public int x, y;          // location dot in canvas pixels
        public Paint[] fgPaints;  // either faaWPPaints[] or userWPPaints[]
        public Rect dotBox;       // xy limits of where dot is drawn on canvas including background
                                  // - dotBox is never moved
        public Rect txtBox;       // xy limits of where text is drawn on canvas including background
                                  // - txtBox can be moved if it overlaps something else
        public String ident;      // ident string text

        public LinkedList<DrawWaypoint> intersector;
                                  // null: not part of intersector set, so txtBox position is final
                                  // else: part of intersector set, final txtBox position not yet determined

        public DrawWaypoint (String id, Point pt, Paint[] fgs)
        {
            ident    = id;
            x        = pt.x;
            y        = pt.y;
            fgPaints = fgs;

            dotBox = new Rect (x-5, y-5, x+5, y+5);

            txtBox = new Rect ();
            wayptBGPaint.getTextBounds (ident, 0, ident.length (), txtBox);
            txtBox.offsetTo (x+STOFF, y+STOFF);
        }

        /**
         * See if this waypoint's ident string steps on another
         * waypoint's ident string or another waypoint's location
         * dot.
         * @return true iff so
         */
        public boolean StepsOnSomethingElse ()
        {
            for (DrawWaypoint dw : allDrawWaypoints) {

                // never compare with ourself as we always step on ourself
                if (this == dw) continue;

                // we don't want to step on anyone else's location dot
                if (Rect.intersects (this.txtBox, dw.dotBox)) return true;

                // as for strings, don't test against ones that haven't been placed yet
                if (this.intersector == dw.intersector) continue;

                // we don't want to step on a string that has been placed
                if (Rect.intersects (this.txtBox, dw.txtBox)) return true;
            }

            // ok our string doesn't step on anything else other than
            // possibly strings that haven't been placed yet
            return false;
        }

        /**
         * Draw location dot and string as placed.
         * @param canvas = what to draw them on
         * @param dofg = false: draw background
         *                true: draw foreground
         */
        public void DrawIt (Canvas canvas, boolean dofg)
        {
            if (!dofg) {
                canvas.drawCircle (x, y, 9, wayptBGPaint);
                canvas.drawText (ident, txtBox.left, txtBox.top, wayptBGPaint);
            } else {
                Paint fgPaint = fgPaints[color%fgPaints.length];
                canvas.drawCircle (x, y, 5, fgPaint);
                canvas.drawText (ident, txtBox.left, txtBox.top, fgPaint);
            }
        }
    }

    private void DrawAllWaypoints (Canvas canvas)
    {
        /*
         * Build list of intersecting (overlapping) waypoint identifiers.
         * Each element of allIntersectors is a list of identifiers that overlap each other.
         */
        LinkedList<LinkedList<DrawWaypoint>> allIntersectors = new LinkedList<> ();
        int count = allDrawWaypoints.size ();
        DrawWaypoint[] array = new DrawWaypoint[count];
        array = allDrawWaypoints.toArray (array);
        for (int i = 0; i < count; i ++) {
            DrawWaypoint dwi = array[i];
            for (int j = i; ++ j < count;) {
                DrawWaypoint dwj = array[j];

                /*
                 * See if the two strings overlap.
                 */
                if (Rect.intersects (dwi.txtBox, dwj.txtBox)) {

                    /*
                     * Ok, add the two strings to an intersector list.
                     * Either add to one or the other's existing list
                     * or create a new one if none exists.  If both are
                     * already part of different intersector lists,
                     * then we merge the two lists.
                     */
                    if ((dwi.intersector == null) && (dwj.intersector == null)) {

                        // neither is part of an existing intersector list,
                        // create a new intersector list and add both to it.
                        LinkedList<DrawWaypoint> intersector = new LinkedList<> ();
                        dwi.intersector = dwj.intersector = intersector;
                        intersector.add (dwi);
                        intersector.add (dwj);
                        allIntersectors.add (intersector);
                    } else if ((dwi.intersector != null) && (dwj.intersector == null)) {

                        // dwi already is in an intersector list but dwj isn't.
                        // add dwj to dwi's intersector list.
                        dwj.intersector = dwi.intersector;
                        dwj.intersector.add (dwj);
                    } else if ((dwi.intersector == null) && (dwj.intersector != null)) {

                        // dwj already is in an intersector list but dwi isn't.
                        // add dwi to dwj's intersector list.
                        dwi.intersector = dwj.intersector;
                        dwi.intersector.add (dwi);
                    } else if (dwi.intersector != dwj.intersector) {

                        // dwi and dwj are already in different intersector lists.
                        // add all of dwj's intersector elements to dwi's list and
                        // delete dwj's intersector.
                        LinkedList<DrawWaypoint> dwjIntersector = dwj.intersector;
                        allIntersectors.remove (dwjIntersector);
                        for (int k = 0; k < count; k ++) {
                            DrawWaypoint dwk = array[k];
                            if (dwk.intersector == dwjIntersector) {
                                dwk.intersector = dwi.intersector;
                                dwi.intersector.add (dwk);
                            }
                        }
                    }
                }
            }
        }

        /*
         * Go through each list of overlapping strings.
         * For each element of an overlapping string set:
         *   1) assign it an unique color for that set
         *   2) find an offset on Y-axis where there is no other string
         *      (ignoring strings in this set that haven't been offset yet)
         *      start with offset 0, then 1, then -1, then 2, then -2, ...
         *   3) mark string as processed so it can't be stepped on by something else
         */
        for (LinkedList<DrawWaypoint> intersector : allIntersectors) {
            int color = 0;
            DrawWaypoint dw;
            while ((dw = intersector.poll ()) != null) {
                dw.color = ++ color;
                for (int step = 0; ++ step > 0;) {
                    int offset = ((step & 1) == 0) ? step / 2 : -(step / 2);
                    dw.txtBox.offsetTo (dw.x + DrawWaypoint.STOFF,
                            dw.y + DrawWaypoint.STOFF + offset * dw.txtBox.height () * 5 / 4);
                    if (!dw.StepsOnSomethingElse ()) {
                        dw.intersector = null;
                        break;
                    }
                }
            }
        }

        /*
         * Finally draw all the waypoint strings and the location dots.
         */
        for (DrawWaypoint dw : allDrawWaypoints) {
            dw.DrawIt (canvas, false);
        }
        for (DrawWaypoint dw : allDrawWaypoints) {
            dw.DrawIt (canvas, true);
        }
    }

    /**
     * Draw selected course line on the canvas.
     */
    private void DrawCourseLine (Canvas canvas)
    {
        float orgLat   = chartView.orgLat;         // start point of course line
        float orgLon   = chartView.orgLon;
        float dstLat   = chartView.clDest.lat;     // end point of course line
        float dstLon   = chartView.clDest.lon;
        float arrowLat = wairToNow.currentGPSLat;  // aircraft current position
        float arrowLon = wairToNow.currentGPSLon;
        float heading  = wairToNow.currentGPSHdg;  // aircraft current heading
        float speed    = wairToNow.currentGPSSpd;  // aircraft current speed
        float scaling  = chartView.scaling;        // map display scaling factor

        // draw solid line from planned starting point to current destination waypoint
        DrawCourseLine (canvas, orgLat, orgLon, dstLat, dstLon, true);

        // we might still be in the middle of turning from a previous route segment onto the current
        // if so, draw a fillet for the remainder of the turn
        LatLon isect = drawCourseLineISect;
        if (speed > WairToNow.gpsMinSpeedMPS) {

            // see how far away we are from the current course line and don't bother drawing fillet if close
            float offcourse = Lib.GCOffCourseDist (orgLat, orgLon, dstLat, dstLon, arrowLat, arrowLon);
            if (Math.abs (offcourse) > 0.1F / scaling) {

                // find point that the current heading intersects the course line at
                // and make sure that the intersection point is ahead of us on the course line
                if (Lib.GCIntersect (orgLat, orgLon, dstLat, dstLon, arrowLat, arrowLon, heading, isect)) {

                    // and make sure that intersection is not beyond end of the current segment
                    float distFromCurrToISect  = Lib.LatLonDist (arrowLat, arrowLon, isect.lat, isect.lon);
                    float distFromCurrToSegEnd = Lib.LatLonDist (arrowLat, arrowLon, dstLat, dstLon);
                    if (distFromCurrToISect < distFromCurrToSegEnd) {

                        // draw fillet to finish the turn that gets us on to the current course line
                        DrawCourseFillet (canvas, arrowLat, arrowLon, isect.lat, isect.lon, dstLat, dstLon);
                    }
                }
            }
        }

        // see if a route is being tracked on route page
        RouteView routeView = wairToNow.routeView;
        if (routeView.trackingOn) {

            // get route waypoint array.  theoretically clDest = ara[pai] and org{Lat,Lon} = ara[pai-1].
            Waypoint[] ara = routeView.analyzedRouteArray;
            int len = ara.length;
            int pai = routeView.pointAhead;

            // see if there is another segment beyond our current segment that we will turn to
            if (++ pai < len) {
                Waypoint lastwp = chartView.clDest;
                Waypoint nextwp = ara[pai];
                if (speed > WairToNow.gpsMinSpeedMPS) {

                    // make sure we are some minimal distance from next segment so we get a valid intersect point
                    float offcourse = Lib.GCOffCourseDist (nextwp.lat, nextwp.lon, lastwp.lat, lastwp.lon, arrowLat, arrowLon);
                    if (Math.abs (offcourse) > 0.1F / scaling) {

                        // calculate where we would intersect the next segment if we kept on current
                        // heading from where we are now and make sure that point is in front of us
                        if (Lib.GCIntersect (nextwp.lat, nextwp.lon, lastwp.lat, lastwp.lon, arrowLat, arrowLon, heading, isect)) {

                            // make sure intersection point isn't way out at infinity
                            float distFromCurrToDest = Lib.LatLonDist (arrowLat, arrowLon, lastwp.lat, lastwp.lon);
                            float distFromISectToDest = Lib.LatLonDist (isect.lat, isect.lon, lastwp.lat, lastwp.lon);
                            if (distFromISectToDest < distFromCurrToDest * 1.2F) {

                                // draw fillet showing the turn from current course to next course
                                DrawCourseFillet (canvas, arrowLat, arrowLon, isect.lat, isect.lon, nextwp.lat, nextwp.lon);
                            }
                        }
                    }
                }

                // draw remaining course segments beyond that as dotted lines with no fillets
                while (true) {
                    DrawCourseLine (canvas, lastwp.lat, lastwp.lon, nextwp.lat, nextwp.lon, false);
                    if (++ pai >= len) break;
                    lastwp = nextwp;
                    nextwp = ara[pai];
                }
            }
        }
    }

    /**
     * Draw turning radius starting on path from prev past curr ending on path to next.
     */
    private void DrawCourseFillet (Canvas canvas,
            float prevlat, float prevlon,
            float currlat, float currlon,
            float nextlat, float nextlon)
    {
        // two points on line being turned from
        Point fr  = drawCourseFilletFr;
        Point com = drawCourseFilletCom;
        LatLon2CanPixExact (prevlat, prevlon, fr);
        LatLon2CanPixExact (currlat, currlon, com);

        // two points on line being turned to (com is one of them)
        Point to  = drawCourseFilletTo;
        LatLon2CanPixExact (nextlat, nextlon, to);

        // get heading on current segment (ie, heading we are supposed to be on now)
        float currsegtcdeg = Mathf.toDegrees (Mathf.atan2 (com.x - fr.x, fr.y - com.y));

        // get heading on next segment (ie, heading we want to turn to)
        float nextsegtcdeg = Mathf.toDegrees (Mathf.atan2 (to.x - com.x, com.y - to.y));

        // see how far we have to turn
        float hdgdiffdeg = nextsegtcdeg - currsegtcdeg;
        if (hdgdiffdeg < -180.0F) hdgdiffdeg += 360.0F;
        if (hdgdiffdeg >= 180.0F) hdgdiffdeg -= 360.0F;
        float hdgdiffdegabs = Math.abs (hdgdiffdeg);

        // don't bother with arc if less than 1 sec of turning to do
        // also don't bother if hairpin turn as we have already blown past it
        if ((hdgdiffdegabs > GlassView.STDRATETURN) && (hdgdiffdegabs < 180 - GlassView.STDRATETURN)) {

            // radius[met] = speed[met/sec] / turnrate[rad/sec]
            float radiusmet = wairToNow.currentGPSSpd / Mathf.toRadians (GlassView.STDRATETURN);
            float radiuspix = radiusmet / Mathf.sqrt (pixelWidthM * pixelHeightM);

            // find center of turn
            PointF cp = drawCourseFilletCp;
            Lib.circleTangentToTwoLineSegs (fr.x, fr.y, com.x, com.y, to.x, to.y, radiuspix, cp);

            // draw arc along turn path
            RectF oval  = drawCourseFilletOval;
            oval.left   = cp.x - radiuspix;
            oval.top    = cp.y - radiuspix;
            oval.right  = cp.x + radiuspix;
            oval.bottom = cp.y + radiuspix;
            float startAngle = currsegtcdeg + 180.0F;
            if (hdgdiffdeg < 0.0F) {
                startAngle = nextsegtcdeg;
                hdgdiffdeg = - hdgdiffdeg;
            }
            canvas.drawArc (oval, startAngle, hdgdiffdeg, false, courseLnPaint);
        }
    }

    /**
     * Draw great circle course line from one point to the other.
     */
    private void DrawCourseLine (Canvas canvas, float srcLat, float srcLon, float dstLat, float dstLon, boolean solid)
    {
        float scaling = chartView.scaling;  // current map display scale factor
        PixelMapper pmap = chartView.pmap;  // current lat/lon => screen pixel mapper
        Point pt = onDrawPt;

        /*
         * See if primarily east/west or north/south route.
         */
        float tcquad = Lib.LatLonTC_rad (srcLat, srcLon, dstLat, dstLon);
        tcquad = Math.abs (tcquad);
        if (tcquad > Mathf.PI / 2.0F) tcquad = Mathf.PI - tcquad;
        if (tcquad > Mathf.PI / 4.0F) {
            // primarily east/west

            /*
             * Find east/west limits of course.  Wrap east to be .ge. west if necessary.
             */
            float courseWestLon = Lib.Westmost (srcLon, dstLon);
            float courseEastLon = Lib.Eastmost (srcLon, dstLon);
            if (courseEastLon < courseWestLon) courseEastLon += 360.0F;

            /*
             * If canvas is completely west of course, try wrapping canvas eastbound.
             * Eg, course KLAX->PGUM and canvas at lon=-170
             * So courseEastLon=145, courseWestLon=-118+360=242
             *    and canvasEastLon=-171, canvasWestLon=-169
             * but we want
             *    canvasEastLon=-171+360=189, canvasWestLon=-169+360=191
             *    ...so the canvas numbers end up between the course numbers
             */
            float cwl = pmap.canvasWestLon;
            float cel = pmap.canvasEastLon;
            if (cel < courseWestLon) {
                cwl += 360.0F;
                cel += 360.0F;
            }

            /*
             * Determine longitude limits of what to plot.
             */
            if (cwl < courseWestLon) cwl = courseWestLon;
            if (cel > courseEastLon) cel = courseEastLon;

            float lonstep = Mathf.sin (tcquad) / scaling / Lib.NMPerDeg / Mathf.cos (Mathf.toRadians (pmap.centerLat) / 2.0);

            int lastx = 0;
            int lasty = 0;
            int nstep = 0;
            for (float lon = cwl;; lon += lonstep) {
                if (lon > cel) lon = cel;
                float lat = Lib.GCLon2Lat (srcLat, srcLon, dstLat, dstLon, lon);
                LatLon2CanPixExact (lat, lon, pt);
                if ((nstep > 0) && (solid || ((nstep & 1) == 0))) {
                    canvas.drawLine (lastx, lasty, pt.x, pt.y, courseLnPaint);
                }
                lastx = pt.x;
                lasty = pt.y;
                nstep ++;
                if (lon >= cel) break;
            }
        } else {
            // primarily north/south
            float csl = Math.min (srcLat, dstLat);
            float cnl = Math.max (srcLat, dstLat);
            if (csl < pmap.canvasSouthLat) csl = pmap.canvasSouthLat;
            if (cnl > pmap.canvasNorthLat) cnl = pmap.canvasNorthLat;

            float latstep = Mathf.cos (tcquad) / scaling / Lib.NMPerDeg;

            int lastx = 0;
            int lasty = 0;
            int nstep = 0;
            for (float lat = csl;; lat += latstep) {
                if (lat > cnl) lat = cnl;
                float lon = Lib.GCLat2Lon (srcLat, srcLon, dstLat, dstLon, lat);
                LatLon2CanPixExact (lat, lon, pt);
                if ((nstep > 0) && (solid || ((nstep & 1) == 0))) {
                    canvas.drawLine (lastx, lasty, pt.x, pt.y, courseLnPaint);
                }
                lastx = pt.x;
                lasty = pt.y;
                nstep ++;
                if (lat >= cnl) break;
            }
        }
    }

    /**
     * Draw all visible Nexrads.
     */
    private void DrawNexrads (Canvas canvas)
    {
        // quick exit for common case
        if (wairToNow.nexradRepo.isEmpty ()) return;

        // maybe we are on an off-blink cycle
        if (blinknexrad) {
            long nowms = SystemClock.uptimeMillis ();
            if (!reDrawQueued) {
                reDrawQueued = true;
                WairToNow.wtnHandler.runDelayed (2048 - nowms % 2048, reDraw);
            }
            if (((nowms / 2048) & 1) == 0) return;
        }

        // get current lat/lon => canvas pixel mapping
        PixelMapper pmap = chartView.pmap;

        // set up the four corners of the nexrad tiles
        // all nexrad tiles are the same pixel dimensions
        Matrix matrix = poly2polyMatrix;
        float[] mapping = poly2polyFloats;
        mapping[0] = 0;                     // tile northwest (top left)
        mapping[1] = 0;
        mapping[2] = NexradImage.WIDTH;     // tile northeast (top rite)
        mapping[3] = 0;
        mapping[4] = 0;                     // tile southwest (bot left)
        mapping[5] = NexradImage.HEIGHT;
        mapping[6] = NexradImage.WIDTH;     // tile southeast (bot rite)
        mapping[7] = NexradImage.HEIGHT;

        // doesn't matter if ADS-B receiver/decoder is blocked while we draw
        // we shouldn't be blocked for long cuz AdsbGpsRThread.adsbNexrad() just locks for inserts/removes
        synchronized (wairToNow.nexradRepo) {

            // loop through all tiles starting with the oldest received
            // thus the newer ones will be drawn on top
            for (NexradImage nexrad : wairToNow.nexradRepo) {

                // see if nexrad tile is possibly visible to the canvas
                // values are all pre-computed so we can test quickly
                if (!Lib.LatOverlap (pmap.canvasSouthLat, pmap.canvasNorthLat,
                        nexrad.southLat, nexrad.northLat)) continue;
                if (!Lib.LonOverlap (pmap.canvasWestLon, pmap.canvasEastLon,
                        nexrad.eastLon, nexrad.westLon)) continue;

                // compute canvas pixels for the four corners
                // can't pre-compute cuz mapping different each time
                LatLon2CanPixExact (nexrad.northLat, nexrad.westLon, canpix1);
                LatLon2CanPixExact (nexrad.northLat, nexrad.eastLon, canpix2);
                LatLon2CanPixExact (nexrad.southLat, nexrad.westLon, canpix3);
                LatLon2CanPixExact (nexrad.southLat, nexrad.eastLon, canpix4);

                // rotate, stretch, warp as needed
                mapping[ 8] = canpix1.x;    // canvas northwest
                mapping[ 9] = canpix1.y;
                mapping[10] = canpix2.x;    // canvas northeast
                mapping[11] = canpix2.y;
                mapping[12] = canpix3.x;    // canvas southwest
                mapping[13] = canpix3.y;
                mapping[14] = canpix4.x;    // canvas southeast
                mapping[15] = canpix4.y;

                // draw it
                if (matrix.setPolyToPoly (mapping, 0, mapping, 8, 4)) {
                    canvas.drawBitmap (nexrad.getBitmap (), matrix, null);
                }

                // draw outline around tile
                /*if (false) {
                    canvas.drawLine (canpix1.x, canpix1.y, canpix2.x, canpix2.y, currentLnPaint);
                    canvas.drawLine (canpix2.x, canpix2.y, canpix4.x, canpix4.y, currentLnPaint);
                    canvas.drawLine (canpix3.x, canpix3.y, canpix4.x, canpix4.y, currentLnPaint);
                    canvas.drawLine (canpix3.x, canpix3.y, canpix1.x, canpix1.y, currentLnPaint);
                }*/
            }
        }
    }

    private final Runnable reDraw = new Runnable () {
        @Override
        public void run ()
        {
            reDrawQueued = false;
            invalidate ();
        }
    };

    /**
     * Draw CAP grid lines and numbers.
     */
    private void DrawCapGrid (Canvas canvas)
    {
        if (capgrids == null) {
            capgrids = new LinkedList<> ();
            try {
                AssetManager assetManager = wairToNow.getAssets ();
                BufferedReader caprdr = new BufferedReader (new InputStreamReader (assetManager.open ("capgrid.dat")), 1024);
                String caprec;
                while ((caprec = caprdr.readLine ()) != null) {
                    CapGrid capgrid = new CapGrid (caprec);
                    capgrids.addLast (capgrid);
                }
                caprdr.close ();
            } catch (IOException ioe) {
                Log.e (TAG, "error reading asset capgrid.dat", ioe);
                return;
            }
        }

        PixelMapper pmap = chartView.pmap;
        float westlon  = Mathf.floor (pmap.canvasWestLon  * 4.0F) / 4.0F;
        float eastlon  = Mathf.ceil  (pmap.canvasEastLon  * 4.0F) / 4.0F;
        float southlat = Mathf.floor (pmap.canvasSouthLat * 4.0F) / 4.0F;
        float northlat = Mathf.ceil  (pmap.canvasNorthLat * 4.0F) / 4.0F;

        if (eastlon < westlon) eastlon += 360.0F;

        for (float lat = southlat; lat <= northlat; lat += 0.25F) {
            for (float lon = westlon; lon <= eastlon; lon += 0.25F) {
                LatLon2CanPixExact (lat, lon, canpix1);
                LatLon2CanPixExact (lat, lon + 0.25F, canpix2);
                LatLon2CanPixExact (lat + 0.25F, lon, canpix3);
                int x1 = canpix1.x;
                int y1 = canpix1.y;
                canvas.drawLine (x1, y1, canpix2.x, canpix2.y, capGridLnPaint);
                canvas.drawLine (x1, y1, canpix3.x, canpix3.y, capGridLnPaint);

                int bestn = 999;
                String bestid = null;
                for (CapGrid cg : capgrids) {
                    int n = cg.number (Lib.NormalLon (lon), lat);
                    if ((n > 0) && (bestn > n)) {
                        bestn = n;
                        bestid = cg.id;
                    }
                }
                if (bestid != null) {
                    canvas.save ();
                    try {
                        float theta = Mathf.atan2 (canpix2.y - y1, canpix2.x - x1);
                        canvas.rotate (Mathf.toDegrees (theta), x1, y1);
                        String s = bestid + " " + bestn;
                        canvas.drawText (s, x1 + 10, y1 - 10, capGridBGPaint);
                        canvas.drawText (s, x1 + 10, y1 - 10, capGridTxPaint);
                    } finally {
                        canvas.restore ();
                    }
                }
            }
        }
    }

    /**
     * Set up latlon <-> canvas pixel mapping.
     */
    private void MapLatLonsToCanvasPixels (int cw, int ch)
    {
        float centerLat = chartView.centerLat;
        float centerLon = chartView.centerLon;
        float scaling   = chartView.scaling;

        /*
         * Compute how many charted metres fit in a pixel.
         */
        pixelWidthM  = Lib.MMPerIn / 1000 / wairToNow.dotsPerInchX / scaling * scalebase;
        pixelHeightM = Lib.MMPerIn / 1000 / wairToNow.dotsPerInchY / scaling * scalebase;

        /*
         * Do the monsterous calculations only if something significant has changed since last time.
         */
        if ((mappingCanvasHdgRads == GetCanvasHdgRads ()) &&
            (mappingCenterLat     == centerLat)     &&
            (mappingCenterLon     == centerLon)     &&
            (mappingCanvasHeight  == ch)            &&
            (mappingCanvasWidth   == cw)            &&
            (mappingPixelHeightM  == pixelHeightM)  &&
            (mappingPixelWidthM   == pixelWidthM)) return;

        mappingCanvasHdgRads = GetCanvasHdgRads ();
        mappingCenterLat     = centerLat;
        mappingCenterLon     = centerLon;
        mappingCanvasHeight  = ch;
        mappingCanvasWidth   = cw;
        mappingPixelHeightM  = pixelHeightM;
        mappingPixelWidthM   = pixelWidthM;

        /*
         * Compute the canvas pixel that gets the center lat/lon.
         */
        int canvasCenterX = cw / 2;
        int canvasCenterY = ch / 2;

        /*
         * Compute the lat/lon of the four corners of the canvas such
         * that each pixel covers the same delta metres in X and Y.
         */
        float metresFromCenterToRite   = canvasCenterX * pixelWidthM;
        float metresFromCenterToBot    = canvasCenterY * pixelHeightM;
        float metresFromCenterToCorner = Mathf.hypot (metresFromCenterToBot, metresFromCenterToRite);

        float angleFromCenterToBotRite   = Mathf.atan2 (metresFromCenterToBot, metresFromCenterToRite) + mappingCanvasHdgRads;
        float metresFromCenterToBotRiteX = metresFromCenterToCorner * Mathf.cos (angleFromCenterToBotRite);
        float metresFromCenterToBotRiteY = metresFromCenterToCorner * Mathf.sin (angleFromCenterToBotRite);

        float metresFromCenterToTopLeftX = -metresFromCenterToBotRiteX;
        float metresFromCenterToTopLeftY = -metresFromCenterToBotRiteY;

        float angleFromCenterToBotLeft   = Mathf.atan2 (metresFromCenterToBot, -metresFromCenterToRite) + mappingCanvasHdgRads;
        float metresFromCenterToBotLeftX = metresFromCenterToCorner * Mathf.cos (angleFromCenterToBotLeft);
        float metresFromCenterToBotLeftY = metresFromCenterToCorner * Mathf.sin (angleFromCenterToBotLeft);

        float metresFromCenterToTopRiteX = -metresFromCenterToBotLeftX;
        float metresFromCenterToTopRiteY = -metresFromCenterToBotLeftY;

        float brLat = centerLat - metresFromCenterToBotRiteY / Lib.MPerNM / Lib.NMPerDeg;
        float brLon = centerLon + metresFromCenterToBotRiteX / Lib.MPerNM / Lib.NMPerDeg / Mathf.cos (brLat / 180.0 * Mathf.PI);

        float blLat = centerLat - metresFromCenterToBotLeftY / Lib.MPerNM / Lib.NMPerDeg;
        float blLon = centerLon + metresFromCenterToBotLeftX / Lib.MPerNM / Lib.NMPerDeg / Mathf.cos (blLat / 180.0 * Mathf.PI);

        float trLat = centerLat - metresFromCenterToTopRiteY / Lib.MPerNM / Lib.NMPerDeg;
        float trLon = centerLon + metresFromCenterToTopRiteX / Lib.MPerNM / Lib.NMPerDeg / Mathf.cos (trLat / 180.0 * Mathf.PI);

        float tlLat = centerLat - metresFromCenterToTopLeftY / Lib.MPerNM / Lib.NMPerDeg;
        float tlLon = centerLon + metresFromCenterToTopLeftX / Lib.MPerNM / Lib.NMPerDeg / Mathf.cos (tlLat / 180.0 * Mathf.PI);

        tlLon = Lib.NormalLon (tlLon);
        trLon = Lib.NormalLon (trLon);
        blLon = Lib.NormalLon (blLon);
        brLon = Lib.NormalLon (brLon);

        /*
         * Set up mapping.
         */
        chartView.pmap.setup (cw, ch, tlLat, tlLon, trLat, trLon, blLat, blLon, brLat, brLon);
    }

    /**
     * Same as LatLon2CanPixExact() except it accepts an altitude parameter
     * which is ignored since this is a 2D projection.
     */
    @Override  // ChartView.Backing
    public boolean LatLonAlt2CanPixExact (float lat, float lon, float alt, Point pix)
    {
        return LatLon2CanPixExact (lat, lon, pix);
    }

    /**
     * Use currently selected chart projection to compute exact canvas pixel for a given lat/lon.
     * If no chart selected, use linear interpolation from the four corners of the screen.
     */
    public boolean LatLon2CanPixExact (float lat, float lon, Point canpix)
    {
        PixelMapper pmap = chartView.pmap;
        DisplayableChart sc = chartView.selectedChart;
        if ((sc == null) || !sc.LatLon2CanPixExact (lat, lon, canpix)) {
            pmap.LatLon2CanPixAprox (lat, lon, canpix);
        }
        return (canpix.x >= 0) && (canpix.x < pmap.canvasWidth) && (canpix.y >= 0) && (canpix.y < pmap.canvasHeight);
    }

    /**
     * Use currently selected chart projection to compute exact lat/lon for a given canvas pixel.
     * If no chart selected, use linear interpolation from the four corners of the screen.
     */
    public void CanPix2LatLonExact (float canvasPixX, float canvasPixY, LatLon ll)
    {
        DisplayableChart sc = chartView.selectedChart;
        if ((sc == null) || !sc.CanPix2LatLonExact (canvasPixX, canvasPixY, ll)) {
            chartView.pmap.CanPix2LatLonAprox (canvasPixX, canvasPixY, ll);
        }
    }

    /**
     * One of these per sectional giving CAP gridding.
     */
    private static class CapGrid {
        public float east;      // east longitude
        public float north;     // north latitude
        public float south;     // south latitude
        public float west;      // west longitude
        public String id;       // 3-letter chart id
        public String name;     // sectional name

        // NEW YORK,NYC,44-00N,40-00N,77-00W,69-00W,512,
        public CapGrid (String caprec)
        {
            String[] capcols = Lib.QuotedCSVSplit (caprec);
            name = capcols[0];
            id   = capcols[1];
            north = decodelat (capcols[2]);
            south = decodelat (capcols[3]);
            west  = decodelon (capcols[4]);
            east  = decodelon (capcols[5]);
        }

        /**
         * Get the grid number for a given latitude,longitude
         *
         *    +-------------north-------------+
         *    | 1  2  3  4  5  ...          w |  y = 1
         *    | w+1 ...                       |  y = 2
         *    w                               e
         *    e                               a
         *    s                               s
         *    t                               t
         *    |                               |
         *    |                               |
         *    +-------------south-------------+
         */
        public int number (float lon, float lat)
        {
            int w = (int) ((east - west) * 4.0F);  // width in 15' increments
            int x = (int) ((lon  - west) * 4.0F);  // X to right of west in 15' increments
            if (w < 0) w += 360 * 4;
            if (x < 0) x += 360 * 4;
            if (x >= w) return -1;

            int h = (int) ((north - south) * 4.0F);    // height in 15' increments
            int y = (int) ((north - lat) * 4.0F) - 1;  // Y below north in 15' increments
            if ((y < 0) || (y >= h)) return -1;

            return w * y + x + 1;
        }

        private static float decodelat (String str)
        {
            int i = str.indexOf ('-');
            float deg = Integer.parseInt (str.substring (0, i ++));
            float min = Integer.parseInt (str.substring (i, i + 2));
            deg += min / 60.0;
            if (str.endsWith ("S")) deg = -deg;
            return deg;
        }

        private static float decodelon (String str)
        {
            int i = str.indexOf ('-');
            float deg = Integer.parseInt (str.substring (0, i ++));
            float min = Integer.parseInt (str.substring (i, i + 2));
            deg += min / 60.0;
            if (str.endsWith ("W")) deg = -deg;
            return deg;
        }
    }
}
