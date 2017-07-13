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
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;

/**
 * This view presents the plate to the user (apt diagram, iap, sid, star, etc),
 * and allows scaling and translation by finger gestures.
 * APDs and IAPs have georef info so can present the current position.
 */
@SuppressLint("ViewConstructor")
public class PlateView extends LinearLayout implements WairToNow.CanBeMainView {
    private final static String TAG = "WairToNow";
    private final static int MAXZOOMIN = 16;
    private final static int MAXZOOMOUT = 8;

    private boolean full;
    private int expdate;                // plate expiration date, eg, 20150917
    private int screenOrient;
    private Paint exppaint;
    public  PlateImage plateImage;      // displays the plate image bitmap
    private String plateid;             // which plate, eg, "IAP-LOC RWY 16"
    private WairToNow wairToNow;
    private WaypointView waypointView;  // airport waypoint page we are part of
    private Waypoint.Airport airport;   // which airport, eg, "KBVY"

    private final static String[] columns_apdgeorefs1 = new String[] { "gr_wfta", "gr_wftb", "gr_wftc", "gr_wftd", "gr_wfte", "gr_wftf" };
    private final static String[] columns_georefs21 = new String[] { "gr_clat", "gr_clon", "gr_stp1", "gr_stp2", "gr_rada", "gr_radb",
            "gr_tfwa", "gr_tfwb", "gr_tfwc", "gr_tfwd", "gr_tfwe", "gr_tfwf" };

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

        Display display = wairToNow.getWindowManager().getDefaultDisplay();
        screenOrient = (display.getHeight () < display.getWidth ()) ?
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        if (plateid.startsWith ("APD-")) {
            plateImage = new APDPlateImage (fn);
        } else if (plateid.startsWith ("IAP-")) {
            plateImage = new IAPPlateImage (fn);
        } else if (plateid.startsWith ("RWY-")) {
            plateImage = new RWYPlateImage ();
        } else {
            plateImage = new NGRPlateImage (fn);
        }

