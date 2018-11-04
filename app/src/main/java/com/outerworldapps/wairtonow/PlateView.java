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
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    private final static Paint invertpaint = makeInvertPaint ();

    private boolean full;               // plate occupies full screen, draw GPS available outline
    private boolean invcolors;          // invert plate colors (black<->white and everything else)
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
            "gr_tfwa", "gr_tfwb", "gr_tfwc", "gr_tfwd", "gr_tfwe", "gr_tfwf", "gr_circtype", "gr_circrweps" };

    // adapted from https://gist.github.com/moneytoo/87e3772c821cb1e86415
    private static Paint makeInvertPaint ()
    {
        ColorMatrix matrixgrayscale = new ColorMatrix ();
        matrixgrayscale.setSaturation (0.0F);
        ColorMatrix matrixinvert = new ColorMatrix ();
        matrixinvert.set (new float [] {
                -1.0F, 0.0F, 0.0F, 0.0F, 255.0F,
                0.0F, -1.0F, 0.0F, 0.0F, 255.0F,
                0.0F, 0.0F, -1.0F, 0.0F, 255.0F,
                0.0F, 0.0F, 0.0F, 1.0F, 0.0F
        });
        matrixinvert.preConcat (matrixgrayscale);
        ColorMatrixColorFilter cmcf = new ColorMatrixColorFilter (matrixinvert);
        Paint invpaint = new Paint ();
        invpaint.setColorFilter (cmcf);
        return invpaint;
    }

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
        } else if (plateid.startsWith (IAPSynthPlateImage.prefix)) {
            plateImage = new IAPSynthPlateImage ();
        } else if (plateid.startsWith ("IAP-")) {
            plateImage = new IAPRealPlateImage (fn);
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
     * Read gif bitmap from state-specific zip file
     * @param fn = name of gif file within the zip file
     */
    private Bitmap readGif (String fn)
            throws IOException
    {
        if (fn.startsWith ("gif_150/")) fn = fn.substring (8);
        ZipFile zf = wairToNow.maintView.getStateZipFile (airport.state);
        if (zf == null) {
            throw new FileNotFoundException ("state " + airport.state + " not downloaded");
        }
        ZipEntry ze = zf.getEntry (fn);
        if (ze == null) {
            throw new FileNotFoundException ("gif " + fn + " not found in state " + airport.state);
        }
        InputStream is = zf.getInputStream (ze);
        try {
            Bitmap bm = BitmapFactory.decodeStream (is);
            if (bm == null) {
                throw new IOException ("bitmap " + fn + " corrupt in state " + airport.state);
            }
            invcolors = wairToNow.optionsView.invPlaColOption.checkBox.isChecked ();
            if (invcolors) {
                int h = bm.getHeight ();
                int w = bm.getWidth ();
                Bitmap newbm = Bitmap.createBitmap (w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas (newbm);
                canvas.drawBitmap (bm, 0, 0, invertpaint);
                bm.recycle ();
                bm = newbm;
            }
            return bm;
        } finally {
            is.close ();
        }
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
        }

        public abstract void CloseBitmaps ();
        protected abstract void GotMouseDown (double x, double y);
        protected abstract void GotMouseUp (double x, double y);

        public void SetGPSLocation () { }

        // used by PlateDME
        public Waypoint FindWaypoint (String wayptid)
        {
            return airport.GetNearbyWaypoint (wayptid);
        }

        /**
         * Callback for mouse events on the image.
         * We use this for scrolling the map around.
         */
        @Override  // View
        public boolean onTouchEvent (@NonNull MotionEvent event)
        {
            if (advPanAndZoom == null) advPanAndZoom = new ADVPanAndZoom ();
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
         * Open first plate bitmap image page file and map to view.
         */
        protected Bitmap OpenFirstPage (String filename)
        {
            Bitmap bm;
            try {
                bm = readGif (filename + ".p1");
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
         * Since the plate is georeferenced, re-draw it with new GPS location.
         */
        @Override
        public void SetGPSLocation ()
        {
            invalidate ();
        }

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
         *   (ilat,ilon) <=> (ipixelx,ipixely)
         *   (jlat,jlon) <=> (jpixelx,jpixely)
         */
        @SuppressWarnings("SameParameterValue")
        protected void SetLatLon2Bitmap (double ilat, double ilon, double jlat, double jlon,
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
            //return Math.atan2 (xfmwftb, xfmwfta);
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
            Collection<Waypoint> wps = new Waypoint.Within (wairToNow).Get (minlat, maxlat, minlon, maxlon);
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

    /*
     * A real or synthetic IAP plate.
     */
    public abstract class IAPPlateImage extends GRPlateImage {
        protected long       plateLoadedUptime;
        protected PlateCIFP  plateCIFP;
        private   PlateDME   plateDME;
        private   PlateTimer plateTimer;

        protected IAPPlateImage ()
        {
            plateDME   = new PlateDME (wairToNow, this, airport.ident, plateid);
            plateTimer = new PlateTimer (wairToNow, this);
            plateLoadedUptime = SystemClock.uptimeMillis ();
        }

        // maybe we know what navaid is being used by the plate
        public abstract Waypoint GetRefNavWaypt ();

        protected void DrawCommon (Canvas canvas)
        {
            /*
             * Draw any CIFP, enabled DMEs and timer.
             */
            if (!wairToNow.optionsView.typeBOption.checkBox.isChecked ()) {
                if (plateCIFP != null) plateCIFP.DrawCIFP (canvas);
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

        protected void GotMouseDown (double x, double y)
        {
            plateDME.GotMouseDown (x, y);
            plateTimer.GotMouseDown (x, y);
            if (plateCIFP != null) plateCIFP.GotMouseDown (x, y);
            wairToNow.currentCloud.MouseDown ((int) Math.round (x), (int) Math.round (y),
                    System.currentTimeMillis ());
        }

        protected void GotMouseUp (double x, double y)
        {
            plateDME.GotMouseUp ();
            if (plateCIFP != null) plateCIFP.GotMouseUp ();
            wairToNow.currentCloud.MouseUp ((int) Math.round (x), (int) Math.round (y),
                    System.currentTimeMillis ());
        }

        @Override  // PlateImage
        public void SetGPSLocation ()
        {
            super.SetGPSLocation ();
            if (plateCIFP != null) plateCIFP.SetGPSLocation ();
        }

        public abstract void ShadeCirclingAreaWork ();
    }

    /**
     * Display a real IAP plate image file.
     */
    public class IAPRealPlateImage extends IAPPlateImage {

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
                paint.setColor (invcolors ? Color.WHITE : Color.BLACK);
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
        private byte[]       circrwyepts;
        private double       bitmapLat, bitmapLon;
        private double       bmxy2llx = Double.NaN;
        private double       bmxy2lly = Double.NaN;
        private int          circradopt;
        private Lambert      lccmap;                // non-null: FAA-provided georef data
        private LatLon       bmxy2latlon = new LatLon ();
        private LinkedList<DMEArc> dmeArcs;
        private PointD       bitmapPt = new PointD ();
        private String       filename;              // name of gifs on flash, without ".p<pageno>" suffix

        public IAPRealPlateImage (String fn)
        {
            filename = fn;

            /*
             * Get any georeference points in database.
             */
            GetIAPGeoRefPoints ();

            /*
             * Init CIFP class.
             * Constructor uses lat/lon <=> bitmap pixel mapping.
             */
            plateCIFP = new PlateCIFP (wairToNow, this, airport, plateid);
        }

        /**
         * Maybe CIFP knows what navaid the approach uses.
         */
        @Override  // IAPPlateImage
        public Waypoint GetRefNavWaypt ()
        {
            return plateCIFP.getRefNavWaypt ();
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

        /**
         * Check for FAA-supplied georeference information.
         */
        private void GetIAPGeoRefPoints ()
        {
            String[] grkey = new String[] { airport.ident, plateid };
            String machinedbname = "plates_" + expdate + ".db";
            SQLiteDBs machinedb = SQLiteDBs.create (machinedbname);
            if (machinedb.tableExists ("iapgeorefs2")) {
                if (!machinedb.columnExists ("iapgeorefs2", "gr_circtype")) {
                    machinedb.execSQL ("ALTER TABLE iapgeorefs2 ADD COLUMN gr_circtype INTEGER NOT NULL DEFAULT " + CRT_UNKN);
                }
                if (!machinedb.columnExists ("iapgeorefs2", "gr_circrweps")) {
                    machinedb.execSQL ("ALTER TABLE iapgeorefs2 ADD COLUMN gr_circrweps BLOB");
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
                        circrwyepts = result.getBlob (13);
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
             * Draw a bunch of stuff on the plate.
             */
            DrawCommon (canvas);
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
                    plateid.replace (" ", "_").replace ("/", "_") + "-" + expdate + ".gif");
            if (imgfile.lastModified () < csvfile.lastModified ()) {
                synchronized (WriteGifThread.list) {
                    if (!WriteGifThread.list.containsKey (imgfile)) {
                        WriteGifThread.Node node = new WriteGifThread.Node (fgbitmap);
                        WriteGifThread.list.put (imgfile, node);
                        if (WriteGifThread.thread == null) {
                            WriteGifThread.thread = new WriteGifThread ();
                            WriteGifThread.thread.start ();
                        }
                    }
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
        @Override  // IAPPlateImage
        @SuppressWarnings("ConstantConditions")  // method too complex
        public void ShadeCirclingAreaWork ()
        {
            try {

                // maybe need to compute circling area type
                //  CRT_OLD: old style, independent of circling MDA
                //  CRT_NEW: new style, depends on circling MDA
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
                    values.put ("gr_circtype", circradtype);
                    String[] whargs = new String[] { airport.ident, plateid };
                    String machinedbname = "plates_" + expdate + ".db";
                    SQLiteDBs machinedb = SQLiteDBs.create (machinedbname);
                    machinedb.update ("iapgeorefs2", values, "gr_icaoid=? AND gr_plate=?", whargs);
                }

                // maybe we need to compute circling ring endpoints
                // it is a convex polygon whose vertices are the runway endpoints
                // any interior points are discarded/ignored
                if ((circrwyepts == null) || (circrwyepts.length == 0)) {

                    // make array of runway endpoints
                    Collection<Waypoint.Runway> runways = airport.GetRunways ().values ();
                    int nrwyeps     = runways.size ();
                    if (nrwyeps == 0) return;
                    PointD[] rwyeps = new PointD[nrwyeps];
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

                    // write endpoints in order to database blob so we don't have to recompute it next time
                    // packed as pairs of shorts, x,y, for each endpoint
                    circrwyepts = new byte[nrwyeps*4];
                    int j = 0;
                    for (i = 0; i < nrwyeps; i ++) {
                        long xx = Math.round (rwyeps[i].x);
                        long yy = Math.round (rwyeps[i].y);
                        if ((xx != (short) xx) || (yy != (short) yy)) {
                            throw new Exception ("bad runway x,y");
                        }
                        circrwyepts[j++] = (byte) xx;
                        circrwyepts[j++] = (byte) (xx >> 8);
                        circrwyepts[j++] = (byte) yy;
                        circrwyepts[j++] = (byte) (yy >> 8);
                    }

                    ContentValues values = new ContentValues (1);
                    values.put ("gr_circrweps", circrwyepts);
                    String[] whargs = new String[] { airport.ident, plateid };
                    String machinedbname = "plates_" + expdate + ".db";
                    SQLiteDBs machinedb = SQLiteDBs.create (machinedbname);
                    machinedb.update ("iapgeorefs2", values, "gr_icaoid=? AND gr_plate=?", whargs);
                }

                // get circling radius in tenths of a nautical mile based on circling MDA MSL and category
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
                Log.i (TAG, airport.ident + " " + plateid + " using " + type + " circling radius of " + (circrad10ths / 10.0));

                // get circling radius in bitmap pixels
                double circradlat = airport.lat + circrad10ths / (Lib.NMPerDeg * 10.0);
                double aptbmx = LatLon2BitmapX (airport.lat, airport.lon);
                double aptbmy = LatLon2BitmapY (airport.lat, airport.lon);
                double radbmx = LatLon2BitmapX (circradlat, airport.lon);
                double radbmy = LatLon2BitmapY (circradlat, airport.lon);
                double circradbmp = Math.hypot (aptbmx - radbmx, aptbmy - radbmy);

                // draw outline circradbmp outside that polygon, rounding the corners
                int nrwyeps = circrwyepts.length / 4;
                Path  path = new Path  ();
                RectF oval = new RectF ();
                int qx = circRwyEpX ((nrwyeps * 2 - 2) % nrwyeps);
                int qy = circRwyEpY ((nrwyeps * 2 - 2) % nrwyeps);
                int rx = circRwyEpX (nrwyeps - 1);
                int ry = circRwyEpY (nrwyeps - 1);
                double tcqr = Math.toDegrees (Math.atan2 (rx - qx, qy - ry));
                for (int i = 0; i < nrwyeps; i ++) {

                    // three points going clockwise around the perimeter P -> Q -> R
                    qx = rx; qy = ry;
                    rx = circRwyEpX (i);
                    ry = circRwyEpY (i);

                    // standing at Q looking at R:
                    //  draw arc from Q of displaced PQ to Q of displaced QR
                    // we can assume it is a right turn at Q from PQ to QR
                    //  the turn amount to the right should be -1..+181 degrees
                    //  (the extra degree on each end is for rounding errors)
                    // the Path object will fill in a line from the last arc to this one
                    oval.bottom  = (float) (qy + circradbmp);
                    oval.left    = (float) (qx - circradbmp);
                    oval.right   = (float) (qx + circradbmp);
                    oval.top     = (float) (qy - circradbmp);
                    double tcpq  = tcqr;
                    tcqr         = Math.toDegrees (Math.atan2 (rx - qx, qy - ry));
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
                paint.setColor (invcolors ? Color.argb (170, 170, 143, 0) : Color.argb (255, 255, 215, 0));
                paint.setStrokeWidth (0);
                paint.setStyle (Paint.Style.FILL_AND_STROKE);
                paint.setXfermode (new PorterDuffXfermode (
                        invcolors ? PorterDuff.Mode.LIGHTEN : PorterDuff.Mode.DARKEN));
                canvas.drawPath (path, paint);
            } catch (Exception e) {
                Log.w (TAG, "error shading circling area", e);
            }
        }

        private int circRwyEpX (int i)
        {
            i *= 4;
            int xx = circrwyepts[i++] & 0xFF;
            xx += circrwyepts[i] << 8;
            return xx;
        }

        private int circRwyEpY (int i)
        {
            i *= 4;
            i += 2;
            int yy = circrwyepts[i++] & 0xFF;
            yy += circrwyepts[i]   << 8;
            return yy;
        }
    }

    /**
     * First time displaying a plat with a DME arc on it,
     * write to a .gif file in a background thread.
     */
    private static class WriteGifThread extends Thread {
        public static class Node {
            public int[] data;
            public int width;
            public int height;

            public Node (Bitmap bm)
            {
                width  = bm.getWidth  ();
                height = bm.getHeight ();
                data   = new int[width*height];
                bm.getPixels (data, 0, width, 0, 0, width, height);
            }
        }

        public static final HashMap<File,Node> list = new HashMap<> ();
        public static WriteGifThread thread;

        @Override
        public void run ()
        {
            setName ("WriteGifThread");
            setPriority (Thread.MIN_PRIORITY);

            File imgfile = null;
            Node node;
            while (true) {

                // see if another gif to write
                // exit thread if not
                synchronized (list) {
                    if (imgfile != null) list.remove (imgfile);
                    Iterator<File> it = list.keySet ().iterator ();
                    if (!it.hasNext ()) {
                        thread = null;
                        break;
                    }
                    imgfile = it.next ();
                    node = list.get (imgfile);
                }

                // write pixel data to temp file in gif format
                long ms = SystemClock.uptimeMillis ();
                Log.i (TAG, "creating " + imgfile.getPath ());
                try {
                    File tmpfile = new File (imgfile.getPath () + ".tmp");
                    FileOutputStream tmpouts = new FileOutputStream (tmpfile);
                    try {
                        AnimatedGifEncoder encoder = new AnimatedGifEncoder ();
                        encoder.start (tmpouts);
                        encoder.addFrame (node.data, node.width, node.height);
                        encoder.finish ();
                    } finally {
                        tmpouts.close ();
                    }

                    // temp written successfully, rename to permanent name
                    if (!tmpfile.renameTo (imgfile)) {
                        throw new IOException ("error renaming from " + tmpfile.getPath ());
                    }
                } catch (IOException ioe) {

                    // failed to write temp file
                    Log.w (TAG, "error writing " + imgfile.getPath () + ": " + ioe.getMessage ());
                    Lib.Ignored (imgfile.delete ());
                }

                // finished one way or the other
                ms = SystemClock.uptimeMillis () - ms;
                Log.i (TAG, "finished " + imgfile.getPath () + ", " + ms + " ms");
            }
        }
    }

    /**
     * Display the IAP plate image file.
     */
    public class IAPSynthPlateImage extends IAPPlateImage implements DisplayableChart.Invalidatable {
        public final static String prefix = "IAP-Synth ILS/DME ";

        // pixel size of real plate files
        private final static int platewidth  = 1614;
        private final static int plateheight = 2475;

        // 300dpi on plate
        // same scale as sectional chart
        // 300dp(500,000in) in real world
        // = 42.33333 metres/pixel
        private final static double mperbmpix = 500000.0 / 300.0 / 12.0 / Lib.FtPerM;

        private class TopoRect {
            public double bmpbot, bmpleft, bmprite, bmptop;
            public int color;
        }

        private boolean  firstDraw = true;
        private double   centerlat;     // lat,lon at center of needle
        private double   centerlon;     // = initially at center of canvas
        private double   rwymcbin;      // runway magnetic course (-180 to +180)
        private double   rwynm, rwytc;  // runway length and true course
        private double[] needlebmpxys = new double[26];
        private float[]  needlecanxys = new float[26];
        private int infotextswarn;
        private LinkedList<TopoRect> topoRects = new LinkedList<> ();
        private Paint    rwclPaint;
        private Rect     textbounds = new Rect ();
        private String   gsaltstr;      // glideslope intercept altitude string
        private String   gsintdmestr;   // glideslope intercept DME string
        private String   ptlinstr;      // PT inbound heading string
        private String   ptrinstr;
        private String   ptloutstr;     // PT outbound heading string
        private String   ptroutstr;
        private String   rwymcstr;      // runway mag course string
        private String[] infotexts;     // text lines at top center of canvas
        private Waypoint.Localizer synthloc;
        private Waypoint.Runway runway;

        @SuppressWarnings("PointlessArithmeticExpression")
        public IAPSynthPlateImage ()
        {
            runway = airport.GetRunways ().get (plateid.substring (prefix.length ()));
            synthloc = runway.GetSynthLoc ();

            rwytc = Lib.LatLonTC (runway.lat, runway.lon, runway.endLat, runway.endLon);
            rwynm = Lib.LatLonDist (runway.lat, runway.lon, runway.endLat, runway.endLon);

            rwymcbin = rwytc + Lib.MagVariation (runway.lat, runway.lon, runway.elev / Lib.FtPerM);
            rwymcstr = Lib.Hdg2Str (rwymcbin);
            if (rwymcbin < 0.0) {
                ptlinstr  = Lib.Hdg2Str (rwymcbin + 135.0) + '\u2192';  // right arrow
                ptloutstr = '\u2190' + Lib.Hdg2Str (rwymcbin -  45.0);  // left arrow
                ptrinstr  = Lib.Hdg2Str (rwymcbin - 135.0) + '\u2192';  // right arrow
                ptroutstr = '\u2190' + Lib.Hdg2Str (rwymcbin +  45.0);  // left arrow
            } else {
                ptlinstr  = Lib.Hdg2Str (rwymcbin -  45.0) + '\u2192';  // right arrow
                ptloutstr = '\u2190' + Lib.Hdg2Str (rwymcbin + 135.0);  // left arrow
                ptrinstr  = Lib.Hdg2Str (rwymcbin +  45.0) + '\u2192';  // right arrow
                ptroutstr = '\u2190' + Lib.Hdg2Str (rwymcbin - 135.0);  // left arrow
            }

            rwclPaint = new Paint ();
            rwclPaint.setColor (Color.WHITE);
            rwclPaint.setStyle (Paint.Style.FILL_AND_STROKE);

            /*
             * Set up lat/lon <-> bitmap pixel mapping.
             *   actual plates are 5.38 x 8.25 inches
             *                     1614 x 2475 pixels
             *                     same scale as section charts (500,000 : 1)
             *                    36.86 x 56.57 nm
             *   At 300dpi, actual plates are 1614 x 2475 pix = 5.38 x 8.25
             *   And actual plates are same scale as sectional charts
             * Position such that center of needle is in center of plate.
             */
            double loclat = synthloc.lat;
            double loclon = synthloc.lon;
            centerlat = Lib.LatHdgDist2Lat (loclat, rwytc + 180.0, 7.5);
            centerlon = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 180.0, 7.5);

            /*
             * Glideslope calculations.
             */
            // glideslope intercept altitude (feet), approx 2000ft AGL
            int gsaltbin = ((int) Math.round (runway.elev / 100.0)) * 100 + 2000;
            gsaltstr = Integer.toString (gsaltbin) + '\'';

            /*
             * Compute needle points.
             *  15nm long
             *  3deg wide each side
             */
            double[] needlells = new double[26];

            // tail of needle on centerline
            needlells[ 6] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 180.0, rwynm + 15.0);
            needlells[ 7] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 180.0, rwynm + 15.0);
            // tail of needle left of centerline
            needlells[ 4] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 183.0, rwynm + 15.5);
            needlells[ 5] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 183.0, rwynm + 15.5);
            // tail of needle right of centerline
            needlells[ 8] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 177.0, rwynm + 15.5);
            needlells[ 9] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 177.0, rwynm + 15.5);

            // tip of needle
            needlells[ 0] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 180.0, rwynm + 0.2);
            needlells[ 1] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 180.0, rwynm + 0.2);
            // near tip left of centerline
            needlells[ 2] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 183.0, rwynm + 0.5);
            needlells[ 3] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 183.0, rwynm + 0.5);
            // near tip right of centerline
            needlells[10] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 177.0, rwynm + 0.5);
            needlells[11] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 177.0, rwynm + 0.5);

            // most way toward tail on centerline
            needlells[12] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 180.0, rwynm + 10.0);
            needlells[13] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 180.0, rwynm + 10.0);

            // start of PT barb near numbers
            needlells[18] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 180.0, rwynm + 12.0);
            needlells[19] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 180.0, rwynm + 12.0);

            // elbow of PT barb near tail
            needlells[20] = Lib.LatHdgDist2Lat    (loclat,         rwytc + 180.0, rwynm + 14.0);
            needlells[21] = Lib.LatLonHdgDist2Lon (loclat, loclon, rwytc + 180.0, rwynm + 14.0);

            // end of left 45deg leg of PT barb
            needlells[22] = Lib.LatHdgDist2Lat    (needlells[20],                rwytc + 135.0, 2.0);
            needlells[23] = Lib.LatLonHdgDist2Lon (needlells[20], needlells[21], rwytc + 135.0, 2.0);

            // end of right 45deg leg of PT barb
            needlells[24] = Lib.LatHdgDist2Lat    (needlells[20],                rwytc - 135.0, 2.0);
            needlells[25] = Lib.LatLonHdgDist2Lon (needlells[20], needlells[21], rwytc - 135.0, 2.0);

            for (int i = 0; i < 26; i += 2) {
                needlebmpxys[i+0] = LatLon2BitmapX (needlells[i+0], needlells[i+1]);
                needlebmpxys[i+1] = LatLon2BitmapY (needlells[i+0], needlells[i+1]);
            }

            double needletailwidth = Math.hypot (needlebmpxys[8] - needlebmpxys[4],
                    needlebmpxys[9] - needlebmpxys[5]);

            // glideslope intercept position
            // glideslope antenna is located 1000' down from near end of runway
            double gslat = synthloc.gs_lat;
            double gslon = synthloc.gs_lon;
            double gsintaltagl = gsaltbin - runway.elev;
            double gsintdistft = gsintaltagl / Math.tan (Math.toRadians (synthloc.gs_tilt));
            double gsintdistnm = gsintdistft / Lib.FtPerNM;
            double gsintlat = Lib.LatHdgDist2Lat (gslat, rwytc + 180.0, gsintdistnm);
            double gsintlon = Lib.LatLonHdgDist2Lon (gslat, gslon, rwytc + 180.0, gsintdistnm);
            double gsintbmpx = LatLon2BitmapX (gsintlat, gsintlon);
            double gsintbmpy = LatLon2BitmapY (gsintlat, gsintlon);
            needlebmpxys[14] = gsintbmpx - needletailwidth * Math.cos (Math.toRadians (rwytc));
            needlebmpxys[15] = gsintbmpy - needletailwidth * Math.sin (Math.toRadians (rwytc));
            needlebmpxys[16] = gsintbmpx + needletailwidth * Math.cos (Math.toRadians (rwytc));
            needlebmpxys[17] = gsintbmpy + needletailwidth * Math.sin (Math.toRadians (rwytc));

            // DME antenna is located at near end of runway
            double dmelat = synthloc.GetDMELat ();
            double dmelon = synthloc.GetDMELon ();
            double gsintdmenm = Lib.LatLonDist (gsintlat, gsintlon, dmelat, dmelon);
            gsintdmestr = " " + Lib.DoubleNTZ (gsintdmenm) + " DME ";

            /*
             * Informational text lines.
             */
            infotexts = new String[] {
                    plateid,
                    airport.name + " (" + airport.faaident + ')',
                    "Runway length: " + Math.round (rwynm * Lib.FtPerNM) + '\'',
                    "Threshold elev: " + Math.round (runway.elev) + '\'',
                    "Decision height: " + Math.round (runway.elev + 250.0) + '\'',
                    "Although relative topography shown...",
                    "...NO OBSTRUCTION CLEARANCE PROVIDED",
                    "USE ONLY IN VFR CONDITIONS"
            };
            infotextswarn = infotexts.length - 3;

            /*
             * Set up topo rectangles +/-5nm from needle course line.
             */
            double widnm  = 5.0;
            int minlatmin = (int) Math.round (Math.min (runway.endLat, needlells[6]) * 60.0 - widnm);
            int maxlatmin = (int) Math.round (Math.max (runway.endLat, needlells[6]) * 60.0 + widnm);
            double avglatcos = Math.cos (Math.toRadians (minlatmin + maxlatmin) / 120.0);
            int minlonmin = (int) Math.round (Lib.Westmost (runway.endLon, needlells[7]) * 60.0 - widnm / avglatcos);
            int maxlonmin = (int) Math.round (Lib.Eastmost (runway.endLon, needlells[7]) * 60.0 + widnm / avglatcos);
            if (maxlonmin < minlonmin) maxlonmin += 360 * 60;

            // loop through all possible lat/lon one minute at a time
            for (int latmin = minlatmin; latmin <= maxlatmin; latmin ++) {
                double latdeg = latmin / 60.0;
                for (int lonmin = minlonmin; lonmin <= maxlonmin; lonmin ++) {
                    double londeg = lonmin / 60.0;

                    // skip if not within widnm of the runway course line
                    double ocd = Lib.GCOffCourseDist (runway.endLat, runway.endLon, needlells[6], needlells[7], latdeg, londeg);
                    if (Math.abs (ocd) > widnm) continue;

                    // skip if more than needle length from runway and more than widnm from needle tail
                    // also skip if vice versa
                    double fromrunwy = Lib.LatLonDist (latdeg, londeg, runway.endLat, runway.endLon);
                    double fromntail = Lib.LatLonDist (latdeg, londeg, needlells[6], needlells[7]);
                    if ((fromrunwy > 15.0) && (fromntail > widnm)) continue;
                    if ((fromrunwy > widnm) && (fromntail > 15.0)) continue;

                    // get ground elevation at that lat/lon position
                    // skip if unknown (leaving background black)
                    short elevMetres = Topography.getElevMetres (latdeg, londeg);
                    if (elevMetres == Topography.INVALID_ELEV) continue;

                    // compute gap between terrain and altitude we should be flying at for this position
                    double elevft  = elevMetres * Lib.FtPerM;  // terrain elevation in feet
                    double flydist = Lib.LatLonDist (runway.lat, runway.lon, latdeg, londeg);
                    if (flydist > 5.0) flydist = 5.0;   // how far away from runway we are
                    double flyarw  = flydist * 400.0;   // how high above runway we should be flying
                    double gapft   = runway.elev + flyarw - elevft;  // actual gap between flying and ground

                    // if flying through dirt, we want full RED color
                    // if gap is at least half distance to runway height, full GREEN color
                    if (gapft < 0.0) gapft = 0.0;       // flying through dirt -- fully RED
                    if (gapft > flyarw / 2.0) {         // gap more than half way above runway
                        gapft = flyarw / 2.0;           // -- fully GREEN
                    }
                    double hue = gapft / (flyarw / 2.0) * Math.PI / 2.0;  // 0(red)..pi/2(green)

                    // make a new rectangle entry
                    TopoRect tr = new TopoRect ();
                    tr.color = Color.rgb ((int) Math.round (Math.cos (hue) * 255),
                            (int) Math.round (Math.sin (hue) * 255), 0);

                    tr.bmpleft = LatLon2BitmapX (latdeg - 1.0/120.0, londeg - 1.0/120.0);
                    tr.bmpbot  = LatLon2BitmapY (latdeg - 1.0/120.0, londeg - 1.0/120.0);
                    tr.bmprite = LatLon2BitmapX (latdeg + 1.0/120.0, londeg + 1.0/120.0);
                    tr.bmptop  = LatLon2BitmapY (latdeg + 1.0/120.0, londeg + 1.0/120.0);

                    topoRects.addLast (tr);
                }
            }

            /*
             * Init CIFP class.
             * Constructor uses lat/lon <=> bitmap pixel mapping.
             */
            ////TODO plateCIFP = new PlateCIFP (wairToNow, this, airport, plateid);
        }

        /**
         * We always reference the synthetic ILS/DME antennae.
         */
        @Override  // IAPPlateImage
        public Waypoint GetRefNavWaypt ()
        {
            return runway.GetSynthLoc ();
        }

        /**
         * Close any open bitmaps so we don't take up too much memory.
         */
        public void CloseBitmaps ()
        {
            // re-center when opened again
            // might be switching between FAAWP1,2 and
            // right side of VirtNav1,2 pages
            firstDraw = true;
        }

        /**
         * Draw the plate image and any other marks on it we want.
         */
        @SuppressWarnings("PointlessArithmeticExpression")
        @Override  // View
        public void onDraw (Canvas canvas)
        {
            if (firstDraw) {
                SetBitmapMapping (platewidth, plateheight);
                firstDraw = false;
            }

            /* back display with auto sectional
               - doesn't translate/zoom
               - too messy to read
            double bmpleft = 0;
            double bmprite = platewidth;
            double bmptop  = 0;
            double bmpbot  = plateheight;
            double tlLat = BitmapXY2Lat (bmpleft, bmptop);
            double tlLon = BitmapXY2Lon (bmpleft, bmptop);
            double trLat = BitmapXY2Lat (bmprite, bmptop);
            double trLon = BitmapXY2Lon (bmprite, bmptop);
            double blLat = BitmapXY2Lat (bmpleft, bmpbot);
            double blLon = BitmapXY2Lon (bmpleft, bmpbot);
            double brLat = BitmapXY2Lat (bmprite, bmpbot);
            double brLon = BitmapXY2Lon (bmprite, bmpbot);
            pmap.setup (platewidth, plateheight,
                    tlLat, tlLon, trLat, trLon,
                    blLat, blLon, brLat, brLon);
            wairToNow.chartView.autoAirChartSEC.DrawOnCanvas (pmap, canvas, this, 0.0);
            */

            /*
             * Draw topo rectangles +/-3nm from courseline.
             */
            for (TopoRect tr : topoRects) {
                rwclPaint.setColor (tr.color);
                float canleft = (float) BitmapX2CanvasX (tr.bmpleft);
                float canrite = (float) BitmapX2CanvasX (tr.bmprite);
                float cantop  = (float) BitmapY2CanvasY (tr.bmptop);
                float canbot  = (float) BitmapY2CanvasY (tr.bmpbot);
                canvas.drawRect (canleft, cantop, canrite, canbot, rwclPaint);
            }

            /*
             * Draw runways.
             */
            rwclPaint.setColor (Color.WHITE);
            for (Waypoint.Runway rwy : airport.GetRunwayPairs ()) {
                double begBmpX = LatLon2BitmapX (rwy.lat, rwy.lon);
                double begBmpY = LatLon2BitmapY (rwy.lat, rwy.lon);
                double endBmpX = LatLon2BitmapX (rwy.endLat, rwy.endLon);
                double endBmpY = LatLon2BitmapY (rwy.endLat, rwy.endLon);
                double begCanX = BitmapX2CanvasX (begBmpX);
                double begCanY = BitmapY2CanvasY (begBmpY);
                double endCanX = BitmapX2CanvasX (endBmpX);
                double endCanY = BitmapY2CanvasY (endBmpY);
                float lengthft = rwy.getLengthFt ();
                float widthft  = rwy.getWidthFt ();
                float lengthcp = (float) Math.hypot (endCanX - begCanX, endCanY - begCanY);
                float widthcp  = lengthcp * widthft / lengthft;
                if (widthcp < wairToNow.thinLine) widthcp = wairToNow.thinLine;
                rwclPaint.setStrokeWidth (widthcp / 2.0F);
                canvas.drawLine ((float)begCanX, (float)begCanY, (float)endCanX, (float)endCanY, rwclPaint);
            }

            /*
             * Draw localizer needle.
             */
            // needle outline
            for (int i = 0; i < 26; i += 2) {
                needlecanxys[i+0] = (float) BitmapX2CanvasX (needlebmpxys[i+0]);
                needlecanxys[i+1] = (float) BitmapY2CanvasY (needlebmpxys[i+1]);
            }
            rwclPaint.setStrokeWidth (wairToNow.thinLine / 4.0F);
            for (int i = 0; i < 12; i += 2) {
                int j = i + 2;
                if (j >= 12) j -= 12;
                canvas.drawLine (needlecanxys[i+0], needlecanxys[i+1],
                        needlecanxys[j+0], needlecanxys[j+1],
                        rwclPaint);
            }

            // line down center
            rwclPaint.setStrokeWidth (wairToNow.thinLine / 2.0F);
            canvas.drawLine (needlecanxys[ 0], needlecanxys[ 1],
                    needlecanxys[12], needlecanxys[13], rwclPaint);

            // PT barbs
            canvas.drawLine (needlecanxys[18], needlecanxys[19],
                    needlecanxys[20], needlecanxys[21], rwclPaint);
            canvas.drawLine (needlecanxys[20], needlecanxys[21],
                    needlecanxys[22], needlecanxys[23], rwclPaint);
            canvas.drawLine (needlecanxys[20], needlecanxys[21],
                    needlecanxys[24], needlecanxys[25], rwclPaint);

            // mag course text
            float textsize = (float) Math.hypot (needlecanxys[6] - needlecanxys[4], needlecanxys[7] - needlecanxys[5]);
            rwclPaint.setStrokeWidth (wairToNow.thinLine / 6.0F);
            rwclPaint.setTextSize (textsize);
            rwclPaint.getTextBounds (rwymcstr, 0, rwymcstr.length (), textbounds);
            rwclPaint.setTextAlign ((rwymcbin < 0.0) ? Paint.Align.LEFT : Paint.Align.RIGHT);
            float textheight = textbounds.height ();
            float textrotate = (float) ((rwymcbin < 0.0) ? (rwytc - 270.0) : (rwytc - 90.0));
            canvas.save ();
            canvas.rotate (textrotate, needlecanxys[12], needlecanxys[13]);
            canvas.translate (0.0F, textheight * 0.5F);
            canvas.drawText (rwymcstr, needlecanxys[12], needlecanxys[13], rwclPaint);

            // gs intercept altitude string above mag course text
            rwclPaint.setTextSize (textsize * 0.75F);
            canvas.translate (0.0F, textheight * -1.5F);
            canvas.drawText (gsaltstr, needlecanxys[12], needlecanxys[13], rwclPaint);
            canvas.restore ();

            // glideslope intercept point crosswise line
            rwclPaint.setStrokeWidth (wairToNow.thinLine / 4.0F);
            canvas.drawLine (needlecanxys[14], needlecanxys[15],
                    needlecanxys[16], needlecanxys[17], rwclPaint);
            rwclPaint.setStrokeWidth (wairToNow.thinLine / 6.0F);
            int k = (rwymcbin < 0.0) ? 14 : 16;
            canvas.save ();
            canvas.rotate (textrotate, needlecanxys[k+0], needlecanxys[k+1]);
            canvas.drawText (gsintdmestr, needlecanxys[k+0], needlecanxys[k+1], rwclPaint);
            canvas.restore ();

            // PT in/out heading strings
            canvas.save ();
            canvas.rotate (textrotate - 45.0F, needlecanxys[22], needlecanxys[23]);
            canvas.drawText (ptlinstr,  needlecanxys[22], needlecanxys[23], rwclPaint);
            canvas.translate (0.0F, textheight);
            canvas.drawText (ptloutstr, needlecanxys[22], needlecanxys[23], rwclPaint);
            canvas.restore ();
            canvas.save ();
            canvas.rotate (textrotate + 45.0F, needlecanxys[24], needlecanxys[25]);
            canvas.drawText (ptroutstr, needlecanxys[24], needlecanxys[25], rwclPaint);
            canvas.translate (0.0F, textheight);
            canvas.drawText (ptrinstr,  needlecanxys[24], needlecanxys[25], rwclPaint);
            canvas.restore ();

            /*
             * Draw informational text.
             */
            rwclPaint.setTextSize (wairToNow.textSize);
            rwclPaint.setTextAlign (Paint.Align.CENTER);
            float infotextx = getWidth () / 2.0F;
            float infotexty = 10.0F;
            int ninfotexts  = (SystemClock.uptimeMillis () - plateLoadedUptime > 10000) ?
                    infotextswarn : infotexts.length;
            for (int i = 0; i < ninfotexts; i ++) {
                rwclPaint.setColor ((i < infotextswarn) ? Color.WHITE : Color.RED);
                String infotext = infotexts[i];
                infotexty += wairToNow.textSize;
                canvas.drawText (infotext, infotextx, infotexty, rwclPaint);
            }

            /*
             * Draw a bunch of stuff on the plate.
             */
            DrawCommon (canvas);
        }

        /**
         * Convert lat,lon to bitmap X,Y.
         * Use LCC conversion if FAA-supplied georef data given.
         */
        @Override  // GRPPlateImage
        protected boolean LatLon2BitmapOK ()
        {
            return true;
        }

        @Override  // GRPPlateImage
        public double LatLon2BitmapX (double lat, double lon)
        {
            double easting = (lon - centerlon) * (Lib.NMPerDeg * Lib.MPerNM) *
                    Math.cos (Math.toRadians (lat));
            return platewidth / 2.0 + easting / mperbmpix;
        }

        @Override  // GRPPlateImage
        public double LatLon2BitmapY (double lat, double lon)
        {
            double northing = (lat - centerlat) * (Lib.NMPerDeg * Lib.MPerNM);
            return plateheight / 2.0 - northing / mperbmpix;
        }

        @Override  // GRPPlateImage
        public double BitmapXY2Lat (double bmx, double bmy)
        {
            // bmy = plateheight / 2.0 - northing / mperbmpix
            // northing / mperbmpix = plateheight / 2.0 - bmy
            double northing = (plateheight / 2.0 - bmy) * mperbmpix;

            // northing = (lat - centerlat) * (Lib.NMPerDeg * Lib.MPerNM)
            // northing / (Lib.NMPerDeg * Lib.MPerNM) = (lat - centerlat)
            return northing / (Lib.NMPerDeg * Lib.MPerNM) + centerlat;
        }

        @Override  // GRPPlateImage
        public double BitmapXY2Lon (double bmx, double bmy)
        {
            double lat = BitmapXY2Lat (bmx, bmy);

            // bmx = platewidth / 2.0 + easting / mperbmpix
            // bmx - platewidth / 2.0 = easting / mperbmpix
            double easting = (bmx - platewidth / 2.0) * mperbmpix;

            // easting = (lon - centerlon) * (Lib.NMPerDeg * Lib.MPerNM) * Math.cos (Math.toRadians (lat))
            // easting / (Lib.NMPerDeg * Lib.MPerNM) / Math.cos (Math.toRadians (lat)) = (lon - centerlon)
            return easting / (Lib.NMPerDeg * Lib.MPerNM) / Math.cos (Math.toRadians (lat)) + centerlon;
        }

        /**
         * This runs in the ShadeCirclingAreaThread to scan bitmap for the inverse [C] if necessary
         * then shade in the circling area on the bitmap.
         */
        @Override  // IAPPlateImage
        public void ShadeCirclingAreaWork ()
        { }
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
            if (fn.startsWith ("gif_150/")) fn = fn.substring (8);
            filename = fn;

            /*
             * We are given the base name ending in .gif.
             * But the files are multiple pages ending in .gif.p<pageno>.
             * So count how many pages we actually have for this plate.
             */
            String fndotp = fn + ".p";
            int fndotplen = fndotp.length ();
            try {
                ZipFile zf = wairToNow.maintView.getStateZipFile (airport.state);
                for (Enumeration<? extends ZipEntry> it = zf.entries (); it.hasMoreElements ();) {
                    ZipEntry ze = it.nextElement ();
                    String en = ze.getName ();
                    if (en.startsWith (fndotp)) {
                        int p = Integer.parseInt (en.substring (fndotplen));
                        if (numPages < p) numPages = p;
                    }
                }
            } catch (IOException ioe) {
                Log.w (TAG, "error scanning state " + airport.state + " zip file", ioe);
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
                            bm = readGif (fn);
                        } catch (Throwable e1) {
                            for (int i = 0; i < numPages; i ++) {
                                bm = bitmaps[i];
                                if (bm != null) bm.recycle ();
                                bitmaps[i] = null;
                            }
                            try {
                                bm = readGif (fn);
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
