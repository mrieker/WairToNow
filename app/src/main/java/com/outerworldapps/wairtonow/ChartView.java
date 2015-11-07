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
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.location.Location;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Display chart and current position and a specified course line.
 */
public class ChartView
        extends View
        implements WairToNow.CanBeMainView {
    private static final int wstep = 256;
    private static final int hstep = 256;
    private static final int overlap = 10;
    private static final int waypointopentime = 1000;
    private final static float rotatestep = Mathf.toRadians (5.0F);  // manual rotation stepping
    private final static float[] scalesteps = new float[] {       // manual scale stepping
        1.00F, 1.25F, 1.50F, 2.00F, 2.50F, 3.00F, 4.00F, 5.00F, 6.00F, 8.00F, 10.0F, 99.0F
    };
    private final static int   tracktotal = 60;        // total number of seconds for tracking ahead
    private final static int   trackspace = 10;        // number of seconds spacing between tracking dots
    public  static final float scalemin   = 0.2F;      // how far to allow zoom out
    public  static final float scalemax   = 100.0F;    // how far to allow zoom in
    private static final float scalebase  = 500000;    // chart scale factor (when scaling=1.0, using 500000
                                                       // makes sectionals appear actual size theoretically)
    private static final float FtPerM     = 3.28084F;  // feet per metre
    private static final float NMPerDeg   = 60.0F;     // naut.mi per degree (lat)

    private static final int capGridColor = Color.MAGENTA;
    private static final int centerColor  = Color.BLUE;
    private static final int courseColor  = Color.rgb (0, 128, 0);
    private static final int currentColor = Color.RED;
    private static final int selectColor  = Color.BLACK;

    private abstract class Chart {
        public String basename;    // chart name, eg, "New York 86"
        public abstract int DownldLinkColor ();
        public abstract void DrawOnCanvas (Canvas canvas);
    }

    private interface LoadedBitmap {
        void CloseBitmap ();
    }

    private static class Pointer {
        public int id;        // event pointer id
        public float lx, ly;  // latest x,y on the canvas
        public float sx, sy;  // starting x,y on the canvas
    }

    private boolean showCenterInfo = true;
    private boolean showCourseInfo = true;
    private boolean showGPSInfo    = true;
    private DisplayMetrics metrics = new DisplayMetrics ();
    private float altitude;                   // metres MSL
    private float canvasEastLon, canvasWestLon;
    private float canvasNorthLat, canvasSouthLat;
    private float canvasHdgRads;              // what heading is UP on the canvas
    private float heading;                    // degrees
    private float pixelHeightM;               // how many chart metres high pixels are
    private float pixelWidthM;                // how many chart metres wide pixels are
    private float speed;                      // metres/second
    private float tlLat, tlLon;               // lat/lon at top left of canvas
    private float trLat, trLon;               // lat/lon at top right of canvas
    private float blLat, blLon;               // lat/lon at bottom left of canvas
    private float brLat, brLon;               // lat/lon at bottom right of canvas

    private AirTileLoader airTileLoader = null;
    private ArrayList<DrawWaypoint> allDrawWaypoints = new ArrayList<> ();
    private AutoAirChart autoSecAirChart = new AutoAirChart ("SEC");
    private AutoAirChart autoWacAirChart = new AutoAirChart ("WAC");
    private Bitmap airplaneBitmap;
    private boolean centerInfoMphOption, centerInfoTrueOption;
    private boolean courseInfoMphOpt, courseInfoTrueOpt;
    private boolean gpsInfoMphOpt;
    private boolean gpsInfoTrueOpt;
    private boolean tabButtonVisibile;
    private Chart selectedChart;
    private ChartSelectView chartSelectView;
    private Collection<Waypoint.Runway> dstRunways;
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
    private float[] airTileCorners     = new float[8];
    private float[] drawOnCanvasPoints = new float[16];
    private float[] trackpoints        = new float[tracktotal/trackspace*2];
    private LatLon newCtrLL = new LatLon ();
    private LatLon[] canvasCornersLatLon = new LatLon[] { new LatLon (), new LatLon (), new LatLon (), new LatLon () };
    private final LinkedList<AirTile> tilesToLoad = new LinkedList<> ();
    private LinkedList<CapGrid> capgrids = new LinkedList<> ();
    private int canvasWidth, canvasHeight;     // last seen canvas width,height
    private int centerInfoCanvasHeight;
    private int centerInfoLLOption;
    private int courseInfoCanvasWidth;
    private int gpsInfoLLOpt;
    private int mappingCanvasHeight;
    private int mappingCanvasWidth;
    private int tabButtonCanvasHeight, tabButtonCanvasWidth;
    private LinkedList<AirChart> allDownloadedAirCharts = new LinkedList<> ();
    private LinkedList<LoadedBitmap>  loadedBitmaps = new LinkedList<> ();
    private long downOnCenterInfo   = 0;
    private long downOnChartSelect  = 0;
    private long downOnCourseInfo   = 0;
    private long downOnGPSInfo      = 0;
    private long downOnTabButton    = 0;
    private long lastGPSTimestamp   = 0;
    private long thisGPSTimestamp   = 0;
    private long viewDrawCycle      = 0;       // incremented each drawing cycle
    private long waypointOpenAt     = Long.MAX_VALUE;
    private Matrix airplaneMatrix;
    private Matrix drawOnCanvasChartMat = new Matrix ();
    private Matrix drawOnCanvasTileMat  = new Matrix ();
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
    private Paint fillBlackPaint    = new Paint ();
    private Paint tabVisButPaint    = new Paint ();
    private Paint trailPaint        = new Paint ();
    private Paint wayptBGPaint      = new Paint ();
    private Paint[] faaWPPaints     = new Paint[] { new Paint (), new Paint () };
    private Paint[] userWPPaints    = new Paint[] { new Paint (), new Paint () };
    private Path centerInfoPath     = new Path ();
    private Path chartSelectPath    = new Path ();
    private Path courseInfoPath     = new Path ();
    private Path drawOnCanvasPath   = new Path ();
    private Path fillPath           = new Path ();
    private Path gpsInfoPath;
    private Path tabButtonPath      = new Path ();
    private Path trailPath          = new Path ();
    private PixelMapper pmap        = new PixelMapper ();
    private Point onDrawPt          = new Point ();
    private Point canpix1           = new Point ();
    private Point canpix2           = new Point ();
    private Point canpix3           = new Point ();
    private Point drawOnCanvasPoint = new Point ();
    private PointF onDrawPtD        = new PointF ();
    private Pointer firstPointer;
    private Pointer secondPointer;
    private Pointer transPointer    = new Pointer ();
    private Rect canvasBounds       = new Rect (0, 0, 0, 0);
    private Rect centerInfoBounds   = new Rect ();
    private Rect chartSelectBounds  = new Rect ();
    private Rect courseInfoBounds   = new Rect ();
    private Rect drawBoundedStringBounds = new Rect ();
    private Rect gpsInfoBounds      = new Rect ();
    private RectF tabButtonBounds   = new RectF ();
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
    private WairToNow wairToNow;
    private Waypoint.Within waypointsWithin = new Waypoint.Within ();

    private boolean holdPosition;          // gps updates centering screen are blocked (eg, when screen has been panned)
    private float arrowLat, arrowLon;      // degrees
    public  float centerLat, centerLon;    // lat/lon at center+displaceX,Y of canvas
    public  float dstLat, dstLon;          // course destination lat/lon
    public  float orgLat, orgLon;          // course origination lat/lon
    public  float scaling = 1.0F;          // 0.5 = can see 2x as much as with 1.0, ie, 4 chart pixels -> 1 canvas pixel
    public  String clName = null;          // course line destination name

    public ChartView (WairToNow na)
        throws IOException
    {
        super (na);

        wairToNow = na;

        airplaneMatrix = new Matrix ();
        Bitmap abm = BitmapFactory.decodeResource (na.getResources (), R.drawable.airplane);
        int abmw = abm.getWidth ();
        int abmh = abm.getHeight ();
        float abmwhalf = abmw / 2.0F;
        float abmhhalf = abmh / 2.0F;
        airplaneBitmap = Bitmap.createBitmap (abmw, abmh, Bitmap.Config.ARGB_4444);
        for (int y = abmh; -- y >= 0;) {
            for (int x = abmw; -- x >= 0;) {
                float radsq = Sq (x - abmwhalf) + Sq (y - abmhhalf);
                if (radsq >= abmwhalf * abmhhalf) {
                    airplaneBitmap.setPixel (x, y, Color.TRANSPARENT);
                } else if (abm.getPixel (x, y) != Color.WHITE) {
                    airplaneBitmap.setPixel (x, y, Color.TRANSPARENT);
                } else {
                    airplaneBitmap.setPixel (x, y, currentColor);
                }
            }
        }

        float ts = wairToNow.textSize;
        capGridBGPaint.setColor (Color.argb (170,255,255,255));
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
        courseLnPaint.setStrokeWidth (10);
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

        tabVisButPaint.setColor (Color.GRAY);
        tabVisButPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        tabVisButPaint.setStrokeWidth (10);

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

        fillBlackPaint.setColor (Color.BLACK);
        fillBlackPaint.setStyle (Paint.Style.FILL_AND_STROKE);

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
        streetChart = new StreetChart ();

        /*
         * Used to display buttons that select which chart to display.
         */
        chartSelectView = new ChartSelectView ();

        /*
         * See what air charts we currently have downloaded.
         */
        DiscoverAirChartFiles ();
    }

    /**
     * Figure out what air chart files we have.
     */
    public void DiscoverAirChartFiles ()
    {
        allDownloadedAirCharts.clear ();
        autoSecAirChart.ClearCharts ();
        autoWacAirChart.ClearCharts ();

        /*
         * Read about all charts from charts/<undername>.csv.
         * These .csv files tell us the lat/lon <-> pixel conversion parameters for the corresponding chart.
         * These may change from revision to revision.
         * Don't use chartlimits.csv cuz it can have numbers that don't match the version that is downloaded.
         */
        File[] chartsCSVFiles = new File (WairToNow.dbdir + "/charts").listFiles ();
        if (chartsCSVFiles != null) {
            for (File csvFile : chartsCSVFiles) {
                String csvName = csvFile.getPath ();
                if (!csvName.endsWith (".csv")) continue;
                AirChart chart = null;
                try {
                    BufferedReader csvReader = new BufferedReader (new FileReader (csvName), 256);
                    try {
                        String csvLine = csvReader.readLine ();
                        chart = new AirChart (csvLine);
                    } finally {
                        csvReader.close ();
                    }
                } catch (Exception e) {
                    Log.w ("ChartView", "error loading chart " + csvName);
                    Log.w ("ChartView", " - " + e.getMessage ());
                }
                if (chart != null) {
                    allDownloadedAirCharts.add (chart);
                    if (csvName.contains ("SEC")) autoSecAirChart.AddChart (chart);
                    if (csvName.contains ("WAC")) autoWacAirChart.AddChart (chart);
                }
            }
        }

        /*
         * Update chart selection buttons.
         */
        chartSelectView.Build ();
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
    public void SetGPSLocation (Location loc)
    {
        lastGPSTimestamp = thisGPSTimestamp;
        lastGPSHeading   = heading;

        thisGPSTimestamp = loc.getTime ();
        altitude = (float) loc.getAltitude ();
        arrowLat = (float) loc.getLatitude ();
        arrowLon = (float) loc.getLongitude ();
        heading  = loc.getBearing ();
        speed    = loc.getSpeed ();

        if (!holdPosition) {
            centerLat = arrowLat;
            centerLon = arrowLon;
            UpdateCanvasHdgRads ();
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
        centerLon    = lon;
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
     * Use name=null to disable.
     * @param oLat = origination latitude
     * @param oLon = origination longitude
     * @param dLat = destination latitude
     * @param dLon = destination longitude
     * @param name = destination name
     */
    public void SetCourseLine (float oLat, float oLon, float dLat, float dLon, String name)
    {
        orgLat = oLat;
        orgLon = oLon;
        dstLat = dLat;
        dstLon = dLon;
        clName = name;
        UpdateCanvasHdgRads ();

        dstRunways = null;
        Waypoint wp = Waypoint.FindWaypoint (clName, dstLat, dstLon);
        if (wp instanceof Waypoint.Airport) {
            Waypoint.Airport awp = (Waypoint.Airport) wp;
            dstRunways = awp.GetRunways ().values ();
        }
    }

    /**
     * See how much to rotate maps by.
     * canvasHdgRads = what heading on chart appears 'up' on canvas
     */
    private void UpdateCanvasHdgRads ()
    {
        switch (wairToNow.optionsView.chartTrackOption.getVal ()) {

            // Course Up : rotate charts counter-clockwise by the heading along the route
            // that is adjacent to the current position.  If no route, use track-up mode.
            case OptionsView.CTO_COURSEUP: {
                if (clName != null) {
                    canvasHdgRads = Mathf.toRadians (Lib.GCOnCourseHdg (orgLat, orgLon, dstLat, dstLon, arrowLat, arrowLon));
                } else {
                    canvasHdgRads = Mathf.toRadians (heading);
                }
                break;
            }

            // North Up : leave chart rotated as per user's manual rotation
            case OptionsView.CTO_NORTHUP: {
                break;
            }

            // Track up : rotate charts counter-clockwise by the current heading
            case OptionsView.CTO_TRACKUP: {
                canvasHdgRads = Mathf.toRadians (heading);
                break;
            }

            // who knows what?
            default: throw new RuntimeException ();
        }
    }

    /**
     * What to show when the back button is pressed
     */
    @Override
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
        wairToNow.setRequestedOrientation (wairToNow.optionsView.chartOrientOption.getVal ());
        UpdateCanvasHdgRads ();  // maybe option was just changed
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
        LoadedBitmap tile;
        while ((tile = loadedBitmaps.poll ()) != null) {
            tile.CloseBitmap ();
        }
        wairToNow.openStreetMap.CloseBitmaps ();
    }

    /**
     * Chart tab re-clicked when already active.
     * Switch to chart selection menu screen.
     */
    @Override  // CanBeMainView
    public void ReClicked ()
    {
        chartSelectView.Show ();
    }

    /**
     * Callback for mouse events on the image.
     * We use this for scrolling the map around.
     */
    @Override
    public boolean onTouchEvent (@NonNull MotionEvent event)
    {
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
        Log.d ("ChartView", "onTouchEvent*: action=" + actionName + " pointerCount=" + pointerCount + " actionIndex=" + event.getActionIndex ());
        for (int pointerIndex = 0; pointerIndex < pointerCount; pointerIndex ++) {
            int pointerId = event.getPointerId (pointerIndex);
            float p = event.getPressure (pointerIndex);
            float x = event.getX (pointerIndex);
            float y = event.getY (pointerIndex);
            Log.d ("ChartView", "onTouchEvent*: " + pointerId + ": p=" + p + " " + " x=" + x + " y=" + y);
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
        rotStart = canvasHdgRads;
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
        boolean steps = true;  // enables discrete steps

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
        centerLat     = latStart;
        centerLon     = lonStart;
        canvasHdgRads = rotStart;
        scaling       = scaStart;
        MapLatLonsToCanvasPixels ();

        // figure out where new center point is in the old canvas
        float newCtrX = canvasWidth  / 2;
        float newCtrY = canvasHeight / 2;
        float oldCtrX = fc * newCtrX + fs * newCtrY + tx;
        float oldCtrY = fc * newCtrY - fs * newCtrX + ty;

        // compute the lat/lon for that point using the old canvas transform
        pmap.CanvasPixToLatLon (oldCtrX, oldCtrY, newCtrLL);

        // that lat/lon will be at center of new canvas
        centerLat = newCtrLL.lat;
        centerLon = newCtrLL.lon;

        // rotate chart by this much counterclockwise
        if (wairToNow.optionsView.rotationOption.checkBox.isChecked ()) {
            float rotInt = rotStart - r;
            while (rotInt < -Mathf.PI) rotInt += Mathf.PI * 2.0;
            while (rotInt >= Mathf.PI) rotInt -= Mathf.PI * 2.0;
            canvasHdgRads = steps ? Mathf.round (rotInt / rotatestep) * rotatestep : rotInt;
        }

        // zoom out on chart by this much
        float scaInt = scaStart / f;
        if (scaInt < scalemin) scaInt = scalemin;
        if (scaInt > scalemax) scaInt = scalemax;
        float scaletemp = scaInt;
        if (steps) {
            int scalexpon = 0;
            while (scaletemp >= 10.0) {
                scaletemp /= 10.0;
                scalexpon ++;
            }
            while (scaletemp < 1.0) {
                scaletemp *= 10.0;
                scalexpon --;
            }
            for (int i = 0; i < scalesteps.length - 1; i ++) {
                float a = scalesteps[i+0];
                float b = scalesteps[i+1];
                float e = scaletemp / a;
                float g = b / scaletemp;
                if ((e >= 1.0) && (e < g)) {
                    scaletemp = a;
                    break;
                }
                if (g >= 1.0) {
                    scaletemp = b;
                    break;
                }
            }
            while (scalexpon < 0) {
                scaletemp /= 10.0;
                scalexpon ++;
            }
            while (scalexpon > 0) {
                scaletemp *= 10.0;
                scalexpon --;
            }
        }
        scaling = scaletemp;
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
        waypointOpenAt = now + waypointopentime;

        WairToNow.wtnHandler.runDelayed (waypointopentime, new Runnable () {
                    @Override
                    public void run ()
                    {
                        if (System.currentTimeMillis () >= waypointOpenAt) {
                            waypointOpenAt = Long.MAX_VALUE;
                            LatLon ll = new LatLon ();
                            pmap.CanvasPixToLatLon (mouseDownPosX, mouseDownPosY, ll);
                            wairToNow.waypointView1.OpenWaypointAtLatLon (ll.lat, ll.lon);
                        }
                    }
                });

        if (centerInfoBounds.contains ((int)x, (int)y)) {
            downOnCenterInfo = now;
        }
        if (chartSelectBounds.contains ((int)x, (int)y)) {
            downOnChartSelect = now;
        }
        if (courseInfoBounds.contains ((int)x, (int)y)) {
            downOnCourseInfo = now;
        }
        if (gpsInfoBounds.contains ((int)x, (int)y)) {
            downOnGPSInfo = now;
        }
        if (tabButtonBounds.contains (x, y)) {
            downOnTabButton = now;
        }
    }

    private void MouseUp ()
    {
        long now = System.currentTimeMillis ();
        showCenterInfo   ^= (now - downOnCenterInfo < 500);
        showCourseInfo   ^= (now - downOnCourseInfo < 500);
        showGPSInfo      ^= (now - downOnGPSInfo    < 500);
        if (now - downOnChartSelect < 500) {
            chartSelectView.Show ();
        }
        if (now - downOnTabButton < 500) {
            wairToNow.SetTabVisibility (!wairToNow.GetTabVisibility ());
        }
        downOnCenterInfo  = 0;
        downOnChartSelect = 0;
        downOnCourseInfo  = 0;
        downOnGPSInfo     = 0;
        downOnTabButton   = 0;
        waypointOpenAt    = Long.MAX_VALUE;
        invalidate ();
    }

    private static float Sq (float x) { return x*x; }

    /**
     * Callback to draw the tiles in position to fill the screen.
     */
    @Override
    public void onDraw (Canvas canvas)
    {
        OnDraw (canvas);
    }
    public void OnDraw (Canvas canvas)
    {
        /*
         * Set up latlon <-> canvas pixel mapping.
         */
        MapLatLonsToCanvasPixels ();

        /*
         * Draw map image as background.
         */
        try {
            DrawMapImage (canvas);
        } catch (OutOfMemoryError oome) {
            // sometimes fast panning gives bitmap oom errors
            // force all bitmaps closed, garbage collect and give up for now
            CloseDisplay ();
            System.gc ();
            canvas.drawText ("out of memory error", canvasWidth / 2, canvasHeight * 3/4, courseTxPaint);
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
        LinkedList<Location> shownTrail = wairToNow.crumbsView.GetShownTrail ();
        if (shownTrail != null) {
            // set up arrow size when heading north
            // - go down 1mm, go right 1.5mm
            float dy = metrics.xdpi / Lib.MMPerIn;
            float dx = dy * 1.5F;
            // stroke width 0.3mm
            float sw = dy * 0.3F;
            // loop through each point and try to convert to canvas pixel
            for (Location loc : shownTrail) {
                if (pmap.LatLonToCanvasPix ((float) loc.getLatitude (), (float) loc.getLongitude (), pt)) {
                    trailPath.rewind ();
                    // if standing still, draw a circle with our thinnest line
                    float mps = loc.getSpeed ();
                    if (mps < 1.0F) {
                        trailPaint.setStrokeWidth (sw);
                        trailPath.addCircle (pt.x, pt.y, dx, Path.Direction.CCW);
                    } else {
                        // if moving, draw arrow with thickness proportional to speed
                        trailPaint.setStrokeWidth ((Mathf.log10 (mps) + 1.0F) * sw);
                        float a = Mathf.toRadians (loc.getBearing ()) - canvasHdgRads;
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
                    canvasSouthLat, canvasNorthLat, canvasEastLon, canvasWestLon)) {
                if (pmap.LatLonToCanvasPix (faaWP.lat, faaWP.lon, pt)) {
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
                if (pmap.LatLonToCanvasPix (userWP.lat, userWP.lon, pt)) {
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
            pmap.LatLonToCanvasPix (centerLat, centerLon, pt);
            float len = wairToNow.textSize * 1.5F;
            canvas.drawLine (pt.x - len, pt.y, pt.x + len, pt.y, centerLnPaint);
            canvas.drawLine (pt.x, pt.y - len, pt.x, pt.y + len, centerLnPaint);
        }

        /*
         * Draw course line if defined and enabled.
         */
        if ((clName != null) && showCourseInfo) {
            DrawCourseLine (canvas);

            /*
             * If we are nearby and it has runways, draw runway approach lines.
             */
            if (dstRunways != null) {
                DrawRunways (canvas);
            }
        }

        /*
         * Draw an airplane icon where the GPS (current) lat/lon point is.
         */
        if (pmap.LatLonToCanvasPix (arrowLat, arrowLon, pt)) {
            DrawLocationArrow (canvas, pt, canvasHdgRads);
        }

        /*
         * Draw center position text.
         */
        DrawCenterInfo (canvas, centerBGPaint);
        DrawCenterInfo (canvas, centerTxPaint);

        /*
         * Draw course destination text.
         */
        if (clName != null) {
            DrawCourseInfo (canvas, courseBGPaint);
            DrawCourseInfo (canvas, courseTxPaint);
        }

        /*
         * Draw GPS (current position) info text.
         */
        DrawGPSInfo (canvas, currentBGPaint);
        DrawGPSInfo (canvas, currentTxPaint);

        /*
         * Draw chart selection button.
         */
        DrawChartSelect (canvas, chartSelBGPaint);
        DrawChartSelect (canvas, chartSelTxPaint);

        /*
         * Draw Tab Button Visibility Toggle button.
         */
        DrawTabButtonVisible (canvas, tabVisButPaint);

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

        wairToNow.drawGPSAvailable (canvas, this);
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
        Point pt = onDrawPt;

        /*
         * Find east/west limits of course.  Wrap east to be .ge. west if necessary.
         */
        float courseWestLon = Lib.Westmost (orgLon, dstLon);
        float courseEastLon = Lib.Eastmost (orgLon, dstLon);
        if (courseEastLon < courseWestLon) courseEastLon += 360.0;

        /*
         * See if primarily east/west or north/south route.
         */
        if (Math.abs (dstLat - orgLat) < courseEastLon - courseWestLon) {

            /*
             * If canvas is completely west of course, try wrapping canvas eastbound.
             * Eg, course KLAX->PGUM and canvas at lon=-170
             * So courseEastLon=145, courseWestLon=-118+360=242
             *    and canvasEastLon=-171, canvasWestLon=-169
             * but we want
             *    canvasEastLon=-171+360=189, canvasWestLon=-169+360=191
             *    ...so the canvas numbers end up between the course numbers
             */
            float cwl = canvasWestLon;
            float cel = canvasEastLon;
            if (cel < courseWestLon) {
                cwl += 360.0;
                cel += 360.0;
            }

            /*
             * Determine longitude limits of what to plot.
             */
            if (cwl < courseWestLon) cwl = courseWestLon;
            if (cel > courseEastLon) cel = courseEastLon;

            float pixelWidthDeg = pixelWidthM / Lib.MPerNM / NMPerDeg / Mathf.cos (Mathf.toRadians (canvasSouthLat + canvasNorthLat) / 2.0);

            for (float lon = cwl; lon <= cel; lon += pixelWidthDeg * 2) {
                float lat = Lib.GCLon2Lat (orgLat, orgLon, dstLat, dstLon, lon);
                pmap.LatLonToCanvasPix (lat, lon, pt);
                canvas.drawPoint (pt.x, pt.y, courseLnPaint);
            }
        } else {
            // primarily north/south
            float courseSouthLon = Math.min (orgLat, dstLat);
            float courseNorthLon = Math.max (orgLat, dstLat);

            float csl = canvasSouthLat;
            float cnl = canvasNorthLat;
            if (csl < courseSouthLon) csl = courseSouthLon;
            if (cnl > courseNorthLon) cnl = courseNorthLon;

            float pixelHeightDeg = pixelHeightM / Lib.MPerNM / NMPerDeg;

            for (float lat = csl; lat <= cnl; lat += pixelHeightDeg * 2) {
                float lon = Lib.GCLat2Lon (orgLat, orgLon, dstLat, dstLon, lat);
                pmap.LatLonToCanvasPix (lat, lon, pt);
                canvas.drawPoint (pt.x, pt.y, courseLnPaint);
            }
        }
    }

    /**
     * Draw lines showing approach path to the runways for the destination airport.
     */
    private void DrawRunways (Canvas canvas)
    {
        boolean mphOpt = wairToNow.optionsView.ktsMphOption.getAlt ();
        PointF dpt = onDrawPtD;
        for (Waypoint.Runway rwy : dstRunways) {
            pmap.LatLonToCanvasPix (rwy.begLat, rwy.begLon, dpt);
            float begX = dpt.x;
            float begY = dpt.y;
            float appX = 0;
            float appY = 0;
            float rwyTrueRevHdg = rwy.trueHdg + 180.0F;
            int distStep = (int) Math.ceil (2.0 / scaling);
            for (int dist = 0; (dist += distStep) <= 10;) {
                float distnm = dist;
                if (mphOpt) distnm /= Lib.SMPerNM;
                float appLat = Lib.LatHdgDist2Lat (rwy.begLat, rwyTrueRevHdg, distnm);
                float appLon = Lib.LatLonHdgDist2Lon (rwy.begLat, rwy.begLon, rwyTrueRevHdg, distnm);
                pmap.LatLonToCanvasPix (appLat, appLon, dpt);
                appX = dpt.x;
                appY = dpt.y;
                float dx = appX - begX;
                float dy = appY - begY;
                float dd = Mathf.hypot (dx, dy) / 12.0F;
                dx /= dd;
                dy /= dd;
                canvas.drawLine (appX + dy, appY - dx, appX - dy, appY + dx, wairToNow.waypointView1.rwPaint);
            }
            canvas.drawLine (appX, appY, begX, begY, wairToNow.waypointView1.rwPaint);
        }
    }

    /**
     * Draw the airplane location arrow symbol.
     * @param canvas     = canvas to draw it on
     * @param pt         = where on canvas to draw symbol
     * @param canHdgRads = true heading on canvas that is up
     * Other inputs:
     *   heading = true heading for the arrow symbol (degrees, latest gps sample)
     *   speed = airplane speed (mps, latest gps sample)
     *   airplaneBitmap = bitmap of drawing with airplane pointing at heading 30deg true
     *   thisGPSTimestamp = time of latest gps sample
     *   lastGPSTimestamp = time of previous gps sample
     *   lastGPSHeading = true heading for the arrow symbol (previous gps sample)
     */
    public void DrawLocationArrow (Canvas canvas, Point pt, float canHdgRads)
    {
        /*
         * Draw the icon.
         */
        float hdg  = heading - Mathf.toDegrees (canHdgRads);
        float diam = wairToNow.textSize * 2.75F;
        airplaneMatrix.setScale (diam / airplaneBitmap.getWidth (), diam / airplaneBitmap.getHeight ());
        airplaneMatrix.postRotate (hdg - 30, diam / 2, diam / 2);
        airplaneMatrix.postTranslate (pt.x - diam / 2, pt.y - diam / 2);
        canvas.drawBitmap (airplaneBitmap, airplaneMatrix, null);

        /*
         * If we have two fairly recent GPS samples, draw track forward for next 60 seconds.
         */
        long gpsTimeDiff = thisGPSTimestamp - lastGPSTimestamp;
        if ((gpsTimeDiff > 100) && (gpsTimeDiff < 10000)) {

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

            // real-world metres per pixel
            float mPerXPix = pixelWidthM;
            float mPerYPix = pixelHeightM;

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
     * Draw the map image to the canvas.
     * The charts are selected and drawn such that the centerLat,Lon
     * is drawn at the center of the canvas.
     */
    private void DrawMapImage (Canvas canvas)
    {
        /*
         * Invalidate cached stuff.
         */
        viewDrawCycle ++;

        /*
         * Display the selected chart.
         */
        if (selectedChart != null) {
            selectedChart.DrawOnCanvas (canvas);
        }

        /*
         * Close any unreferenced bitmaps so we don't run out of memory.
         */
        for (Iterator<LoadedBitmap> it = loadedBitmaps.iterator (); it.hasNext();) {
            LoadedBitmap lbm = it.next ();
            if (lbm instanceof AirTile) {
                AirTile tile = (AirTile) lbm;
                if (tile.tileDrawCycle < viewDrawCycle) {
                    tile.CloseBitmap ();
                    tile.tileDrawCycle = 0;
                    it.remove ();
                }
            }
        }
    }

    /**
     * Draw CAP grid lines and numbers.
     */
    private void DrawCapGrid (Canvas canvas)
    {
        float westlon  = Mathf.floor (canvasWestLon  * 4.0F) / 4.0F;
        float eastlon  = Mathf.ceil  (canvasEastLon  * 4.0F) / 4.0F;
        float southlat = Mathf.floor (canvasSouthLat * 4.0F) / 4.0F;
        float northlat = Mathf.ceil  (canvasNorthLat * 4.0F) / 4.0F;

        for (float lat = southlat; lat <= northlat; lat += 0.25) {
            for (float lon = westlon; lon <= eastlon; lon += 0.25) {
                pmap.LatLonToCanvasPix (lat, lon, canpix1);
                pmap.LatLonToCanvasPix (lat, lon + 0.25F, canpix2);
                pmap.LatLonToCanvasPix (lat + 0.25F, lon, canpix3);
                int x1 = canpix1.x;
                int y1 = canpix1.y;
                canvas.drawLine (x1, y1, canpix2.x, canpix2.y, capGridLnPaint);
                canvas.drawLine (x1, y1, canpix3.x, canpix3.y, capGridLnPaint);
                for (CapGrid cg : capgrids) {
                    int n = cg.number (lon, lat);
                    if (n > 0) {
                        canvas.save ();
                        try {
                            float theta = Mathf.atan2 (canpix2.y - y1, canpix2.x - x1);
                            canvas.rotate (Mathf.toDegrees (theta), x1, y1);
                            String s = cg.id + " " + n;
                            canvas.drawText (s, x1 + 10, y1 - 10, capGridBGPaint);
                            canvas.drawText (s, x1 + 10, y1 - 10, capGridTxPaint);
                        } finally {
                            canvas.restore ();
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Set up latlon <-> canvas pixel mapping.
     */
    private void MapLatLonsToCanvasPixels ()
    {
        /*
         * Get canvas dimensions (eg (600,1024)).
         * This is actually the visible part of the canvas that shows in the parent's window.
         */
        canvasWidth  = getWidth ();
        canvasHeight = getHeight ();

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
        if ((mappingCanvasHdgRads == canvasHdgRads) &&
            (mappingCenterLat     == centerLat)     &&
            (mappingCenterLon     == centerLon)     &&
            (mappingCanvasHeight  == canvasHeight)  &&
            (mappingCanvasWidth   == canvasWidth)   &&
            (mappingPixelHeightM  == pixelHeightM)  &&
            (mappingPixelWidthM   == pixelWidthM)) return;

        mappingCanvasHdgRads = canvasHdgRads;
        mappingCenterLat     = centerLat;
        mappingCenterLon     = centerLon;
        mappingCanvasHeight  = canvasHeight;
        mappingCanvasWidth   = canvasWidth;
        mappingPixelHeightM  = pixelHeightM;
        mappingPixelWidthM   = pixelWidthM;

        /*
         * Compute the canvas pixel that gets the center lat/lon.
         */
        int canvasCenterX = canvasWidth  / 2;
        int canvasCenterY = canvasHeight / 2;

        /*
         * Compute the lat/lon of the four corners of the canvas such
         * that each pixel covers the same delta metres in X and Y.
         */
        float metresFromCenterToRite   = canvasCenterX * pixelWidthM;
        float metresFromCenterToBot    = canvasCenterY * pixelHeightM;
        float metresFromCenterToCorner = Mathf.hypot (metresFromCenterToBot, metresFromCenterToRite);

        float angleFromCenterToBotRite   = Mathf.atan2 (metresFromCenterToBot, metresFromCenterToRite) + canvasHdgRads;
        float metresFromCenterToBotRiteX = metresFromCenterToCorner * Mathf.cos (angleFromCenterToBotRite);
        float metresFromCenterToBotRiteY = metresFromCenterToCorner * Mathf.sin (angleFromCenterToBotRite);

        float metresFromCenterToTopLeftX = -metresFromCenterToBotRiteX;
        float metresFromCenterToTopLeftY = -metresFromCenterToBotRiteY;

        float angleFromCenterToBotLeft   = Mathf.atan2 (metresFromCenterToBot, -metresFromCenterToRite) + canvasHdgRads;
        float metresFromCenterToBotLeftX = metresFromCenterToCorner * Mathf.cos (angleFromCenterToBotLeft);
        float metresFromCenterToBotLeftY = metresFromCenterToCorner * Mathf.sin (angleFromCenterToBotLeft);

        float metresFromCenterToTopRiteX = -metresFromCenterToBotLeftX;
        float metresFromCenterToTopRiteY = -metresFromCenterToBotLeftY;

        brLat = centerLat - metresFromCenterToBotRiteY / Lib.MPerNM / NMPerDeg;
        brLon = centerLon + metresFromCenterToBotRiteX / Lib.MPerNM / NMPerDeg / Mathf.cos (brLat / 180.0 * Mathf.PI);

        blLat = centerLat - metresFromCenterToBotLeftY / Lib.MPerNM / NMPerDeg;
        blLon = centerLon + metresFromCenterToBotLeftX / Lib.MPerNM / NMPerDeg / Mathf.cos (blLat / 180.0 * Mathf.PI);

        trLat = centerLat - metresFromCenterToTopRiteY / Lib.MPerNM / NMPerDeg;
        trLon = centerLon + metresFromCenterToTopRiteX / Lib.MPerNM / NMPerDeg / Mathf.cos (trLat / 180.0 * Mathf.PI);

        tlLat = centerLat - metresFromCenterToTopLeftY / Lib.MPerNM / NMPerDeg;
        tlLon = centerLon + metresFromCenterToTopLeftX / Lib.MPerNM / NMPerDeg / Mathf.cos (tlLat / 180.0 * Mathf.PI);

        /*
         * Put canvas corner lat/lons in an array.
         */
        canvasCornersLatLon[0].lat = tlLat;
        canvasCornersLatLon[0].lon = tlLon;
        canvasCornersLatLon[1].lat = trLat;
        canvasCornersLatLon[1].lon = trLon;
        canvasCornersLatLon[2].lat = blLat;
        canvasCornersLatLon[2].lon = blLon;
        canvasCornersLatLon[3].lat = brLat;
        canvasCornersLatLon[3].lon = brLon;

        /*
         * Set up mapping.
         */
        pmap.setup (canvasWidth, canvasHeight,
                tlLat, tlLon, trLat, trLon,
                blLat, blLon, brLat, brLon);

        canvasSouthLat = pmap.canvasSouthLat;
        canvasNorthLat = pmap.canvasNorthLat;
        canvasWestLon  = pmap.canvasWestLon;
        canvasEastLon  = pmap.canvasEastLon;
    }

    /**
     * Draw Center Point info in lower left corner of canvas.
     */
    private void DrawCenterInfo (Canvas canvas, Paint paint)
    {
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
            if (centerLLChanged || optionsChanged ||
                    (centerInfoScaling       != scaling)       ||
                    (centerInfoCanvasHdgRads != canvasHdgRads) ||
                    (centerInfoAltitude      != altitude)) {
                centerInfoScaling       = scaling;
                centerInfoCanvasHdgRads = canvasHdgRads;
                centerInfoAltitude      = altitude;
                centerInfoScaStr = "x " + new DecimalFormat ("#.##").format (scaling);
                centerInfoRotStr = wairToNow.optionsView.HdgString (Mathf.toDegrees (canvasHdgRads), centerLat, centerLon, altitude);
            }
            DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 2, centerInfoScaStr);
            DrawBoundedString (canvas, centerInfoBounds, paint, cx, by - dy * 1, centerInfoRotStr);
        } else {

            // user doesn't want any info shown, draw a little button instead
            float ts = wairToNow.textSize;
            centerInfoBounds.left   = 0;
            centerInfoBounds.right  = (int) (ts * 2);
            centerInfoBounds.top    = (int) (canvasHeight - ts * 2);
            centerInfoBounds.bottom = canvasHeight;

            if (centerInfoCanvasHeight != canvasHeight) {
                centerInfoCanvasHeight = canvasHeight;
                centerInfoPath.rewind ();
                centerInfoPath.moveTo (ts, canvasHeight - ts * 2);
                centerInfoPath.lineTo (ts * 2, canvasHeight - ts * 2);
                centerInfoPath.lineTo (ts * 2, canvasHeight - ts);
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
            if ((courseInfoArrowLat != arrowLat) ||
                (courseInfoArrowLon != arrowLon) ||
                (courseInfoDstLat   != dstLat)   ||
                (courseInfoDstLon   != dstLon)   ||
                (courseInfoMphOpt   != mphOpt)   ||
                (courseInfoTrueOpt  != trueOpt)) {
                courseInfoArrowLat = arrowLat;
                courseInfoArrowLon = arrowLon;
                courseInfoDstLat   = dstLat;
                courseInfoDstLon   = dstLon;
                courseInfoMphOpt   = mphOpt;
                courseInfoTrueOpt  = trueOpt;

                float dist = Lib.LatLonDist (arrowLat, arrowLon, dstLat, dstLon);
                courseInfoDistStr = Lib.DistString (dist, mphOpt);

                float tcto = Lib.LatLonTC (arrowLat, arrowLon, dstLat, dstLon);
                courseInfoMCToStr = wairToNow.optionsView.HdgString (tcto, arrowLat, arrowLon, altitude);

                courseInfoEteStr = "";
                if (speed >= 1.0) {
                    int etesec = (int) (dist * Lib.MPerNM / speed + 0.5);
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
            DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 1, clName);
            DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 2, courseInfoDistStr);
            DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 3, courseInfoMCToStr);
            DrawBoundedString (canvas, courseInfoBounds, paint, cx, dy * 4, courseInfoEteStr);
        } else {
            float ts = wairToNow.textSize;

            courseInfoBounds.left   = (int) (canvasWidth - ts * 2);
            courseInfoBounds.right  = canvasWidth;
            courseInfoBounds.top    = 0;
            courseInfoBounds.bottom = (int) (ts * 2);

            if (courseInfoCanvasWidth != canvasWidth) {
                courseInfoCanvasWidth = canvasWidth;
                courseInfoPath.rewind ();
                courseInfoPath.moveTo (canvasWidth - ts * 2, ts);
                courseInfoPath.lineTo (canvasWidth - ts * 2, ts * 2);
                courseInfoPath.lineTo (canvasWidth - ts, ts * 2);
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
                gpsInfoAltStr = Integer.toString ((int)(altitude * FtPerM)) + " ft";
            }

            if ((gpsInfoMphOpt != mphOpt) || (gpsInfoSpeed != speed)) {
                gpsInfoMphOpt = mphOpt;
                gpsInfoSpeed  = speed;
                gpsInfoSpdStr = Lib.SpeedString (speed * Lib.KtPerMPS, mphOpt);
            }

            int cx = (int)(paint.getTextSize () * 3.125);
            int dy = (int)paint.getFontSpacing ();

            gpsInfoBounds.setEmpty ();
            DrawBoundedString (canvas, gpsInfoBounds, paint, cx, dy * 1, gpsInfoLatStr);
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
        /*
         * First time, display words so they know what this is about.
         */
        if (selectedChart == null) {
            chartSelectBounds.setEmpty ();

            int cx = canvasWidth - (int)(paint.getTextSize () * 3.125);
            int dy = (int)paint.getFontSpacing ();
            int by = canvasHeight - 10;

            DrawBoundedString (canvas, chartSelectBounds, paint, cx, by - dy * 2, "select");
            DrawBoundedString (canvas, chartSelectBounds, paint, cx, by - dy * 1, "chart");
        } else {

            /*
             * Otherwise, show little arrow button so it doesn't take up so much room.
             */
            if ((chartSelectBounds.right != canvasWidth - 1) || (chartSelectBounds.bottom != canvasHeight - 1)) {
                float ts = wairToNow.textSize;
                chartSelectBounds.left   = (int) (canvasWidth - ts * 2);
                chartSelectBounds.top    = (int) (canvasHeight - ts * 2);
                chartSelectBounds.right  = canvasWidth - 1;
                chartSelectBounds.bottom = canvasHeight - 1;

                chartSelectPath.rewind ();
                chartSelectPath.moveTo (canvasWidth - ts, canvasHeight - ts * 2);
                chartSelectPath.lineTo (canvasWidth - ts * 2, canvasHeight - ts * 2);
                chartSelectPath.lineTo (canvasWidth - ts * 2, canvasHeight - ts);
            }
            canvas.drawPath (chartSelectPath, paint);
        }
    }

    /**
     * Draw little arrow button to toggle tab button visibility.
     */
    private void DrawTabButtonVisible (Canvas canvas, Paint paint)
    {
        boolean visibile = wairToNow.GetTabVisibility ();
        if ((tabButtonCanvasHeight != canvasHeight) ||
            (tabButtonCanvasWidth  != canvasWidth) ||
            (tabButtonVisibile     != visibile)) {
            tabButtonCanvasHeight = canvasHeight;
            tabButtonCanvasWidth  = canvasWidth;
            tabButtonVisibile     = visibile;

            float ts = wairToNow.textSize;
            float xc = canvasWidth / 2;
            float xl = xc - ts * 2;
            float xr = xc + ts * 2;
            float yt = canvasHeight - ts * 2;
            float yb = canvasHeight - ts;

            tabButtonBounds.left = xl;
            tabButtonBounds.right = xr;
            tabButtonBounds.top = yt;
            tabButtonBounds.bottom = yb;

            tabButtonPath.rewind ();
            if (visibile) {
                tabButtonPath.moveTo (xl, yt);
                tabButtonPath.lineTo (xc, yb);
                tabButtonPath.lineTo (xr, yt);
                tabButtonPath.lineTo (xl, yt);
            } else {
                tabButtonPath.moveTo (xl, yb);
                tabButtonPath.lineTo (xc, yt);
                tabButtonPath.lineTo (xr, yb);
                tabButtonPath.lineTo (xl, yb);
            }
        }
        canvas.drawPath (tabButtonPath, paint);
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
     * Group of same-sized air charts sown together.
     */
    private class AutoAirChart extends Chart implements Comparator<AirChart> {
        private AirChart[] chartArray;
        private LinkedList<AirChart> chartList = new LinkedList<> ();

        public AutoAirChart (String cat)
        {
            basename = "Auto " + cat;
        }

        public void ClearCharts ()
        {
            chartList.clear ();
            chartArray = null;
        }

        public void AddChart (AirChart chart)
        {
            chartList.add (chart);
            chartArray = null;
        }

        /**
         * Return color to draw button text color.
         */
        @Override  // Chart
        public int DownldLinkColor ()
        {
            return Color.CYAN;
        }

        /**
         * Draw chart on canvas possibly scaled and/or rotated.
         * @param canvas = canvas to draw on
         */
        @Override  // Chart
        public void DrawOnCanvas (Canvas canvas)
        {
            if (chartArray == null) {
                chartArray = chartList.toArray (new AirChart[chartList.size()]);
                Arrays.sort (chartArray, this);
            }
            for (AirChart c : chartArray) {
                MaintView.PossibleChart pc = c.possibleChart;
                if (pc.LatLonIsCharted (tlLat, tlLon) ||
                    pc.LatLonIsCharted (trLat, trLon) ||
                    pc.LatLonIsCharted (brLat, brLon) ||
                    pc.LatLonIsCharted (blLat, blLon)) {
                    c.DrawOnCanvas (canvas, false, true);
                }
            }
        }

        /**
         * Sort such that northeastern charts are drawn first,
         * as the north and east edges should be on top when
         * there is any overlapping.
         */
        @Override  // Comparator
        public int compare (AirChart a, AirChart b)
        {
            int aao = a.possibleChart.autoOrder;
            int bao = b.possibleChart.autoOrder;
            return aao - bao;
        }
    }

    /**
     * Contains one aeronautical chart.
     */
    private class AirChart extends Chart {
        private AirTile[] tilez;                  // tiles that compose the chart
        public MaintView.PossibleChart possibleChart;

        public AirChart (String csvLine)
        {
            String[] values = Lib.QuotedCSVSplit (csvLine);
            basename = values[15];

            for (MaintView.Downloadable dl : wairToNow.maintView.allDownloadables) {
                if (dl instanceof MaintView.PossibleChart) {
                    MaintView.PossibleChart pc = (MaintView.PossibleChart) dl;
                    if (basename.startsWith (pc.spacename + " ")) {
                        possibleChart = pc;
                        break;
                    }
                }
            }
        }

        /**
         * Compute a latitude given a longitude and a Y-pixel.
         * @param lon = longitude
         * @param pixy = Y-pixel relative to top of chart
         * @returns latitude at that point
         */
        /*
        public float LonPixY2Lat (float lon, int pixy)
        {
            if (tfw_b != 0) throw new RuntimeException ("tu stoopid to handle tilted chart");

            float l = lon / 180.0F * Mathf.PI;
            float N = tfw_d * pixy + tfw_f;

            // From LatLon2Pixel():
            //
            //   p = r * F / (tan (pi/4 + o/2) ** n)
            //   t = n * (l - l0)
            //   E = p * sin (t)
            //   N = p0 - p * cos (t)
            //
            // Now solve for o given l and N (we don't care about E):
            //
            //   t = n * (l - l0)
            //
            //   N = p0 - p * cos (t)  =>  p = (p0 - N) / cos (t)
            //
            //   p = r * F / (tan (pi/4 + o/2) ** n)  => tan (pi/4 + o/2) ** n = r * F / p  =>
            //       o = arctan ((r * F / p) ** 1/n) * 2 - pi/2 = arctan ((r * F * cos (t) / (p0 - N)) ** 1/n) * 2 - pi/2

            float t = lamb_n * (l - lamb_l0);
            float o = Mathf.atan (Mathf.pow (radius * lamb_F * Mathf.cos (t) / (lamb_p0 - N), 1.0 / lamb_n)) * 2 - Mathf.PI / 2;

            return o / Mathf.PI * 180.0;
        }
        */

        /**
         * Compute a longitude given a latitude and an X-pixel.
         * @param lat = latitude
         * @param pixx = X-pixel relative to left edge of chart
         * @returns latitude at that point
         */
        /*
        public float LatPixX2Lon (float lat, int pixx)
        {
            if (tfw_c != 0) throw new RuntimeException ("tu stoopid to handle tilted chart");

            float o = lat / 180.0F * Mathf.PI;
            float E = (tfw_a * pixx + tfw_e) / (1.0 + 1.0 / 250.0);

            // From LatLon2Pixel():
            //
            //   p = r * F / (tan (pi/4 + o/2) ** n)
            //   t = n * (l - l0)
            //   E = p * sin (t)
            //   N = p0 - p * cos (t)
            //
            // Now solve for l given o and E (we don't care about N):
            //
            //   p = r * F / (tan (pi/4 + o/2) ** n)
            //
            //   E = p * sin (t)  =>  t = arcsin (E / p)
            //
            //   t = n * (l - l0)  =>  l = t / n + l0

            float p = radius * lamb_F / Mathf.pow (Mathf.tan (Mathf.PI / 4 + o / 2), lamb_n);
            float t = Mathf.asin (E / p);
            float l = t / lamb_n + lamb_l0;

            return l / Mathf.PI * 180.0;
        }
        */

        /**
         * Return color to draw button text color.
         */
        @Override  // Chart
        public int DownldLinkColor ()
        {
            return possibleChart.DownldLinkColor ();
        }

        /**
         * Draw chart on canvas possibly scaled and/or rotated.
         * @param canvas = canvas to draw on
         */
        @Override  // Chart
        public void DrawOnCanvas (Canvas canvas)
        {
            DrawOnCanvas (canvas, true, false);
        }
        public void DrawOnCanvas (Canvas canvas, boolean includeLegends, boolean blankBackground)
        {
            /*
             * Create transformation matrix that scales and rotates the chart
             * as a whole such that the given points match up.
             *
             *    [matrix] * [some_xy_in_chart] => [some_xy_in_canvas]
             */
            float[] points = drawOnCanvasPoints;
            Point point = drawOnCanvasPoint;

            possibleChart.LatLon2Pixel (tlLat, tlLon, point);
            points[ 0] = point.x;  points[ 8] = 0;
            points[ 1] = point.y;  points[ 9] = 0;

            possibleChart.LatLon2Pixel (trLat, trLon, point);
            points[ 2] = point.x;  points[10] = canvasWidth;
            points[ 3] = point.y;  points[11] = 0;

            possibleChart.LatLon2Pixel (blLat, blLon, point);
            points[ 4] = point.x;  points[12] = 0;
            points[ 5] = point.y;  points[13] = canvasHeight;

            possibleChart.LatLon2Pixel (brLat, brLon, point);
            points[ 6] = point.x;  points[14] = canvasWidth;
            points[ 7] = point.y;  points[15] = canvasHeight;

            if (!drawOnCanvasChartMat.setPolyToPoly (points, 0, points, 8, 4)) {
                Log.e ("ChartView", "can't position chart");
                return;
            }

            /*
             * Get size of the canvas that we are drawing on.
             * Don't use clip bounds because we want all tiles for the canvas marked
             * as referenced so we don't keep loading and unloading the bitmaps.
             */
            canvasBounds.right  = canvasWidth;
            canvasBounds.bottom = canvasHeight;

            /*
             * When zoomed out, scaling < 1.0 and we want to undersample the bitmaps by that
             * much.  Eg, if scaling = 0.5, we want to sample half**2 the bitmap pixels and
             * scale the bitmap back up to twice its size.  But when zoomed in, ie, scaling
             * > 1.0, we don't want to oversample the bitmaps, just sample them normally, so
             * we don't run out of memory doing so.  Also, using a Mathf.ceil() on it seems to
             * use up a lot less memory than using arbitrary real numbers.
             */
            float ts = Mathf.hypot (points[6] - points[0], points[7] - points[1])
                               / Mathf.hypot (canvasWidth, canvasHeight);
            int tileScaling = (int) Mathf.ceil (ts);

            /*
             * Scan through each tile that composes the chart.
             */
            Matrix tileMat = drawOnCanvasTileMat;
            for (AirTile tile : GetAirTiles ()) {

                /*
                 * Maybe skip tiles that contain only legend info.
                 */
                boolean wholeTile = true;
                if (!includeLegends) {
                    if (tile.isLegendOnly) continue;
                    wholeTile = tile.isChartedOnly;
                }

                /*
                 * Set up a matrix that maps the tile onto the canvas.
                 */
                tileMat.setTranslate (tile.leftPixel, tile.topPixel);
                                                            // translate tile into position within whole chart
                tileMat.postConcat (drawOnCanvasChartMat);  // position tile onto canvas

                /*
                 * Compute the canvas points corresponding to the four corners of the tile.
                 */
                points[0] =          0; points[1] =           0;
                points[2] = tile.width; points[3] =           0;
                points[4] =          0; points[5] = tile.height;
                points[6] = tile.width; points[7] = tile.height;
                tileMat.mapPoints (points, 8, points, 0, 4);

                /*
                 * Get canvas bounds of the tile, given that the tile may be tilted.
                 */
                int testLeft = (int)Math.min (Math.min (points[ 8], points[10]), Math.min (points[12], points[14]));
                int testRite = (int)Math.max (Math.max (points[ 8], points[10]), Math.max (points[12], points[14]));
                int testTop  = (int)Math.min (Math.min (points[ 9], points[11]), Math.min (points[13], points[15]));
                int testBot  = (int)Math.max (Math.max (points[ 9], points[11]), Math.max (points[13], points[15]));

                /*
                 * Attempt to draw tile iff it intersects the part of canvas we are drawing.
                 * Otherwise, don't waste time and memory loading the bitmap, and certainly
                 * don't mark the tile as referenced so we don't keep it in memory.
                 */
                if (canvasBounds.intersects (testLeft, testTop, testRite, testBot)) {
                    if (tileScaling != 1) {
                        tileMat.preScale ((float)tileScaling, (float)tileScaling);
                    }
                    try {
                        Bitmap bm = tile.GetScaledBitmap (tileScaling);
                        if (bm != null) {
                            boolean saved = false;
                            try {
                                if (!wholeTile) {
                                    // part of the tile contains legend info that we don't want displayed
                                    // set up a clipping path to exclude legend pixels leaving charted pixels intact
                                    canvas.save ();
                                    saved = true;
                                    tile.GetScaledChartedClipPath (tileScaling, tileMat, drawOnCanvasPath);
                                    canvas.clipPath (drawOnCanvasPath);
                                }
                                if (blankBackground) {
                                    // fill the area behind the bitmap with black paint.
                                    // necessary when drawing overlapping sectional tiles
                                    // because some of the chart black ink is really just
                                    // alpha=0 so it gets washed out if we don't do this.
                                    fillPath.rewind ();
                                    fillPath.moveTo (points[ 8], points[ 9]);
                                    fillPath.lineTo (points[10], points[11]);
                                    fillPath.lineTo (points[14], points[15]);
                                    fillPath.lineTo (points[12], points[13]);
                                    fillPath.lineTo (points[ 8], points[ 9]);
                                    canvas.drawPath (fillPath, fillBlackPaint);
                                }
                                canvas.drawBitmap (bm, tileMat, null);
                            } finally {
                                if (saved) canvas.restore ();
                            }
                            if (tile.tileDrawCycle == 0) loadedBitmaps.add (tile);
                            tile.tileDrawCycle = viewDrawCycle;
                        }
                    } catch (Throwable t) {
                        Log.e ("ChartView", "error drawing bitmap", t);
                    }
                }
            }
        }

        /**
         * Get list of tiles in this chart.
         * Does not actually open the image tile files.
         */
        private AirTile[] GetAirTiles ()
        {
            if (tilez == null) {
                int width  = possibleChart.width;
                int height = possibleChart.height;
                tilez = new AirTile[((height+hstep-1)/hstep)*((width+wstep-1)/wstep)];
                int i = 0;
                for (int h = 0; h < height; h += hstep) {
                    for (int w = 0; w < width; w += wstep) {
                        tilez[i++] = new AirTile (this, w, h);
                    }
                }
            }
            return tilez;
        }
    }

    /**
     * Contains an image that is a small part of an air chart.
     */
    private class AirTile implements LoadedBitmap {
        public AirChart chart;         // chart that this tile is part of
        public boolean isChartedOnly;  // tile contains only pixels that are in charted area
        public boolean isLegendOnly;   // tile contains only pixels that are in legend area
        public boolean queued;         // queued to AirTileLoader thread
        public int leftPixel;          // where the left edge is within the chart
        public int topPixel;           // where the top edge is within the chart
        public int width;              // width of this tile (always wstep+overlap except for rightmost tiles)
        public int height;             // height of this tile (always hstep+overlap except for bottommost tiles)
        public long tileDrawCycle;     // marks the tile as being in use

        private Bitmap bitmap;         // bitmap that contains the image
        private float[] corners;       // for mixed charted/legend tiles, corners enclosing charted area
        private int scaling;           // scaling factor used to fetch bitmap with

        public AirTile (AirChart ch, int w, int h)
        {
            chart     = ch;
            leftPixel = w;
            topPixel  = h;

            width  = chart.possibleChart.width  - leftPixel;
            height = chart.possibleChart.height - topPixel;
            if (width  > wstep + overlap) width  = wstep + overlap;
            if (height > hstep + overlap) height = hstep + overlap;

            boolean a = chart.possibleChart.PixelIsCharted (leftPixel, topPixel);
            boolean b = chart.possibleChart.PixelIsCharted (leftPixel + width, topPixel);
            boolean c = chart.possibleChart.PixelIsCharted (leftPixel + width, topPixel + height);
            boolean d = chart.possibleChart.PixelIsCharted (leftPixel, topPixel + height);
            isChartedOnly = a & b & c & d;
            isLegendOnly  = !a & !d & !c & !d;
        }

        /**
         * Load the corresponding bitmap and scale it.
         * Scale it here to save memory, eg, if we are
         * drawing a 128x128 pixel area on canvas, only
         * load a 128x128 pixel bitmap.
         * @param s = scaling:
         *          1: full size
         *          2: half-sized, eg, condense 256x256 image down to 128x128 pixels
         *          3: third-sized, etc
         * @return bitmap of the tile (or null if not available right now)
         */
        public Bitmap GetScaledBitmap (int s)
        {
            Bitmap bm = null;
            synchronized (tilesToLoad) {

                // don't mess with it if it is queued for loading
                if (!queued) {

                    // not queued, make sure same scaling as before
                    // discard previous bitmap if scaling different
                    if (scaling != s) {
                        if (bitmap != null) {
                            bitmap.recycle ();
                            bitmap = null;
                        }
                        scaling = s;
                    }

                    // if we don't have bitmap in memory,
                    // queue to thread for loading
                    bm = bitmap;
                    if (bm == null) {
                        tilesToLoad.addLast (this);
                        queued = true;
                        if (airTileLoader == null) {
                            airTileLoader = new AirTileLoader ();
                            airTileLoader.start ();
                        } else {
                            tilesToLoad.notifyAll ();
                        }
                    }
                }
            }
            return bm;
        }

        /**
         * Called by AirTileThread to read bitmap into memorie.
         */
        public Bitmap LoadBitmap ()
        {
            BitmapFactory.Options bfo = new BitmapFactory.Options ();
            bfo.inDensity = scaling;
            bfo.inInputShareable = true;
            bfo.inPurgeable = true;
            bfo.inScaled = true;
            bfo.inTargetDensity = 1;

            String pngName = WairToNow.dbdir + "/charts/" +
                    chart.basename.replace (' ', '_') + "/" +
                    Integer.toString (topPixel / hstep) + "/" +
                    Integer.toString (leftPixel / wstep) + ".png";
            try {
                return BitmapFactory.decodeFile (pngName, bfo);
            } catch (Throwable t) {
                Log.e ("WairToNow", "error loading bitmap " + pngName, t);
                return null;
            }
        }

        @Override  // LoadedBitmap
        public void CloseBitmap ()
        {
            if (bitmap != null) {
                bitmap.recycle ();
                bitmap = null;
            }
            tileDrawCycle = 0;
        }

        /**
         * Tile contains legend info that we don't want and charted info that we do want.
         * So set up clipping path that only includes the charting pixels.
         * @param s = scale factor used to fetch bitmap
         * @param mat = maps scaled tile bitmap pixels to canvas pixels
         * @param clip = where to put clipping path in canvas pixels
         */
        public void GetScaledChartedClipPath (int s, Matrix mat, Path clip)
        {
            // get unscaled bitmap corners clipped to the charting pixels, excluding legend pixels.
            float[] bmc = corners;
            if (bmc == null) {
                corners = bmc = new float[8];
                chart.possibleChart.GetChartedClipPath (bmc, leftPixel, topPixel, leftPixel + width, topPixel + height);
                for (int i = 0; i < 8;) {
                    bmc[i++] -= leftPixel;
                    bmc[i++] -= topPixel;
                }
            }

            // make them relative to the scaled bitmap
            float[] atc = airTileCorners;
            for (int i = 0; i < 8; i += 2) {
                atc[i+0] = bmc[i+0] / s;
                atc[i+1] = bmc[i+1] / s;
            }

            // convert them to canvas pixels
            mat.mapPoints (atc);

            // make clip path based on the canvas pixels that enclose the charting pixels
            clip.rewind ();
            clip.moveTo (atc[0], atc[1]);
            clip.lineTo (atc[2], atc[3]);
            clip.lineTo (atc[4], atc[5]);
            clip.lineTo (atc[6], atc[7]);
            clip.lineTo (atc[0], atc[1]);
        }
    }

    /**
     * Load air tiles in a background thread to keep GUI responsive.
     */
    private class AirTileLoader extends Thread {

        @Override
        public void run ()
        {
            setName ("AirTile loader");

            try {
                /*
                 * Once started, we keep going forever.
                 */
                while (true) {

                    /*
                     * Get a tile to load.  If none, wait for something.
                     * But tell GUI thread to re-draw screen cuz we probably
                     * just loaded a bunch of tiles.
                     */
                    AirTile tile;
                    synchronized (tilesToLoad) {
                        while (tilesToLoad.isEmpty ()) {
                            postInvalidate ();
                            tilesToLoad.wait ();
                        }
                        tile = tilesToLoad.removeFirst ();
                    }

                    /*
                     * Got something to do, try to load the tile in memory.
                     */
                    Bitmap bm = tile.LoadBitmap ();

                    /*
                     * Mark that we are done trying to load the bitmap.
                      */
                    synchronized (tilesToLoad) {
                        tile.bitmap = bm;
                        tile.queued = false;
                    }
                }
            } catch (InterruptedException ie) {
                Log.e ("WairToNow", "error loading tiles", ie);
            } finally {
                SQLiteDBs.CloseAll ();
                airTileLoader = null;
            }
        }
    }

    /**
     * Single instance contains all the street map tiles for the whole world.
     */
    private class StreetChart extends Chart implements LoadedBitmap {
        public StreetChart ()
        {
            basename = "Street";
        }

        /**
         * Return color to draw button text color.
         */
        @Override  // Chart
        public int DownldLinkColor ()
        {
            return Color.CYAN;
        }

        /**
         * Draw tiles to canvas corresponding to lat,lon of current screen.
         */
        @Override  // Chart
        public void DrawOnCanvas (Canvas canvas)
        {
            wairToNow.openStreetMap.Draw (canvas, pmap, ChartView.this, canvasHdgRads);
        }

        /**
         * Screen is being closed, close all open bitmaps.
         */
        @Override  // LoadedBitmap
        public void CloseBitmap ()
        {
            wairToNow.openStreetMap.CloseBitmaps ();
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
            int w = (int) ((east  - west)  * 4.0);    // width in 15' increments
            int h = (int) ((north - south) * 4.0);    // height in 15' increments
            int x = (int) ((lon  - west) * 4.0);      // X to right of west in 15' increments
            int y = (int) ((north - lat) * 4.0) - 1;  // Y below north in 15' increments
            if ((x < 0) || (x >= w)) return -1;
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
    private class ChartSelectView extends ScrollView implements WairToNow.CanBeMainView {
        private RadioGroup rg;
        public ChartSelectView ()
        {
            super (wairToNow);
            rg = new RadioGroup (wairToNow);
            addView (rg);
        }

        /**
         * Build button list based on what charts cover the current position
         * and display them.
         */
        public void Show ()
        {
            boolean gotOne = Build ();
            if (!gotOne) {
                TextView tv = new TextView (wairToNow);
                tv.setText ("no charts for this location");
                wairToNow.SetTextSize (tv);
                rg.addView (tv);
            }
            wairToNow.SetCurrentTab (this);
        }

        /**
         * Build button list based on what charts cover the current position
         */
        public boolean Build ()
        {
            /*
             * Find charts that cover each of the corners.
             * Street chart coverts the whole world so it is always downloaded.
             */
            TreeMap<String,Chart> downloadedCharts = new TreeMap<> ();
            TreeMap<String,MaintView.PossibleChart> possibleCharts = new TreeMap<> ();
            downloadedCharts.put (streetChart.basename, streetChart);

            boolean autoSecHit = false;
            boolean autoWacHit = false;
            for (MaintView.Downloadable dl : wairToNow.maintView.allDownloadables) {
                if (!(dl instanceof MaintView.PossibleChart)) continue;
                MaintView.PossibleChart pc = (MaintView.PossibleChart) dl;
                for (LatLon ccLatLon : canvasCornersLatLon) {
                    if (pc.LatLon2Pixel (ccLatLon.lat, ccLatLon.lon, null)) {
                        String spn = pc.spacename;
                        Chart chart = null;
                        for (Chart c : allDownloadedAirCharts) {
                            if (c.basename.startsWith (spn + " ")) {
                                chart = c;
                                break;
                            }
                        }
                        downloadedCharts.put (spn, chart);
                        possibleCharts.put (spn, pc);

                        if (!autoSecHit && spn.contains ("SEC") && pc.LatLonIsCharted (ccLatLon.lat, ccLatLon.lon)) {
                            downloadedCharts.put (autoSecAirChart.basename, autoSecAirChart);
                            autoSecHit = true;
                        }
                        if (!autoWacHit && spn.contains ("WAC") && pc.LatLonIsCharted (ccLatLon.lat, ccLatLon.lon)) {
                            downloadedCharts.put (autoWacAirChart.basename, autoWacAirChart);
                            autoWacHit = true;
                        }
                        break;
                    }
                }
            }

            /*
             * Make a button for each chart found.
             * List them in alphabetical order.
             */
            rg.removeAllViews ();
            boolean gotOne = false;
            for (String spacename : downloadedCharts.keySet ()) {
                gotOne = true;
                rg.addView (new ChartSelectButton (
                            possibleCharts.get (spacename),
                            downloadedCharts.get (spacename)
                ));
            }
            return gotOne;
        }

        // CanBeMainView implementation
        public String GetTabName () { return "Chart"; }
        public void OpenDisplay () { }
        public void CloseDisplay () { }
        public View GetBackPage () { return ChartView.this; }
        public void ReClicked ()
        {
            wairToNow.SetCurrentTab (ChartView.this);
        }
    }

    /**
     * Button used to select a particular chart for display.
     * 'chart' being null means the chart isn't downloaded.
     */
    private class ChartSelectButton extends RadioButton implements OnClickListener, DialogInterface.OnClickListener {
        private Chart chart;
        private MaintView.PossibleChart possibleChart;

        public ChartSelectButton (MaintView.PossibleChart pc, Chart c)
        {
            super (wairToNow);

            possibleChart = pc;
            chart = c;

            setChecked ((chart != null) && (chart == selectedChart));

            setText ((pc != null) ? pc.spacename : c.basename);
            wairToNow.SetTextSize (this);
            UpdateTextColor ();

            setOnClickListener (this);
        }

        public void UpdateTextColor ()
        {
            setTextColor ((chart == null) ? Color.WHITE : chart.DownldLinkColor ());
        }

        /**
         * Clicked on the radio button to select the chart.
         * If the chart is not downloaded, ask user if they want it downloaded.
         */
        @Override  // [Button.]OnClickListener
        public void onClick (View v)
        {
            if (isChecked ()) {
                if (chart != null) {
                    selectedChart = chart;
                    wairToNow.SetCurrentTab (ChartView.this);
                    ChartView.this.invalidate ();
                } else {
                    setChecked (false);
                    AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                    adb.setMessage ("Download " + possibleChart.spacename + "?");
                    adb.setPositiveButton ("OK", this);
                    adb.setNegativeButton ("Cancel", null);
                    adb.show ();
                }
            }
        }

        /**
         * Clicked the OK to download chart button.
         */
        @Override  // DialogInterface.OnClickListener
        public void onClick (DialogInterface dialogInterface, int i)
        {
            possibleChart.DownloadSingle ();
        }
    }
}