        plateImage.setLayoutParams (new LayoutParams (LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView (plateImage);
    }

    /**
     * New GPS location received, update georef'd plate.
     */
    public void SetGPSLocation ()
    {
        plateImage.SetGPSLocation ();
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return waypointView.GetTabName ();
    }

    @Override  // CanBeMainView
    public int GetOrientation ()
    {
        return screenOrient;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return wairToNow.optionsView.powerLockOption.checkBox.isChecked ();
    }

    /**
     * Close any open bitmaps so we don't take up too much memory.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        plateImage.CloseBitmaps ();
    }

    /**
     * Maybe we should keep screen powered up while plate is being displayed.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    { }

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
        waypointView.selectedPlateView = null;
        wairToNow.SetCurrentTab (waypointView);
    }

    /**
     * Display the plate image file.
     * Allows panning and zooming.
     */
    private abstract class PlateImage extends View {
        private ADVPanAndZoom advPanAndZoom;
        private float         unzoomedCanvasWidth;
        private Rect          bitmapRect = new Rect ();    // biggest centered square completely filled by bitmap
        private RectF         canvasRect = new RectF ();   // initially biggest centered square completely filled by display area
                                                           // ... but gets panned/zoomed by user

        protected PlateImage ()
        {
            super (wairToNow);
            advPanAndZoom = new ADVPanAndZoom ();
        }

        public abstract void CloseBitmaps ();
        protected abstract void GotMouseDown (float x, float y);
        protected abstract void GotMouseUp (float x, float y);

        public void SetGPSLocation () { }

        /**
         * Callback for mouse events on the image.
         * We use this for scrolling the map around.
         */
        @Override  // View
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

            public void MouseUp (float x, float y)
            {
                GotMouseUp (x, y);
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
            float xdpi = wairToNow.dotsPerInchX;
            float ydpi = wairToNow.dotsPerInchY;
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
            float xdpi = wairToNow.dotsPerInchX;
            float ydpi = wairToNow.dotsPerInchY;
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
        protected float BitmapX2CanvasX (float bitmapX)
        {
            float nx = (bitmapX - bitmapRect.left) / (float) bitmapRect.width ();
            return nx * canvasRect.width () + canvasRect.left;
        }
        protected float BitmapY2CanvasY (float bitmapY)
        {
            float ny = (bitmapY - bitmapRect.top) / (float) bitmapRect.height ();
            return ny * canvasRect.height () + canvasRect.top;
        }

        protected float CanvasX2BitmapX (float canvasX)
        {
            float nx = (canvasX - canvasRect.left) / canvasRect.width ();
            return nx * bitmapRect.width () + bitmapRect.left;
        }
        protected float CanvasY2BitmapY (float canvasY)
        {
            float ny = (canvasY - canvasRect.top) / canvasRect.height ();
            return ny * bitmapRect.height () + bitmapRect.top;
        }

        /**
         * If plate is expired, draw an hash on top of it.
         * @param canvas = where to draw hash
         * @param outline = outline of plate on canvas
         */
        protected void DrawExpiredHash (Canvas canvas, RectF outline)
        {
            if (expdate < MaintView.deaddate) {
                if (exppaint == null) {
                    exppaint = new Paint ();
                    exppaint.setColor (Color.RED);
                    exppaint.setStrokeWidth (2);
                }
                canvas.save ();
                canvas.clipRect (outline);
                float ch = outline.height ();
                float step = wairToNow.dotsPerInch * 0.75F;
                for (float i = outline.left - ch; i < outline.right; i += step) {
                    canvas.drawLine (i, outline.top, ch + i, outline.bottom, exppaint);
                    canvas.drawLine (i, outline.bottom, ch + i, outline.top, exppaint);
                }
                canvas.restore ();
            }
        }
    }

    /**
     * Single-page georeferenced plates.
     */
    private abstract class GRPlateImage extends PlateImage {
        protected float xfmtfwa, xfmtfwb, xfmtfwc, xfmtfwd, xfmtfwe, xfmtfwf;
        protected float xfmwfta, xfmwftb, xfmwftc, xfmwftd, xfmwfte, xfmwftf;
        protected float mPerBmpPix;

        private PointF canPoint   = new PointF ();
        private RectF  canTmpRect = new RectF  ();

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

            /*
             * Draw hash if expired.
             */
            DrawExpiredHash (canvas, canTmpRect);
        }

        /**
         * Draw airplane icon showing current location on plate.
         */
        protected boolean DrawLocationArrow (Canvas canvas, boolean isAptDgm)
        {
            if (!LatLon2BitmapOK ()) return false;

            if (!wairToNow.optionsView.typeBOption.checkBox.isChecked () ||
                    (isAptDgm && (wairToNow.currentGPSSpd < 80.0F * Lib.MPerNM / 3600.0F))) {
                float bitmapX = LatLon2BitmapX (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                float bitmapY = LatLon2BitmapY (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                canPoint.x = BitmapX2CanvasX (bitmapX);
                canPoint.y = BitmapY2CanvasY (bitmapY);
                wairToNow.DrawLocationArrow (canvas, canPoint, 0.0F);
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

            //noinspection UnnecessaryLocalVariable
            float inorthing = ilat;                // 60nm units north of equator
            float ieasting  = ilon * avglatcos;    // 60nm units east of prime meridian
            //noinspection UnnecessaryLocalVariable
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
        protected boolean LatLon2BitmapOK ()
        {
            return (xfmwfte != 0.0) || (xfmwftf != 0.0);
        }
        protected float LatLon2BitmapX (float lat, float lon)
        {
            return xfmwfta * lon + xfmwftc * lat + xfmwfte;
        }
        protected float LatLon2BitmapY (float lat, float lon)
        {
            return xfmwftb * lon + xfmwftd * lat + xfmwftf;
        }

        /**
         * Gif pixel => Lat/Lon
         */
        protected float BitmapXY2Lat (float bmx, float bmy)
        {
            return xfmtfwb * bmx + xfmtfwd * bmy + xfmtfwf;
        }
        protected float BitmapXY2Lon (float bmx, float bmy)
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
            rwPaint.setStrokeWidth (wairToNow.thinLine / 2.0F);
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
        protected void GotMouseUp (float x, float y) { }

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
                    float begBmX = LatLon2BitmapX (rwy.begLat, rwy.begLon);
                    float begBmY = LatLon2BitmapY (rwy.begLat, rwy.begLon);
                    float endBmX = LatLon2BitmapX (rwy.endLat, rwy.endLon);
                    float endBmY = LatLon2BitmapY (rwy.endLat, rwy.endLon);
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
                float midLon = Lib.AvgLons (rwy.lon, rwy.endLon);
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
                begLeftBmpX = Math.round (LatLon2BitmapX (begLeftLat, begLeftLon));
                begLeftBmpY = Math.round (LatLon2BitmapY (begLeftLat, begLeftLon));
                begRiteBmpX = Math.round (LatLon2BitmapX (begRiteLat, begRiteLon));
                begRiteBmpY = Math.round (LatLon2BitmapY (begRiteLat, begRiteLon));

                midLeftBmpX = Math.round (LatLon2BitmapX (midLeftLat, midLeftLon));
                midLeftBmpY = Math.round (LatLon2BitmapY (midLeftLat, midLeftLon));
                midRiteBmpX = Math.round (LatLon2BitmapX (midRiteLat, midRiteLon));
                midRiteBmpY = Math.round (LatLon2BitmapY (midRiteLat, midRiteLon));

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
            public float bmpX, bmpY;
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
        protected void GotMouseUp (float x, float y) { }

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

            float leftbmx = CanvasX2BitmapX (0);
            float ritebmx = CanvasX2BitmapX (canWidth);
            float topbmy  = CanvasY2BitmapY (0);
            float botbmy  = CanvasY2BitmapY (canHeight);

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
     */
    public class IAPPlateImage extends GRPlateImage {

        // dme arcs added via dmearcs.txt (see PlateCIFP.java)
        private class DMEArc {
            public Waypoint nav;
            public float beg, end, nm;

            public void drawIt (Canvas bmcan)
            {
                // wrap beg to be in range (0..360] as that is the one that gets a label
                while (beg <=  0.0F) beg += 360.0F;
                while (beg > 360.0F) beg -= 360.0F;

                // wrap end to be within 180 of beg
                // it might end up negative, it might end up more than 360
                while (end - beg < -180.0F) end += 360.0F;
                while (end - beg >= 180.0F) end += 360.0F;

                // calculate true endpoints
                float magvar = nav.GetMagVar (nav.elev);
                float trubeg = beg - magvar;
                float truend = end - magvar;

                // get lat,lon of arc endpoints
                float beglat = Lib.LatHdgDist2Lat (nav.lat, trubeg, nm);
                float beglon = Lib.LatLonHdgDist2Lon (nav.lat, nav.lon, trubeg, nm);
                float endlat = Lib.LatHdgDist2Lat (nav.lat, truend, nm);
                float endlon = Lib.LatLonHdgDist2Lon (nav.lat, nav.lon, truend, nm);

                // convert all the lat,lon to bitmap x,y
                float begbmx = LatLon2BitmapX (beglat, beglon);
                float begbmy = LatLon2BitmapY (beglat, beglon);
                float endbmx = LatLon2BitmapX (endlat, endlon);
                float endbmy = LatLon2BitmapY (endlat, endlon);
                float navbmx = LatLon2BitmapX (nav.lat, nav.lon);
                float navbmy = LatLon2BitmapY (nav.lat, nav.lon);

                float radius = Mathf.sqrt (Math.hypot (begbmx - navbmx, begbmy - navbmy) *
                        Math.hypot (endbmx - navbmx, endbmy - navbmy));

                // make rectangle the arc circle fits in
                RectF rect = new RectF (navbmx - radius, navbmy - radius,
                        navbmx + radius, navbmy + radius);

                // draw arc
                Paint paint = new Paint ();
                paint.setColor (Color.BLACK);
                paint.setFlags (Paint.ANTI_ALIAS_FLAG);
                paint.setStrokeWidth (6.0F);
                paint.setStyle (Paint.Style.STROKE);
                float start = Math.min (trubeg, truend) - 90.0F;
                float sweep = Math.abs (truend - trubeg);
                bmcan.drawArc (rect, start, sweep, false, paint);

                // draw radial line segment at beginning of arc
                float trubegsin = Mathf.sin (Math.toRadians (trubeg));
                float trubegcos = Mathf.cos (Math.toRadians (trubeg));
                float pixpernm = radius / nm;
                float innerx = navbmx + (nm - 0.6F) * pixpernm * trubegsin;
                float innery = navbmy - (nm - 0.6F) * pixpernm * trubegcos;
                float outerx = navbmx + (nm + 0.6F) * pixpernm * trubegsin;
                float outery = navbmy - (nm + 0.6F) * pixpernm * trubegcos;
                paint.setStrokeWidth (3.0F);
                bmcan.drawLine (innerx, innery, outerx, outery, paint);

                // draw legend
                String legend1 = nav.ident + " " + Math.round (beg) + "\u00B0";
                String legend2 = String.format ("%.1f", nm) + " nm";
                paint.setStrokeWidth (1.0F);
                paint.setStyle (Paint.Style.FILL_AND_STROKE);
                paint.setTextSize (25.0F);
                bmcan.save ();
                if (trubeg <= 180.0F) {
                    bmcan.rotate (trubeg - 90.0F, outerx, outery);
                } else {
                    Rect bounds1 = new Rect ();
                    Rect bounds2 = new Rect ();
                    paint.getTextBounds (legend1, 0, legend1.length (), bounds1);
                    paint.getTextBounds (legend2, 0, legend2.length (), bounds2);
                    int width = Math.max (bounds1.width (), bounds2.width ());
                    bmcan.rotate (trubeg - 270.0F, outerx, outery);
                    bmcan.translate (- width, 0);
                }
                bmcan.drawText (legend1, outerx, outery, paint);
                outery += paint.getTextSize ();
                bmcan.drawText (legend2, outerx, outery, paint);
                bmcan.restore ();
            }
        }

        private Bitmap      bitmap;                           // single plate page
        private boolean     hasDMEArcs;
        private float       bitmapLat, bitmapLon;
        private float       bmxy2llx = Float.NaN;
        private float       bmxy2lly = Float.NaN;
        private LambertConicalConformal lccmap;               // non-null: FAA-provided georef data
        private LatLon      bmxy2latlon = new LatLon ();
        private LinkedList<DMEArc> dmeArcs;
        private long        plateLoadedUptime;
        private Paint       runwayPaint;                      // paints georef stuff on plate image
        public  PlateCIFP   plateCIFP;
        private PlateDME    plateDME;
        private PlateTimer  plateTimer;
        private PointF      bitmapPt = new PointF ();
        private String      filename;                         // name of gifs on flash, without ".p<pageno>" suffix

        public IAPPlateImage (String fn)
        {
            filename = fn;

            runwayPaint = new Paint ();
            runwayPaint.setColor (Color.MAGENTA);
            runwayPaint.setStrokeWidth (wairToNow.thinLine / 2.0F);
            runwayPaint.setStyle (Paint.Style.FILL_AND_STROKE);
            runwayPaint.setTextAlign (Paint.Align.CENTER);
            runwayPaint.setTextSize (wairToNow.textSize);

            plateDME   = new PlateDME (wairToNow, this, airport.ident, plateid);
            plateTimer = new PlateTimer (wairToNow, this);

            /*
             * Get any georeference points in database.
             */
            GetIAPGeoRefPoints ();

            /*
             * Init CIFP class.
             * Constructor uses lat/lon <=> bitmap pixel mapping.
             */
            plateCIFP = new PlateCIFP (wairToNow, this, airport, plateid);

            plateLoadedUptime = SystemClock.uptimeMillis ();
        }

        /**
         * Set up to draw a DME arc on the plate.
         * These are arcs as specified in dmearcs.txt.
         * (called by PlateCIFP constructor)
         * @param nav = navaid the arc is based on
         * @param beg = beginning of arc (magnetic), this gets a tick mark and legend
         * @param end = end of arc (magnetic), should be recip of final approach course
         * @param nm  = arc radius, usually 10.0F
         */
        public void DrawDMEArc (Waypoint nav, float beg, float end, float nm)
        {
            // add it to list of DME arcs to be drawn on plate
            if (dmeArcs == null) dmeArcs = new LinkedList<> ();
            DMEArc dmeArc = new DMEArc ();
            dmeArc.nav = nav;
            dmeArc.beg = beg;
            dmeArc.end = end;
            dmeArc.nm  = nm;
            dmeArcs.addLast (dmeArc);
            hasDMEArcs = true;
        }

        @Override  // PlateImage
        public void SetGPSLocation ()
        {
            plateCIFP.SetGPSLocation ();
        }

        // used by PlateDME
        public Waypoint FindWaypoint (String wayptid)
        {
            return airport.GetNearbyWaypoint (wayptid);
        }

        /**
         * Check for FAA-supplied georeference information.
         */
        private void GetIAPGeoRefPoints ()
        {
            String[] grkey = new String[] { airport.ident, plateid };
            String machinedbname = "plates_" + expdate + ".db";
            SQLiteDBs machinedb = SQLiteDBs.create (machinedbname);
            if (machinedb.tableExists ("iapgeorefs2")) {
                Cursor result = machinedb.query ("iapgeorefs2", columns_georefs21,
                        "gr_icaoid=? AND gr_plate=?", grkey,
                        null, null, null, null);
                try {
                    if (result.moveToFirst ()) {
                        lccmap = new LambertConicalConformal (
                                result.getFloat (0),
                                result.getFloat (1),
                                result.getFloat (2),
                                result.getFloat (3),
                                result.getFloat (4),
                                result.getFloat (5),
                                new float[] {
                                    result.getFloat (6),
                                    result.getFloat (7),
                                    result.getFloat (8),
                                    result.getFloat (9),
                                    result.getFloat (10),
                                    result.getFloat (11)
                                }
                        );
                    }
                } finally {
                    result.close ();
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
            plateCIFP.GotMouseDown (x, y);
            plateDME.GotMouseDown (x, y);
            plateTimer.GotMouseDown (x, y);
            wairToNow.currentCloud.MouseDown (Math.round (x), Math.round (y),
                    System.currentTimeMillis ());
        }

        protected void GotMouseUp (float x, float y)
        {
            plateCIFP.GotMouseUp ();
            plateDME.GotMouseUp ();
            if (wairToNow.currentCloud.MouseUp (Math.round (x), Math.round (y),
                    System.currentTimeMillis ())) invalidate ();
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
                if (hasDMEArcs && (bitmap != null)) {
                    DrawDMEArcsOnBitmap ();
                }
                if (bitmap == null) return;
            }

            /*
             * Display bitmap to canvas.
             */
            ShowSinglePage (canvas, bitmap);

            /*
             * Draw runways if FAA-provided georef present.
             */
            if ((lccmap != null) && (SystemClock.uptimeMillis () - plateLoadedUptime < 10000)) {
                HashMap<String,Waypoint.Runway> runways = airport.GetRunways ();
                for (Waypoint.Runway runway : runways.values ()) {
                    float begbmx = LatLon2BitmapX (runway.lat, runway.lon);
                    float begbmy = LatLon2BitmapY (runway.lat, runway.lon);
                    float endbmx = LatLon2BitmapX (runway.endLat, runway.endLon);
                    float endbmy = LatLon2BitmapY (runway.endLat, runway.endLon);
                    float begx = BitmapX2CanvasX (begbmx);
                    float begy = BitmapY2CanvasY (begbmy);
                    float endx = BitmapX2CanvasX (endbmx);
                    float endy = BitmapY2CanvasY (endbmy);
                    canvas.drawLine (begx, begy, endx, endy, runwayPaint);
                }
            }

            /*
             * Draw any CIFP, enabled DMEs and timer.
             */
            if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
                plateCIFP.DrawCIFP (canvas);
                plateDME.DrawDMEs (canvas, wairToNow.currentGPSLat, wairToNow.currentGPSLon,
                        wairToNow.currentGPSAlt, wairToNow.currentGPSTime);
            }
            plateTimer.DrawTimer (canvas);

            /*
             * Draw airplane and current location info if we have enough georeference info.
             */
            DrawLocationArrow (canvas, false);
            wairToNow.currentCloud.DrawIt (canvas);

            /*
             * Draw GPS Available box around plate border.
             */
            if (full && !wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
                wairToNow.drawGPSAvailable (canvas, this);
            }
        }

        /**
         * Convert lat,lon to bitmap X,Y.
         * Use LCC conversion if FAA-supplied georef data given.
         */
        @Override  // GRPPlateImage
        protected boolean LatLon2BitmapOK ()
        {
            return lccmap != null;
        }

        @Override  // GRPPlateImage
        public float LatLon2BitmapX (float lat, float lon)
        {
            if (lccmap == null) return Float.NaN;
            bitmapLat = lat;
            bitmapLon = lon;
            lccmap.LatLon2ChartPixelExact (lat, lon, bitmapPt);
            return bitmapPt.x;
        }

        @Override  // GRPPlateImage
        public float LatLon2BitmapY (float lat, float lon)
        {
            if (lccmap == null) return Float.NaN;
            if ((bitmapLat != lat) || (bitmapLon != lon)) {
                bitmapLat = lat;
                bitmapLon = lon;
                lccmap.LatLon2ChartPixelExact (lat, lon, bitmapPt);
            }
            return bitmapPt.y;
        }

        @Override  // GRPPlateImage
        public float BitmapXY2Lat (float bmx, float bmy)
        {
            if (lccmap == null) return Float.NaN;
            if ((bmx != bmxy2llx) || (bmy != bmxy2lly)) {
                bmxy2llx = bmx;
                bmxy2lly = bmy;
                lccmap.ChartPixel2LatLonExact (bmx, bmy, bmxy2latlon);
            }
            return bmxy2latlon.lat;
        }

        @Override  // GRPPlateImage
        public float BitmapXY2Lon (float bmx, float bmy)
        {
            if (lccmap == null) return Float.NaN;
            if ((bmx != bmxy2llx) || (bmy != bmxy2lly)) {
                bmxy2llx = bmx;
                bmxy2lly = bmy;
                lccmap.ChartPixel2LatLonExact (bmx, bmy, bmxy2latlon);
            }
            return bmxy2latlon.lon;
        }

        /**
         * Adds DME arcs to copy of original plate bitmap image.
         * Write to file so user can print it.
         */
        private void DrawDMEArcsOnBitmap ()
        {
            // draw DME arc(s) on a mutable copy of bitmap
            Bitmap bm = bitmap.copy (Bitmap.Config.RGB_565, true);
            Canvas bmcan = new Canvas (bm);
            for (DMEArc dmeArc : dmeArcs) dmeArc.drawIt (bmcan);
            bitmap = bm;

            // if we don't already have an image file, create one
            File csvfile = new File (WairToNow.dbdir + "/dmearcs.txt");
            File imgfile = new File (WairToNow.dbdir + "/dmearcs-" + airport.ident + "-" +
                    plateid.replace (" ", "_").replace ("/", "_") + "-" + expdate + ".png");
            if (imgfile.lastModified () < csvfile.lastModified ()) {
                try {
                    File tmpfile = new File (imgfile.getPath () + ".tmp");
                    FileOutputStream tmpouts = new FileOutputStream (tmpfile);
                    try {
                        if (!bitmap.compress (Bitmap.CompressFormat.PNG, 0, tmpouts)) {
                            throw new IOException ("bitmap.compress failed");
                        }
                    } finally {
                        tmpouts.close ();
                    }
                    if (!tmpfile.renameTo (imgfile)) {
                        throw new IOException ("error renaming from " + tmpfile.getPath ());
                    }
                } catch (IOException ioe) {
                    Log.w (TAG, "error writing " + imgfile.getPath () + ": " + ioe.getMessage ());
                    Lib.Ignored (imgfile.delete ());
                }
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
        protected void GotMouseUp (float x, float y) { }

        /**
         * Draw the plate image.
         */
        @SuppressLint("DrawAllocation")
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

                    /*
                     * Draw hash if expired.
                     */
                    DrawExpiredHash (canvas, canTmpRect);
                }
            }
        }
    }
}
