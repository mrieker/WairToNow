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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ListAdapter;

/**
 * Display chart and current position and a specified course line.
 */
@SuppressLint("ViewConstructor")
public class ChartView
        extends View
        implements DisplayableChart.Invalidatable, WairToNow.CanBeMainView {

    private static final int coursecleartime  = 1000;
    private static final int waypointopentime = 1000;
    private final static float rotatestep = Mathf.toRadians (5.0F);  // manual rotation stepping
    private final static float[] scalesteps = new float[] {       // manual scale stepping
                      0.20F, 0.25F, 0.30F, 0.40F, 0.50F, 0.60F, 0.80F, 1.00F,
        1.25F, 1.50F, 2.00F, 2.50F, 3.00F, 4.00F, 5.00F, 6.00F, 8.00F, 10.0F,
        12.5F, 15.0F, 20.0F, 25.0F, 30.0F, 40.0F, 50.0F, 60.0F, 80.0F, 100.0F
    };
    private final static boolean enablesteps = true;   // enables discrete steps

    private final static int   tracktotal = 60;        // total number of seconds for tracking ahead
    private final static int   trackspace = 10;        // number of seconds spacing between tracking dots
    private static final float scalebase  = 500000;    // chart scale factor (when scaling=1.0, using 500000
                                                       // makes sectionals appear actual size theoretically)

    private static final int capGridColor = Color.DKGRAY;
    private static final int centerColor  = Color.BLUE;
    public  static final int courseColor  = Color.rgb (170, 0, 170);
    private static final int currentColor = Color.RED;
    private static final int selectColor  = Color.BLACK;

    private static class Pointer {
        public int id;        // event pointer id
        public float lx, ly;  // latest x,y on the canvas
        public float sx, sy;  // starting x,y on the canvas
    }

    private boolean reselectLastChart = true;
    private boolean showCenterInfo = true;
    private boolean showCourseInfo = true;
    private boolean showGPSInfo    = true;
    public  DisplayMetrics metrics = new DisplayMetrics ();
    private float altitude;                   // metres MSL
    private float userRotationRad;            // user applied rotation (only in finger-rotation mode)
    private float heading;                    // degrees
    private float pixelHeightM;               // how many chart metres high pixels are
    private float pixelWidthM;                // how many chart metres wide pixels are
    private float speed;                      // metres/second

    private ArrayList<DrawWaypoint> allDrawWaypoints = new ArrayList<> ();
    private AutoAirChart[] autoAirCharts;
    private boolean centerInfoMphOption, centerInfoTrueOption;
    private boolean courseInfoMphOpt, courseInfoTrueOpt;
    private boolean gpsInfoMphOpt;
    private boolean gpsInfoTrueOpt;
    private ChartSelectDialog chartSelectDialog;
    private DecimalFormat scalingFormat = new DecimalFormat ("#.##");
    private DisplayableChart waitingForChartDownload;
    private DisplayableChart selectedChart;
    private float airplaneScale;
    private float canvasHdgRadOverride;
    private float centerInfoAltitude;
    private float centerInfoArrowLat, centerInfoArrowLon;
    private float centerInfoCanvasHdgRads;
    private float centerInfoCenterLat, centerInfoCenterLon;
    private float centerInfoScaling;
    private float courseInfoArrowLat, courseInfoArrowLon;
    private float courseInfoDstLat, courseInfoDstLon;
    private float gpsInfoAltitude;
    private float gpsInfoArrowLat, gpsInfoArrowLon;
    private float gpsInfoSpeed;
    private float lastGPSHeading;
    private float latStart, lonStart;
    private float mappingCanvasHdgRads;
    private float mappingCenterLat;
    private float mappingCenterLon;
    private float mappingPixelHeightM;
    private float mappingPixelWidthM;
    private float rotStart, scaStart;
    private float mouseDownPosX, mouseDownPosY;
    private float smoothSpeed;                 // metres per second
    private float smoothTurnRate;              // degrees per second
    private float[] trackpoints        = new float[tracktotal/trackspace*2];
    private LatLon drawCourseLineISect = new LatLon ();
    private LatLon newCtrLL = new LatLon ();
    private LinkedList<CapGrid> capgrids = new LinkedList<> ();
    private int centerInfoCanvasHeight;
    private int centerInfoLLOption;
    private int courseInfoCanvasWidth;
    private int gpsInfoLLOpt;
    private int mappingCanvasHeight;
    private int mappingCanvasWidth;
    private long downOnCenterInfo   = 0;
    private long downOnChartSelect  = 0;
    private long downOnCourseInfo   = 0;
    private long downOnGPSInfo      = 0;
    private long lastGPSTimestamp   = 0;
    private long timestamp          = 0;
    private long waypointOpenAt     = Long.MAX_VALUE;
    private Paint airplanePaint     = new Paint ();
    private Paint capGridBGPaint    = new Paint ();
    private Paint capGridLnPaint    = new Paint ();
    private Paint capGridTxPaint    = new Paint ();
    private Paint centerBGPaint     = new Paint ();
    private Paint centerLnPaint     = new Paint ();
    private Paint centerTxPaint     = new Paint ();
    private Paint chartSelBGPaint   = new Paint ();
    private Paint chartSelTxPaint   = new Paint ();
    private Paint courseBGPaint     = new Paint ();
    private Paint courseLnPaint     = new Paint ();
    private Paint courseTxPaint     = new Paint ();
    private Paint currentBGPaint    = new Paint ();
    private Paint currentLnPaint    = new Paint ();
    private Paint currentTxPaint    = new Paint ();
    private Paint topoPaint         = new Paint ();
    private Paint trailPaint        = new Paint ();
    private Paint wayptBGPaint      = new Paint ();
    private Paint[] faaWPPaints     = new Paint[] { new Paint (), new Paint () };
    private Paint[] userWPPaints    = new Paint[] { new Paint (), new Paint () };
    private Path airplanePath       = new Path ();
    private Path centerInfoPath     = new Path ();
    private Path chartSelectPath    = new Path ();
    private Path courseInfoPath     = new Path ();
    private Path gpsInfoPath;
    private Path trailPath          = new Path ();
    public  PixelMapper pmap        = new PixelMapper ();
    private Point onDrawPt          = new Point ();
    private Point canpix1           = new Point ();
    private Point canpix2           = new Point ();
    private Point canpix3           = new Point ();
    private Point drawCourseFilletCom = new Point ();
    private Point drawCourseFilletFr  = new Point ();
    private Point drawCourseFilletTo  = new Point ();
    private Pointer firstPointer;
    private Pointer secondPointer;
    private Pointer transPointer    = new Pointer ();
    private PointF drawCourseFilletCp = new PointF ();
    private Rect centerInfoBounds   = new Rect ();
    private Rect chartSelectBounds  = new Rect ();
    private Rect courseInfoBounds   = new Rect ();
    private Rect drawBoundedStringBounds = new Rect ();
    private Rect gpsInfoBounds      = new Rect ();
    private RectF drawCourseFilletOval = new RectF ();
    private StreetChart streetChart;
    private String centerInfoDistStr, centerInfoMCToStr;
    private String centerInfoLatStr, centerInfoLonStr;
    private String centerInfoRotStr, centerInfoScaStr;
    private String courseInfoDistStr, courseInfoMCToStr, courseInfoEteStr;
    private String gpsInfoAltStr;
    private String gpsInfoLatStr;
    private String gpsInfoLonStr;
    private String gpsInfoHdgStr;
    private String gpsInfoSpdStr;
    public  WairToNow wairToNow;
    private Waypoint.Within waypointsWithin = new Waypoint.Within ();

    private boolean holdPosition;          // gps updates centering screen are blocked (eg, when screen has been panned)
    public  float arrowLat, arrowLon;      // degrees
    public  float centerLat, centerLon;    // lat/lon at center+displaceX,Y of canvas
    public  float orgLat, orgLon;          // course origination lat/lon
    public  float scaling = 1.0F;          // < 1 : zoomed out; > 1 : zoomed in
    public  Waypoint clDest = null;        // course line destination name

    public ChartView (WairToNow na)
        throws IOException
    {
        super (na);

        wairToNow = na;
        float ts = wairToNow.textSize;

        UnSetCanvasHdgRad ();

        autoAirCharts = new AutoAirChart[] {
            new AutoAirChart (wairToNow, "ENR"),
            new AutoAirChart (wairToNow, "SEC"),
            new AutoAirChart (wairToNow, "WAC")
        };

        // airplane icon pointing up with center at (0,0)
        int acy = 181;
        airplanePath.moveTo (   0, 313 - acy);
        airplanePath.lineTo ( -44, 326 - acy);
        airplanePath.lineTo ( -42, 301 - acy);
        airplanePath.lineTo ( -15, 281 - acy);
        airplanePath.lineTo ( -18, 216 - acy);
        airplanePath.lineTo (-138, 255 - acy);
        airplanePath.lineTo (-138, 219 - acy);
        airplanePath.lineTo ( -17, 150 - acy);
        airplanePath.lineTo ( -17,  69 - acy);
        airplanePath.cubicTo (  0,  39 - acy,
                                0,  39 - acy,
                              +17,  69 - acy);
        airplanePath.lineTo ( +17, 150 - acy);
        airplanePath.lineTo (+138, 219 - acy);
        airplanePath.lineTo (+138, 255 - acy);
        airplanePath.lineTo ( +18, 216 - acy);
        airplanePath.lineTo ( +15, 281 - acy);
        airplanePath.lineTo ( +42, 301 - acy);
        airplanePath.lineTo ( +44, 326 - acy);
        airplanePath.lineTo (   0, 313 - acy);
        airplaneScale = ts * 1.5F / (313 - 69);

        airplanePaint.setColor (currentColor);
        airplanePaint.setStrokeWidth (10);
        airplanePaint.setTextAlign (Paint.Align.CENTER);

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

        centerBGPaint.setColor (Color.WHITE);
        centerBGPaint.setStyle (Paint.Style.STROKE);
        centerBGPaint.setStrokeWidth (30);
        centerBGPaint.setTextSize (ts);
        centerBGPaint.setTextAlign (Paint.Align.CENTER);
        centerLnPaint.setColor (centerColor);
        centerLnPaint.setStyle (Paint.Style.STROKE);
        centerLnPaint.setStrokeWidth (10);
        centerTxPaint.setColor (centerColor);
        centerTxPaint.setStyle (Paint.Style.FILL);
        centerTxPaint.setStrokeWidth (2);
        centerTxPaint.setTextSize (ts);
        centerTxPaint.setTextAlign (Paint.Align.CENTER);

        chartSelBGPaint.setColor (Color.WHITE);
        chartSelBGPaint.setStyle (Paint.Style.STROKE);
        chartSelBGPaint.setStrokeWidth (30);
        chartSelBGPaint.setTextSize (ts);
        chartSelBGPaint.setTextAlign (Paint.Align.CENTER);
        chartSelTxPaint.setColor (selectColor);
        chartSelTxPaint.setStyle (Paint.Style.FILL);
        chartSelTxPaint.setStrokeWidth (2);
        chartSelTxPaint.setTextSize (ts);
        chartSelTxPaint.setTextAlign (Paint.Align.CENTER);

        courseBGPaint.setColor (Color.WHITE);
        courseBGPaint.setStyle (Paint.Style.STROKE);
        courseBGPaint.setStrokeWidth (30);
        courseBGPaint.setTextSize (ts);
        courseBGPaint.setTextAlign (Paint.Align.CENTER);
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

        topoPaint.setColor (Color.BLUE);
        topoPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        topoPaint.setStrokeWidth (5);
        topoPaint.setTextSize (ts);

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

        AssetManager assetManager = na.getAssets ();
        BufferedReader caprdr = new BufferedReader (new InputStreamReader (assetManager.open ("capgrid.dat")), 1024);
        String caprec;
        while ((caprec = caprdr.readLine ()) != null) {
            CapGrid capgrid = new CapGrid (caprec);
            capgrids.addLast (capgrid);
        }
        caprdr.close ();

        /*
         * The street charts theoretically cover the whole world,
         * so there is just one chart object.
         */
        streetChart = new StreetChart (wairToNow);

        /*
         * Used to display menu that selects which chart to display.
         */
        chartSelectDialog = new ChartSelectDialog ();
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Chart";
    }

    /**
     * The GPS has a new lat/lon and we want to put the arrow at that point.
     * Also, if we aren't displaced by the mouse, move that point to center screen.
     */
    public void SetGPSLocation ()
    {
        lastGPSTimestamp = timestamp;
        lastGPSHeading   = heading;

        timestamp = wairToNow.currentGPSTime;
        altitude  = wairToNow.currentGPSAlt;
        arrowLat  = wairToNow.currentGPSLat;
        arrowLon  = wairToNow.currentGPSLon;
        heading   = wairToNow.currentGPSHdg;
        speed     = wairToNow.currentGPSSpd;

        if (!holdPosition) {
            centerLat = arrowLat;
            centerLon = arrowLon;
        }

        if (reselectLastChart) {
            reselectLastChart = false;
            SharedPreferences prefs = wairToNow.getPreferences (Activity.MODE_PRIVATE);
            String spacenamesansrev = prefs.getString ("selectedChart", null);
            if (spacenamesansrev != null) {
                TreeMap<String,DisplayableChart> charts = new TreeMap<> ();
                charts.put (streetChart.GetSpacenameSansRev (), streetChart);
                for (AutoAirChart aac : autoAirCharts) charts.put (aac.GetSpacenameSansRev (), aac);
                for (Iterator<AirChart> it = wairToNow.maintView.GetAirChartIterator (); it.hasNext ();) {
                    AirChart ac = it.next ();
                    charts.put (ac.GetSpacenameSansRev (), ac);
                }
                DisplayableChart dc = charts.get (spacenamesansrev);
                if ((dc != null) && dc.IsDownloaded ()) {
                    selectedChart = dc;
                    invalidate ();
                }
            }
        }
    }

    /**
     * Set the center point of display
     * @param lat = latitude (degrees)
     * @param lon = longitude (degrees)
     */
    public void SetCenterLatLon (float lat, float lon)
    {
        centerLat    = lat;   // put this point in center of display
        centerLon    = Lib.NormalLon (lon);
        holdPosition = true;  // don't re-center on GPS updates (until ReCenter() is called)

        invalidate ();
    }

    /**
     * Re-center the arrow at the center of the screen.
     */
    public void ReCenter ()
    {
        centerLat    = arrowLat;
        centerLon    = arrowLon;
        holdPosition = false;

        invalidate ();
    }

    /**
     * Set course line to be drawn on charts.
     * Use dest=null to disable.
     * @param oLat = origination latitude
     * @param oLon = origination longitude
     * @param dest = destination
     */
    public void SetCourseLine (float oLat, float oLon, Waypoint dest)
    {
        if ((orgLat != oLat) || (orgLon != oLon) || (clDest != dest)) {
            orgLat = oLat;
            orgLon = Lib.NormalLon (oLon);
            clDest = dest;
        }
    }

    /**
     * See how much to rotate chart by.
     */
    public float GetCanvasHdgRads ()
    {
        if (canvasHdgRadOverride != -99999.0F) return canvasHdgRadOverride;

        switch (wairToNow.optionsView.chartTrackOption.getVal ()) {

            // Course Up : rotate charts counter-clockwise by the heading along the route
            // that is adjacent to the current position.  If no route, use track-up mode.
            case OptionsView.CTO_COURSEUP: {
                if (clDest != null) {
                    float courseUpRad = Mathf.toRadians (Lib.GCOnCourseHdg (orgLat, orgLon,
                            clDest.lat, clDest.lon, arrowLat, arrowLon));
                    userRotationRad = Math.round (courseUpRad / rotatestep) * rotatestep;
                    return courseUpRad;
                }
                // fall through
            }

            // Track up : rotate charts counter-clockwise by the current heading
            case OptionsView.CTO_TRACKUP: {
                float trackUpRad = Mathf.toRadians (heading);
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
     * What to show when the back button is pressed
     */
    @Override  // WairToNow.CanBeMainView
    public View GetBackPage ()
    {
        return this;
    }

    /**
     * This screen is about to become active so let the user specify orientation.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        //noinspection ResourceType
        wairToNow.setRequestedOrientation (wairToNow.optionsView.chartOrientOption.getVal ());
        if (wairToNow.optionsView.powerLockOption.checkBox.isChecked ()) {
            wairToNow.getWindow ().addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * This screen is no longer current so close bitmaps to conserve memory.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        wairToNow.getWindow ().clearFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        for (Iterator<AirChart> it = wairToNow.maintView.GetAirChartIterator (); it.hasNext ();) {
            it.next ().CloseBitmaps ();
        }
        for (AutoAirChart aac : autoAirCharts) aac.CloseBitmaps ();
        streetChart.CloseBitmaps ();
    }

    /**
     * Chart tab re-clicked when already active.
     * Show selection dialog.
     */
    @Override  // CanBeMainView
    public void ReClicked ()
    {
        chartSelectDialog.Show ();
    }

    /**
     * Callback for mouse events on the image.
     * We use this for scrolling the map around.
     */
    @Override
    public boolean onTouchEvent (@NonNull MotionEvent event)
    {
        // we might get divide-by-zero if we don't know canvas size yet
        if ((pmap.canvasWidth == 0) || (pmap.canvasHeight == 0)) return false;

        /*
        int actionInt = event.getActionMasked ();
        String actionName = Integer.toString (actionInt);
        if (actionInt == MotionEvent.ACTION_CANCEL) actionName = "CANCEL";
        if (actionInt == MotionEvent.ACTION_DOWN) actionName = "DOWN";
        if (actionInt == MotionEvent.ACTION_MOVE) actionName = "MOVE";
        if (actionInt == MotionEvent.ACTION_OUTSIDE) actionName = "OUTSIDE";
        if (actionInt == MotionEvent.ACTION_POINTER_DOWN) actionName = "POINTER_DOWN";
        if (actionInt == MotionEvent.ACTION_POINTER_UP) actionName = "POINTER_UP";
        if (actionInt == MotionEvent.ACTION_UP) actionName = "UP";

        int pointerCount = event.getPointerCount ();
        Log.d (TAG, "onTouchEvent*: action=" + actionName + " pointerCount=" + pointerCount + " actionIndex=" + event.getActionIndex ());
        for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex ++) {
            int pointerId = event.getPointerId (pointerIndex);
            float p = event.getPressure (pointerIndex);
            float x = event.getX (pointerIndex);
            float y = event.getY (pointerIndex);
            Log.d (TAG, "onTouchEvent*: " + pointerId + ": p=" + p + " " + " x=" + x + " y=" + y);
        }
        */
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
            case MotionEvent.ACTION_UP: {
                MouseUp ();
                // fall through
            }
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
                    holdPosition = true;

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
        latStart = centerLat;
        lonStart = centerLon;
        rotStart = GetCanvasHdgRads ();
        scaStart = scaling;

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
        centerLat       = latStart;
        centerLon       = lonStart;
        userRotationRad = rotStart;
        scaling         = scaStart;
        MapLatLonsToCanvasPixels (pmap.canvasWidth, pmap.canvasHeight);

        // figure out where new center point is in the old canvas
        float newCtrX = pmap.canvasWidth  / 2;
        float newCtrY = pmap.canvasHeight / 2;
        float oldCtrX = fc * newCtrX + fs * newCtrY + tx;
        float oldCtrY = fc * newCtrY - fs * newCtrX + ty;

        // compute the lat/lon for that point using the old canvas transform
        pmap.CanPix2LatLonAprox (oldCtrX, oldCtrY, newCtrLL);

        // that lat/lon will be at center of new canvas
        centerLat = newCtrLL.lat;
        centerLon = newCtrLL.lon;

        // rotate chart by this much counterclockwise
        float rotInt = rotStart - r;
        while (rotInt < -Mathf.PI) rotInt += Mathf.PI * 2.0F;
        while (rotInt >= Mathf.PI) rotInt -= Mathf.PI * 2.0F;
        if (enablesteps) rotInt = Math.round (rotInt / rotatestep) * rotatestep;
        userRotationRad = rotInt;

        // zoom out on chart by this much
        scaling = scaStart / f;
        int m = scalesteps.length - 1;
        if (enablesteps) {
            while (m > 0) {
                float a = scalesteps[m-1];
                float b = scalesteps[m];
                float e = scaling / a;
                float g = b / scaling;
                if (e > g) break;  // scaling closer to scalesteps[m] than scalesteps[m-1]
                -- m;
            }
            scaling = scalesteps[m];
        } else {
            if (scaling < scalesteps[0]) scaling = scalesteps[0];
            if (scaling > scalesteps[m]) scaling = scalesteps[m];
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

        if (centerInfoBounds.contains ((int)x, (int)y)) {
            downOnCenterInfo = now;
            return;
        }
        if (chartSelectBounds.contains ((int)x, (int)y)) {
            downOnChartSelect = now;
            return;
        }
        if (courseInfoBounds.contains ((int)x, (int)y)) {
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
            return;
        }
        if (gpsInfoBounds.contains ((int)x, (int)y)) {
            downOnGPSInfo = now;
            return;
        }

        // none of the above so set up to detect long click on a nearby waypoint
        waypointOpenAt = now + waypointopentime;
        WairToNow.wtnHandler.runDelayed (waypointopentime, new Runnable () {
            @Override
            public void run ()
            {
                if (System.currentTimeMillis () >= waypointOpenAt) {
                    waypointOpenAt = Long.MAX_VALUE;
                    LatLon ll = new LatLon ();
                    CanPix2LatLonExact (mouseDownPosX, mouseDownPosY, ll);
                    wairToNow.waypointView1.OpenWaypointAtLatLon (ll.lat, ll.lon);
                }
            }
        });
    }

    private void MouseUp ()
    {
        long now = System.currentTimeMillis ();
        showCenterInfo   ^= (now - downOnCenterInfo < 500);
        showCourseInfo   ^= (now - downOnCourseInfo < 500);
        showGPSInfo      ^= (now - downOnGPSInfo    < 500);
        if (now - downOnChartSelect < 500) {
            chartSelectDialog.Show ();
        }
        downOnCenterInfo  = 0;
        downOnChartSelect = 0;
        downOnCourseInfo  = 0;
        downOnGPSInfo     = 0;
        waypointOpenAt    = Long.MAX_VALUE;
        invalidate ();
    }

    /**
     * Long click on course info clears destination fix and course line.
     */
    private void ConfirmClearCourse ()
    {
        if (clDest != null) {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Confirm clear destination");
            adb.setMessage (clDest.ident);
            adb.setPositiveButton ("Clear it", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    wairToNow.routeView.ShutTrackingOff ();
                    clDest = null;
                    invalidate ();
                }
            });
            adb.setNegativeButton ("Keep it", null);
            adb.show ();
        }
    }

    /**
     * Callback to draw the tiles in position to fill the screen.
     */
    @Override
    public void onDraw (Canvas canvas)
    {
        DrawChart (canvas, getWidth (), getHeight ());

        /*
         * Draw course destination text.
         */
        if (clDest != null) {
            DrawCourseInfo (canvas, courseBGPaint);
            DrawCourseInfo (canvas, courseTxPaint);
        }

        /*
         * Draw GPS (current position) info text.
         */
        if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
            DrawGPSInfo (canvas, currentBGPaint);
            DrawGPSInfo (canvas, currentTxPaint);
        }

        /*
         * Draw chart selection button.
         */
        DrawChartSelect (canvas, chartSelBGPaint);
        DrawChartSelect (canvas, chartSelTxPaint);

        /*
         * Draw circles around spot where screen is being touched.
         */
        if (firstPointer != null) {
            float r = Mathf.sqrt (metrics.xdpi * metrics.ydpi) * 0.25F;
            canvas.drawCircle (firstPointer.lx, firstPointer.ly, r, currentLnPaint);
        }
        if (secondPointer != null) {
            float r = Mathf.sqrt (metrics.xdpi * metrics.ydpi) * 0.25F;
            canvas.drawCircle (secondPointer.lx, secondPointer.ly, r, currentLnPaint);
        }

        /*
         * Draw box around perimeter warning that GPS is not available.
         */
        if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
            wairToNow.drawGPSAvailable (canvas, this);
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

        if (selectedChart != null) {

            /*
             * Draw whatever chart was selected by user.
             */
            DrawSelectedChart (canvas);

            /*
             * Draw center position text.
             */
            DrawCenterInfo (canvas, centerBGPaint);
            DrawCenterInfo (canvas, centerTxPaint);
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
            airplanePaint.setStyle (Paint.Style.FILL);
            canvas.save ();
            canvas.translate (cw / 2, ch / 2);              // anything drawn below will be translated this much
            canvas.scale (airplaneScale, airplaneScale);    // anything drawn below will be scaled this much
            canvas.drawPath (airplanePath, airplanePaint);  // draw the airplane with vectors and filling
            canvas.restore ();                              // remove translation/scaling/rotation
        }
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
            selectedChart.DrawOnCanvas (pmap, canvas, this, GetCanvasHdgRads ());
        } catch (OutOfMemoryError oome) {
            // sometimes fast panning gives bitmap oom errors
            // force all bitmaps closed, garbage collect and give up for now
            CloseDisplay ();
            System.gc ();
            canvas.drawText ("out of memory error", pmap.canvasWidth / 2, pmap.canvasHeight * 3/4, courseTxPaint);
        }

        if (wairToNow.optionsView.topoOption.checkBox.isChecked ()) {
            DrawTopography (canvas);
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
            float dy = metrics.xdpi / Lib.MMPerIn;
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
                    pmap.canvasSouthLat, pmap.canvasNorthLat, pmap.canvasWestLon, pmap.canvasEastLon)) {
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
        if (showCenterInfo && holdPosition) {
            LatLon2CanPixExact (centerLat, centerLon, pt);
            float len = wairToNow.textSize * 1.5F;
            canvas.drawLine (pt.x - len, pt.y, pt.x + len, pt.y, centerLnPaint);
            canvas.drawLine (pt.x, pt.y - len, pt.x, pt.y + len, centerLnPaint);
        }

        /*
         * Draw course line if defined and enabled.
         */
        if ((clDest != null) && showCourseInfo) {
            DrawCourseLine (canvas);
        }

        /*
         * Draw an airplane icon where the GPS (current) lat/lon point is.
         */
        if (!wairToNow.optionsView.typeBOption.checkBox.isChecked () && LatLon2CanPixExact (arrowLat, arrowLon, pt)) {
            DrawLocationArrow (canvas, pt, GetCanvasHdgRads (), pixelWidthM, pixelHeightM);
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
        // draw solid line from planned starting point to current destination waypoint
        DrawCourseLine (canvas, orgLat, orgLon, clDest.lat, clDest.lon, true);

        // we might still be in the middle of turning from a previous route segment onto the current
        // if so, draw a fillet for the remainder of the turn
        LatLon isect = drawCourseLineISect;
        if (speed > WairToNow.gpsMinSpeedMPS) {

            // see how far away we are from the current course line and don't bother drawing fillet if close
            float offcourse = Lib.GCOffCourseDist (orgLat, orgLon, clDest.lat, clDest.lon, arrowLat, arrowLon);
            if (Math.abs (offcourse) > 0.1F / scaling) {

                // find point that the current heading intersects the course line at
                // and make sure that the intersection point is ahead of us on the course line
                if (Lib.GCIntersect (orgLat, orgLon, clDest.lat, clDest.lon, arrowLat, arrowLon, heading, isect)) {

                    // and make sure that intersection is not beyond end of the current segment
                    float distFromCurrToISect  = Lib.LatLonDist (arrowLat, arrowLon, isect.lat, isect.lon);
                    float distFromCurrToSegEnd = Lib.LatLonDist (arrowLat, arrowLon, clDest.lat, clDest.lon);
                    if (distFromCurrToISect < distFromCurrToSegEnd) {

                        // draw fillet to finish the turn that gets us on to the current course line
                        DrawCourseFillet (canvas, arrowLat, arrowLon, isect.lat, isect.lon, clDest.lat, clDest.lon);
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
                Waypoint lastwp = clDest;
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
            float radiusmet = speed / Mathf.toRadians (GlassView.STDRATETURN);
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
     * Draw the airplane location arrow symbol.
     * @param canvas     = canvas to draw it on
     * @param pt         = where on canvas to draw symbol
     * @param canHdgRads = true heading on canvas that is up
     * @param mPerXPix   = real-world metres per canvas X pixel
     * @param mPerYPix   = real-world metres per canvas Y pixel
     * Other inputs:
     *   heading = true heading for the arrow symbol (degrees, latest gps sample)
     *   speed = airplane speed (mps, latest gps sample)
     *   airplanePath,Scaling = drawing of airplane pointing at 0deg true
     *   thisGPSTimestamp = time of latest gps sample
     *   lastGPSTimestamp = time of previous gps sample
     *   lastGPSHeading = true heading for the arrow symbol (previous gps sample)
     */
    public void DrawLocationArrow (Canvas canvas, Point pt, float canHdgRads, float mPerXPix, float mPerYPix)
    {
        /*
         * If not receiving GPS signal, blink the icon.
         */
        if (wairToNow.blinkingRedOn ()) return;

        /*
         * If not moving, draw circle instead of airplane.
         */
        if (speed < WairToNow.gpsMinSpeedMPS) {
            airplanePaint.setStyle (Paint.Style.STROKE);
            canvas.drawCircle (pt.x, pt.y, wairToNow.textSize * 0.5F, airplanePaint);
            return;
        }

        /*
         * Heading angle relative to UP on screen.
         */
        float hdg = heading - Mathf.toDegrees (canHdgRads);

        /*
         * Draw the icon.
         */
        airplanePaint.setStyle (Paint.Style.FILL);
        canvas.save ();
        canvas.translate (pt.x, pt.y);                  // anything drawn below will be translated this much
        canvas.scale (airplaneScale, airplaneScale);    // anything drawn below will be scaled this much
        canvas.rotate (hdg);                            // anything drawn below will be rotated this much
        canvas.drawPath (airplanePath, airplanePaint);  // draw the airplane with vectors and filling
        canvas.restore ();                              // remove translation/scaling/rotation

        /*
         * If we have two fairly recent GPS samples, draw track forward for next 60 seconds.
         */
        long gpsTimeDiff = timestamp - lastGPSTimestamp;
        if ((mPerXPix > 0) && (mPerYPix > 0) && (gpsTimeDiff > 100) && (gpsTimeDiff < 10000)) {

            // smoothed metres per second
            smoothSpeed = smoothSpeed * 3 / 4 + speed * 1 / 4;

            // current turning rate (degrees per second)
            // - positive if turning right
            // - negative if turning left
            float hdgDiff = heading - lastGPSHeading;
            while (hdgDiff < -180.0F) hdgDiff += 360.0F;
            while (hdgDiff >= 180.0F) hdgDiff -= 360.0F;
            float rateOfTurn = hdgDiff * 1000 / gpsTimeDiff;
            smoothTurnRate = smoothTurnRate * 3 / 4 + rateOfTurn * 1 / 4;

            // components of heading of icon drawn on chart
            float hdgcos = Mathf.cosdeg (hdg);
            float hdgsin = Mathf.sindeg (hdg);

            // low turn rate means we are going straight ahead
            // (to avoid divide-by-zero errors)
            if ((smoothTurnRate > -0.125) && (smoothTurnRate < 0.125)) {
                float dx = smoothSpeed * trackspace * hdgsin / mPerXPix;
                float dy = smoothSpeed * trackspace * hdgcos / mPerYPix;
                float x  = pt.x;
                float y  = pt.y;
                for (int i = trackpoints.length; i > 0;) {
                    y -= dy;
                    x += dx;
                    trackpoints[--i] = y;
                    trackpoints[--i] = x;
                }
            } else {

                // radius of current turn (real-world metres)
                // positive means turning right
                // negative means turning left
                // - if we turn 90 deg, length = PI/2*radius
                //   so in general, length = turn * radius
                //               or, speed = turnrate * radius
                //            thus, radius = speed / turnrate
                float turnRadiusM = smoothSpeed / (smoothTurnRate / 180 * Mathf.PI);

                // center of turn in pixels
                float turnCenterXPix = turnRadiusM * hdgcos / mPerXPix + pt.x;
                float turnCenterYPix = turnRadiusM * hdgsin / mPerYPix + pt.y;

                // draw a dot every trackspace seconds along the path
                for (int i = trackpoints.length; i > 0;) {
                    hdg += smoothTurnRate * trackspace;
                    trackpoints[--i] = turnCenterYPix - turnRadiusM / mPerYPix * Mathf.sindeg (hdg);
                    trackpoints[--i] = turnCenterXPix - turnRadiusM / mPerXPix * Mathf.cosdeg (hdg);
                }
            }

            canvas.drawPoints (trackpoints, 0, trackpoints.length, currentLnPaint);
        }
    }

    /**
     * Draw elevation every 10mins on chart.
     */
    private void DrawTopography (Canvas canvas)
    {
        final float spacing = 10.F/60.0F;
        int ilatmin = Math.round (pmap.canvasSouthLat / spacing);
        int ilatmax = Math.round (pmap.canvasNorthLat / spacing);
        int ilonmin = Math.round (pmap.canvasWestLon  / spacing);
        int ilonmax = Math.round (pmap.canvasEastLon  / spacing);
        if (ilonmax < ilonmin) ilonmax += Math.round (360.0F / spacing);
        for (int ilat = ilatmin; ilat <= ilatmax; ilat ++) {
            for (int ilon = ilonmin; ilon <= ilonmax; ilon ++) {
                float lat = ilat * spacing;
                float lon = ilon * spacing;
                if (LatLon2CanPixExact (lat, lon, canpix1)) {
                    short elev = Topography.getElevMetres (lat, lon);
                    if (elev != Topography.INVALID_ELEV) {
                        String elevstr = Integer.toString (Math.round (elev * Lib.FtPerM));
                        canvas.drawCircle (canpix1.x, canpix1.y, 5, topoPaint);
                        canvas.drawText (elevstr, canpix1.x + 10, canpix1.y - 10, topoPaint);
                    }
                }
            }
        }
    }

    /**
     * Draw CAP grid lines and numbers.
     */
    private void DrawCapGrid (Canvas canvas)
    {
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
        /*
         * Read display metrics each time in case of orientation change.
         */
        wairToNow.getWindowManager ().getDefaultDisplay ().getMetrics (metrics);

        /*
         * Compute how many charted metres fit in a pixel.
         */
        pixelWidthM  = Lib.MMPerIn / 1000 / metrics.xdpi / scaling * scalebase;
        pixelHeightM = Lib.MMPerIn / 1000 / metrics.ydpi / scaling * scalebase;

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
        pmap.setup (cw, ch, tlLat, tlLon, trLat, trLon, blLat, blLon, brLat, brLon);
    }

    /**
     * Use currently selected chart projection to compute exact canvas pixel for a given lat/lon.
     * If no chart selected, use linear interpolation from the four corners of the screen.
     */
    public boolean LatLon2CanPixExact (float lat, float lon, Point canpix)
    {
        if ((selectedChart == null) || !selectedChart.LatLon2CanPixExact (lat, lon, canpix)) {
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
        if ((selectedChart == null) || !selectedChart.CanPix2LatLonExact (canvasPixX, canvasPixY, ll)) {
            pmap.CanPix2LatLonAprox (canvasPixX, canvasPixY, ll);
        }
    }

    /**
     * Draw Center Point info in lower left corner of canvas.
     */
    private void DrawCenterInfo (Canvas canvas, Paint paint)
    {
        if (showCenterInfo) {
            int cx = (int)(paint.getTextSize () * 3.125);
            int dy = (int)paint.getFontSpacing ();
            int by = pmap.canvasHeight - 10;

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
                if ((centerInfoArrowLat != arrowLat) ||
                    (centerInfoArrowLon != arrowLon) ||
                    centerLLChanged ||
                    optionsChanged) {
                    centerInfoArrowLat = arrowLat;
                    centerInfoArrowLon = arrowLon;
                    float distFromGPS = Lib.LatLonDist (arrowLat, arrowLon, centerLat, centerLon);
                    float tcorFromGPS = Lib.LatLonTC (arrowLat, arrowLon, centerLat, centerLon);
                    centerInfoDistStr = Lib.DistString (distFromGPS, mphOption);
                    centerInfoMCToStr = wairToNow.optionsView.HdgString (tcorFromGPS, arrowLat, arrowLon, altitude);
                }
                DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 6, centerInfoDistStr);
                DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 5, centerInfoMCToStr);
            }
            if (holdPosition || !showGPSInfo) {

                // display center lat/lon iff different than airplane lat/lon or airplane lat/lon not being shown
                if (centerLLChanged) {
                    centerInfoLatStr = wairToNow.optionsView.LatLonString (centerLat, 'N', 'S');
                    centerInfoLonStr = wairToNow.optionsView.LatLonString (centerLon, 'E', 'W');
                }
                DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 4, centerInfoLatStr);
                DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 3, centerInfoLonStr);
            }

            // always display scaling and rotation
            float chr = GetCanvasHdgRads ();
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
            DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 2, centerInfoScaStr);
            DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy,     centerInfoRotStr);
        } else {

            // user doesn't want any info shown, draw a little button instead
            float ts = wairToNow.textSize;
            int   ch = pmap.canvasHeight;
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
        if (showCourseInfo) {
            boolean mphOpt  = wairToNow.optionsView.ktsMphOption.getAlt ();
            boolean trueOpt = wairToNow.optionsView.magTrueOption.getAlt ();
            if ((courseInfoArrowLat != arrowLat)   ||
                (courseInfoArrowLon != arrowLon)   ||
                (courseInfoDstLat   != clDest.lat) ||
                (courseInfoDstLon   != clDest.lon) ||
                (courseInfoMphOpt   != mphOpt)     ||
                (courseInfoTrueOpt  != trueOpt)) {
                courseInfoArrowLat = arrowLat;
                courseInfoArrowLon = arrowLon;
                courseInfoDstLat   = clDest.lat;
                courseInfoDstLon   = clDest.lon;
                courseInfoMphOpt   = mphOpt;
                courseInfoTrueOpt  = trueOpt;

                float dist = Lib.LatLonDist (arrowLat, arrowLon, courseInfoDstLat, courseInfoDstLon);
                courseInfoDistStr = Lib.DistString (dist, mphOpt);

                float tcto = Lib.LatLonTC (arrowLat, arrowLon, courseInfoDstLat, courseInfoDstLon);
                courseInfoMCToStr = wairToNow.optionsView.HdgString (tcto, arrowLat, arrowLon, altitude);

                courseInfoEteStr = "";
                if (speed >= WairToNow.gpsMinSpeedMPS) {
                    int etesec = (int) (dist * Lib.MPerNM / speed + 0.5);
                    int etemin = etesec / 60;
                    int etehrs = etemin / 60;
                    etesec %= 60;
                    etemin %= 60;
                    courseInfoEteStr = Integer.toString (etehrs) + ":" + Integer.toString (etemin + 100).substring (1)
                            + ":" + Integer.toString (etesec + 100).substring (1);
                }
            }

            int cx = pmap.canvasWidth - (int)(paint.getTextSize () * 3);
            int dy = (int)paint.getFontSpacing ();

            courseInfoBounds.setEmpty ();
            DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy,     clDest.ident);
            DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 2, courseInfoDistStr);
            DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 3, courseInfoMCToStr);
            DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 4, courseInfoEteStr);
        } else {
            float ts = wairToNow.textSize;
            int   cw = pmap.canvasWidth;

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
     * Draw GPS info text in upper left corner of canvas.
     */
    private void DrawGPSInfo (Canvas canvas, Paint paint)
    {
        if (showGPSInfo) {
            int     llOpt   = wairToNow.optionsView.latLonOption.getVal ();
            boolean mphOpt  = wairToNow.optionsView.ktsMphOption.getAlt ();
            boolean trueOpt = wairToNow.optionsView.magTrueOption.getAlt ();

            if ((gpsInfoLLOpt != llOpt) ||
                    (gpsInfoTrueOpt != trueOpt) ||
                    (gpsInfoArrowLat != arrowLat) ||
                    (gpsInfoArrowLon != arrowLon)) {
                gpsInfoLLOpt    = llOpt;
                gpsInfoTrueOpt  = trueOpt;
                gpsInfoArrowLat = arrowLat;
                gpsInfoArrowLon = arrowLon;
                gpsInfoLatStr = wairToNow.optionsView.LatLonString (arrowLat, 'N', 'S');
                gpsInfoLonStr = wairToNow.optionsView.LatLonString (arrowLon, 'E', 'W');
                gpsInfoHdgStr = wairToNow.optionsView.HdgString (heading, arrowLat, arrowLon, altitude);
            }

            if (gpsInfoAltitude != altitude) {
                gpsInfoAltitude = altitude;
                gpsInfoAltStr = Integer.toString ((int)(altitude * Lib.FtPerM)) + " ft";
            }

            if ((gpsInfoMphOpt != mphOpt) || (gpsInfoSpeed != speed)) {
                gpsInfoMphOpt = mphOpt;
                gpsInfoSpeed  = speed;
                gpsInfoSpdStr = Lib.SpeedString (speed * Lib.KtPerMPS, mphOpt);
            }

            int cx = (int)(paint.getTextSize () * 3.125);
            int dy = (int)paint.getFontSpacing ();

            gpsInfoBounds.setEmpty ();
            DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy, gpsInfoLatStr);
            DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 2, gpsInfoLonStr);
            DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 3, gpsInfoAltStr);
            DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 4, gpsInfoHdgStr);
            DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 5, gpsInfoSpdStr);
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

    /**
     * Draw chart select button in lower right corner.
     */
    private void DrawChartSelect (Canvas canvas, Paint paint)
    {
        int cw = pmap.canvasWidth;
        int ch = pmap.canvasHeight;

        /*
         * First time, display words so they know what this is about.
         */
        if (selectedChart == null) {
            chartSelectBounds.setEmpty ();

            int cx = cw - (int)(paint.getTextSize () * 3.125);
            int dy = (int)paint.getFontSpacing ();
            int by = ch - 10;

            DrawBoundedString (canvas, chartSelectBounds, paint, cx, by - dy * 2, "select");
            DrawBoundedString (canvas, chartSelectBounds, paint, cx, by - dy,     "chart");
        } else {

            /*
             * Otherwise, show little arrow button so it doesn't take up so much room.
             */
            if ((chartSelectBounds.right != cw - 1) || (chartSelectBounds.bottom != ch - 1)) {
                float ts = wairToNow.textSize;
                chartSelectBounds.left   = (int) (cw - ts * 2);
                chartSelectBounds.top    = (int) (ch - ts * 2);
                chartSelectBounds.right  = cw - 1;
                chartSelectBounds.bottom = ch - 1;

                chartSelectPath.rewind ();
                chartSelectPath.moveTo (cw - ts, ch - ts * 2);
                chartSelectPath.lineTo (cw - ts * 2, ch - ts * 2);
                chartSelectPath.lineTo (cw - ts * 2, ch - ts);
            }
            canvas.drawPath (chartSelectPath, paint);
        }
    }

    /**
     * Draw a string and keep track of its bounds.
     * @param canvas = canvas to draw string on
     * @param overallBounds = keeps track of bounds of several strings, start with 0,0,0,0
     * @param paint = paint used to draw string
     * @param cx = string's X center
     * @param ty = string's Y top
     * @param st = string
     */
    private void DrawBoundedString (Canvas canvas, Rect overallBounds, Paint paint, int cx, int ty, String st)
    {
        if (st != null) {
            canvas.drawText (st, cx, ty, paint);

            if ((overallBounds.left == 0) && (overallBounds.right == 0)) {
                paint.getTextBounds (st, 0, st.length (), overallBounds);
                overallBounds.offset (cx - overallBounds.width () / 2, ty);
            } else {
                paint.getTextBounds (st, 0, st.length (), drawBoundedStringBounds);
                drawBoundedStringBounds.offset (cx - drawBoundedStringBounds.width () / 2, ty);
                overallBounds.union (drawBoundedStringBounds);
            }
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

    /**
     * Display a list of buttons to select currently displayed chart
     * from list of eligible charts.
     */
    private class ChartSelectDialog implements ListAdapter,
            DialogInterface.OnClickListener {
        private TreeMap<String,DisplayableChart> displayableCharts;
        private View[] chartViews;

        /**
         * Build button list based on what charts cover the current position
         * and display them.
         */
        public void Show ()
        {
            reselectLastChart = false;
            waitingForChartDownload = null;

            /*
             * Find charts that cover each of the corners and edges.
             * Street chart coverts the whole world so it is always downloaded.
             */
            displayableCharts = new TreeMap<> ();
            displayableCharts.put (streetChart.GetSpacenameSansRev (), streetChart);

            boolean[] autoAirChartHits = new boolean[autoAirCharts.length];
            for (Iterator<AirChart> it = wairToNow.maintView.GetAirChartIterator (); it.hasNext ();) {
                AirChart ac = it.next ();
                if (ac.ContributesToCanvas (pmap)) {
                    String spn = ac.GetSpacenameSansRev ();
                    displayableCharts.put (spn, ac);

                    for (int i = 0; i < autoAirCharts.length; i ++) {
                        AutoAirChart aac = autoAirCharts[i];
                        if (!autoAirChartHits[i] && aac.Matches (spn)) {
                            displayableCharts.put (aac.GetSpacenameSansRev (), aac);
                            autoAirChartHits[i] = true;
                        }
                    }
                }
            }

            /*
             * Make a button for each chart found.
             * List them in alphabetical order.
             */
            int ncharts = displayableCharts.size ();
            chartViews = new View[ncharts];
            int i = 0;
            for (String spacename : displayableCharts.keySet ()) {
                DisplayableChart dc = displayableCharts.get (spacename);

                View v = dc.GetMenuSelector (ChartView.this);
                v.setBackgroundColor ((dc == selectedChart) ? Color.DKGRAY : Color.BLACK);
                v.setTag (spacename);

                chartViews[i++] = v;
            }

            /*
             * Build dialog box to select chart from.
             */
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Chart Select");
            if (i == 0) {
                adb.setMessage ("No charts for this location");
                adb.setPositiveButton ("OK", null);
            } else {
                adb.setAdapter (this, this);
                adb.setPositiveButton ("None", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialogInterface, int i)
                    {
                        selectedChart = null;
                        invalidate ();
                    }
                });
                adb.setNegativeButton ("Cancel", null);
            }
            adb.show ();
        }

        /********************************\
         *  ListAdapter implementation  *
        \********************************/

        @Override
        public boolean areAllItemsEnabled ()
        {
            return true;
        }

        @Override
        public boolean isEnabled (int position)
        {
            return true;
        }

        @Override
        public int getCount ()
        {
            return chartViews.length;
        }

        @Override
        public Object getItem (int position)
        {
            return chartViews[position].getTag ();
        }

        @Override
        public long getItemId (int position)
        {
            return position;
        }

        @Override
        public int getItemViewType (int position)
        {
            return 0;
        }

        @Override
        public View getView (int position, View convertView, ViewGroup parent)
        {
            return chartViews[position];
        }

        @Override
        public int getViewTypeCount ()
        {
            return 1;
        }

        @Override
        public boolean hasStableIds ()
        {
            return true;
        }

        @Override
        public boolean isEmpty ()
        {
            return chartViews.length == 0;
        }

        @Override
        public void registerDataSetObserver (DataSetObserver observer)
        { }

        @Override
        public void unregisterDataSetObserver (DataSetObserver observer)
        { }

        /****************************************************\
         *  DialogInterface.OnClickListener implementation  *
        \****************************************************/

        @Override
        public void onClick (DialogInterface dialog, int which)
        {
            View tv = chartViews[which];
            String spacename = (String) tv.getTag ();
            final DisplayableChart dc = displayableCharts.get (spacename);
            if (dc.IsDownloaded ()) {
                SelectChart (dc);
            } else {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Chart Select");
                adb.setMessage ("Download " + spacename + "?");
                adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialog, int which)
                    {
                        waitingForChartDownload = dc;
                        wairToNow.maintView.StartDownloadingChart (dc.GetSpacenameSansRev ());
                    }
                });
                adb.setNegativeButton ("Cancel", null);
                adb.show ();
            }
        }
    }

    /**
     * Download completed, redraw screen with newly downloaded chart.
     */
    public void DownloadComplete ()
    {
        if (waitingForChartDownload != null) {
            if (waitingForChartDownload.IsDownloaded ()) {
                SelectChart (waitingForChartDownload);
            }
            waitingForChartDownload = null;
        }
    }

    /**
     * Select the given chart.  Remember it in case we are restarted so we can reselect it.
     * @param dc = null: show splash screen; else: show given chart if it is in range
     */
    private void SelectChart (DisplayableChart dc)
    {
        if (selectedChart != null) {
            selectedChart.CloseBitmaps ();
        }
        selectedChart = dc;
        selectedChart.UserSelected ();
        invalidate ();
        SharedPreferences prefs = wairToNow.getPreferences (Activity.MODE_PRIVATE);
        SharedPreferences.Editor editr = prefs.edit ();
        editr.putString ("selectedChart", dc.GetSpacenameSansRev ());
        editr.commit ();
    }
}
