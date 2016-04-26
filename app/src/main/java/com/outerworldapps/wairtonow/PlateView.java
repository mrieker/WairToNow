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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * This view presents the plate to the user (apt diagram, iap, sid, star, etc),
 * and allows scaling and translation by finger gestures.
 * APDs and IAPs have georef info so can present the current position.
 */
public class PlateView extends LinearLayout implements WairToNow.CanBeMainView {
    private final static String TAG = "WairToNow";
    private final static boolean disableWebServer = true;
    private final static int GEOREFBOX = 64;
    private final static float GEOREFDIMND = 1;
    private final static int GEOREFMAXNM = 50;
    private final static int MAXZOOMIN = 16;
    private final static int MAXZOOMOUT = 8;
    private final static String GeoRefDBName = "georefs.db";
    private final static String GeoRefLastPath = WairToNow.dbdir + "/georefs.last";

    private static class IAPGeoRefMap {
        public boolean manual;      // true: manually entered; false: machine detected
        public boolean used;        // being used to map latlon->pixels
        public boolean valid;       // pixels[] array verifies with actual image
        public float lat, lon;      // fix's lat/lon
        public int pixelx, pixely;  // bitmap pixel co-ordinate for the fix
        public byte[] pixels;       // pixels in vacinity of fix when saved
        public String ident;        // fix id
    }

    private final static byte[] nullblob = new byte[0];

    private boolean full;
    private int expdate;                // plate expiration date, eg, 20150917
    private PlateImage plateImage;      // displays the plate image bitmap
    private String plateid;             // which plate, eg, "IAP-LOC RWY 16"
    private WairToNow wairToNow;
    private WaypointView waypointView;  // airport waypoint page we are part of
    private Waypoint.Airport airport;   // which airport, eg, "KBVY"

    private final static String[] columns_apdgeorefs1 = new String[] { "gr_wfta", "gr_wftb", "gr_wftc", "gr_wftd", "gr_wfte", "gr_wftf" };
    private final static String[] columns_georefs1 = new String[] { "rowid", "gr_icaoid", "gr_plate", "gr_waypt", "gr_bmpixx", "gr_bmpixy", "gr_zpixels" };
    private final static String[] columns_georefs2 = new String[] { "gr_waypt", "gr_bmpixx", "gr_bmpixy", "gr_zpixels" };
    private final static String[] columns_iapgeorefs1 = new String[] { "gr_waypt", "gr_bmpixx", "gr_bmpixy" };

