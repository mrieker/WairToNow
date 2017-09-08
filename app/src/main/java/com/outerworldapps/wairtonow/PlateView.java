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
import android.content.ContentValues;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
import java.io.InputStream;
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

    // https://www.nbaa.org/ops/airspace/issues/20130418-faa-expands-size-of-protected-airspace-for-circling-approaches.php
    // https://aviation.stackexchange.com/questions/973/when-conducting-a-circling-approach-how-close-do-you-have-to-stay-to-the-airpor
    private final static int[][] newcircrad10ths = new int[][] {
        new int[] { 13, 17, 27, 36, 45 },   //    0-1000 ft msl
        new int[] { 13, 18, 28, 37, 46 },   // 1001-3000 ft msl
        new int[] { 13, 18, 29, 38, 48 },   // 3001-5000 ft msl
        new int[] { 13, 19, 30, 40, 50 },   // 5001-7000 ft msl
        new int[] { 14, 20, 32, 42, 53 },   // 7001-9000 ft msl
        new int[] { 14, 21, 33, 44, 55 }    // 9001+     ft msl
    };
    private final static int[] oldcircrad10ths = new int[] {
                    13, 15, 17, 23, 45
    };

    private final static byte CRT_UNKN = 0;  // circling radius type unknown
    private final static byte CRT_NEW  = 1;  // circling radius type new style (newcircrad10ths)
    private final static byte CRT_OLD  = 2;  // circling radius type old style (oldcircrad10ths)
    private final static Object shadeCirclingAreaLock = new Object ();
    private static ShadeCirclingAreaThread shadeCirclingAreaThread;

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
            "gr_tfwa", "gr_tfwb", "gr_tfwc", "gr_tfwd", "gr_tfwe", "gr_tfwf", "gr_circ" };

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
        private double        unzoomedCanvasWidth;
        protected Rect        bitmapRect = new Rect ();    // biggest centered square completely filled by bitmap
        protected RectF       canvasRect = new RectF ();   // initially biggest centered square completely filled by display area
                                                           // ... but gets panned/zoomed by user

        protected PlateImage ()
        {
            super (wairToNow);
            advPanAndZoom = new ADVPanAndZoom ();
        }

        public abstract void CloseBitmaps ();
        protected abstract void GotMouseDown (double x, double y);
        protected abstract void GotMouseUp (double x, double y);

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

            public void MouseDown (double x, double y)
            {
                GotMouseDown (x, y);
            }

            public void MouseUp (double x, double y)
            {
                GotMouseUp (x, y);
            }

            public void Panning (double x, double y, double dx, double dy)
            {
                canvasRect.left   += dx;
                canvasRect.right  += dx;
                canvasRect.top    += dy;
                canvasRect.bottom += dy;
                invalidate ();
            }

            public void Scaling (double fx, double fy, double sf)
            {
                double canvasWidth = canvasRect.width () * sf;
                if ((canvasWidth > unzoomedCanvasWidth / MAXZOOMOUT) &&
                    (canvasWidth < unzoomedCanvasWidth * MAXZOOMIN)){
                    canvasRect.left   = (float) ((canvasRect.left   - fx) * sf + fx);
                    canvasRect.right  = (float) ((canvasRect.right  - fx) * sf + fx);
                    canvasRect.top    = (float) ((canvasRect.top    - fy) * sf + fy);
                    canvasRect.bottom = (float) ((canvasRect.bottom - fy) * sf + fy);
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
            double xdpi = wairToNow.dotsPerInchX;
            double ydpi = wairToNow.dotsPerInchY;
            double canInWidth  = canWidth  / xdpi;
            double canInHeight = canHeight / ydpi;

            // get largest square centered on canvas that can be seen in total
            // ie, there might be unused parts of two of the canvas' margins
            if (canInHeight > canInWidth) {
                canvasRect.left   = 0;
                canvasRect.right  = canWidth;
                canvasRect.top    = (float) (ydpi * (canInHeight - canInWidth) / 2);
                canvasRect.bottom = (float) (ydpi * (canInHeight + canInWidth) / 2);
            } else {
                canvasRect.left   = (float) (xdpi * (canInWidth - canInHeight) / 2);
                canvasRect.right  = (float) (xdpi * (canInWidth + canInHeight) / 2);
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
            double xdpi = wairToNow.dotsPerInchX;
            double ydpi = wairToNow.dotsPerInchY;
            double canInWidth  = canWidth  / xdpi;
            double canInHeight = canHeight / ydpi;

            // get largest square centered on canvas that can be seen in total
            // ie, there might be unused parts of two of the canvas' margins
            if (canInHeight > canInWidth) {
                canvasRect.left   = 0;
                canvasRect.right  = canWidth;
                canvasRect.top    = (float) (ydpi * (canInHeight - canInWidth) / 2);
                canvasRect.bottom = (float) (ydpi * (canInHeight + canInWidth) / 2);
            } else {
                canvasRect.left   = (float) (xdpi * (canInWidth - canInHeight) / 2);
                canvasRect.right  = (float) (xdpi * (canInWidth + canInHeight) / 2);
                canvasRect.top    = 0;
                canvasRect.bottom = canHeight;
            }
            unzoomedCanvasWidth = canvasRect.width ();
        }

        /**
         * bitmap <=> view mapping.
         */
        protected double BitmapX2CanvasX (double bitmapX)
        {
            double nx = (bitmapX - bitmapRect.left) / (double) bitmapRect.width ();
            return nx * canvasRect.width () + canvasRect.left;
        }
        protected double BitmapY2CanvasY (double bitmapY)
        {
            double ny = (bitmapY - bitmapRect.top) / (double) bitmapRect.height ();
            return ny * canvasRect.height () + canvasRect.top;
        }

        protected double CanvasX2BitmapX (double canvasX)
        {
            double nx = (canvasX - canvasRect.left) / canvasRect.width ();
            return nx * bitmapRect.width () + bitmapRect.left;
        }
        protected double CanvasY2BitmapY (double canvasY)
        {
            double ny = (canvasY - canvasRect.top) / canvasRect.height ();
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
                double ch = outline.height ();
                double step = wairToNow.dotsPerInch * 0.75;
                for (double i = outline.left - ch; i < outline.right; i += step) {
                    canvas.drawLine ((float) i, outline.top, (float) (ch + i), outline.bottom, exppaint);
                    canvas.drawLine ((float) i, outline.bottom, (float) (ch + i), outline.top, exppaint);
                }
                canvas.restore ();
            }
        }
    }

    /**
     * Single-page georeferenced plates.
     */
    private abstract class GRPlateImage extends PlateImage {
        protected double xfmtfwa, xfmtfwb, xfmtfwc, xfmtfwd, xfmtfwe, xfmtfwf;
        protected double xfmwfta, xfmwftb, xfmwftc, xfmwftd, xfmwfte, xfmwftf;
        protected double mPerBmpPix;

        private Paint  rwclPaint;
        private PointD canPoint   = new PointD ();
        private RectF  canTmpRect = new RectF  ();

        /**
         * Display single-page plate according to current zoom/pan.
         */
        protected void ShowSinglePage (Canvas canvas, Bitmap bitmap)
        {
            /*
             * See where page maps to on canvas.
             */
            canTmpRect.top    = (float) BitmapY2CanvasY (0);
            canTmpRect.bottom = (float) BitmapY2CanvasY (bitmap.getHeight ());
            canTmpRect.left   = (float) BitmapX2CanvasX (0);
            canTmpRect.right  = (float) BitmapX2CanvasX (bitmap.getWidth  ());

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
                    (isAptDgm && (wairToNow.currentGPSSpd < 80.0 * Lib.MPerNM / 3600.0))) {
                double bitmapX = LatLon2BitmapX (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                double bitmapY = LatLon2BitmapY (wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                canPoint.x = BitmapX2CanvasX (bitmapX);
                canPoint.y = BitmapY2CanvasY (bitmapY);
                wairToNow.DrawLocationArrow (canvas, canPoint, 0.0);
            }
            return true;
        }

        /**
         * Set lat/lon => bitmap transform via two example mappings.
         */
        protected double SetLatLon2Bitmap (double ilat, double ilon, double jlat, double jlon,
                                          int ipixelx, int ipixely, int jpixelx, int jpixely)
        {
            double avglatcos = Math.cos (Math.toRadians (airport.lat));

            //noinspection UnnecessaryLocalVariable
            double inorthing = ilat;                // 60nm units north of equator
            double ieasting  = ilon * avglatcos;    // 60nm units east of prime meridian
            //noinspection UnnecessaryLocalVariable
            double jnorthing = jlat;                // 60nm units north of equator
            double jeasting  = jlon * avglatcos;    // 60nm units east of prime meridian

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

            double[][] mat = new double[][] {
                    new double[] { 1,  0, 0, 1, 0, 0, 0 },
                    new double[] { 0, -1, 1, 0, 0, 0, 0 },
                    new double[] { ieasting, 0, inorthing, 0, 1, 0, ipixelx },
                    new double[] { 0, ieasting, 0, inorthing, 0, 1, ipixely },
                    new double[] { jeasting, 0, jnorthing, 0, 1, 0, jpixelx },
                    new double[] { 0, jeasting, 0, jnorthing, 0, 1, jpixely }
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

            mPerBmpPix = Lib.NMPerDeg * Lib.MPerNM / Math.hypot (xfmwftc, xfmwftd);

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

            double rev[][] = new double[][] {
                new double[] { xfmwfta, xfmwftb, 0, 1, 0, 0 },
                new double[] { xfmwftc, xfmwftd, 0, 0, 1, 0 },
                new double[] { xfmwfte, xfmwftf, 1, 0, 0, 1 },
            };
            Lib.RowReduce (rev);

            xfmtfwa = rev[0][3];
            xfmtfwb = rev[0][4];
            xfmtfwc = rev[1][3];
            xfmtfwd = rev[1][4];
            xfmtfwe = rev[2][3];
            xfmtfwf = rev[2][4];

            // return rotation angle

            return Math.atan2 (xfmwftb, xfmwfta);
        }

        /**
         * Lat/Lon => gif pixel
         */
        protected boolean LatLon2BitmapOK ()
        {
            return (xfmwfte != 0.0) || (xfmwftf != 0.0);
        }
        protected double LatLon2BitmapX (double lat, double lon)
        {
            return xfmwfta * lon + xfmwftc * lat + xfmwfte;
        }
        protected double LatLon2BitmapY (double lat, double lon)
        {
            return xfmwftb * lon + xfmwftd * lat + xfmwftf;
        }

        /**
         * Gif pixel => Lat/Lon
         */
        protected double BitmapXY2Lat (double bmx, double bmy)
        {
            return xfmtfwb * bmx + xfmtfwd * bmy + xfmtfwf;
        }
        protected double BitmapXY2Lon (double bmx, double bmy)
        {
            return xfmtfwa * bmx + xfmtfwc * bmy + xfmtfwe;
        }

        /**
         * Draw lines down the centerline of runways.
         */
        protected void DrawRunwayCenterlines (Canvas canvas)
        {
            if (rwclPaint == null) {
                rwclPaint = new Paint ();
                rwclPaint.setColor (Color.MAGENTA);
                rwclPaint.setStrokeWidth (wairToNow.thinLine / 2.0F);
                rwclPaint.setStyle (Paint.Style.FILL_AND_STROKE);
            }

            for (Waypoint.Runway rwy : airport.GetRunwayPairs ()) {
                double begBmpX = LatLon2BitmapX (rwy.lat, rwy.lon);
                double begBmpY = LatLon2BitmapY (rwy.lat, rwy.lon);
                double endBmpX = LatLon2BitmapX (rwy.endLat, rwy.endLon);
                double endBmpY = LatLon2BitmapY (rwy.endLat, rwy.endLon);
                double begCanX = BitmapX2CanvasX (begBmpX);
                double begCanY = BitmapY2CanvasY (begBmpY);
                double endCanX = BitmapX2CanvasX (endBmpX);
                double endCanY = BitmapY2CanvasY (endBmpY);
                canvas.drawLine ((float) begCanX, (float) begCanY, (float) endCanX, (float) endCanY, rwclPaint);
            }
        }
    }

    /**
     * Display the APD plate image file.
     */
    private class APDPlateImage extends GRPlateImage {
        private Bitmap bitmap;    // single plate page
        private long   plateLoadedUptime;
        private String filename;  // name of gif on flash, without ".p<pageno>" suffix

        public APDPlateImage (String fn)
        {
            filename = fn;
            plateLoadedUptime = SystemClock.uptimeMillis ();

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
                        xfmwfta = result.getDouble (0);
                        xfmwftb = result.getDouble (1);
                        xfmwftc = result.getDouble (2);
                        xfmwftd = result.getDouble (3);
                        xfmwfte = result.getDouble (4);
                        xfmwftf = result.getDouble (5);
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

        protected void GotMouseDown (double x, double y) { }
        protected void GotMouseUp (double x, double y) { }

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
            if (DrawLocationArrow (canvas, true) && (SystemClock.uptimeMillis () - plateLoadedUptime < 10000)) {
                DrawRunwayCenterlines (canvas);
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
                double lengthNM = (double) rwy.getLengthFt () / Lib.FtPerM / Lib.MPerNM;
                double widthNM  = (double) rwy.getWidthFt  () / Lib.FtPerM / Lib.MPerNM;

                // get runway midpoint and heading from this end to the midpoint
                double midLat = (rwy.lat + rwy.endLat) / 2.0;
                double midLon = Lib.AvgLons (rwy.lon, rwy.endLon);
                double hdgDeg = Lib.LatLonTC (rwy.lat, rwy.lon, midLat, midLon);

                // get lat/lon on centerline of beginning of runway
                double begLat = Lib.LatHdgDist2Lat (midLat, hdgDeg + 180.0, lengthNM / 2.0);
                double begLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg + 180.0, lengthNM / 2.0);

                // get lat/lon of left & right sides of this end of runway
                double begLeftLat = Lib.LatHdgDist2Lat (begLat, hdgDeg - 90.0, widthNM / 2.0);
                double begLeftLon = Lib.LatLonHdgDist2Lon (begLat, begLon, hdgDeg - 90.0, widthNM / 2.0);

                double begRiteLat = Lib.LatHdgDist2Lat (begLat, hdgDeg + 90.0, widthNM / 2.0);
                double begRiteLon = Lib.LatLonHdgDist2Lon (begLat, begLon, hdgDeg + 90.0, widthNM / 2.0);

                // get lat/lon of left & right sides of midpoint on runway
                double midLeftLat = Lib.LatHdgDist2Lat (midLat, hdgDeg - 90.0, widthNM / 2.0);
                double midLeftLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg - 90.0, widthNM / 2.0);

                double midRiteLat = Lib.LatHdgDist2Lat (midLat, hdgDeg + 90.0, widthNM / 2.0);
                double midRiteLon = Lib.LatLonHdgDist2Lon (midLat, midLon, hdgDeg + 90.0, widthNM / 2.0);

                // compute corresponding bitmap pixels
                begLeftBmpX = (int) Math.round (LatLon2BitmapX (begLeftLat, begLeftLon));
                begLeftBmpY = (int) Math.round (LatLon2BitmapY (begLeftLat, begLeftLon));
                begRiteBmpX = (int) Math.round (LatLon2BitmapX (begRiteLat, begRiteLon));
                begRiteBmpY = (int) Math.round (LatLon2BitmapY (begRiteLat, begRiteLon));

                midLeftBmpX = (int) Math.round (LatLon2BitmapX (midLeftLat, midLeftLon));
                midLeftBmpY = (int) Math.round (LatLon2BitmapY (midLeftLat, midLeftLon));
                midRiteBmpX = (int) Math.round (LatLon2BitmapX (midRiteLat, midRiteLon));
                midRiteBmpY = (int) Math.round (LatLon2BitmapY (midRiteLat, midRiteLon));

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
            public double bmpX, bmpY;
            public String ident;
            public String type;

            public WpInfo (Waypoint wp)
            {
                bmpX  = LatLon2BitmapX (wp.lat, wp.lon);
                bmpY  = LatLon2BitmapY (wp.lat, wp.lon);
                ident = wp.ident;
                type  = wp.GetTypeAbbr ();
            }
        }

        private final static double defnm = 0.75;
        private final static double padnm = 0.25;

        private boolean firstDraw = true;
        private double longestNM;
        private double minlat;
        private double maxlat;
        private double minlon;
        private double maxlon;
        private double qtrTextHeight;
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
            qtrTextHeight = bounds.height () / 4.0;

            double avglatcos = Math.cos (Math.toRadians (airport.lat));
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
                longestNM = (defnm - padnm) * 2.0;
            } else {
                for (Waypoint.Runway rwywp : rwywps) {
                    if (minlat > rwywp.lat) minlat = rwywp.lat;
                    if (maxlat < rwywp.lat) maxlat = rwywp.lat;
                    if (minlon > rwywp.lon) minlon = rwywp.lon;
                    if (maxlon < rwywp.lon) maxlon = rwywp.lon;
                    double lenNM = rwywp.getLengthFt () / Lib.FtPerM / Lib.MPerNM;
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

        protected void GotMouseDown (double x, double y) { }
        protected void GotMouseUp (double x, double y) { }

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
            wairToNow.openStreetMap.Draw (canvas, pmap, this);

            /*
             * Show debugging lines through airport reference point.
             */
            /*{
                rwyPaint.setColor (Color.WHITE);
                double aptCanX = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon));
                double aptCanY = BitmapY2CanvasY (LatLon2BitmapY (airport.lat, airport.lon));
                canvas.drawLine (0, aptCanY, width, aptCanY, rwyPaint);
                canvas.drawLine (aptCanX, 0, aptCanX, height, rwyPaint);
            }*/

            /*
             * Draw scale.
             */
            /*
            rwyPaint.setColor (Color.CYAN);
            double aptlatcos = Math.cos (Math.toRadians (airport.lat));
            double smFactor  = wairToNow.optionsView.ktsMphOption.getAlt () ? Lib.SMPerNM : 1.0;
            double canx0     = BitmapX2CanvasX (0);
            double cany0     = BitmapY2CanvasY (synthHeight);
            double canx0lim  = canx0 + metrics.xdpi / 4;
            double cany0lim  = cany0 - metrics.ydpi / 4;
            int   nsteps    = (int) Math.ceil (longestNM * smFactor / stepMi / 2.0);
            for (int step = 0; step <= nsteps; step ++) {
                String str = Double.toString (step * stepMi);
                int strlen = str.length ();

                double dlatdeg = step * stepMi / smFactor / Lib.NMPerDeg;
                double dlondeg = dlatdeg / aptlatcos;

                // left column
                rwyPaint.setTextAlign (Paint.Align.LEFT);
                double cany1 = BitmapY2CanvasY (LatLon2BitmapY (airport.lat + dlatdeg, airport.lon)) + qtrTextHeight * 2;
                canvas.drawText (str, 0, strlen, canx0, cany1, rwyPaint);
                double cany2 = BitmapY2CanvasY (LatLon2BitmapY (airport.lat - dlatdeg, airport.lon)) + qtrTextHeight * 2;
                if (cany2 < cany0lim) canvas.drawText (str, 0, strlen, canx0, cany2, rwyPaint);

                // bottom row
                rwyPaint.setTextAlign (Paint.Align.CENTER);
                double canx1 = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon + dlondeg));
                canvas.drawText (str, 0, strlen, canx1, cany0, rwyPaint);
                double canx2 = BitmapX2CanvasX (LatLon2BitmapX (airport.lat, airport.lon - dlondeg));
                if (canx2 > canx0lim) canvas.drawText (str, 0, strlen, canx2, cany0, rwyPaint);
            }
            */

            /*
             * Draw rectangles for each runway.
             */
            for (RwyInfo rwy : runways.values ()) {

                // compute corresponding canvas pixels
                double begLeftCanX = BitmapX2CanvasX (rwy.begLeftBmpX);
                double begLeftCanY = BitmapY2CanvasY (rwy.begLeftBmpY);
                double begRiteCanX = BitmapX2CanvasX (rwy.begRiteBmpX);
                double begRiteCanY = BitmapY2CanvasY (rwy.begRiteBmpY);

                double midLeftCanX = BitmapX2CanvasX (rwy.midLeftBmpX);
                double midLeftCanY = BitmapY2CanvasY (rwy.midLeftBmpY);
                double midRiteCanX = BitmapX2CanvasX (rwy.midRiteBmpX);
                double midRiteCanY = BitmapY2CanvasY (rwy.midRiteBmpY);

                // draw rectangle for this runway half
                rwyPath.reset ();
                rwyPath.moveTo ((float) begLeftCanX, (float) begLeftCanY);
                rwyPath.lineTo ((float) begRiteCanX, (float) begRiteCanY);
                rwyPath.lineTo ((float) midRiteCanX, (float) midRiteCanY);
                rwyPath.lineTo ((float) midLeftCanX, (float) midLeftCanY);
                rwyPath.lineTo ((float) begLeftCanX, (float) begLeftCanY);

                rwyPaint.setColor (rwy.surfColor);
                canvas.drawPath (rwyPath, rwyPaint);
            }

            /*
             * Draw waypoints.
             */
            rwyPaint.setColor (Color.MAGENTA);
            rwyPaint.setTextAlign (Paint.Align.LEFT);
            for (WpInfo wpInfo : waypoints.values ()) {
                double canX = BitmapX2CanvasX (wpInfo.bmpX);
                double canY = BitmapY2CanvasY (wpInfo.bmpY);
                canvas.drawCircle ((float) canX, (float) canY, (float) qtrTextHeight, rwyPaint);
                canX += qtrTextHeight * 2;
                canY -= qtrTextHeight;
                canvas.drawText (wpInfo.ident, 0, wpInfo.ident.length (), (float) canX, (float) canY, rwyPaint);
                canY += qtrTextHeight * 6;
                canvas.drawText (wpInfo.type, 0, wpInfo.type.length (), (float) canX, (float) canY, rwyPaint);
            }

            /*
             * Draw runway numbers.
             */
            rwyPaint.setColor (Color.RED);
            rwyPaint.setTextAlign (Paint.Align.CENTER);
            for (RwyInfo rwy : runways.values ()) {
                canvas.save ();
                double canX = BitmapX2CanvasX (rwy.numsTopBmpX);
                double canY = BitmapY2CanvasY (rwy.numsTopBmpY);
                double deg  = rwy.rwywp.trueHdg;
                canvas.rotate ((float) deg, (float) canX, (float) canY);
                canvas.drawText (rwy.rwywp.number, 0, rwy.rwywp.number.length (),
                        (float) canX, (float) (canY + rwy.numBounds.height () * 1.5), rwyPaint);
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

            double leftbmx = CanvasX2BitmapX (0);
            double ritebmx = CanvasX2BitmapX (canWidth);
            double topbmy  = CanvasY2BitmapY (0);
            double botbmy  = CanvasY2BitmapY (canHeight);

            double tllat = BitmapXY2Lat (leftbmx, topbmy);
            double tllon = BitmapXY2Lon (leftbmx, topbmy);
            double trlat = BitmapXY2Lat (ritebmx, topbmy);
            double trlon = BitmapXY2Lon (ritebmx, topbmy);
            double bllat = BitmapXY2Lat (leftbmx, botbmy);
            double bllon = BitmapXY2Lon (leftbmx, botbmy);
            double brlat = BitmapXY2Lat (ritebmx, botbmy);
            double brlon = BitmapXY2Lon (ritebmx, botbmy);

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
            public double beg, end, nm;

            public void drawIt (Canvas bmcan)
            {
                // wrap beg to be in range (0..360] as that is the one that gets a label
                while (beg <=  0.0) beg += 360.0;
                while (beg > 360.0) beg -= 360.0;

                // wrap end to be within 180 of beg
                // it might end up negative, it might end up more than 360
                while (end - beg < -180.0) end += 360.0;
                while (end - beg >= 180.0) end += 360.0;

                // calculate true endpoints
                double magvar = nav.GetMagVar (nav.elev);
                double trubeg = beg - magvar;
                double truend = end - magvar;

                // get lat,lon of arc endpoints
                double beglat = Lib.LatHdgDist2Lat (nav.lat, trubeg, nm);
                double beglon = Lib.LatLonHdgDist2Lon (nav.lat, nav.lon, trubeg, nm);
                double endlat = Lib.LatHdgDist2Lat (nav.lat, truend, nm);
                double endlon = Lib.LatLonHdgDist2Lon (nav.lat, nav.lon, truend, nm);

                // convert all the lat,lon to bitmap x,y
                double begbmx = LatLon2BitmapX (beglat, beglon);
                double begbmy = LatLon2BitmapY (beglat, beglon);
                double endbmx = LatLon2BitmapX (endlat, endlon);
                double endbmy = LatLon2BitmapY (endlat, endlon);
                double navbmx = LatLon2BitmapX (nav.lat, nav.lon);
                double navbmy = LatLon2BitmapY (nav.lat, nav.lon);

                double radius = Math.sqrt (Math.hypot (begbmx - navbmx, begbmy - navbmy) *
                        Math.hypot (endbmx - navbmx, endbmy - navbmy));

                // make rectangle the arc circle fits in
                RectF rect = new RectF ((float) (navbmx - radius), (float) (navbmy - radius),
                        (float) (navbmx + radius), (float) (navbmy + radius));

                // draw arc
                Paint paint = new Paint ();
                paint.setColor (Color.BLACK);
                paint.setFlags (Paint.ANTI_ALIAS_FLAG);
                paint.setStrokeWidth (6.0F);
                paint.setStyle (Paint.Style.STROKE);
                double start = Math.min (trubeg, truend) - 90.0;
                double sweep = Math.abs (truend - trubeg);
                bmcan.drawArc (rect, (float) start, (float) sweep, false, paint);

                // draw radial line segment at beginning of arc
                double trubegsin = Math.sin (Math.toRadians (trubeg));
                double trubegcos = Math.cos (Math.toRadians (trubeg));
                double pixpernm = radius / nm;
                double innerx = navbmx + (nm - 0.6) * pixpernm * trubegsin;
                double innery = navbmy - (nm - 0.6) * pixpernm * trubegcos;
                double outerx = navbmx + (nm + 0.6) * pixpernm * trubegsin;
                double outery = navbmy - (nm + 0.6) * pixpernm * trubegcos;
                paint.setStrokeWidth (3.0F);
                bmcan.drawLine ((float) innerx, (float) innery, (float) outerx, (float) outery, paint);

                // draw legend
                String legend1 = nav.ident + " " + Math.round (beg) + "\u00B0";
                String legend2 = String.format (Locale.US, "%.1f", nm) + " nm";
                paint.setStrokeWidth (1.0F);
                paint.setStyle (Paint.Style.FILL_AND_STROKE);
                paint.setTextSize (25.0F);
                bmcan.save ();
                if (trubeg <= 180.0) {
                    bmcan.rotate ((float) (trubeg - 90.0), (float) outerx, (float) outery);
                } else {
                    Rect bounds1 = new Rect ();
                    Rect bounds2 = new Rect ();
                    paint.getTextBounds (legend1, 0, legend1.length (), bounds1);
                    paint.getTextBounds (legend2, 0, legend2.length (), bounds2);
                    int width = Math.max (bounds1.width (), bounds2.width ());
                    bmcan.rotate ((float) (trubeg - 270.0), (float) outerx, (float) outery);
                    bmcan.translate (- width, 0);
                }
                bmcan.drawText (legend1, (float) outerx, (float) outery, paint);
                outery += paint.getTextSize ();
                bmcan.drawText (legend2, (float) outerx, (float) outery, paint);
                bmcan.restore ();
            }
        }

        private Bitmap       fgbitmap;              // single plate page
        private boolean      hasDMEArcs;
        private byte         circradtype;
        private double       bitmapLat, bitmapLon;
        private double       bmxy2llx = Double.NaN;
        private double       bmxy2lly = Double.NaN;
        private int          circradopt;
        private Lambert      lccmap;                // non-null: FAA-provided georef data
        private LatLon       bmxy2latlon = new LatLon ();
        private LinkedList<DMEArc> dmeArcs;
        private long         plateLoadedUptime;
        public  PlateCIFP    plateCIFP;
        private PlateDME     plateDME;
        private PlateTimer   plateTimer;
        private PointD       bitmapPt = new PointD ();
        private String       filename;              // name of gifs on flash, without ".p<pageno>" suffix

        public IAPPlateImage (String fn)
        {
            filename = fn;

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
         * @param nm  = arc radius, usually 10.0
         */
        public void DrawDMEArc (Waypoint nav, double beg, double end, double nm)
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
                if (!machinedb.columnExists ("iapgeorefs2", "gr_circ")) {
                    machinedb.execSQL ("ALTER TABLE iapgeorefs2 ADD COLUMN gr_circ INTEGER NOT NULL DEFAULT " + CRT_UNKN);
                }
                Cursor result = machinedb.query ("iapgeorefs2", columns_georefs21,
                        "gr_icaoid=? AND gr_plate=?", grkey,
                        null, null, null, null);
                try {
                    if (result.moveToFirst ()) {
                        lccmap = new Lambert (
                                result.getDouble (0),
                                result.getDouble (1),
                                result.getDouble (2),
                                result.getDouble (3),
                                result.getDouble (4),
                                result.getDouble (5),
                                new double[] {
                                    result.getDouble (6),
                                    result.getDouble (7),
                                    result.getDouble (8),
                                    result.getDouble (9),
                                    result.getDouble (10),
                                    result.getDouble (11)
                                }
                        );
                        circradtype = (byte) result.getInt (12);
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
            if (fgbitmap != null) {
                fgbitmap.recycle ();
                fgbitmap = null;
            }
        }

        protected void GotMouseDown (double x, double y)
        {
            plateCIFP.GotMouseDown (x, y);
            plateDME.GotMouseDown (x, y);
            plateTimer.GotMouseDown (x, y);
            wairToNow.currentCloud.MouseDown ((int) Math.round (x), (int) Math.round (y),
                    System.currentTimeMillis ());
        }

        protected void GotMouseUp (double x, double y)
        {
            plateCIFP.GotMouseUp ();
            plateDME.GotMouseUp ();
            if (wairToNow.currentCloud.MouseUp ((int) Math.round (x), (int) Math.round (y),
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
            if (fgbitmap == null) {

                // get original page
                Bitmap bm = OpenFirstPage (filename);
                if (bm == null) return;
                if (bm.isMutable ()) {
                    fgbitmap = bm;
                } else {
                    fgbitmap = bm.copy (Bitmap.Config.ARGB_8888, true);
                    bm.recycle ();
                }

                // add DME arcs if any defined
                if (hasDMEArcs) {
                    DrawDMEArcsOnBitmap ();
                }

                // shade circling area
                if (lccmap != null) {
                    ShadeCirclingArea ();
                }
            }

            /*
             * Display bitmap to canvas.
             */
            ShowSinglePage (canvas, fgbitmap);

            /*
             * Draw runways if FAA-provided georef present.
             */
            if ((lccmap != null) && (SystemClock.uptimeMillis () - plateLoadedUptime < 10000)) {
                DrawRunwayCenterlines (canvas);
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
        public double LatLon2BitmapX (double lat, double lon)
        {
            if (lccmap == null) return Double.NaN;
            bitmapLat = lat;
            bitmapLon = lon;
            lccmap.LatLon2ChartPixelExact (lat, lon, bitmapPt);
            return bitmapPt.x;
        }

        @Override  // GRPPlateImage
        public double LatLon2BitmapY (double lat, double lon)
        {
            if (lccmap == null) return Double.NaN;
            if ((bitmapLat != lat) || (bitmapLon != lon)) {
                bitmapLat = lat;
                bitmapLon = lon;
                lccmap.LatLon2ChartPixelExact (lat, lon, bitmapPt);
            }
            return bitmapPt.y;
        }

        @Override  // GRPPlateImage
        public double BitmapXY2Lat (double bmx, double bmy)
        {
            if (lccmap == null) return Double.NaN;
            if ((bmx != bmxy2llx) || (bmy != bmxy2lly)) {
                bmxy2llx = bmx;
                bmxy2lly = bmy;
                lccmap.ChartPixel2LatLonExact (bmx, bmy, bmxy2latlon);
            }
            return bmxy2latlon.lat;
        }

        @Override  // GRPPlateImage
        public double BitmapXY2Lon (double bmx, double bmy)
        {
            if (lccmap == null) return Double.NaN;
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
            // draw DME arc(s) on bitmap
            Canvas bmcan = new Canvas (fgbitmap);
            for (DMEArc dmeArc : dmeArcs) dmeArc.drawIt (bmcan);

            // if we don't already have an image file, create one
            File csvfile = new File (WairToNow.dbdir + "/dmearcs.txt");
            File imgfile = new File (WairToNow.dbdir + "/dmearcs-" + airport.ident + "-" +
                    plateid.replace (" ", "_").replace ("/", "_") + "-" + expdate + ".png");
            if (imgfile.lastModified () < csvfile.lastModified ()) {
                try {
                    File tmpfile = new File (imgfile.getPath () + ".tmp");
                    FileOutputStream tmpouts = new FileOutputStream (tmpfile);
                    try {
                        if (!fgbitmap.compress (Bitmap.CompressFormat.PNG, 0, tmpouts)) {
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

        /**
         * Set up fgbitmap with circling area shaded in.
         */
        private void ShadeCirclingArea ()
        {
            // get speed category selected by user
            // don't do anything more if user has disabled circling area shading
            circradopt = wairToNow.optionsView.circCatOption.getVal ();
            if (circradopt < 0) return;

            // fill in shading in background thread
            // make sure we only have one thread so we don't run out of memory
            synchronized (shadeCirclingAreaLock) {
                ShadeCirclingAreaThread scat = shadeCirclingAreaThread;
                if (scat == null) {
                    shadeCirclingAreaThread = scat = new ShadeCirclingAreaThread ();
                    scat.start ();
                }
                scat.nextPlateImage = this;
            }
        }

        /**
         * This runs in the ShadeCirclingAreaThread to scan bitmap for the inverse [C] if necessary
         * then shade in the circling area on the bitmap.
         */
        @SuppressWarnings("ConstantConditions")  // method too complex
        public void ShadeCirclingAreaWork ()
        {
            try {

                // maybe need to compute circling area type
                if (circradtype == CRT_UNKN) {

                    // read bitmap containing prototype white C on black background
                    InputStream tagstream = wairToNow.getAssets ().open ("circletag.png");
                    Bitmap tagbmp = BitmapFactory.decodeStream (tagstream);
                    tagstream.close ();
                    int tagwidth  = tagbmp.getWidth  ();
                    int tagheight = tagbmp.getHeight ();
                    int[] tagpixs = new int[tagwidth*tagheight];
                    tagbmp.getPixels (tagpixs, 0, tagwidth, 0, 0, tagwidth, tagheight);
                    tagbmp.recycle ();
                    boolean[] tagblacks = new boolean[tagwidth*tagheight];
                    for (int i = tagwidth * tagheight; -- i >= 0;) {
                        tagblacks[i] = Color.red (tagpixs[i]) < 128;
                    }

                    // get lower left quadrant of plate image
                    int bmpwidth  = fgbitmap.getWidth  () / 2;
                    int bmpheight = fgbitmap.getHeight () / 2;
                    int[] pixels = new int[bmpwidth*bmpheight];
                    fgbitmap.getPixels (pixels, 0, bmpwidth, 0, bmpheight, bmpwidth, bmpheight);
                    boolean[] blacks = new boolean[bmpwidth*bmpheight];
                    for (int i = bmpwidth * bmpheight; -- i >= 0;) {
                        blacks[i] = Color.red (pixels[i]) < 128;
                    }

                    // search lower left quadrant for inverse [C]
                    circradtype = CRT_OLD;
                    for (int y = 0; y < bmpheight - tagheight; y ++) {
                        for (int x = 0; x < bmpwidth - tagwidth; x ++) {
                            int matches = 0;
                            for (int yy = 0; yy < tagheight;) {
                                for (int xx = 0; xx < tagwidth; xx ++) {
                                    boolean black = blacks[(y+yy)*bmpwidth+(x+xx)];
                                    if (black == tagblacks[yy*tagwidth+xx]) matches ++;
                                }
                                if (matches * 8 < ++ yy * tagwidth * 7) break;
                            }
                            if (matches * 8 > tagheight * tagwidth * 7) {
                                circradtype = CRT_NEW;
                                x = bmpwidth;
                                y = bmpheight;
                            }
                        }
                    }

                    // write value to database so we don't have to scan this plate again
                    ContentValues values = new ContentValues (1);
                    values.put ("gr_circ", circradtype);
                    String[] whargs = new String[] { airport.ident, plateid };
                    String machinedbname = "plates_" + expdate + ".db";
                    SQLiteDBs machinedb = SQLiteDBs.create (machinedbname);
                    machinedb.update ("iapgeorefs2", values, "gr_icaoid=? AND gr_plate=?", whargs);
                }

                // fill shading in on plate
                // get radius in tenths of a nautical mile
                // we don't have circling MDA so use airport elev + 200 which is conservative
                int circrad10ths;
                String type;
                if (circradtype == CRT_NEW) {
                    int aptelevidx = (airport.elev == Waypoint.ELEV_UNKNOWN) ? 0 : ((int) airport.elev + 1200) / 2000;
                    if (aptelevidx > 5) aptelevidx = 5;
                    circrad10ths = newcircrad10ths[aptelevidx][circradopt];
                    type = "new";
                } else {
                    circrad10ths = oldcircrad10ths[circradopt];
                    type = "old";
                }
                Log.i (TAG, airport.ident + " " + plateid + " using " + type + " circling radii of " + (circrad10ths / 10.0));

                // make array of runway endpoints
                // also compute circling radius in bitmap pixels
                Collection<Waypoint.Runway> runways = airport.GetRunways ().values ();
                int nrwyeps     = runways.size ();
                if (nrwyeps == 0) return;
                PointD[] rwyeps = new PointD[nrwyeps];
                double circradbmp = Double.NaN;
                double minrwybmpx = Double.MAX_VALUE;
                double minrwybmpy = Double.MAX_VALUE;
                double maxrwybmpx = Double.MIN_VALUE;
                double maxrwybmpy = Double.MIN_VALUE;
                int i = 0;
                for (Waypoint.Runway runway : runways) {
                    double rwybmpx = LatLon2BitmapX (runway.lat, runway.lon);
                    double rwybmpy = LatLon2BitmapY (runway.lat, runway.lon);
                    rwyeps[i++]    = new PointD (rwybmpx, rwybmpy);
                    if (minrwybmpx > rwybmpx) minrwybmpx = rwybmpx;
                    if (minrwybmpy > rwybmpy) minrwybmpy = rwybmpy;
                    if (maxrwybmpx < rwybmpx) maxrwybmpx = rwybmpx;
                    if (maxrwybmpy < rwybmpy) maxrwybmpy = rwybmpy;
                    if (Double.isNaN (circradbmp)) {
                        double offbmpx = LatLon2BitmapX (runway.lat + 0.1 / Lib.NMPerDeg, runway.lon);
                        double offbmpy = LatLon2BitmapY (runway.lat + 0.1 / Lib.NMPerDeg, runway.lon);
                        double bmpixper10th = Math.hypot (rwybmpx - offbmpx, rwybmpy - offbmpy);
                        circradbmp = bmpixper10th * circrad10ths;
                    }
                }

                // make a clockwise convex polygon out of the points,
                // ignoring any on the interior
                if (nrwyeps > 2) {
                    PointD[] temp = new PointD[nrwyeps];
                    i = 0;

                    // find westmost point, guaranteed to be an outie
                    int bestj = 0;
                    for (int j = 0; ++ j < nrwyeps;) {
                        if (rwyeps[bestj].x > rwyeps[j].x) bestj = j;
                    }
                    temp[i++] = rwyeps[bestj];
                    rwyeps[bestj] = rwyeps[--nrwyeps];

                    // find next point of lowest heading from that
                    // due north is 0, east is 90, south is 180, west is 270
                    // repeat until the answer is the starting point
                    // since we are at westmost point to start, next point will be east of it
                    double lasth = 0.0;
                    while (true) {
                        PointD lastep = temp[i-1];
                        double besth = Double.MAX_VALUE;
                        bestj = -1;
                        for (int j = 0; j < nrwyeps; j ++) {
                            PointD rwyep = rwyeps[j];

                            // get heading from last point to this point
                            // range is -PI .. +PI
                            double h = Math.atan2 (rwyep.x - lastep.x, lastep.y - rwyep.y);

                            // lasth is in range 0 .. +2*PI
                            // compute how much we have to turn right
                            // so resultant range of h is -3*PI .. +PI
                            h -= lasth;

                            // wrap to range -PI/10 .. +PI*19/10
                            // the -PI/10 allows for a little turning left for rounding errors
                            if (h < - Math.PI / 10.0) h += Math.PI * 2.0;

                            // save it if it is the least amount of right turn
                            // (this includes saving if it is a little bit of a left turn
                            //  from rounding errors)
                            if (besth > h) {
                                besth = h;
                                bestj = j;
                            }
                        }

                        // if made it all the way back to westmost point, we're done
                        if (rwyeps[bestj] == temp[0]) break;

                        // otherwise save point, remove from list, and continue on
                        temp[i++] = rwyeps[bestj];
                        rwyeps[bestj] = (i == 2) ? temp[0] : rwyeps[--nrwyeps];
                        lasth += besth;
                    }

                    // replace old list with new one
                    // it may have fewer points (some were on interior)
                    rwyeps = temp;
                    nrwyeps = i;
                }

                // draw outline circradbmp outside that polygon, rounding the corners
                Path  path = new Path  ();
                RectF oval = new RectF ();
                PointD q = rwyeps[(nrwyeps*2-2)%nrwyeps];
                PointD r = rwyeps[nrwyeps-1];
                double tcqr = Math.toDegrees (Math.atan2 (r.x - q.x, q.y - r.y));
                for (i = 0; i < nrwyeps; i ++) {

                    // three points going clockwise around the perimeter P -> Q -> R
                    q = r;
                    r = rwyeps[i];

                    // standing at Q looking at R:
                    //  draw arc from Q of displaced PQ to Q of displaced QR
                    // we can assume it is a right turn at Q from PQ to QR
                    //  the turn amount to the right should be -1..+181 degrees
                    //  (the extra degree on each end is for rounding errors)
                    // the Path object will fill in a line from the last arc to this one
                    oval.bottom  = (float) (q.y + circradbmp);
                    oval.left    = (float) (q.x - circradbmp);
                    oval.right   = (float) (q.x + circradbmp);
                    oval.top     = (float) (q.y - circradbmp);
                    double tcpq  = tcqr;
                    tcqr         = Math.toDegrees (Math.atan2 (r.x - q.x, q.y - r.y));
                    double start = tcpq + 180.0;
                    double sweep = tcqr - tcpq;
                    if (sweep < -10.0) sweep += 360.0;
                    if (sweep > 0.0) {  // slight left turn from rounding errors gets no curve
                        path.arcTo (oval, (float) start, (float) sweep);
                    }
                }

                // close the path by joining the end of the last arc to the beginning of the first arc
                path.close ();

                // draw the path and fill it in
                // use darken mode to leave black stuff fully black
                Canvas canvas = new Canvas (fgbitmap);
                Paint  paint  = new Paint  ();
                paint.setColor (Color.argb (255, 255, 215, 0));
                paint.setStrokeWidth (0);
                paint.setStyle (Paint.Style.FILL_AND_STROKE);
                paint.setXfermode (new PorterDuffXfermode (PorterDuff.Mode.DARKEN));
                canvas.drawPath (path, paint);
            } catch (Exception e) {
                Log.w (TAG, "error shading circling area", e);
            }
        }
    }

    /**
     * If we don't know if the plate uses the old or new circling area definitions,
     * scan it and write the tag to the database.  Then draw the circling area.
     * If we erroneously don't find the inverse C, then we assume old-style circling
     * radii which are more conservative than the new ones which is OK.
     */
    private static class ShadeCirclingAreaThread extends Thread {
        public IAPPlateImage nextPlateImage;

        @Override
        public void run ()
        {
            setName ("ShadeCirclingAreaThread");
            setPriority (MIN_PRIORITY);
            while (true) {
                IAPPlateImage iapPlateImage;
                synchronized (shadeCirclingAreaLock) {
                    iapPlateImage = nextPlateImage;
                    if (iapPlateImage == null) {
                        shadeCirclingAreaThread = null;
                        break;
                    }
                    nextPlateImage = null;
                }
                iapPlateImage.ShadeCirclingAreaWork ();
            }
            SQLiteDBs.CloseAll ();
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

        protected void GotMouseDown (double x, double y) { }
        protected void GotMouseUp (double x, double y) { }

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
                canTmpRect.top    = (float) BitmapY2CanvasY (imageTop) + vpi * 2;
                canTmpRect.bottom = (float) BitmapY2CanvasY (imageBot) + vpi * 2;
                if (canTmpRect.top    >= canHeight) continue;
                if (canTmpRect.bottom <= 0)         continue;

                for (int hpi = 0; vpi + hpi < numPages; hpi ++) {
                    int imageLeft = hpi * bmWidth;
                    int imageRite = imageLeft + bmWidth;

                    /*
                     * See where it maps to on canvas and skip if not visible.
                     */
                    canTmpRect.left  = (float) BitmapX2CanvasX (imageLeft) + hpi * 2;
                    canTmpRect.right = (float) BitmapX2CanvasX (imageRite) + hpi * 2;
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