    public PlateView (WaypointView wv, String fn, Waypoint.Airport aw, String pd, int ex, boolean fu)
    {
        super (wv.wairToNow);
        full = fu;
        waypointView = wv;
        wairToNow = wv.wairToNow;
        construct (fn, aw, pd, ex);
    }
    public PlateView (WairToNow wtn, String fn, Waypoint.Airport aw, String pd, int ex, boolean fu)
    {
        super (wtn);
        full = fu;
        waypointView = null;
        wairToNow = wtn;
        construct (fn, aw, pd, ex);
    }
    private void construct (String fn, Waypoint.Airport aw, String pd, int ex)
    {
        airport = aw;  // airport, eg, "KBVY"
        plateid = pd;  // plate descrip, eg, "IAP-LOC RWY 16", "APD-AIRPORT DIAGRAM", "MIN-ALTERNATE MINIMUMS", etc
        expdate = ex;

        setOrientation (VERTICAL);

        if (plateid.startsWith ("APD-")) {
            plateImage = new APDPlateImage (fn);
        } else if (plateid.startsWith ("IAP-")) {
            wairToNow.lastPlate_fn = fn;
            wairToNow.lastPlate_aw = aw;
            wairToNow.lastPlate_pd = pd;
            wairToNow.lastPlate_ex = ex;
            plateImage = new IAPPlateImage (fn);
        } else if (plateid.startsWith ("RWY-")) {
            plateImage = new RWYPlateImage ();
        } else {
            plateImage = new NGRPlateImage (fn);
        }

        plateImage.setLayoutParams (new LayoutParams (LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView (plateImage);
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return waypointView.GetTabName ();
    }

    /**
     * Close any open bitmaps so we don't take up too much memory.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        wairToNow.getWindow ().clearFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        plateImage.CloseBitmaps ();
    }

    /**
     * Maybe we should keep screen powered up while plate is being displayed.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        if (wairToNow.optionsView.powerLockOption.checkBox.isChecked ()) {
            wairToNow.getWindow ().addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * If back arrow pressed when viewing a plate,
     * show the waypoint view page.
     */
    @Override  // WairToNow.GetBackPage
    public View GetBackPage ()
    {
        return waypointView;
    }

    /**
     * Tab re-clicked, switch back to search screen.
     */
    @Override
    public void ReClicked ()
    {
        wairToNow.SetCurrentTab (waypointView);
    }

    /**
     * Find waypoint by name in database.
     * If more than one found, pick one closest to this airport.
     */
    private Waypoint FindWaypoint (String wayptid)
    {
        // see if there is a [dmedist/hdgtrue suffix on the waypoint identifier
        // if so, save the values and strip it off
        float dmedist = 0;
        float hdgtrue = 0;
        int j = -1;
        int i = wayptid.indexOf ('[');
        if (i >= 0) j = wayptid.indexOf ('/', i);
        if (j >= 0) {
            dmedist = Float.parseFloat (wayptid.substring (i + 1, j));
            hdgtrue = Float.parseFloat (wayptid.substring (j + 1));
            wayptid = wayptid.substring (0, i);
        }

        // if it might be a runway, look it up in the current airport
        Waypoint bestwp = null;
        if (wayptid.startsWith ("RW")) {
            String rwno = wayptid.substring (2);
            HashMap<String,Waypoint.Runway> rws = airport.GetRunways ();
            bestwp = rws.get (rwno);
            if (bestwp == null) bestwp = rws.get ("0" + rwno);
        }

        if (bestwp == null) {

            // not a runway, look up identifier as given
            float bestnm = GEOREFMAXNM;
            LinkedList<Waypoint> wps = Waypoint.GetWaypointsByIdent (wayptid);
            for (Waypoint wp : wps) {
                float nm = Lib.LatLonDist (wp.lat, wp.lon, airport.lat, airport.lon);
                if (bestnm > nm) {
                    bestnm = nm;
                    bestwp = wp;
                }
            }

            // maybe it is a localizer witout the _ after the I
            if (wayptid.startsWith ("I") && !wayptid.startsWith ("I-")) {
                wps = Waypoint.GetWaypointsByIdent ("I-" + wayptid.substring (1));
                for (Waypoint wp : wps) {
                    float nm = Lib.LatLonDist (wp.lat, wp.lon, airport.lat, airport.lon);
                    if (bestnm > nm) {
                        bestnm = nm;
                        bestwp = wp;
                    }
                }
            }
        }

        // apply any dme distance and true heading
        if ((bestwp != null) && (j >= 0)) {
            bestwp = new Waypoint.DMEOffset (bestwp, dmedist, hdgtrue);
        }

        return bestwp;
    }

    /**
     * Display the plate image file.
     * Allows panning and zooming.
     */
    private abstract class PlateImage extends View {
        protected DisplayMetrics metrics = new DisplayMetrics ();

        private ADVPanAndZoom advPanAndZoom;
        private float         unzoomedCanvasWidth;
        private Rect          bitmapRect = new Rect ();    // biggest centered square completely filled by bitmap
        private RectF         canvasRect = new RectF ();   // initially biggest centered square completely filled by display area
                                                           // ... but gets panned/zoomed by user

        protected PlateImage ()
        {
            super (wairToNow);
            advPanAndZoom = new ADVPanAndZoom ();
            wairToNow.getWindowManager ().getDefaultDisplay ().getMetrics (metrics);
        }

        public abstract void CloseBitmaps ();
        protected abstract void GotMouseDown (float x, float y);
        protected abstract void GotMouseUp ();

        /**
         * Callback for mouse events on the image.
         * We use this for scrolling the map around.
         */
        @Override
        public boolean onTouchEvent (@NonNull MotionEvent event)
        {
            return advPanAndZoom.OnTouchEvent (event);
        }

        /**
         * Handle panning and zooming the display.
         */
        private class ADVPanAndZoom extends PanAndZoom {

            public ADVPanAndZoom ()
            {
                super (wairToNow);
            }

            public void MouseDown (float x, float y)
            {
                GotMouseDown (x, y);
            }

            public void MouseUp ()
            {
                GotMouseUp ();
            }

            public void Panning (float x, float y, float dx, float dy)
            {
                canvasRect.left   += dx;
                canvasRect.right  += dx;
                canvasRect.top    += dy;
                canvasRect.bottom += dy;
                invalidate ();
            }

            public void Scaling (float fx, float fy, float sf)
            {
                float canvasWidth = canvasRect.width () * sf;
                if ((canvasWidth > unzoomedCanvasWidth / MAXZOOMOUT) &&
                    (canvasWidth < unzoomedCanvasWidth * MAXZOOMIN)){
                    canvasRect.left   = (canvasRect.left   - fx) * sf + fx;
                    canvasRect.right  = (canvasRect.right  - fx) * sf + fx;
                    canvasRect.top    = (canvasRect.top    - fy) * sf + fy;
                    canvasRect.bottom = (canvasRect.bottom - fy) * sf + fy;
                }
                invalidate ();
            }
        }

        /**
         * Open first plate bitmap image page file and map tp view.
         */
        protected Bitmap OpenFirstPage (String filename)
        {
            Bitmap bm;
            try {
                if (!new File (filename + ".p1").exists ()) {
                    throw new FileNotFoundException (filename + ".p1 not found");
                }
                bm = BitmapFactory.decodeFile (filename + ".p1");
            } catch (Throwable e) {
                Log.w (TAG, "onDraw: bitmap load error", e);
                return null;
            }

            // get bitmap dimensions
            int bmWidth  = bm.getWidth  ();
            int bmHeight = bm.getHeight ();

            // map to view
            SetBitmapMapping (bmWidth, bmHeight);
            return bm;
        }

        /**
         * Set up initial mapping of bitmap->view such that bitmap fills display area.
         * The bitmaps are typically a little taller than the display area, in which case
         * the top and bottom margins of the bitmap will be chopped off.
         */
        protected void SetBitmapMapping (int bmWidth, int bmHeight)
        {
            // get largest square centered on bitmap that is completely filled with something
            // ie, part of bitmap around two of the sides might be trimmed off
            if (bmHeight > bmWidth) {
                bitmapRect.left   = 0;
                bitmapRect.right  = bmWidth;
                bitmapRect.top    = (bmHeight - bmWidth) / 2;
                bitmapRect.bottom = (bmHeight + bmWidth) / 2;
            } else {
                bitmapRect.left   = (bmWidth - bmHeight) / 2;
                bitmapRect.right  = (bmWidth + bmHeight) / 2;
                bitmapRect.top    = 0;
                bitmapRect.bottom = bmHeight;
            }

            // get drawring area dimensions in pixels
            int canWidth  = getWidth  ();
            int canHeight = getHeight ();

            // get drawring area dimensions in inches
            float xdpi = metrics.xdpi;
            float ydpi = metrics.ydpi;
            float canInWidth  = canWidth  / xdpi;
            float canInHeight = canHeight / ydpi;

            // get largest square centered on canvas that can be seen in total
            // ie, there might be unused parts of two of the canvas' margins
            if (canInHeight > canInWidth) {
                canvasRect.left   = 0;
                canvasRect.right  = canWidth;
                canvasRect.top    = ydpi * (canInHeight - canInWidth) / 2;
                canvasRect.bottom = ydpi * (canInHeight + canInWidth) / 2;
            } else {
                canvasRect.left   = xdpi * (canInWidth - canInHeight) / 2;
                canvasRect.right  = xdpi * (canInWidth + canInHeight) / 2;
                canvasRect.top    = 0;
                canvasRect.bottom = canHeight;
            }
            unzoomedCanvasWidth = canvasRect.width ();
        }

        /**
         * Set up initial mapping of bitmap->view such that whole bitmap is visible.
         */
        protected void SetBitmapMapping2 (int bmWidth, int bmHeight, int canWidth, int canHeight)
        {
            // get smallest square centered on bitmap that contains whole bitmap
            // ie, there might be unfilled margins on the sides
            if (bmHeight < bmWidth) {
                bitmapRect.left   = 0;
                bitmapRect.right  = bmWidth;
                bitmapRect.top    = (bmHeight - bmWidth) / 2;
                bitmapRect.bottom = (bmHeight + bmWidth) / 2;
            } else {
                bitmapRect.left   = (bmWidth - bmHeight) / 2;
                bitmapRect.right  = (bmWidth + bmHeight) / 2;
                bitmapRect.top    = 0;
                bitmapRect.bottom = bmHeight;
            }

            // get drawring area dimensions in inches
            float xdpi = metrics.xdpi;
            float ydpi = metrics.ydpi;
            float canInWidth  = canWidth  / xdpi;
            float canInHeight = canHeight / ydpi;

            // get largest square centered on canvas that can be seen in total
            // ie, there might be unused parts of two of the canvas' margins
            if (canInHeight > canInWidth) {
                canvasRect.left   = 0;
                canvasRect.right  = canWidth;
                canvasRect.top    = ydpi * (canInHeight - canInWidth) / 2;
                canvasRect.bottom = ydpi * (canInHeight + canInWidth) / 2;
            } else {
                canvasRect.left   = xdpi * (canInWidth - canInHeight) / 2;
                canvasRect.right  = xdpi * (canInWidth + canInHeight) / 2;
                canvasRect.top    = 0;
                canvasRect.bottom = canHeight;
            }
            unzoomedCanvasWidth = canvasRect.width ();
        }

        /**
         * bitmap <=> view mapping.
         */
        protected float BitmapX2CanvasX (int bitmapX)
        {
            float nx = (bitmapX - bitmapRect.left) / (float) bitmapRect.width ();
            return nx * canvasRect.width () + canvasRect.left;
        }
        protected float BitmapY2CanvasY (int bitmapY)
        {
            float ny = (bitmapY - bitmapRect.top) / (float) bitmapRect.height ();
            return ny * canvasRect.height () + canvasRect.top;
        }

        protected int CanvasX2BitmapX (float canvasX)
        {
            float nx = (canvasX - canvasRect.left) / canvasRect.width ();
            return (int) (nx * bitmapRect.width () + 0.5F) + bitmapRect.left;
        }
        protected int CanvasY2BitmapY (float canvasY)
        {
            float ny = (canvasY - canvasRect.top) / canvasRect.height ();
            return (int) (ny * bitmapRect.height () + 0.5F) + bitmapRect.top;
        }
    }

    /**
     * Single-page georeferenced plates.
     */
    private abstract class GRPlateImage extends PlateImage {
        protected float xfmtfwa, xfmtfwb, xfmtfwc, xfmtfwd, xfmtfwe, xfmtfwf;
        protected float xfmwfta, xfmwftb, xfmwftc, xfmwftd, xfmwfte, xfmwftf;
        protected float mPerBmpPix;

        private Point canPoint   = new Point ();
        private RectF canTmpRect = new RectF ();

        /**
         * Display single-page plate according to current zoom/pan.
         */
        protected void ShowSinglePage (Canvas canvas, Bitmap bitmap)
        {
            /*
             * See where page maps to on canvas.
             */
            canTmpRect.top    = BitmapY2CanvasY (0);
            canTmpRect.bottom = BitmapY2CanvasY (bitmap.getHeight ());
            canTmpRect.left   = BitmapX2CanvasX (0);
            canTmpRect.right  = BitmapX2CanvasX (bitmap.getWidth  ());

            /*
             * Display bitmap to canvas.
             */
            canvas.drawBitmap (bitmap, null, canTmpRect, null);
        }

        /**
         * Draw airplane icon showing current location on plate.
         */
        protected boolean DrawLocationArrow (Canvas canvas, boolean isAptDgm)
        {
            if ((xfmwfte == 0.0) && (xfmwftf == 0.0)) return false;

            if (!wairToNow.optionsView.typeBOption.checkBox.isChecked () ||
                    (isAptDgm && (wairToNow.currentGPSSpd < 80.0F * Lib.MPerNM / 3600.0F))) {
                int bitmapX = LatLon2BitmapX (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                int bitmapY = LatLon2BitmapY (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                float canPointX = BitmapX2CanvasX (bitmapX);
                float canPointY = BitmapY2CanvasY (bitmapY);
                canPoint.x = Math.round (canPointX);
                canPoint.y = Math.round (canPointY);
                float canXPerBmpX = BitmapX2CanvasX (bitmapX + 1) - canPointX;
                float canYPerBmpY = BitmapY2CanvasY (bitmapY + 1) - canPointY;
                float mPerCanX = mPerBmpPix / canXPerBmpX;
                float mPerCanY = mPerBmpPix / canYPerBmpY;
                wairToNow.chartView.DrawLocationArrow (canvas, canPoint, 0.0F, mPerCanX, mPerCanY);
            }
            return true;
        }

        /**
         * Set lat/lon => bitmap transform via two example mappings.
         */
        protected float SetLatLon2Bitmap (float ilat, float ilon, float jlat, float jlon,
                                          int ipixelx, int ipixely, int jpixelx, int jpixely)
        {
            float avglatcos = Mathf.cos (Mathf.toRadians (airport.lat));

            float inorthing = ilat;                // 60nm units north of equator
            float ieasting  = ilon * avglatcos;    // 60nm units east of prime meridian
            float jnorthing = jlat;                // 60nm units north of equator
            float jeasting  = jlon * avglatcos;    // 60nm units east of prime meridian

            // determine transform to convert northing/easting to pixel

            //                     [ wfta' wftb' 0 ]
            //  [ east north 1 ] * [ wftc' wftd' 0 ] = [ pixx pixy 1 ]
            //                     [ wfte' wftf' 1 ]

            //   wfta * lon + wftc * lat + wfte = pixx  as given in LatLon2BitmapX()
            //   wftb * lon + wftd * lat + wftf = pixy  as given in LatLon2BitmapY()

            //   wfta' * easting + wftc' * northing + wfte' = pixx
            //   wftb' * easting + wftd' * northing + wftf' = pixy

            // we want to allow only scaling, rotation, translation, but we also want to invert pixy axis:
            //    wfta' + wftd' = 0
            //    wftb' - wftc' = 0

            float[][] mat = new float[][] {
                    new float[] { 1,  0, 0, 1, 0, 0, 0 },
                    new float[] { 0, -1, 1, 0, 0, 0, 0 },
                    new float[] { ieasting, 0, inorthing, 0, 1, 0, ipixelx },
                    new float[] { 0, ieasting, 0, inorthing, 0, 1, ipixely },
                    new float[] { jeasting, 0, jnorthing, 0, 1, 0, jpixelx },
                    new float[] { 0, jeasting, 0, jnorthing, 0, 1, jpixely }
            };
            Lib.RowReduce (mat);

            // save transformation values for LatLon2Bitmap{X,Y}()

            //                  [ wfta wftb 0 ]
            //  [ lon lat 1 ] * [ wftc wftd 0 ] = [ pixx pixy 1 ]
            //                  [ wfte wftf 1 ]

            xfmwfta = mat[0][6];
            xfmwftb = mat[1][6];
            xfmwftc = mat[2][6];
            xfmwftd = mat[3][6];
            xfmwfte = mat[4][6];
            xfmwftf = mat[5][6];

            // save real-world metres per bitmap pixel
            // xfmwft{a,b,c,d} are in pixels/degree

            mPerBmpPix = Lib.NMPerDeg * Lib.MPerNM / Mathf.hypot (xfmwftc, xfmwftd);

            // convert the wfta',wftb' to wfta,wftb include factor to convert longitude => easting
            // wftc',wftd',wfte',wftf' are the same as wftc,wftd,wfte,wftf

            xfmwfta *= avglatcos;
            xfmwftb *= avglatcos;

            // invert for reverse transform

            //                  [ wfta wftb 0 ]
            //  [ lon lat 1 ] * [ wftc wftd 0 ] = [ pixx pixy 1 ]
            //                  [ wfte wftf 1 ]

            //                  [ wfta wftb 0 ]   [ tfwa tfwb 0 ]                     [ tfwa tfwb 0 ]
            //  [ lon lat 1 ] * [ wftc wftd 0 ] * [ tfwc tfwd 0 ] = [ pixx pixy 1 ] * [ tfwc tfwd 0 ]
            //                  [ wfte wftf 1 ]   [ tfwe tfwf 1 ]                     [ tfwe tfwf 1 ]

            //                                    [ tfwa tfwb 0 ]
            //  [ lon lat 1 ] = [ pixx pixy 1 ] * [ tfwc tfwd 0 ]
            //                                    [ tfwe tfwf 1 ]

            //   tfwa * pixx + tfwc * pixy + tfwe = lon
            //   tfwb * pixx + tfwd * pixy + tfwf = lat

            float rev[][] = new float[][] {
                new float[] { xfmwfta, xfmwftb, 0, 1, 0, 0 },
                new float[] { xfmwftc, xfmwftd, 0, 0, 1, 0 },
                new float[] { xfmwfte, xfmwftf, 1, 0, 0, 1 },
            };
            Lib.RowReduce (rev);

            xfmtfwa = rev[0][3];
            xfmtfwb = rev[0][4];
            xfmtfwc = rev[1][3];
            xfmtfwd = rev[1][4];
            xfmtfwe = rev[2][3];
            xfmtfwf = rev[2][4];

            // return rotation angle

            return Mathf.atan2 (xfmwftb, xfmwfta);
        }

        /**
         * Lat/Lon => gif pixel
         */
        protected int LatLon2BitmapX (float lat, float lon)
        {
            return (int) (xfmwfta * lon + xfmwftc * lat + xfmwfte + 0.5);
        }
        protected int LatLon2BitmapY (float lat, float lon)
        {
            return (int) (xfmwftb * lon + xfmwftd * lat + xfmwftf + 0.5);
        }

        /**
         * Gif pixel => Lat/Lon
         */
        protected float BitmapXY2Lat (int bmx, int bmy)
        {
            return xfmtfwb * bmx + xfmtfwd * bmy + xfmtfwf;
        }
        protected float BitmapXY2Lon (int bmx, int bmy)
        {
            return xfmtfwa * bmx + xfmtfwc * bmy + xfmtfwe;
        }
    }

    /**
     * Display the APD plate image file.
     */
    private class APDPlateImage extends GRPlateImage {
        private Bitmap bitmap;    // single plate page
        private Paint rwPaint;
        private String filename;  // name of gif on flash, without ".p<pageno>" suffix

        public APDPlateImage (String fn)
        {
            filename = fn;

            rwPaint = new Paint ();
            rwPaint.setColor (Color.MAGENTA);
            rwPaint.setStrokeWidth (6);
            rwPaint.setStyle (Paint.Style.FILL_AND_STROKE);

            /*
             * Get georeference points downloaded from server.
             */
            String dbname = "plates_" + expdate + ".db";
            try {
                SQLiteDBs sqldb = SQLiteDBs.create (dbname);
                Cursor result = sqldb.query (
                        "apdgeorefs", columns_apdgeorefs1,
                        "gr_icaoid=?", new String[] { airport.ident },
                        null, null, null, null);
                try {
                    if (result.moveToFirst ()) {
                        xfmwfta = result.getFloat (0);
                        xfmwftb = result.getFloat (1);
                        xfmwftc = result.getFloat (2);
                        xfmwftd = result.getFloat (3);
                        xfmwfte = result.getFloat (4);
                        xfmwftf = result.getFloat (5);
                    }
                } finally {
                    result.close ();
                }
            } catch (Exception e) {
                Log.e (TAG, "error reading " + dbname, e);
            }
        }

        /**
         * Close any open bitmaps so we don't take up too much memory.
         */
        public void CloseBitmaps ()
        {
            if (bitmap != null) {
                bitmap.recycle ();
                bitmap = null;
            }
        }

        protected void GotMouseDown (float x, float y) { }
        protected void GotMouseUp () { }

        /**
         * Draw the plate image and any other marks on it we want.
         */
        @Override  // View
        public void onDraw (Canvas canvas)
        {
            /*
             * Get single page's bitmap.
             */
            if (bitmap == null) {
                bitmap = OpenFirstPage (filename);
                if (bitmap == null) return;
            }

            /*
             * Display bitmap to canvas.
             */
            ShowSinglePage (canvas, bitmap);

            /*
             * Draw airplane if we have enough georeference info.
             * Also draw runway centerlines on airport diagrams
             * so user can easily verify georeference info.
             */
            if (DrawLocationArrow (canvas, true)) {
                for (Waypoint.Runway rwy : airport.GetRunways ().values ()) {
                    int begBmX = LatLon2BitmapX (rwy.begLat, rwy.begLon);
                    int begBmY = LatLon2BitmapY (rwy.begLat, rwy.begLon);
                    int endBmX = LatLon2BitmapX (rwy.endLat, rwy.endLon);
                    int endBmY = LatLon2BitmapY (rwy.endLat, rwy.endLon);
                    float begCanX = BitmapX2CanvasX (begBmX);
                    float begCanY = BitmapY2CanvasY (begBmY);
                    float midCanX = (BitmapX2CanvasX (endBmX) + begCanX) / 2.0F;
                    float midCanY = (BitmapY2CanvasY (endBmY) + begCanY) / 2.0F;
                    canvas.drawLine (begCanX, begCanY, midCanX, midCanY, rwPaint);
                }
            }

            if (full) wairToNow.drawGPSAvailable (canvas, this);
        }
    }

    /**
     * Display a runway diagram made up from runway information,
     * and backed by OpenStreetMap tiles.
     */
    private class RWYPlateImage extends GRPlateImage implements DisplayableChart.Invalidatable {

        // one of these per runway half
        private class RwyInfo {
            public Waypoint.Runway rwywp;

            public int surfColor;

            public int begLeftBmpX;
            public int begLeftBmpY;
            public int begRiteBmpX;
            public int begRiteBmpY;

            public int midLeftBmpX;
            public int midLeftBmpY;
            public int midRiteBmpX;
            public int midRiteBmpY;

            public int numsTopBmpX;
            public int numsTopBmpY;

            public Rect numBounds = new Rect ();

            public RwyInfo (Waypoint.Runway rwy)
            {
                rwywp = rwy;

                // get runway dimensions in NM
                float lengthNM = (float) rwy.getLengthFt () / Lib.FtPerM / Lib.MPerNM;
                float widthNM  = (float) rwy.getWidthFt  () / Lib.FtPerM / Lib.MPerNM;

                // get runway midpoint and heading from this end to the midpoint
                float midLat = (rwy.lat + rwy.endLat) / 2.0F;
                float midLon = (rwy.lon + rwy.endLon) / 2.0F;
                float hdgDeg = Lib.LatLonTC (rwy.lat, rwy.lon, midLat, midLon);

                // get lat/lon on centerline of beginning of runway
                float begLat = Lib.LatHdgDist2Lat (midLat, hdgDeg + 180.0F, lengthNM / 2.0F);
                float begLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg + 180.0F, lengthNM / 2.0F);

                // get lat/lon of left & right sides of this end of runway
                float begLeftLat = Lib.LatHdgDist2Lat (begLat, hdgDeg - 90.0F, widthNM / 2.0F);
                float begLeftLon = Lib.LatLonHdgDist2Lon (begLat, begLon, hdgDeg - 90.0F, widthNM / 2.0F);

                float begRiteLat = Lib.LatHdgDist2Lat (begLat, hdgDeg + 90.0F, widthNM / 2.0F);
                float begRiteLon = Lib.LatLonHdgDist2Lon (begLat, begLon, hdgDeg + 90.0F, widthNM / 2.0F);

                // get lat/lon of left & right sides of midpoint on runway
                float midLeftLat = Lib.LatHdgDist2Lat (midLat, hdgDeg - 90.0F, widthNM / 2.0F);
                float midLeftLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg - 90.0F, widthNM / 2.0F);

                float midRiteLat = Lib.LatHdgDist2Lat (midLat, hdgDeg + 90.0F, widthNM / 2.0F);
                float midRiteLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg + 90.0F, widthNM / 2.0F);

                // compute corresponding bitmap pixels
                begLeftBmpX = LatLon2BitmapX (begLeftLat, begLeftLon);
                begLeftBmpY = LatLon2BitmapY (begLeftLat, begLeftLon);
                begRiteBmpX = LatLon2BitmapX (begRiteLat, begRiteLon);
                begRiteBmpY = LatLon2BitmapY (begRiteLat, begRiteLon);

                midLeftBmpX = LatLon2BitmapX (midLeftLat, midLeftLon);
                midLeftBmpY = LatLon2BitmapY (midLeftLat, midLeftLon);
                midRiteBmpX = LatLon2BitmapX (midRiteLat, midRiteLon);
                midRiteBmpY = LatLon2BitmapY (midRiteLat, midRiteLon);

                // compute top center of runway numbers text
                numsTopBmpX = (begLeftBmpX + begRiteBmpX) / 2;
                numsTopBmpY = (begLeftBmpY + begRiteBmpY) / 2;
                rwyPaint.getTextBounds (rwy.number, 0, rwy.number.length (), numBounds);

                // get runway surface color
                String[] infoLine = rwy.getInfoLine ();
                surfColor = Color.LTGRAY;
                for (String infoWord : infoLine) {
                    if (infoWord.contains ("TURF")) surfColor = Color.GREEN;
                }
            }
        }

        // one of these per waypoint within the display area
        private class WpInfo {
            public int bmpX, bmpY;
            public String ident;
            public String type;

            public WpInfo (Waypoint wp)
            {
                bmpX  = LatLon2BitmapX (wp.lat, wp.lon);
                bmpY  = LatLon2BitmapY (wp.lat, wp.lon);
                ident = wp.ident;
                type  = "(" + wp.GetType ().toLowerCase (Locale.US) + ")";
                type  = type.replace ("(localizer:", "(");
                type  = type.replace ("(navaid:", "(");
            }
        }

        private final static float defnm = 0.75F;
        private final static float padnm = 0.25F;

        private boolean firstDraw = true;
        private float longestNM;
        private float minlat;
        private float maxlat;
        private float minlon;
        private float maxlon;
        private float qtrTextHeight;
        private HashMap<String,RwyInfo> runways = new HashMap<> ();
        private HashMap<String,WpInfo> waypoints = new HashMap<> ();
        private int synthWidth;
        private int synthHeight;
        private Paint rwyPaint;
        private Path rwyPath = new Path ();
        private PixelMapper pmap = new PixelMapper ();

        public RWYPlateImage ()
        {
            rwyPaint = new Paint ();
            rwyPaint.setStrokeWidth (2);
            rwyPaint.setStyle (Paint.Style.FILL_AND_STROKE);
            rwyPaint.setTextAlign (Paint.Align.CENTER);
            rwyPaint.setTextSize (wairToNow.textSize);
            Rect bounds = new Rect ();
            rwyPaint.getTextBounds ("M", 0, 1, bounds);
            qtrTextHeight = bounds.height () / 4.0F;

            float avglatcos = Mathf.cos (Mathf.toRadians (airport.lat));
            minlat = airport.lat;
            maxlat = airport.lat;
            minlon = airport.lon;
            maxlon = airport.lon;

            Collection<Waypoint.Runway> rwywps = airport.GetRunways ().values ();
            if (rwywps.isEmpty ()) {
                minlat -= defnm / Lib.NMPerDeg;
                maxlat += defnm / Lib.NMPerDeg;
                minlon -= defnm / Lib.NMPerDeg / avglatcos;
                maxlon += defnm / Lib.NMPerDeg / avglatcos;
                longestNM = (defnm - padnm) * 2.0F;
            } else {
                for (Waypoint.Runway rwywp : rwywps) {
                    if (minlat > rwywp.lat) minlat = rwywp.lat;
                    if (maxlat < rwywp.lat) maxlat = rwywp.lat;
                    if (minlon > rwywp.lon) minlon = rwywp.lon;
                    if (maxlon < rwywp.lon) maxlon = rwywp.lon;
                    float lenNM = rwywp.getLengthFt () / Lib.FtPerM / Lib.MPerNM;
                    if (longestNM < lenNM) longestNM = lenNM;
                }
                minlat -= padnm / Lib.NMPerDeg;
                maxlat += padnm / Lib.NMPerDeg;
                minlon -= padnm / Lib.NMPerDeg / avglatcos;
                maxlon += padnm / Lib.NMPerDeg / avglatcos;
            }

            synthWidth  = (int) ((maxlon - minlon) * 32768 * avglatcos + 0.5);
            synthHeight = (int) ((maxlat - minlat) * 32768 + 0.5);

            // set up lat/lon => bitmap transformation
            SetLatLon2Bitmap (
                    minlat, minlon, // lower left
                    maxlat, maxlon, // upper right
                    0, synthHeight, // lower left
                    synthWidth, 0); // upper right

            // now that mapping is set up, save runway placement info
            for (Waypoint.Runway rwywp : airport.GetRunways ().values ()) {
                runways.put (rwywp.number, new RwyInfo (rwywp));
            }

            // find waypoints within the area depicted
            // we don't want fixes as there are a lot of them adding clutter
            Collection<Waypoint> wps = new Waypoint.Within ().Get (minlat, maxlat, minlon, maxlon);
            for (Waypoint wp : wps) {
                String type = wp.GetType ();
                if (!type.startsWith ("FIX")) waypoints.put (wp.ident, new WpInfo (wp));
            }
        }

        public void CloseBitmaps ()
        {
            wairToNow.openStreetMap.CloseBitmaps ();
        }

        protected void GotMouseDown (float x, float y) { }
        protected void GotMouseUp () { }

        /**
         * Draw the plate image and any other marks on it we want.
         */
        @Override  // View
        public void onDraw (Canvas canvas)
        {
            /*
             * Draw Open Street Map as background.
             */
            int width  = getWidth ();
            int height = getHeight ();
            CalcOSMBackground (width, height);
            wairToNow.openStreetMap.Draw (canvas, pmap, this, 0.0F);

            /*
             * Show debugging lines through airport reference point.
             */
            /*{
                rwyPaint.setColor (Color.WHITE);
                float aptCanX = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon));
                float aptCanY = BitmapY2CanvasY (LatLon2BitmapY (airport.lat, airport.lon));
                canvas.drawLine (0, aptCanY, width, aptCanY, rwyPaint);
                canvas.drawLine (aptCanX, 0, aptCanX, height, rwyPaint);
            }*/

            /*
             * Draw scale.
             */
            /*
            rwyPaint.setColor (Color.CYAN);
            float aptlatcos = Mathf.cos (Mathf.toRadians (airport.lat));
            float smFactor  = wairToNow.optionsView.ktsMphOption.getAlt () ? Lib.SMPerNM : 1.0;
            float canx0     = BitmapX2CanvasX (0);
            float cany0     = BitmapY2CanvasY (synthHeight);
            float canx0lim  = canx0 + metrics.xdpi / 4;
            float cany0lim  = cany0 - metrics.ydpi / 4;
            int   nsteps    = (int) Mathf.ceil (longestNM * smFactor / stepMi / 2.0);
            for (int step = 0; step <= nsteps; step ++) {
                String str = Float.toString (step * stepMi);
                int strlen = str.length ();

                float dlatdeg = step * stepMi / smFactor / Lib.NMPerDeg;
                float dlondeg = dlatdeg / aptlatcos;

                // left column
                rwyPaint.setTextAlign (Paint.Align.LEFT);
                float cany1 = BitmapY2CanvasY (LatLon2BitmapY (airport.lat + dlatdeg, airport.lon)) + qtrTextHeight * 2;
                canvas.drawText (str, 0, strlen, canx0, cany1, rwyPaint);
                float cany2 = BitmapY2CanvasY (LatLon2BitmapY (airport.lat - dlatdeg, airport.lon)) + qtrTextHeight * 2;
                if (cany2 < cany0lim) canvas.drawText (str, 0, strlen, canx0, cany2, rwyPaint);

                // bottom row
                rwyPaint.setTextAlign (Paint.Align.CENTER);
                float canx1 = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon + dlondeg));
                canvas.drawText (str, 0, strlen, canx1, cany0, rwyPaint);
                float canx2 = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon - dlondeg));
                if (canx2 > canx0lim) canvas.drawText (str, 0, strlen, canx2, cany0, rwyPaint);
            }
            */

            /*
             * Draw rectangles for each runway.
             */
            for (RwyInfo rwy : runways.values ()) {

                // compute corresponding canvas pixels
                float begLeftCanX = BitmapX2CanvasX (rwy.begLeftBmpX);
                float begLeftCanY = BitmapY2CanvasY (rwy.begLeftBmpY);
                float begRiteCanX = BitmapX2CanvasX (rwy.begRiteBmpX);
                float begRiteCanY = BitmapY2CanvasY (rwy.begRiteBmpY);

                float midLeftCanX = BitmapX2CanvasX (rwy.midLeftBmpX);
                float midLeftCanY = BitmapY2CanvasY (rwy.midLeftBmpY);
                float midRiteCanX = BitmapX2CanvasX (rwy.midRiteBmpX);
                float midRiteCanY = BitmapY2CanvasY (rwy.midRiteBmpY);

                // draw rectangle for this runway half
                rwyPath.reset ();
                rwyPath.moveTo (begLeftCanX, begLeftCanY);
                rwyPath.lineTo (begRiteCanX, begRiteCanY);
                rwyPath.lineTo (midRiteCanX, midRiteCanY);
                rwyPath.lineTo (midLeftCanX, midLeftCanY);
                rwyPath.lineTo (begLeftCanX, begLeftCanY);

                rwyPaint.setColor (rwy.surfColor);
                canvas.drawPath (rwyPath, rwyPaint);
            }

            /*
             * Draw waypoints.
             */
            rwyPaint.setColor (Color.MAGENTA);
            rwyPaint.setTextAlign (Paint.Align.LEFT);
            for (WpInfo wpInfo : waypoints.values ()) {
                float canX = BitmapX2CanvasX (wpInfo.bmpX);
                float canY = BitmapY2CanvasY (wpInfo.bmpY);
                canvas.drawCircle (canX, canY, qtrTextHeight, rwyPaint);
                canX += qtrTextHeight * 2;
                canY -= qtrTextHeight;
                canvas.drawText (wpInfo.ident, 0, wpInfo.ident.length (), canX, canY, rwyPaint);
                canY += qtrTextHeight * 6;
                canvas.drawText (wpInfo.type, 0, wpInfo.type.length (), canX, canY, rwyPaint);
            }

            /*
             * Draw runway numbers.
             */
            rwyPaint.setColor (Color.RED);
            rwyPaint.setTextAlign (Paint.Align.CENTER);
            for (RwyInfo rwy : runways.values ()) {
                canvas.save ();
                float canX = BitmapX2CanvasX (rwy.numsTopBmpX);
                float canY = BitmapY2CanvasY (rwy.numsTopBmpY);
                float deg  = rwy.rwywp.trueHdg;
                canvas.rotate (deg, canX, canY);
                canvas.drawText (rwy.rwywp.number, 0, rwy.rwywp.number.length (), canX, canY + rwy.numBounds.height () * 1.5F, rwyPaint);
                canvas.restore ();
            }

            /*
             * Draw airplane.
             */
            DrawLocationArrow (canvas, true);

            if (full) wairToNow.drawGPSAvailable (canvas, this);
        }

        /**
         * Calculate OpenStreetMap tile mapping to the canvas.
         * @param canWidth  = width of the canvas being drawn on
         * @param canHeight = height of the canvas being drawn on
         * returns with pmap filled in with pixel mapping
         */
        public void CalcOSMBackground (int canWidth, int canHeight)
        {
            /*
             * If first time calling onDraw(), set up initial bitmap=>canvas mapping.
             * User can then pan/zoom after this.
             */
            if (firstDraw) {
                SetBitmapMapping2 (synthWidth, synthHeight, canWidth, canHeight);
                firstDraw = false;
            }

            int leftbmx = CanvasX2BitmapX (0);
            int ritebmx = CanvasX2BitmapX (canWidth);
            int topbmy  = CanvasY2BitmapY (0);
            int botbmy  = CanvasY2BitmapY (canHeight);

            float tllat = BitmapXY2Lat (leftbmx, topbmy);
            float tllon = BitmapXY2Lon (leftbmx, topbmy);
            float trlat = BitmapXY2Lat (ritebmx, topbmy);
            float trlon = BitmapXY2Lon (ritebmx, topbmy);
            float bllat = BitmapXY2Lat (leftbmx, botbmy);
            float bllon = BitmapXY2Lon (leftbmx, botbmy);
            float brlat = BitmapXY2Lat (ritebmx, botbmy);
            float brlon = BitmapXY2Lon (ritebmx, botbmy);

            pmap.setup (canWidth, canHeight, tllat, tllon, trlat, trlon, bllat, bllon, brlat, brlon);
        }
    }

    /**
     * Get default (not zoomed or panned) runway diagram mapping of OpenStreetMap tiles.
     */
    public static PixelMapper GetRWYPlateImageDefaultOSMMapping (Waypoint.Airport apt, WairToNow wairToNow)
    {
        PlateView pv = new PlateView (wairToNow, null, apt, "RWY-RUNWAY", 99999999, false);
        int width  = wairToNow.displayWidth;
        int height = wairToNow.displayHeight;
        ((RWYPlateImage) pv.plateImage).CalcOSMBackground (width, height);
        return ((RWYPlateImage) pv.plateImage).pmap;
    }

    /**
     * Display the IAP plate image file.
     * Also display any associated georef info.
     */
    public class IAPPlateImage extends GRPlateImage {
        private Bitmap      bitmap;                           // single plate page
        private boolean     showIAPEditButtons;               // show/hide georef editing toolbar row
        private GeoRefIdent geoRefIdent;                      // georef identifier name input box
        private GeoRefInput iapGeoRefInput;                   // the whole georef editing toolbar row
        private int         bmHeight;                         // height of one image
        private int         bmWidth;                          // width of one image
        private int         crosshairBMX;                     // where georef crosshairs are in bitmap co-ords
        private int         crosshairBMY;
        private Paint       editButtonPaint  = new Paint ();  // draws IAP georef edit tb hide/show button
        private Paint       geoRefPaint;                      // paints georef stuff on plate image
        private Path        diamond          = new Path  ();  // used to draw waypoint diamond shape
        private Path        editButtonPath   = new Path  ();
        private PlateDME    plateDME;
        private PlateTimer  plateTimer;
        private RectF       editButtonBounds = new RectF ();  // where IAP georef edit tb hide/show button is on canvas
        private String      filename;                         // name of gifs on flash, without ".p<pageno>" suffix
        private TextView    geoRefInteg;                      // georef geometry integrity string

        private HashMap<String,IAPGeoRefMap> iapGeoRefMaps = new HashMap<> ();
                                                              // georef points associated with this IAP plate
                                                              // should match what's in local database

        public IAPPlateImage (String fn)
        {
            filename = fn;

            geoRefPaint = new Paint ();
            geoRefPaint.setStrokeWidth (5);
            geoRefPaint.setStyle (Paint.Style.FILL_AND_STROKE);
            geoRefPaint.setTextAlign (Paint.Align.CENTER);
            geoRefPaint.setTextSize (wairToNow.textSize);

            plateDME   = new PlateDME (wairToNow, this, airport.ident, plateid);
            plateTimer = new PlateTimer (wairToNow, this);

            /*
             * Get any georeference points in database.
             */
            GetIAPGeoRefPoints ();

            /*
             * Allow manual entry of waypoints for georeferencing.
             */
            DetentHorizontalScrollView hsv = new DetentHorizontalScrollView (wairToNow);
            wairToNow.SetDetentSize (hsv);
            hsv.setLayoutParams (new LayoutParams (LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            iapGeoRefInput = new GeoRefInput ();
            iapGeoRefInput.setVisibility (GONE);
            hsv.addView (iapGeoRefInput);
            PlateView.this.addView (hsv);
        }

        // used by PlateDME
        public Waypoint FindWaypoint (String wayptid)
        {
            return PlateView.this.FindWaypoint (wayptid);
        }

        /**
         * Read previously manually entered georeference points for this plate.
         * Also get any machine-detected points downloaded from the server.
         * Manual points override machine points of same name.
         */
        private void GetIAPGeoRefPoints ()
        {
            boolean manual = false;
            String machinedbname = "plates_" + expdate + ".db";
            do {
                try {
                    SQLiteDBs sqldb = manual ? OpenGeoRefDB () : SQLiteDBs.create (machinedbname);
                    Cursor result = sqldb.query (
                            manual ? "georefs" : "iapgeorefs",
                            manual ? columns_georefs2 : columns_iapgeorefs1,
                            "gr_icaoid=? AND gr_plate=?", new String[] { airport.ident, plateid },
                            null, null, null, null);
                    try {
                        if (result.moveToFirst ()) {
                            do {
                                String id = result.getString (0);
                                Waypoint wp = FindWaypoint (id);
                                if (wp != null) {
                                    int pixelx = result.getInt (1);
                                    int pixely = result.getInt (2);
                                    if ((pixelx != 0) || (pixely != 0)) {
                                        IAPGeoRefMap grm = new IAPGeoRefMap ();
                                        grm.ident  = wp.ident;
                                        grm.pixelx = pixelx;
                                        grm.pixely = pixely;
                                        grm.lat    = wp.lat;
                                        grm.lon    = wp.lon;
                                        grm.manual = manual;
                                        grm.valid  = !manual;
                                        grm.pixels = manual ? Decompress (result.getBlob (3)) : null;
                                        iapGeoRefMaps.put (wp.ident, grm);
                                    } else {
                                        iapGeoRefMaps.remove (wp.ident);
                                    }
                                }
                            } while (result.moveToNext ());
                        }
                    } finally {
                        result.close ();
                    }
                } catch (Exception e) {
                    Log.e (TAG, "error reading " + (manual ? GeoRefDBName : machinedbname), e);
                }
                manual = !manual;
            } while (manual);

            // can't set xfmwft* until we check validity of manual points
        }

        /**
         * Someone just manually located a fix on the IAP plate.
         * Update the screen, send to webserver and write to local database.
         * @param ident = fix id
         * @param lat   = fix's latitude
         * @param lon   = fix's longitude
         * @param pixx  = fix's bitmap X-coord
         * @param pixy  = fix's bitmap Y-coord
         */
        private void PutIAPGeoRefPoint (String ident, float lat, float lon, int pixx, int pixy)
        {
            IAPGeoRefMap grm = new IAPGeoRefMap ();
            grm.ident  = ident;
            grm.lat    = lat;
            grm.lon    = lon;
            grm.pixelx = pixx;
            grm.pixely = pixy;
            grm.valid  = true;
            grm.manual = true;

            /*
             * Update the plate display.
             */
            iapGeoRefMaps.put (ident, grm);
            UpdateIAPGeoRefs ();
            invalidate ();

            /*
             * Get copy of pixels surrounding waypoint so we can validate it when plate is updated.
             * If plate gets changed next update cycle, this waypoint will show in red and the user
             * will have to update the waypoint's position on the plate.
             */
            byte[] blob = new byte[GEOREFBOX*GEOREFBOX*4];
            int pbi = 0;
            for (int dy = -GEOREFBOX; dy < GEOREFBOX; dy ++) {
                int bmy = pixy + dy;
                for (int dx = -GEOREFBOX; dx < GEOREFBOX; dx ++) {
                    int bmx = pixx + dx;
                    byte p = 0;
                    if ((bmx >= 0) && (bmx < bmWidth) && (bmy >= 0) && (bmy < bmHeight)) {
                        p = Byte666 (bitmap.getPixel (bmx, bmy));
                    }
                    blob[pbi++] = p;
                }
            }
            byte[] zblob = Compress (blob);

            /*
             * Try to send it all to webserver for permanent storage.
             * If not currently accessible, we will try to send it next time a plate is opened.
             */
            int saved = SaveGeoRefToWebserver (airport.ident, plateid, ident, pixx, pixy, zblob);

            /*
             * Write georef data to local database file.
             */
            try {
                SQLiteDBs sqldb = OpenGeoRefDB ();
                WriteGeoRefRec (sqldb, airport.ident, plateid, ident, pixx, pixy, saved, zblob);
            } catch (Exception e) {
                Log.e (TAG, "error writing " + GeoRefDBName, e);
            }
        }

        /**
         * Someone wants to delete a fix from the IAP plate.
         * Update the screen, send to webserver and write to local database.
         * @param ident = fix id
         */
        public void DelIAPGeoRefPoint (String ident)
        {
            IAPGeoRefMap grm = iapGeoRefMaps.get (ident);
            if (grm != null) {

                /*
                 * Update the plate display.
                 */
                iapGeoRefMaps.remove (ident);
                UpdateIAPGeoRefs ();
                invalidate ();

                /*
                 * Try to send it all to webserver for permanent storage.
                 * If not currently accessible, we will try to send it next time a plate is opened.
                 */
                int saved = SaveGeoRefToWebserver (airport.ident, plateid, ident, 0, 0, nullblob);

                /*
                 * Delete georef data from local database file.
                 */
                try {
                    SQLiteDBs sqldb = OpenGeoRefDB ();
                    WriteGeoRefRec (sqldb, airport.ident, plateid, ident, 0, 0, saved, nullblob);
                } catch (Exception e) {
                    Log.e (TAG, "error writing " + GeoRefDBName, e);
                }
            }
        }

        /**
         * Close any open bitmaps so we don't take up too much memory.
         */
        public void CloseBitmaps ()
        {
            if (bitmap != null) {
                bitmap.recycle ();
                bitmap = null;
            }
        }

        protected void GotMouseDown (float x, float y)
        {
            if (editButtonBounds.contains (x, y)) {
                ShowHideEditToolbar ();
            }
            plateDME.GotMouseDown (x, y);
            plateTimer.GotMouseDown (x, y);
        }

        protected void GotMouseUp ()
        {
            plateDME.GotMouseUp ();
        }

        /**
         * Toggle showing/hiding IAP georef editing toolbar.
         */
        private void ShowHideEditToolbar ()
        {
            // show/hide toolbar itself
            showIAPEditButtons = !showIAPEditButtons;
            iapGeoRefInput.setVisibility (showIAPEditButtons ? VISIBLE : GONE);

            // initially position crosshairs in center of screen
            if (showIAPEditButtons) {
                crosshairBMX = CanvasX2BitmapX (getWidth  () / 2);
                crosshairBMY = CanvasY2BitmapY (getHeight () / 2);
            }

            // re-draw litte show/hide arrow button in its new state
            invalidate ();
        }

        /**
         * Draw the plate image and any other marks on it we want.
         */
        @Override  // View
        public void onDraw (Canvas canvas)
        {
            /*
             * Get single page's bitmap.
             */
            if (bitmap == null) {
                bitmap = OpenFirstPage (filename);
                if (bitmap == null) return;
                bmWidth  = bitmap.getWidth  ();
                bmHeight = bitmap.getHeight ();

                /*
                 * Make sure any associated manually entered IAP georefs are still valid
                 * by matching the pixels surrounding the point.
                 */
                for (IAPGeoRefMap grm : iapGeoRefMaps.values ()) {
                    grm.valid = true;
                    if (grm.manual && (grm.pixels != null)) {
                        int i = 0;
                        for (int dy = -GEOREFBOX; dy < GEOREFBOX; dy++) {
                            int y = grm.pixely + dy;
                            for (int dx = -GEOREFBOX; dx < GEOREFBOX; dx++) {
                                int x = grm.pixelx + dx;
                                byte p = 0;
                                if ((x >= 0) && (x < bmWidth) && (y >= 0) && (y < bmHeight)) {
                                    p = Byte666 (bitmap.getPixel (x, y));
                                }
                                if (p != grm.pixels[i++]) {
                                    grm.valid = false;
                                    dx = GEOREFBOX;
                                    dy = GEOREFBOX;
                                }
                            }
                        }
                        grm.pixels = null;
                    }
                }

                /*
                 * Use the valid georefs to map lat/lon -> bitmap pixel.
                 */
                UpdateIAPGeoRefs ();
            }

            /*
             * Display bitmap to canvas.
             */
            ShowSinglePage (canvas, bitmap);

            /*
             * Draw little triangle button at top to hide/show IAP georef edit buttons.
             */
            if (full && !wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
                DrawEditButton (canvas);
            }

            /*
             * Draw diamonds for all defined IAP georeference points.
             *    GREEN : manual, valid and being used
             *   YELLOW : manual, valid but not being used
             *      RED : manual, not valid
             *     BLUE : machine and being used
             *     CYAN : machine but not being used
             * Also draw box and waypoint name if crosshairs enabled.
             */
            if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
                float dimnddx = GEOREFDIMND / Lib.MMPerIn * metrics.xdpi;
                float dimnddy = GEOREFDIMND / Lib.MMPerIn * metrics.ydpi;
                for (IAPGeoRefMap grm : iapGeoRefMaps.values ()) {
                    geoRefPaint.setColor (
                            !grm.manual ? (grm.used ? Color.BLUE : Color.CYAN) :
                            !grm.valid ? Color.RED : !grm.used ? Color.YELLOW : Color.GREEN);
                    float canvasX = BitmapX2CanvasX (grm.pixelx);
                    float canvasY = BitmapY2CanvasY (grm.pixely);
                    diamond.rewind  ();
                    diamond.moveTo  (canvasX, canvasY - dimnddy);
                    diamond.rLineTo ( dimnddx,  dimnddy);
                    diamond.rLineTo (-dimnddx,  dimnddy);
                    diamond.rLineTo (-dimnddx, -dimnddy);
                    diamond.rLineTo ( dimnddx, -dimnddy);
                    canvas.drawPath (diamond, geoRefPaint);

                    if (showIAPEditButtons) {
                        float lox = BitmapX2CanvasX (grm.pixelx - GEOREFBOX);
                        float hix = BitmapX2CanvasX (grm.pixelx + GEOREFBOX);
                        float loy = BitmapY2CanvasY (grm.pixely - GEOREFBOX);
                        float hiy = BitmapY2CanvasY (grm.pixely + GEOREFBOX);
                        canvas.drawLine (lox, loy, hix, loy, geoRefPaint);
                        canvas.drawLine (lox, hiy, hix, hiy, geoRefPaint);
                        canvas.drawLine (lox, loy, lox, hiy, geoRefPaint);
                        canvas.drawLine (hix, loy, hix, hiy, geoRefPaint);
                        float lineHeight = geoRefPaint.getFontSpacing ();
                        canvas.drawText (grm.ident, canvasX, hiy + lineHeight, geoRefPaint);
                    }
                }
            }

            /*
             * Draw runways if editing button enabled for verification.
             */
            if (showIAPEditButtons) {
                geoRefPaint.setColor (Color.MAGENTA);
                HashMap<String,Waypoint.Runway> runways = airport.GetRunways ();
                for (Waypoint.Runway runway : runways.values ()) {
                    int begbmx = LatLon2BitmapX (runway.lat, runway.lon);
                    int begbmy = LatLon2BitmapY (runway.lat, runway.lon);
                    int endbmx = LatLon2BitmapX (runway.endLat, runway.endLon);
                    int endbmy = LatLon2BitmapY (runway.endLat, runway.endLon);
                    float begx = BitmapX2CanvasX (begbmx);
                    float begy = BitmapY2CanvasY (begbmy);
                    float endx = BitmapX2CanvasX (endbmx);
                    float endy = BitmapY2CanvasY (endbmy);
                    canvas.drawLine (begx, begy, endx, endy, geoRefPaint);
                }
            }

            /*
             * Draw any enabled DMEs and timer.
             */
            if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
                plateDME.DrawDMEs (canvas, wairToNow.currentGPSLat, wairToNow.currentGPSLon,
                        wairToNow.currentGPSAlt, wairToNow.currentGPSTime);
            }
            plateTimer.DrawTimer (canvas);

            /*
             * Draw airplane if we have enough georeference info.
             */
            DrawLocationArrow (canvas, false);

            /*
             * Draw IAP georeferencing crosshairs.
             */
            if (showIAPEditButtons) {
                float crosshairCanvasX = BitmapX2CanvasX (crosshairBMX);
                float crosshairCanvasY = BitmapY2CanvasY (crosshairBMY);
                geoRefPaint.setColor (Color.MAGENTA);
                canvas.drawLine (crosshairCanvasX, 0, crosshairCanvasX, getHeight (), geoRefPaint);
                canvas.drawLine (0, crosshairCanvasY, getWidth (), crosshairCanvasY, geoRefPaint);
            }

            /*
             * Draw GPS Available box around plate border.
             */
            if (full && !wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
                wairToNow.drawGPSAvailable (canvas, this);
            }
        }

        /**
         * See which georef points will be used to locate the airplane.
         * Find pairs that give greatest width for longitude and height for latitude.
         */
        private void UpdateIAPGeoRefs ()
        {
            // find two valid points farthest apart
            IAPGeoRefMap besti = null;
            IAPGeoRefMap bestj = null;
            float bestdistxy = 0.0F;
            for (IAPGeoRefMap grmi : iapGeoRefMaps.values ()) {
                grmi.used = false;
                if (grmi.valid) {
                    for (IAPGeoRefMap grmj : iapGeoRefMaps.values ()) {
                        if (grmj.valid) {
                            int dx = grmj.pixelx - grmi.pixelx;
                            int dy = grmj.pixely - grmi.pixely;
                            float distxy = Mathf.hypot (dx, dy);
                            if (bestdistxy < distxy) {
                                bestdistxy = distxy;
                                besti = grmi;
                                bestj = grmj;
                            }
                        }
                    }
                }
            }

            // assume pair not found
            xfmwfta = 0.0F;
            xfmwftb = 0.0F;
            xfmwftc = 0.0F;
            xfmwftd = 0.0F;
            xfmwfte = 0.0F;
            xfmwftf = 0.0F;
            geoRefInteg.setText ("");
            editButtonPaint.setColor (Color.GRAY);

            // see if pair found
            if (besti != null) {

                // let user see which two points were chosen
                besti.used = true;
                bestj.used = true;

                // set up lat/lon => bitmap pixel transformation
                float rotby = SetLatLon2Bitmap (besti.lat, besti.lon, bestj.lat, bestj.lon, besti.pixelx, besti.pixely, bestj.pixelx, bestj.pixely);

                // 1.0: not rotated at all
                // 0.0: downside up
                float upright = 1.0F - Math.abs (rotby) / Mathf.PI;

                geoRefInteg.setText ("geom " + String.format ("%.1f", upright * 100.0) + " %");
                int c = upright > 0.97 ? Color.GREEN : upright > 0.90 ? Color.YELLOW : Color.RED;
                geoRefInteg.setTextColor (c);
                editButtonPaint.setColor (c);
            }
        }

        /**
         * Draw little arrow button to toggle edit button visibility.
         */
        private void DrawEditButton (Canvas canvas)
        {
            float ts = wairToNow.textSize;
            float xc = getWidth () / 2;
            float xl = xc - ts * 2;
            float xr = xc + ts * 2;
            float yt = ts;
            float yb = ts * 2;

            editButtonBounds.left   = xl;
            editButtonBounds.right  = xr;
            editButtonBounds.top    = yt;
            editButtonBounds.bottom = yb;

            editButtonPath.rewind ();
            if (showIAPEditButtons) {
                editButtonPath.moveTo (xl, yb);
                editButtonPath.lineTo (xc, yt);
                editButtonPath.lineTo (xr, yb);
                editButtonPath.lineTo (xl, yb);
            } else {
                editButtonPath.moveTo (xl, yt);
                editButtonPath.lineTo (xc, yb);
                editButtonPath.lineTo (xr, yt);
                editButtonPath.lineTo (xl, yt);
            }
            canvas.drawPath (editButtonPath, editButtonPaint);
        }

        /**
         * IAP GeoRef editing toolbar.
         */
        private class GeoRefInput extends LinearLayout {
            public GeoRefInput ()
            {
                super (wairToNow);
                setOrientation (HORIZONTAL);
                addView (new GeoRefArrow (new int[] { 4, 2, 2, 6, 6, 6, 4, 2 }, 0, -1));  // up
                addView (new GeoRefArrow (new int[] { 4, 6, 2, 2, 6, 2, 4, 6 }, 0,  1));  // down
                addView (new GeoRefArrow (new int[] { 2, 4, 6, 2, 6, 6, 2, 4 }, -1, 0));  // left
                addView (new GeoRefArrow (new int[] { 6, 4, 2, 2, 2, 6, 6, 4 },  1, 0));  // right
                geoRefIdent = new GeoRefIdent ();
                addView (geoRefIdent);
                addView (new GeoRefSave ());
                addView (new GeoRefDelete ());
                geoRefInteg = new TextView (wairToNow);
                wairToNow.SetTextSize (geoRefInteg);
                addView (geoRefInteg);
            }
        }

        private class GeoRefArrow extends Button implements OnClickListener, OnLongClickListener {
            private int deltax, deltay;
            private int[] raw;
            private long downAt, longAt;
            private Path path;

            public GeoRefArrow (int[] logo, int dx, int dy)
            {
                super (wairToNow);
                raw    = logo;
                deltax = dx;
                deltay = dy;
                setOnClickListener (this);
                setOnLongClickListener (this);
                setText (" ");
                setTypeface (Typeface.MONOSPACE);
                wairToNow.SetTextSize (this);
            }

            @Override  // TextView
            public boolean onTouchEvent (@NonNull MotionEvent me)
            {
                switch (me.getActionMasked ()) {
                    case MotionEvent.ACTION_DOWN: {
                        downAt = SystemClock.uptimeMillis ();
                        longAt = 0;
                        break;
                    }
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP: {
                        downAt = 0;
                        longAt = 0;
                        break;
                    }
                }
                return super.onTouchEvent (me);
            }

            @Override  // OnClickListener
            public void onClick (View v)
            {
                Clicked (deltax, deltay);
            }

            @Override  // OnLongClickListener
            public boolean onLongClick (View v)
            {
                longAt = SystemClock.uptimeMillis ();
                LongClicked ();
                return true;
            }

            /**
             * Move the crosshairs by a large amount if button is still down.
             * Then start a timer to move them again by the long press interval.
             */
            public void LongClicked ()
            {
                if (longAt > downAt) {
                    Clicked (deltax * 48, deltay * 48);
                    WairToNow.wtnHandler.runDelayed (longAt - downAt, new Runnable () {
                        @Override
                        public void run ()
                        {
                            LongClicked ();
                        }
                    });
                }
            }

            /**
             * Update crosshairs position by the given amount and re-draw plate image.
             */
            private void Clicked (int dx, int dy)
            {
                crosshairBMX += dx;
                crosshairBMY += dy;
                int w = bitmap.getWidth  () - 1;
                int h = bitmap.getHeight () - 1;
                if (crosshairBMX < 0) crosshairBMX = 0;
                if (crosshairBMY < 0) crosshairBMY = 0;
                if (crosshairBMX > w) crosshairBMX = w;
                if (crosshairBMY > h) crosshairBMY = h;
                IAPPlateImage.this.invalidate ();
            }

            /**
             * Draw the arrow triangle after making sure the button is square.
             */
            @Override
            public void onDraw (@NonNull Canvas canvas)
            {
                super.onDraw (canvas);

                if (path == null) {
                    int size = getHeight ();
                    path = new Path ();
                    for (int i = 0; i < raw.length;) {
                        float x = size * raw[i++] / 8.0F;
                        float y = size * raw[i++] / 8.0F;
                        if (i == 0) path.moveTo (x, y);
                        else path.lineTo (x, y);
                    }
                    setWidth (size);
                } else {
                    canvas.drawPath (path, getPaint ());
                }
            }
        }

        private class GeoRefIdent extends EditText {
            public GeoRefIdent ()
            {
                super (wairToNow);
                setEms (5);
                setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                setSingleLine ();
                wairToNow.SetTextSize (this);
            }
        }

        private class GeoRefSave extends Button implements OnClickListener {
            public GeoRefSave ()
            {
                super (wairToNow);
                setOnClickListener (this);
                setText ("SAVE");
                wairToNow.SetTextSize (this);
            }

            @Override
            public void onClick (View v)
            {
                Waypoint wp = FindWaypoint (geoRefIdent.getText ().toString ().toUpperCase (Locale.US));
                if (wp != null) {
                    geoRefIdent.setText (wp.ident);
                    PutIAPGeoRefPoint (wp.ident, wp.lat, wp.lon, crosshairBMX, crosshairBMY);
                }
            }
        }

        private class GeoRefDelete extends Button implements OnClickListener {
            public GeoRefDelete ()
            {
                super (wairToNow);
                setOnClickListener (this);
                setText ("DELETE");
                wairToNow.SetTextSize (this);
            }

            @Override
            public void onClick (View v)
            {
                DelIAPGeoRefPoint (geoRefIdent.getText ().toString ().toUpperCase (Locale.US));
            }
        }
    }

    /**
     * Display a non-georef multi-page plate image file.
     * Allows panning and zooming.
     */
    private class NGRPlateImage extends PlateImage {
        private Bitmap[] bitmaps;                    // one entry per plate page
        private int      bmHeight;                   // height of one image
        private int      bmWidth;                    // width of one image
        private int      numPages;                   // number of pages in plate
        private RectF    canTmpRect = new RectF ();  // same as canvasRect except shifted for various pages during onDraw()
        private String   filename;                   // name of gifs on flash, without ".p<pageno>" suffix

        public NGRPlateImage (String fn)
        {
            filename = fn;

            /*
             * We are given the base name ending in .gif.
             * But the files are multiple pages ending in .gif.p<pageno>.
             * So count how many pages we actually have for this plate.
             */
            for (int pageno = 1;; pageno ++) {
                File f = new File (filename + ".p" + pageno);
                if (!f.exists ()) break;
                numPages = pageno;
            }
            bitmaps = new Bitmap[numPages];
        }

        /**
         * Close any open bitmaps so we don't take up too much memory.
         */
        public void CloseBitmaps ()
        {
            for (int i = 0; i < numPages; i ++) {
                Bitmap bm = bitmaps[i];
                if (bm != null) bm.recycle ();
                bitmaps[i] = null;
            }
        }

        protected void GotMouseDown (float x, float y) { }
        protected void GotMouseUp () { }

        /**
         * Draw the plate image.
         */
        @Override  // View
        public void onDraw (Canvas canvas)
        {
            /*
             * Get single page's bitmap size.
             * Assume all pages are same size.
             */
            if (bmWidth == 0) {
                bitmaps[0] = OpenFirstPage (filename);
                if (bitmaps[0] == null) return;
                bmWidth  = bitmaps[0].getWidth  ();
                bmHeight = bitmaps[0].getHeight ();
            }

            /*
             * Loop through pages to see which are visible and draw them.
             */
            int canWidth  = getWidth  ();
            int canHeight = getHeight ();
            for (int vpi = 0; vpi < numPages; vpi ++) {
                int imageTop = vpi * bmHeight;
                int imageBot = imageTop + bmHeight;

                /*
                 * See where it maps to on canvas and skip if not visible.
                 */
                canTmpRect.top    = BitmapY2CanvasY (imageTop) + vpi * 2;
                canTmpRect.bottom = BitmapY2CanvasY (imageBot) + vpi * 2;
                if (canTmpRect.top    >= canHeight) continue;
                if (canTmpRect.bottom <= 0)         continue;

                for (int hpi = 0; vpi + hpi < numPages; hpi ++) {
                    int imageLeft = hpi * bmWidth;
                    int imageRite = imageLeft + bmWidth;

                    /*
                     * See where it maps to on canvas and skip if not visible.
                     */
                    canTmpRect.left  = BitmapX2CanvasX (imageLeft) + hpi * 2;
                    canTmpRect.right = BitmapX2CanvasX (imageRite) + hpi * 2;
                    if (canTmpRect.left  >= canWidth) continue;
                    if (canTmpRect.right <= 0)        continue;

                    /*
                     * Some/all of page's image is visible, make sure we have the bitmap loaded.
                     */
                    int pi = vpi + hpi;
                    Bitmap bm = bitmaps[pi];
                    if (bm == null) {
                        String fn = filename + ".p" + (pi + 1);
                        try {
                            if (!new File (fn).exists ()) {
                                throw new FileNotFoundException (fn + " not found");
                            }
                            bm = BitmapFactory.decodeFile (fn);
                        } catch (Throwable e1) {
                            for (int i = 0; i < numPages; i ++) {
                                bm = bitmaps[i];
                                if (bm != null) bm.recycle ();
                                bitmaps[i] = null;
                            }
                            try {
                                if (!new File (fn).exists ()) {
                                    throw new FileNotFoundException (fn + " not found");
                                }
                                bm = BitmapFactory.decodeFile (fn);
                            } catch (Throwable e2) {
                                Log.w (TAG, "NGRPlateImage.onDraw: bitmap load error", e2);
                                continue;
                            }
                        }
                        bitmaps[pi] = bm;
                    }

                    /*
                     * Display bitmap to canvas.
                     */
                    canvas.drawBitmap (bm, null, canTmpRect, null);
                }
            }
        }
    }

    /**
     * Convert a 32-bit ARGB color to a 8-bit RGB color.
     */
    private static byte Byte666 (int pix8888)
    {
        int r = (int) (Color.red   (pix8888) * 6 / 256.0F);
        int g = (int) (Color.green (pix8888) * 6 / 256.0F);
        int b = (int) (Color.blue  (pix8888) * 6 / 256.0F);
        return (byte) (r * 36 + g * 6 + b);
    }

    /**
     * Open georeferencing database, creating it if it doesn't exist.
     * Also try to check for updates from the webserver.
     */
    private static SQLiteDBs OpenGeoRefDB ()
    {
        SQLiteDBs sqldb = SQLiteDBs.create (GeoRefDBName);

        /*
         * Create table if it doesn't already exist.
         */
        if (!sqldb.tableExists ("georefs")) {
            sqldb.execSQL ("CREATE TABLE georefs (gr_icaoid TEXT NOT NULL, gr_plate TEXT NOT NULL, gr_waypt TEXT NOT NULL, gr_bmpixx INTEGER NOT NULL, gr_bmpixy INTEGER NOT NULL, gr_saved INTEGER NOT NULL, gr_zpixels BLOB NOT NULL);");
            sqldb.execSQL ("CREATE UNIQUE INDEX georefuniques ON georefs (gr_icaoid, gr_plate, gr_waypt);");
            sqldb.execSQL ("CREATE INDEX georefbyplate ON georefs (gr_icaoid, gr_plate);");
            sqldb.execSQL ("CREATE INDEX georefuploads ON georefs (gr_saved);");  //TODO: WHERE gr_saved=0
        }

        /*
         * Try to check webserver for updates.
         */
        FetchGeoRefUpdates (sqldb);

        return sqldb;
    }

    /**
     * Get any updates from web server.
     */
    private static void FetchGeoRefUpdates (SQLiteDBs sqldb)
    {
        if (disableWebServer) return;

        /*
         * lastgeorefmark is the ftell() of the end of the server's georefs.csv file that we have seen.
         * Any newer records were appended to that file and we will read the new ones in.
         * If newer records duplicate older ones, the older ones will be overwritten locally.
         */
        int lastgeorefmark = 0;
        try {
            BufferedReader br = new BufferedReader (new FileReader (GeoRefLastPath), 32);
            try {
                lastgeorefmark = Integer.parseInt (br.readLine ());
            } finally {
                br.close ();
            }
        } catch (FileNotFoundException fnfe) {
            Lib.Ignored ();
        } catch (Exception e) {
            Log.w (TAG, "error reading " + GeoRefLastPath, e);
        }

        boolean webreachable = false;
        try {
            URL url = new URL (MaintView.dldir + "/loadgeoref.php?since=" + lastgeorefmark);
            HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
            try {
                httpCon.setRequestMethod ("GET");
                httpCon.connect ();
                int rc = httpCon.getResponseCode ();
                if (rc != HttpURLConnection.HTTP_OK) {
                    throw new IOException ("http response code " + rc + " on loadgeoref.php");
                }
                webreachable = true;
                BufferedReader br = new BufferedReader (new InputStreamReader (httpCon.getInputStream ()), 4096);
                try {
                    lastgeorefmark = Integer.parseInt (br.readLine ());
                    String line;
                    while ((line = br.readLine ()) != null) {
                        String[] cols = Lib.QuotedCSVSplit (line);
                        byte[] zblob = Base64.decode (cols[5], Base64.NO_WRAP);
                        WriteGeoRefRec (sqldb,
                                cols[0], cols[1], cols[2],
                                Integer.parseInt (cols[3]),
                                Integer.parseInt (cols[4]),
                                1, zblob);
                    }
                } finally {
                    br.close ();
                }
            } finally {
                httpCon.disconnect ();
            }
            PrintWriter pw = new PrintWriter (new FileWriter (GeoRefLastPath));
            try {
                pw.println (lastgeorefmark);
            } finally {
                pw.close ();
            }
        } catch (IOException ioe) {
            Log.w (TAG, "error updating georefs from webserver", ioe);
        }

        /*
         * Maybe we have some unsent georefs for the webserver.
         */
        if (webreachable) {

            /*
             * Find all records not saved to webserver.
             */
            Cursor result1 = sqldb.query (
                    "georefs", columns_georefs1,
                    "gr_saved=0", null,
                    null, null, null, null);

            /*
             * Keep track of which ones we are able to save.
             */
            long[] deledRowIDs = new long[result1.getCount()];
            long[] savedRowIDs = new long[result1.getCount()];
            int numDeled = 0;
            int numSaved = 0;

            /*
             * Read through the unsaved records.
             */
            try {
                if (result1.moveToFirst ()) {
                    do {

                        /*
                         * Try to send to webserver.
                         */
                        long rowid    = result1.getLong   (0);
                        String icaoid = result1.getString (1);
                        String plate  = result1.getString (2);
                        String waypt  = result1.getString (3);
                        int bmpixx    = result1.getInt    (4);
                        int bmpixy    = result1.getInt    (5);
                        byte[] zblob  = result1.getBlob   (6);
                        int saved = SaveGeoRefToWebserver (icaoid, plate, waypt, bmpixx, bmpixy, zblob);

                        /*
                         * If unable to send, don't bother with any more as we probably don't have internet.
                         */
                        if (saved == 0) break;

                        /*
                         * Saved to web, update database after done reading.
                         */
                        if (bmpixx == 0) deledRowIDs[numDeled++] = rowid;
                                    else savedRowIDs[numSaved++] = rowid;
                    } while (result1.moveToNext ());
                }
            } finally {
                result1.close ();
            }

            /*
             * Update database for any records just saved to webserver,
             * saying we don't have to try to save them any more.
             */
            for (int i = 0; i < numDeled; i ++) {
                sqldb.delete ("georefs", "rowid=" + deledRowIDs[i], null);
            }
            if (numSaved > 0) {
                ContentValues values = new ContentValues (1);
                values.put ("gr_saved", 1);
                for (int i = 0; i < numSaved; i ++) {
                    long rowid = savedRowIDs[i];
                    sqldb.update ("georefs", values, "rowid=" + rowid, null);
                }
            }
        }
    }

    /**
     * Write georeference waypoint to local database.
     * @param icaoid  = airport the approach is for
     * @param plateid = approach at that airport
     * @param wayptid = waypoint we now the location of
     * @param bmpixx  = where it is on the approach plate (0 means delete from plate)
     * @param bmpixy  = y-coord
     * @param saved   = 0: has not been saved to webserver; 1: has been saved to webserver
     * @param zblob   = compressed bitmap pixels surrounding the waypoint
     */
    private static void WriteGeoRefRec (SQLiteDBs sqldb, String icaoid, String plateid, String wayptid,
                                        int bmpixx, int bmpixy, int saved, byte[] zblob)
    {
        ContentValues values = new ContentValues (7);
        values.put ("gr_icaoid",  icaoid);
        values.put ("gr_plate",   plateid);
        values.put ("gr_waypt",   wayptid);
        values.put ("gr_bmpixx",  bmpixx);
        values.put ("gr_bmpixy",  bmpixy);
        values.put ("gr_saved",   saved);
        values.put ("gr_zpixels", zblob);
        sqldb.insertWithOnConflict ("georefs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Send georeference waypoint to the webserver for permanent storage.
     * @param icaoid  = airport the approach is for
     * @param plateid = approach at that airport
     * @param wayptid = waypoint we now the location of
     * @param bmpixx  = where it is on the approach plate (0 means delete from plate)
     * @param bmpixy  = y-coord
     * @param zblob   = compressed bitmap pixels surrounding the waypoint
     * @return 0: webserver is down, not saved; 1: successfully saved
     */
    private static int SaveGeoRefToWebserver (String icaoid, String plateid, String wayptid,
                                              int bmpixx, int bmpixy, byte[] zblob)
    {
        if (disableWebServer) return 1;

        plateid = Lib.QuotedString (plateid);
        Log.i (TAG, "saving georef " + icaoid + "," + plateid + "," + wayptid + " to webserver");
        try {
            URL url = new URL (MaintView.dldir + "/savegeoref.php");
            HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
            try {
                httpCon.setRequestMethod ("POST");
                httpCon.setDoOutput (true);
                httpCon.setDoInput (true);
                BufferedWriter bw = new BufferedWriter (new OutputStreamWriter (httpCon.getOutputStream (), "UTF-8"));
                try {
                    bw.write (icaoid);
                    bw.write (',');
                    bw.write (plateid);
                    bw.write (',');
                    bw.write (wayptid);
                    bw.write (',');
                    bw.write (Integer.toString (bmpixx));
                    bw.write (',');
                    bw.write (Integer.toString (bmpixy));
                    bw.write (',');
                    bw.write (Base64.encodeToString (zblob, Base64.NO_WRAP));
                } finally {
                    bw.close ();
                }
                httpCon.connect ();
                int rc = httpCon.getResponseCode ();
                if (rc != HttpURLConnection.HTTP_OK) {
                    throw new IOException ("http response code " + rc + " on savegeoref.php");
                }
                BufferedReader br = new BufferedReader (new InputStreamReader (httpCon.getInputStream ()), 32);
                try {
                    String okline = br.readLine ();
                    if (!"OK".equals (okline)) {
                        throw new IOException ("http reply line " + okline + " on savegeoref.php");
                    }
                    return 1;
                } finally {
                    br.close ();
                }
            } finally {
                httpCon.disconnect ();
            }
        } catch (IOException ioe) {
            Log.e (TAG, "error saving georef to webserver", ioe);
        }
        return 0;
    }

    /**
     * Compress a byte array.
     */
    private static byte[] Compress (byte[] blob)
    {
        Deflater compressor = new Deflater ();
        compressor.setLevel (Deflater.BEST_COMPRESSION);
        compressor.setInput (blob);
        compressor.finish ();
        LinkedList<byte[]> bufs = new LinkedList<> ();
        LinkedList<Integer> lens = new LinkedList<> ();
        int tot = 0;
        while (!compressor.finished ()) {
            byte[] buf = new byte[4096];
            int len = compressor.deflate (buf);
            bufs.addLast (buf);
            lens.addLast (len);
            tot += len;
        }
        byte zblob[] = new byte[tot];
        tot = 0;
        for (byte[] buf : bufs) {
            int len = lens.removeFirst ();
            System.arraycopy (buf, 0, zblob, tot, len);
            tot += len;
        }
        return zblob;
    }

    /**
     * Decompress a byte array compressed with Compress().
     */
    private static byte[] Decompress (byte[] zblob) throws DataFormatException
    {
        Inflater decompressor = new Inflater ();
        decompressor.setInput (zblob);
        LinkedList<byte[]> bufs = new LinkedList<> ();
        LinkedList<Integer> lens = new LinkedList<> ();
        int tot = 0;
        while (!decompressor.finished ()) {
            byte[] buf = new byte[4096];
            int len = decompressor.inflate (buf);
            bufs.addLast (buf);
            lens.addLast (len);
            tot += len;
        }
        byte blob[] = new byte[tot];
        tot = 0;
        for (byte[] buf : bufs) {
            int len = lens.removeFirst ();
            System.arraycopy (buf, 0, blob, tot, len);
            tot += len;
        }
        return blob;
    }
}
