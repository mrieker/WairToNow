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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Contains one aeronautical chart, whether or not it is downloaded.
 */
public abstract class AirChart implements DisplayableChart {
    private final static String TAG = "WairToNow";
    private final static boolean showtilenames = false;

    private final static AirTileLoader airTileLoader = new AirTileLoader ();

    private static ThreadLocal<float[]> flt4PerThread = new ThreadLocal<float[]> () {
        @Override protected float[] initialValue () { return new float[4]; }
    };
    private static ThreadLocal<LatLon> llPerThread = new ThreadLocal<LatLon> () {
        @Override protected LatLon initialValue ()
        {
            return new LatLon ();
        }
    };
    private static ThreadLocal<Point> ptPerThread = new ThreadLocal<Point> () {
        @Override protected Point initialValue ()
        {
            return new Point ();
        }
    };

    private AirTile[] tilez;    // list of tiles that make up the chart at the given scaling and with/without legends
    private boolean legends;    // whether or not tilez contains tiles with legend pixels
    private float[] drawOnCanvasPoints = new float[16];
    private int hstep;          // heightwise pixel step for each tile
    private int ntilez;         // number of entries in tilez that are valid
    private int scaling;        // 1=full scale, 2=half sized, 3=third sized, etc
    private int wstep;          // widthwise pixel step for each tile
    private LinkedList<AirTile>  loadedBitmaps = new LinkedList<> ();
    private long viewDrawCycle = 0;  // incremented each drawing cycle
    private MaintView maintView;
    private Matrix drawOnCanvasChartMat   = new Matrix ();
    private Matrix drawOnCanvasTileMat    = new Matrix ();
    private Matrix undrawOnCanvasChartMat = new Matrix ();
    private Paint stnbgpaint;   // "showtilenames" background paint
    private Paint stntxpaint;   // "showtilenames" foreground paint
    private Point drawOnCanvasPoint = new Point ();
    private Rect canvasBounds       = new Rect (0, 0, 0, 0);
    public  String spacenamewr; // eg, "New York SEC 92"
    public  String spacenamenr; // eg, "New York SEC"
    private String revision;    // eg, "92"
    private WairToNow wairToNow;

    private float chartedEastLon;  // *NOT NORMALIZED* -- always >= chartedWestLon
    private float chartedWestLon;  // normalized
    private float chartedSouthLat, chartedNorthLat;
    public  int autoOrder;
    public  int enddate;
    private int chartedBotPix, chartedLeftPix;
    private int chartedRitePix, chartedTopPix;
    public  int chartheight, chartwidth;  // pixel width & height of this chart including legend areas

    private Point[] outline;

    // methods a projection-specific implementation must provide
    protected abstract String ParseParams (String csvLine);
    public abstract boolean LatLon2ChartPixelExact (float lat, float lon, @NonNull Point p);
    protected abstract void ChartPixel2LatLonExact (float x, float y, @NonNull LatLon ll);

    // instantiate based on what projection is required for the chart
    public static AirChart Factory (MaintView maintView, String csvLine)
    {
        AirChart instance;
        if (csvLine.startsWith (PSP.pfx)) instance = new PSP ();
        else if (csvLine.startsWith (Box.pfx)) instance = new Box ();
        else instance = new Lcc ();
        instance.Construct (maintView, csvLine);
        return instance;
    }

    private void Construct (MaintView maintView, String csvLine)
    {
        this.maintView = maintView;
        wairToNow = maintView.wairToNow;

        ParseChartLimitsLine (csvLine);

        // set up paints used to show tile names at top center of each tile
        if (showtilenames) {
            stnbgpaint = new Paint ();
            stnbgpaint.setColor (Color.WHITE);
            stnbgpaint.setStyle (Paint.Style.STROKE);
            stnbgpaint.setStrokeWidth (30);
            stnbgpaint.setTextSize (wairToNow.textSize / 2);
            stnbgpaint.setTextAlign (Paint.Align.CENTER);

            stntxpaint = new Paint ();
            stntxpaint.setColor (Color.RED);
            stntxpaint.setStyle (Paint.Style.FILL);
            stntxpaint.setStrokeWidth (2);
            stntxpaint.setTextSize (wairToNow.textSize / 2);
            stntxpaint.setTextAlign (Paint.Align.CENTER);
        }
    }

    @Override  // DisplayableChart
    public String GetSpacenameSansRev ()
    {
        return spacenamenr;
    }

    @Override  // DisplayableChart
    public boolean IsDownloaded ()
    {
        return enddate > 0;
    }

    /**
     * Get entry for chart selection menu.
     */
    @Override  // DisplayableChart
    public View GetMenuSelector (ChartView chartView)
    {
        return GetMenuSelector (chartView.pmap, chartView.arrowLat, chartView.arrowLon, chartView.metrics);
    }

    /**
     * User just clicked this chart in the chart selection menu.
     */
    @Override  // DisplayableChart
    public void UserSelected ()
    { }

    /**
     * Get entry for chart selection menu.
     *
     * @param pmap = map lat/lon to canvas pixels so we can draw canvas location
     */
    private View GetMenuSelector (PixelMapper pmap, float arrowLat, float arrowLon, DisplayMetrics metrics)
    {
        float arrowradpix = Mathf.sqrt (metrics.xdpi * metrics.ydpi) / 32.0F;
        float canvasradpix = arrowradpix * 1.5F;

        // ratio of chart pixels / icon pixels
        // - size to make chart appear same size as text
        gms_ratio = (float) chartheight / (wairToNow.textSize * 1.5F);

        // oversize the bitmap in case canvas maps off the edge of the chart
        // so the canvas doesn't get completely clipped off the icon
        int bitmapheight = Math.round (wairToNow.textSize * 2.0F);
        int bitmapwidth  = bitmapheight * chartwidth / chartheight;
        Bitmap bm = Bitmap.createBitmap (bitmapwidth, bitmapheight, Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas (bm);

        gms_bitmapcenterx = bitmapwidth / 2;
        gms_bitmapcentery = bitmapheight / 2;

        gms_chartcenterx = chartwidth / 2;
        gms_chartcentery = chartheight / 2;

        Point pt    = new Point ();
        Paint paint = new Paint ();
        paint.setStyle (Paint.Style.FILL_AND_STROKE);

        // if we have an icon image, use it to fill in background
        // otherwise fill background with white
        Rect bmrect  = new Rect ();
        pt.x = 0;
        pt.y = 0;
        ChartPixel2GMSPixel (pt);
        bmrect.left   = pt.x;
        bmrect.top    = pt.y;
        pt.x = chartwidth;
        pt.y = chartheight;
        ChartPixel2GMSPixel (pt);
        bmrect.right  = pt.x;
        bmrect.bottom = pt.y;
        String undername = spacenamenr.replace (' ', '_') + '_' + revision;
        String iconname = WairToNow.dbdir + "/charts/" + undername + "/icon.png";
        if (new File (iconname).exists ()) {
            Bitmap iconbm = BitmapFactory.decodeFile (iconname);
            can.drawBitmap (iconbm, null, bmrect, null);
            iconbm.recycle ();
        } else {
            paint.setColor (Color.WHITE);
            can.drawRect (bmrect, paint);
        }

        // draw cyan rectangle indicating where the display is
        // also draw a filled circle in case rectangle is too small
        Path rectpath = new Path ();
        LatLon2GMSPixel (pmap.centerLat, pmap.centerLon, pt);
        rectpath.addCircle (pt.x, pt.y, canvasradpix, Path.Direction.CCW);
        paint.setColor (Color.CYAN);
        can.drawPath (rectpath, paint);

        rectpath.reset ();
        LatLon2GMSPixel (pmap.lastTlLat, pmap.lastTlLon, pt);
        rectpath.moveTo (pt.x, pt.y);
        LatLon2GMSPixel (pmap.lastTrLat, pmap.lastTrLon, pt);
        rectpath.lineTo (pt.x, pt.y);
        LatLon2GMSPixel (pmap.lastBrLat, pmap.lastBrLon, pt);
        rectpath.lineTo (pt.x, pt.y);
        LatLon2GMSPixel (pmap.lastBlLat, pmap.lastBlLon, pt);
        rectpath.lineTo (pt.x, pt.y);
        LatLon2GMSPixel (pmap.centerLat, pmap.centerLon, pt);
        rectpath.lineTo (pt.x, pt.y);
        can.drawPath (rectpath, paint);

        // draw a red dot where the current GPS location is
        LatLon2GMSPixel (arrowLat, arrowLon, pt);
        paint.setColor (Color.RED);
        can.drawCircle (pt.x, pt.y, arrowradpix, paint);

        // make text part of entry, contains the spacenamenr and a few spaces for spacing
        TextView tv = new TextView (wairToNow);
        tv.setText (spacenamenr + "   ");
        tv.setTextColor (MaintView.DownloadLinkColor (enddate));
        tv.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize * 1.5F);

        LinearLayout.LayoutParams tvllp = new LinearLayout.LayoutParams (ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvllp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        tv.setLayoutParams (tvllp);

        // put the bitmap in an image view
        ImageView iv = new ImageView (wairToNow);
        iv.setImageBitmap (bm);

        LinearLayout.LayoutParams ivllp = new LinearLayout.LayoutParams (ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ivllp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        iv.setLayoutParams (ivllp);

        // put the two of them side-by-side for the menu
        LinearLayout llv = new LinearLayout (wairToNow);
        llv.setOrientation (LinearLayout.HORIZONTAL);
        llv.addView (tv);
        llv.addView (iv);

        return llv;
    }

    private float gms_ratio;
    private int gms_bitmapcenterx, gms_bitmapcentery;
    private int gms_chartcenterx, gms_chartcentery;

    private void LatLon2GMSPixel (float lat, float lon, Point pt)
    {
        LatLon2ChartPixelExact (lat, lon, pt);
        ChartPixel2GMSPixel (pt);
    }
    private void ChartPixel2GMSPixel (Point pt)
    {
        pt.x = Math.round ((pt.x - gms_chartcenterx) / gms_ratio) + gms_bitmapcenterx;
        pt.y = Math.round ((pt.y - gms_chartcentery) / gms_ratio) + gms_bitmapcentery;
    }

    /**
     * Draw chart on canvas possibly scaled and/or rotated.
     * @param canvas = canvas to draw on
     */
    @Override  // DisplayableChart
    public void DrawOnCanvas (ChartView chartView, Canvas canvas)
    {
        DrawOnCanvas (chartView, canvas, true);
    }
    public void DrawOnCanvas (ChartView chartView, Canvas canvas, boolean includeLegends)
    {
        viewDrawCycle ++;

        /*
         * Create transformation matrix that scales and rotates the unscaled chart
         * as a whole such that the given points match up.
         *
         *    [matrix] * [some_xy_in_chart] => [some_xy_in_canvas]
         */
        float[] points = drawOnCanvasPoints;
        Point point = drawOnCanvasPoint;

        LatLon2ChartPixelExact (chartView.tlLat, chartView.tlLon, point);
        points[ 0] = point.x;  points[ 8] = 0;
        points[ 1] = point.y;  points[9] = 0;

        LatLon2ChartPixelExact (chartView.trLat, chartView.trLon, point);
        points[ 2] = point.x;  points[10] = chartView.canvasWidth;
        points[ 3] = point.y;  points[11] = 0;

        LatLon2ChartPixelExact (chartView.blLat, chartView.blLon, point);
        points[ 4] = point.x;  points[12] = 0;
        points[ 5] = point.y;  points[13] = chartView.canvasHeight;

        LatLon2ChartPixelExact (chartView.brLat, chartView.brLon, point);
        points[ 6] = point.x;  points[14] = chartView.canvasWidth;
        points[ 7] = point.y;  points[15] = chartView.canvasHeight;

        if (!drawOnCanvasChartMat.setPolyToPoly (points, 0, points, 8, 4)) {
            Log.e (TAG, "can't position chart");
            return;
        }
        if (!drawOnCanvasChartMat.invert (undrawOnCanvasChartMat)) {
            Log.e (TAG, "can't unposition chart");
            return;
        }

        /*
         * Get size of the canvas that we are drawing on.
         * Don't use clip bounds because we want all tiles for the canvas marked
         * as referenced so we don't keep loading and unloading the bitmaps.
         */
        canvasBounds.right  = chartView.canvasWidth;
        canvasBounds.bottom = chartView.canvasHeight;

        /*
         * When zoomed out, scaling < 1.0 and we want to undersample the bitmaps by that
         * much.  Eg, if scaling = 0.5, we want to sample half**2 the bitmap pixels and
         * scale the bitmap back up to twice its size.  But when zoomed in, ie, scaling
         * > 1.0, we don't want to oversample the bitmaps, just sample them normally, so
         * we don't run out of memory doing so.  Also, using a Mathf.ceil() on it seems to
         * use up a lot less memory than using arbitrary real numbers.
         */
        float ts = Mathf.hypot (points[6] - points[0], points[7] - points[1])
                           / Mathf.hypot (chartView.canvasWidth, chartView.canvasHeight);
        int tileScaling = (int) Mathf.ceil (ts);

        /*
         * Scan through each tile that composes the chart.
         */
        GetAirTiles (chartView, tileScaling, includeLegends);
        Matrix tileMat = drawOnCanvasTileMat;
        for (int i = 0; i < ntilez; i ++) {
            AirTile tile = tilez[i];

            /*
             * Set up a matrix that maps an unscaled tile onto the canvas.
             * If tileScaling=1, the unscaled tile is 256x256 plus overlap
             * If tileScaling=2, the unscaled tile is 512x512 plus overlap
             * If tileScaling=3, the unscaled tile is 768X768 plus overlap
             * ...etc
             */
            tileMat.setTranslate (tile.leftPixel * tileScaling, tile.topPixel * tileScaling);
                                                        // translate tile into position within whole unscaled chart
            tileMat.postConcat (drawOnCanvasChartMat);  // position tile onto canvas

            /*
             * Compute the canvas points corresponding to the four corners of an unscaled tile.
             */
            int tws = tileScaling * tile.width;
            int ths = tileScaling * tile.height;
            points[0] =   0; points[1] =   0;
            points[2] = tws; points[3] =   0;
            points[4] =   0; points[5] = ths;
            points[6] = tws; points[7] = ths;
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
                    Bitmap bm = tile.GetScaledBitmap ();
                    if (bm != null) {
                        canvas.drawBitmap (bm, tileMat, null);
                        if (tile.tileDrawCycle == 0) loadedBitmaps.add (tile);
                        tile.tileDrawCycle = viewDrawCycle;

                        // maybe show tile names at top center of each tile
                        if (showtilenames) {
                            canvas.save ();
                            try {
                                canvas.concat (tileMat);
                                canvas.drawLine (0, 0, tile.width,  0, stntxpaint);
                                canvas.drawLine (0, 0, 0, tile.height, stntxpaint);
                                DrawTileName (canvas, tile, stnbgpaint);
                                DrawTileName (canvas, tile, stntxpaint);
                            } finally {
                                canvas.restore ();
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.e (TAG, "error drawing bitmap", t);
                }
            }
        }

        /**
         * Unload any unreferenced tiles.
         */
        for (Iterator<AirTile> it = loadedBitmaps.iterator (); it.hasNext ();) {
            AirTile tile = it.next ();
            if (tile.tileDrawCycle < viewDrawCycle) {
                it.remove ();
                tile.tileDrawCycle = 0;
                tile.recycle ();
            }
        }
    }

    /**
     * Screen is being closed, so close all open bitmaps.
     */
    @Override  // DisplayableChart
    public void CloseBitmaps ()
    {
        for (Iterator<AirTile> it = loadedBitmaps.iterator (); it.hasNext ();) {
            AirTile tile = it.next ();
            it.remove ();
            tile.tileDrawCycle = 0;
            tile.recycle ();
        }
    }

    /**
     * Use chart projection to map lat/lon to canvas pixel.
     */
    @Override  // DisplayableChart
    public boolean LatLon2CanPixExact (float lat, float lon, @NonNull Point canpix)
    {
        LatLon2ChartPixelExact (lat, lon, canpix);

        float[] flt = flt4PerThread.get ();
        flt[0] = canpix.x;
        flt[1] = canpix.y;
        drawOnCanvasChartMat.mapPoints (flt, 2, flt, 0, 1);
        canpix.x = Math.round (flt[2]);
        canpix.y = Math.round (flt[3]);
        return true;
    }

    /**
     * Use chart projection to map canvas pixel to lat/lon.
     */
    @Override  // DisplayableChart
    public boolean CanPix2LatLonExact (float canpixx, float canpixy, @NonNull LatLon ll)
    {
        float[] flt = flt4PerThread.get ();
        flt[0] = canpixx;
        flt[1] = canpixy;
        undrawOnCanvasChartMat.mapPoints (flt, 2, flt, 0, 1);

        float chartpixx = flt[2];
        float chartpixy = flt[3];
        ChartPixel2LatLonExact (chartpixx, chartpixy, ll);
        return true;
    }

    /**
     * Draw tile filename string top center on the tile for debugging.
     */
    private void DrawTileName (Canvas canvas, AirTile tile, Paint paint)
    {
        float  x = tile.width / 2.0F;
        float  y =  0;
        int    d = 15;
        String s = tile.pngName;
        int j;
        for (j = 0; j + d < s.length (); j += d) {
            y += paint.getTextSize ();
            canvas.drawText (s.substring (j, j + d), x, y, paint);
        }
        y += paint.getTextSize ();
        canvas.drawText (s.substring (j), x, y, paint);
    }

    /**
     * Get list of tiles in this chart.
     * Does not actually open the image tile files.
     * @param s = scaling : 1: normal size
     *                      2: undersampled (1/4 as many original pixels)
     *                      3: 1/9 as many original pixels, etc
     * @param l = include legend pixels
     */
    private void GetAirTiles (ChartView chartView, int s, boolean l)
    {
        // get dimensions of unscaled tile 0,0 and assume that all tiles are the same
        // (except that the ones on the right and bottom edges may be smaller)
        if ((wstep == 0) || (hstep == 0)) {
            AirTile at0 = new AirTile (chartView, 0, 0, 1, true);
            String name0 = at0.getPngName ();
            BitmapFactory.Options bfo = new BitmapFactory.Options ();
            bfo.inJustDecodeBounds = true;
            BitmapFactory.decodeFile (name0, bfo);
            wstep = bfo.outWidth;
            hstep = bfo.outHeight;
            if ((wstep <= 0) || (hstep <= 0)) {
                Log.e (TAG, "error sizing tile " + name0);
                wstep = 0;
                hstep = 0;
                return;
            }
        }

        // if we don't have the requested array of tiles, get them
        if ((tilez == null) || (scaling != s) || (legends != l)) {
            int width  = (chartwidth + s - 1) / s;
            int height = (chartheight + s - 1) / s;
            int length = ((height + hstep - 1) / hstep) * ((width + wstep - 1) / wstep);
            if ((tilez == null) || (tilez.length != length)) {
                tilez = new AirTile[length];
            }
            ntilez = 0;
            for (int h = 0; h < height; h += hstep) {
                for (int w = 0; w < width; w += wstep) {
                    AirTile at = new AirTile (chartView, w, h, s, l);
                    if (l || !at.isLegendOnly) tilez[ntilez++] = at;
                }
            }
            scaling = s;
            legends = l;
        }
    }

    /**
     * Contains an image that is a small part of an air chart.
     * Might have to create a clipped/scaled tile file if none exists already.
     * Clipped tiles (what have legened area alpha'd out) are indicated by an @.png suffix
     * instead of plain .png, eg, New_York_SEC_92/43/38@.png
     * Scaled tiles are indicated by an /S<scale>/ in their name, eg New_York_SEC_92/S4/3/14.png
     */
    private class AirTile {
        public boolean isChartedOnly;  // tile contains only pixels that are in charted area
        public boolean isLegendOnly;   // tile contains only pixels that are in legend area
        public boolean queued;         // queued to AirTileLoader thread
        public int leftPixel;          // where the left edge is within the chart
        public int topPixel;           // where the top edge is within the chart
        public int width;              // width of this tile (always wstep except for rightmost tiles)
        public int height;             // height of this tile (always hstep except for bottommost tiles)
        public long tileDrawCycle;     // marks the tile as being in use

        private Bitmap bitmap;         // bitmap that contains the image
        private boolean legends;       // include legend pixels
        private ChartView chartView;   // where tile is being displayed
        private File pngFile;          // name of bitmap file
        private int scaling;           // scaling factor used to fetch bitmap with
        private String pngName;        // name of bitmap file

        public AirTile (ChartView cv, int lp, int tp, int sf, boolean lg)
        {
            chartView = cv;
            leftPixel = lp;
            topPixel  = tp;
            scaling   = sf;
            legends   = lg;

            width  = (chartwidth + sf - 1) / sf - leftPixel;
            height = (chartheight + sf - 1) / sf - topPixel;
            if (width  > wstep) width  = wstep;
            if (height > hstep) height = hstep;

            boolean a = PixelIsCharted (leftPixel * sf, topPixel * sf);
            boolean b = PixelIsCharted ((leftPixel + width) * sf, topPixel * sf);
            boolean c = PixelIsCharted ((leftPixel + width) * sf, (topPixel + height) * sf);
            boolean d = PixelIsCharted (leftPixel * sf, (topPixel + height) * sf);
            isChartedOnly = a & b & c & d;
            isLegendOnly = !a & !b & !c & !d;
        }

        /**
         * Load the corresponding scaled and/or clipped bitmap.
         * @return bitmap of the tile (or null if not available right now)
         */
        public Bitmap GetScaledBitmap ()
        {
            if (!legends && isLegendOnly) return null;

            // if we don't have bitmap in memory,
            // queue to thread for loading
            Bitmap bm;
            synchronized (airTileLoader) {
                bm = bitmap;
                if ((bm == null) && !queued) {

                    // push this as first cuz there might be a lot of tiles already
                    // queued that we don't really need any more so queue this one
                    // to be next to be processed cuz this one is needed asap
                    AirTileLoader.tilesToLoad.addFirst (this);

                    // don't queue the same tile more than once
                    queued = true;

                    // wake the AirTileLoader thread
                    airTileLoader.notifyAll ();
                }
            }
            return bm;
        }

        /**
         * Called in AirTileThread to read bitmap into memorie.
         */
        public Bitmap LoadBitmap ()
        {
            // see if all legend pixels and caller doesn't want legends
            if (!legends && isLegendOnly) return null;

            // we need some pixels from a bitmap file
            try {
                return ReadBitmap (null);
            } catch (Throwable t) {
                Log.e (TAG, "error loading bitmap " + pngName, t);
                return null;
            }
        }

        /**
         * Called in AirTileThread to read bitmap into memorie,
         * optionally undersampling.  It may throw exceptions and
         * out-of-memory errors.
         */
        private Bitmap ReadBitmap (BitmapFactory.Options bfo) throws IOException
        {
            // make image filename if we haven't already
            getPngName ();

            // see if we already have exact tile needed
            if (!pngFile.exists ()) {

                // unscaled files (unless caller wants legends chopped off) can only come from server
                if ((scaling == 1) && (legends || isChartedOnly)) {
                    throw new FileNotFoundException (pngName);
                }

                // if not, create it
                Log.d (TAG, "creating " + pngName);
                int[] pixels = new int[width*height];
                if (scaling == 1) MakePartialBitmap (pixels);
                else MakeScaledBitmap (pixels);

                // write to flash for next time
                Bitmap bm = Bitmap.createBitmap (pixels, width, height, Bitmap.Config.ARGB_8888);
                Lib.Ignored (pngFile.getParentFile ().mkdirs ());
                FileOutputStream os = new FileOutputStream (pngName + ".tmp");
                bm.compress (Bitmap.CompressFormat.PNG, 0, os);
                os.close ();
                Lib.RenameFile (pngName + ".tmp", pngName);

                // write filename to filelist file so it will be deleted on next revision
                String fn = WairToNow.dbdir + "/charts/" +
                        spacenamenr.replace (' ', '_') + ".filelist.txt";
                FileWriter fw = new FileWriter (fn, true);
                int i = pngName.indexOf ("charts/");
                fw.write (pngName.substring (i) + "\n");
                fw.close ();

                // return created bitmap
                if (bfo == null) return bm;

                // caller wants undersampling, throw this one away
                bm.recycle ();
            }

            // read tile, possibly undersampling
            return BitmapFactory.decodeFile (pngName, bfo);
        }

        public String getPngName ()
        {
            // make image filename if we haven't already
            if (pngFile == null) {
                StringBuilder sb = new StringBuilder ();
                sb.append (WairToNow.dbdir);
                sb.append ("/charts/");
                sb.append (spacenamewr.replace (' ', '_'));
                if (scaling != 1) {
                    sb.append ("/S");
                    sb.append (scaling);
                }
                if ((topPixel == 0) && (leftPixel == 0)) {
                    sb.append ("/0/0");
                } else {
                    sb.append ('/');
                    sb.append (topPixel / hstep);
                    sb.append ('/');
                    sb.append (leftPixel / wstep);
                }
                if (!legends && !isChartedOnly) sb.append ('@');
                sb.append (".png");
                pngName = sb.toString ();
                pngFile = new File (pngName);
            }
            return pngName;
        }

        /**
         * Tile contains mix of charted and legend pixels.
         * Create tile with all legend pixels transparent.
         */
        private void MakePartialBitmap (int[] pixels) throws IOException
        {
            AirTile unclippedAirTile = new AirTile (chartView, leftPixel, topPixel, 1, true);
            Bitmap bm = unclippedAirTile.ReadBitmap (null);
            bm.getPixels (pixels, 0, width, 0, 0, width, height);
            bm.recycle ();
            for (int y = 0; y < height; y ++) {
                for (int x = 0; x < width; x ++) {
                    if (!PixelIsCharted (leftPixel + x, topPixel + y)) {
                        pixels[y*width+x] = Color.TRANSPARENT;
                    }
                }
            }
        }

        /**
         * Called in AirTileThread via LoadBitmap() to read scaled bitmap into memorie.
         * Creates the scaled tile from unscaled tiles if not already on flash.
         *
         * The scaled tiles are 256x256, same as unscaled tiles, they just cover
         * scaling**2 as much area as the unscaled tiles.
         *
         * Eg, scaling=3:
         *
         *  +--------+--------+--------+
         *  |A       |B       |C       |
         *  |        |        |        |
         *  |        |        |        |
         *  +--------+--------+--------+
         *  |D       |E       |F       |
         *  |        |        |        |
         *  |        |        |        |
         *  +--------+--------+--------+
         *  |G       |H       |I       |
         *  |        |        |        |
         *  |        |        |        |
         *  +--------+--------+--------+
         *
         * Also assume leftPixel=20480, topPixel=768
         *
         * A: ystep=0
         *    xstep=0
         *    unscaledpixelsoverfromchartleft=20480*3
         *    unscaledpixelsdownfromcharttop=768*3
         *    scaledpixelsoverfromscaledtileleft=0
         *    scaledpixelsdownfromscaledtiletop=0
         *
         * B: ystep=0
         *    xstep=1
         *    unscaledpixelsoverfromchartleft=20480*3+256
         *    unscaledpixelsdownfromcharttop=768*3
         *    scaledpixelsoverfromscaledtileleft=256/3
         *    scaledpixelsdownfromscaledtiletop=0
         */
        private void MakeScaledBitmap (int[] pixels) throws IOException
        {
            // set up option to undersample unscaled bitmaps
            BitmapFactory.Options bfo = new BitmapFactory.Options ();
            bfo.inDensity        = scaling;
            bfo.inInputShareable = true;
            bfo.inPurgeable      = true;
            bfo.inScaled         = true;
            bfo.inTargetDensity  = 1;

            // step through each unscaled sub-tile to fill in pixels of this scaled tile
            for (int ystep = 0; ystep < scaling; ystep ++) {
                for (int xstep = 0; xstep < scaling; xstep ++) {

                    // calc pixel number in whole unscaled chart for upper left corner being filled in
                    //   {left,top}Pixel = scaled left/top pixel within whole scaled chart of {x,y}step=0,0 tile
                    //   {left,top}Pixel * scaling = unscaled left/top pixel within whole unscaled chart of {x,y}step=0,0 tile
                    //   wstep * xstep = unscaled pixels over from left edge
                    //   hstep * ystep = unscaled pixels down from top edge
                    int unscaledpixelsoverfromchartleft = leftPixel * scaling + wstep * xstep;
                    int unscaledpixelsdownfromcharttop  = topPixel  * scaling + hstep * ystep;

                    // if unscaled pixels start off end of unscaled chart, don't bother with the sub-tile
                    if (unscaledpixelsoverfromchartleft >= chartwidth) continue;
                    if (unscaledpixelsdownfromcharttop >= chartheight) continue;

                    // compute where upper left corner of unscaled sub-tile goes within this scaled tile
                    int scaledpixelsoverfromscaledtileleft = Math.round ((float) xstep * wstep / scaling);
                    int scaledpixelsdownfromscaledtiletop  = Math.round ((float) ystep * hstep / scaling);

                    // read the unscaled sub-tile into memory, undersampling it to scale it down
                    AirTile unscaledTile = new AirTile (chartView,
                            unscaledpixelsoverfromchartleft,
                            unscaledpixelsdownfromcharttop,
                            1, legends);
                    Bitmap bm = unscaledTile.ReadBitmap (bfo);

                    // get the scaled sub-tile width and height.  don't let it run off edge of the scaled tile.
                    int bmw = bm.getWidth ();
                    int bmh = bm.getHeight ();
                    if (bmw > width - scaledpixelsoverfromscaledtileleft) bmw = width - scaledpixelsoverfromscaledtileleft;
                    if (bmh > height - scaledpixelsdownfromscaledtiletop) bmh = height - scaledpixelsdownfromscaledtiletop;

                    // copy pixels from scaled sub-tile to scaled tile.
                    int offset = scaledpixelsdownfromscaledtiletop * width + scaledpixelsoverfromscaledtileleft;
                    bm.getPixels (pixels, offset, width, 0, 0, bmw, bmh);

                    // throw away scaled sub-tile
                    bm.recycle ();
                }
            }

            /* Draw green line on top and left edges of tile
            for (int i = 0; i < 5; i ++) {
                for (int x = 0; x < width; x ++) {
                    pixels[i*width+x] = Color.GREEN;
                }
                for (int y = 0; y < height; y ++) {
                    pixels[y*width+i] = Color.GREEN;
                }
            }
            */
        }

        /**
         * Tile no longer needed, unload from memory.
         */
        public void recycle ()
        {
            if (bitmap != null) {
                bitmap.recycle ();
                bitmap = null;
            }
        }

        /**
         * Tile was loaded in memory, re-draw chart with new tile.
         */
        public void postInvalidate ()
        {
            chartView.postInvalidate ();
        }
    }

    /**
     * Load air tiles in a background thread to keep GUI responsive.
     */
    private static class AirTileLoader extends Thread {
        public final static LinkedList<AirTile> tilesToLoad = new LinkedList<> ();

        public AirTileLoader ()
        {
            start ();
        }

        @Override
        public void run ()
        {
            setName ("AirTile loader");

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
                synchronized (airTileLoader) {
                    while (tilesToLoad.isEmpty ()) {
                        SQLiteDBs.CloseAll ();

                        try {
                            airTileLoader.wait ();
                        } catch (InterruptedException ie) {
                            Lib.Ignored ();
                        }
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
                synchronized (airTileLoader) {
                    tile.bitmap = bm;
                    tile.queued = false;
                }
                tile.postInvalidate ();
            }
        }
    }

    /**
     * Update mapping values based on data provided in csv file.
     */
    public void ParseChartLimitsLine (String csvLine)
    {
        /*
         * Handle projection-specific parameters.
         */
        csvLine = ParseParams (csvLine);

        /*
         * Handle common parameters.
         */
        String[] values = Lib.QuotedCSVSplit (csvLine);
        chartwidth      = Integer.parseInt (values[0]);
        chartheight     = Integer.parseInt (values[1]);
        enddate         = Integer.parseInt (values[2]);
        spacenamewr     = values[3];                      // eg, "New York SEC 87"
        int i           = spacenamewr.lastIndexOf (' ');  // remove revision number from the end
        revision        = spacenamewr.substring (i + 1);
        spacenamenr     = spacenamewr.substring (0, i);

        /*
         * Most helicopter charts don't have a published expiration date,
         * so we get a zero for enddate.  Make it something non-zero so
         * we don't confuse it with an unloaded chart.
         */
        if (enddate == 0) enddate = 99999999;

        /*
         * Determine limits of charted (non-legend) area.
         * There are both lat/lon and pixel limits.
         * A point must be within both sets of limits to be considered to be in charted area.
         */
        // pixel defaults are just the whole chart size
        chartedLeftPix = 0;
        chartedRitePix = chartwidth;
        chartedTopPix  = 0;
        chartedBotPix  = chartheight;

        // check out northwest corner
        LatLon ll = new LatLon ();
        ChartPixel2LatLonExact (0, 0, ll);
        chartedNorthLat = ll.lat;
        chartedWestLon  = ll.lon;

        // check out northeast corner
        ChartPixel2LatLonExact (chartwidth, 0, ll);
        if (chartedNorthLat < ll.lat) chartedNorthLat = ll.lat;
        chartedEastLon = ll.lon;

        // check out southwest corner
        ChartPixel2LatLonExact (0, chartheight, ll);
        chartedSouthLat = ll.lat;
        chartedWestLon = Lib.Westmost (chartedWestLon, ll.lon);

        // check out southeast corner
        ChartPixel2LatLonExact (chartwidth, chartheight, ll);
        if (chartedSouthLat > ll.lat) chartedSouthLat = ll.lat;
        chartedEastLon = Lib.Eastmost (chartedEastLon, ll.lon);

        // values from chartedlims.csv override the defaults
        String[] limParts = maintView.chartedLims.get (spacenamenr);
        if (limParts != null) {
            for (i = limParts.length - 1; --i >= 0; ) {
                String limPart = limParts[i];
                if (limPart.startsWith ("@")) {
                    autoOrder = Integer.parseInt (limPart.substring (1));
                    continue;
                }
                char dir = limPart.charAt (limPart.length () - 1);
                if (limPart.charAt (0) == '#') {
                    int val = Integer.parseInt (limPart.substring (1, limPart.length () - 1));
                    switch (dir) {
                        case 'E':
                        case 'R':
                            chartedRitePix = val;
                            break;
                        case 'N':
                        case 'T':
                            chartedTopPix = val;
                            break;
                        case 'S':
                        case 'B':
                            chartedBotPix = val;
                            break;
                        case 'W':
                        case 'L':
                            chartedLeftPix = val;
                            break;
                    }
                } else {
                    float val = ParseLatLon (limPart.substring (0, limPart.length () - 1));
                    switch (dir) {
                        case 'E':
                            chartedEastLon = val;
                            break;
                        case 'N':
                            chartedNorthLat = val;
                            break;
                        case 'S':
                            chartedSouthLat = val;
                            break;
                        case 'W':
                            chartedWestLon = val;
                            break;
                    }
                }
            }
        }

        /*
         * Normalize west longitude limit to -180.0..+179.999999999.
         * Normalize east longitude limit to just >= of west limit.
         */
        chartedWestLon = Lib.NormalLon (chartedWestLon);
        chartedEastLon = Lib.NormalLon (chartedEastLon);
        if (chartedEastLon < chartedWestLon) chartedEastLon += 360.0F;

        /*
         * Some charts have a record in outlines.txt giving where the charted pixels are.
         * If so, use that as the chart outline.
         * If just two points given, it is the common case of a rectangular area,
         * so use those as the simple left/rite/top/bot limits.
         */
        try {
            BufferedReader outFile = new BufferedReader (new FileReader (WairToNow.dbdir + "/outlines.txt"), 4096);
            try {
                String outLine;
                while ((outLine = outFile.readLine ()) != null) {
                    String[] parts = outLine.split (":");
                    String namenr = parts[0].trim ();
                    if (namenr.equals (spacenamenr)) {
                        parts = parts[1].split ("/");
                        if (parts.length < 2) throw new Exception ("too few pairs");
                        outline = new Point[parts.length+1];
                        int j = 0;
                        for (String part : parts) {
                            String[] xystrs = part.split (",");
                            if (xystrs.length != 2) throw new Exception ("bad x,y " + part);
                            Point p = new Point ();
                            p.x = Integer.parseInt (xystrs[0].trim ());
                            p.y = Integer.parseInt (xystrs[1].trim ());
                            outline[j++] = p;
                        }
                        if (j == 2) {
                            chartedLeftPix = Math.min (outline[0].x, outline[1].x);
                            chartedRitePix = Math.max (outline[0].x, outline[1].x);
                            chartedTopPix  = Math.min (outline[0].y, outline[1].y);
                            chartedBotPix  = Math.max (outline[0].y, outline[1].y);
                            outline = null;
                        } else {
                            outline[j] = outline[0];
                        }
                        break;
                    }
                }
            } finally {
                outFile.close ();
            }
        } catch (FileNotFoundException fnfe) {
            Lib.Ignored ();
        } catch (Exception e) {
            Log.e (TAG, "error reading outlines.txt for " + spacenamenr, e);
            outline = null;
        }
    }

    /**
     * Parse numeric portion of lat/lon string as found in the chartedlims.csv file.
     */
    private static float ParseLatLon (String str)
    {
        int i = str.indexOf ('^');
        if (i < 0) {
            return Float.parseFloat (str);
        }
        float deg = Float.parseFloat (str.substring (0, i));
        return deg + Float.parseFloat (str.substring (++ i)) / 60.0F;
    }

    /**
     * See if the given lat,lon is within the chart area (not legend)
     */
    public boolean LatLonIsCharted (float lat, float lon)
    {
        /*
         * If lat/lon out of lat/lon limits, point is not in chart area
         */
        if ((lat < chartedSouthLat) || (lat > chartedNorthLat)) return false;
        lon = Lib.NormalLon (lon);
        if (lon < chartedWestLon) lon += 360.0F;
        if (lon > chartedEastLon) return false;

        /*
         * It is within lat/lon limits, check pixel limits too.
         */
        Point pt = ptPerThread.get ();
        return LatLon2ChartPixelExact (lat, lon, pt) &&
                TestPixelIsCharted (pt.x, pt.y);
    }

    /**
     * See if the given pixel is within the chart area (not legend)
     */
    private boolean PixelIsCharted (int pixx, int pixy)
    {
        /*
         * If pixel is out of pixel limits, point is not in chart area
         */
        if (!TestPixelIsCharted (pixx, pixy)) return false;

        /*
         * It is within pixel limits, check lat/lon limits too.
         */
        LatLon ll = llPerThread.get ();
        ChartPixel2LatLonExact (pixx, pixy, ll);
        if ((ll.lat < chartedSouthLat) || (ll.lat > chartedNorthLat)) return false;

        if (ll.lon < chartedWestLon) ll.lon += 360.0;
        return ll.lon <= chartedEastLon;
    }

    /**
     * Test given pixel against chart pixel limits.
     * Fortunately most charts have simple rectangle limits.
     */
    private boolean TestPixelIsCharted (int pixx, int pixy)
    {
        if (outline == null) {
            return (pixy >= chartedTopPix) && (pixy <= chartedBotPix) &&
                   (pixx >= chartedLeftPix) && (pixx <= chartedRitePix);
        }

        // arbitrary non-crossing polygon
        return PnPoly.wn (pixx, pixy, outline, outline.length - 1) > 0;
    }

    /**
     * Lambert Conical Conformal charts.
     */
    private static class Lcc extends AirChart {
        private float pixelsize;
        private float e_e, e_F_rada, e_lam0, e_n, e_phi0, e_rho0;
        private float tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f;
        private float wft_a, wft_b, wft_c, wft_d, wft_e, wft_f;

        @Override  // AirChart
        protected String ParseParams (String csvLine)
        {
            String[] values = csvLine.split (",", 16);
            float centerLat = Float.parseFloat (values[ 0]);
            float centerLon = Float.parseFloat (values[ 1]);
            float stanPar1  = Float.parseFloat (values[ 2]);
            float stanPar2  = Float.parseFloat (values[ 3]);
            float rada      = Float.parseFloat (values[ 6]);
            float radb      = Float.parseFloat (values[ 7]);
            tfw_a           = Float.parseFloat (values[ 8]);
            tfw_b           = Float.parseFloat (values[ 9]);
            tfw_c           = Float.parseFloat (values[10]);
            tfw_d           = Float.parseFloat (values[11]);
            tfw_e           = Float.parseFloat (values[12]);
            tfw_f           = Float.parseFloat (values[13]);

            /*
             * Compute projection parameters.
             *
             * Map Projections -- A Working Manual
             * John P. Snyder
             * US Geological Survey Professional Paper 1395
             * http://pubs.usgs.gov/pp/1395/report.pdf
             * see pages viii, v107, v296, ellipsoidal projection
             */
            pixelsize  = Mathf.hypot (tfw_b, tfw_d);
            e_lam0     = Mathf.toRadians (centerLon);
            e_phi0     = Mathf.toRadians (centerLat);
            float phi1 = Mathf.toRadians (stanPar1);
            float phi2 = Mathf.toRadians (stanPar2);

            e_e = Mathf.sqrt (1 - (radb * radb) / (rada * rada));

            // v108: equation 14-15
            float m1 = eq1415 (phi1);
            float m2 = eq1415 (phi2);

            // v108: equation 15-9a
            float t0 = eq159a (e_phi0);
            float t1 = eq159a (phi1);
            float t2 = eq159a (phi2);

            // v108: equation 15-8
            e_n = (float) ((Math.log (m1) - Math.log (m2)) / (Math.log (t1) - Math.log (t2)));

            // v108: equation 15-10
            float F = (float) (m1 / (e_n * Math.pow (t1, e_n)));
            e_F_rada = F * rada;

            // v108: equation 15-7a
            e_rho0 = (float) (e_F_rada * Math.pow (t0, e_n));

            /*
             * tfw_a..f = convert pixel to easting/northing
             * Compute wft_a..f = convert easting/northing to pixel:
             *                [ tfw_a tfw_b 0 ]
             *    [ x y 1 ] * [ tfw_c tfw_d 0 ] = [ e n 1 ]
             *                [ tfw_e tfw_f 1 ]
             *
             * So if wft = inv (tfw):
             *
             *                [ tfw_a tfw_b 0 ]   [ wft_a wft_b 0 ]               [ wft_a wft_b 0 ]
             *    [ x y 1 ] * [ tfw_c tfw_d 0 ] * [ wft_c wft_d 0 ] = [ e n 1 ] * [ wft_c wft_d 0 ]
             *                [ tfw_e tfw_f 1 ]   [ wft_e wft_f 1 ]               [ wft_e wft_f 1 ]
             *
             *                            [ wft_a wft_b 0 ]
             *    [ x y 1 ] = [ e n 1 ] * [ wft_c wft_d 0 ]
             *                            [ wft_e wft_f 1 ]
             */
            float[][] mat = new float[][] {
                    new float[] { tfw_a, tfw_b, 0, 1, 0 },
                    new float[] { tfw_c, tfw_d, 0, 0, 1 },
                    new float[] { tfw_e, tfw_f, 1, 0, 0 }
            };
            Lib.RowReduce (mat);
            wft_a = mat[0][3];
            wft_b = mat[0][4];
            wft_c = mat[1][3];
            wft_d = mat[1][4];
            wft_e = mat[2][3];
            wft_f = mat[2][4];

            /*
             * Return common parameter portion of csvLine.
             * Note that values[14] is not used.
             */
            return values[4] + ',' + values[5] + ',' + values[15];
        }

        /**
         * Given a lat/lon, compute pixel within the chart
         *
         * @param lat = latitude within the chart
         * @param lon = longitude within the chart
         * @param p   = where to return corresponding pixel
         * @return p filled in
         */
        @Override  // AirChart
        public boolean LatLon2ChartPixelExact (float lat, float lon, @NonNull Point p)
        {
            float phi = Mathf.toRadians (lat);
            float lam = Mathf.toRadians (lon);

            while (lam < e_lam0 - Mathf.PI) lam += Mathf.PI * 2.0F;
            while (lam > e_lam0 + Mathf.PI) lam -= Mathf.PI * 2.0F;

            /*
             * Calculate number of metres east of Longitude_of_Central_Meridian
             * and metres north of Latitude_of_Projection_Origin using Lambert
             * Conformal Ellipsoidal Projection formulae.
             */
            // v108: equation 15-9a
            float t = eq159a (phi);

            // v108: equation 15-7
            float rho = (float) (e_F_rada * Math.pow (t, e_n));

            // v108: equation 14-4
            float theta = e_n * (lam - e_lam0);

            // v107: equation 14-1 -> how far east of centerLon
            float easting = rho * Mathf.sin (theta);

            // v107: equation 14-2 -> how far north of centerLat
            float northing = e_rho0 - rho * Mathf.cos (theta);

            /*
             * Compute corresponding image pixel number.
             */
            int x = Math.round (easting * wft_a + northing * wft_c + wft_e);
            int y = Math.round (easting * wft_b + northing * wft_d + wft_f);
            p.x = x;
            p.y = y;

            return (x >= 0) && (x < chartwidth) && (y >= 0) && (y < chartheight);
        }

        /**
         * Given a pixel within the chart, compute corresponding lat/lon
         *
         * @param x  = pixel within the entire side of the sectional
         * @param y  = pixel within the entire side of the sectional
         * @param ll = where to return corresponding lat/lon
         */
        @Override  // AirChart
        protected void ChartPixel2LatLonExact (float x, float y, @NonNull LatLon ll)
        {
            // opposite steps of LatLon2ChartPixelExact()

            float easting  = tfw_a * x + tfw_c * y + tfw_e;
            float northing = tfw_b * x + tfw_d * y + tfw_f;

            // easting = rho * sin (theta)
            // easting / rho = sin (theta)

            // northing = e_rho0 - rho * cos (theta)
            // rho * cos (theta) = e_rho0 - northing
            // cos (theta) = (e_rho0 - northing) / rho

            float theta = (float) Math.atan (easting / (e_rho0 - northing));

            // theta = e_n * (lam - e_lam0)
            // theta / e_n = lam - e_lam0
            // theta / e_n + e_lam0 = lam

            float lam = theta / e_n + e_lam0;

            // v108: equation 14-4
            float costheta = Mathf.cos (e_n * (lam - e_lam0));

            // must calculate phi (latitude) with successive approximation
            // usually takes 3 or 4 iterations to resolve latitude within one pixel

            float phi = e_phi0;
            float metresneedtogonorth;
            do {
                // v108: equation 15-9a
                float t = eq159a (phi);

                // v108: equation 15-7
                float rho = (float) (e_F_rada * Math.pow (t, e_n));

                // v107: equation 14-2 -> how far north of centerLat
                float n = e_rho0 - rho * costheta;

                // update based on how far off our guess is
                // - we are trying to get phi that gives us 'northing'
                // - but we have phi that gives us 'n'
                metresneedtogonorth = northing - n;
                phi += metresneedtogonorth / (Lib.MPerNM * Lib.NMPerDeg * 180.0F / Mathf.PI);
            } while (Math.abs (metresneedtogonorth) > pixelsize);

            ll.lat = Mathf.toDegrees (phi);
            ll.lon = Lib.NormalLon (Mathf.toDegrees (lam));
        }

        private float eq1415 (float phi)
        {
            float w = e_e * Mathf.sin (phi);
            return Mathf.cos (phi) / Mathf.sqrt (1 - w * w);
        }

        private float eq159a (float phi)
        {
            float sinphi = Mathf.sin (phi);
            float u = (1 - sinphi) / (1 + sinphi);
            float v = (1 + e_e * sinphi) / (1 - e_e * sinphi);
            return Mathf.sqrt (u * Math.pow (v, e_e));
        }
    }

    /**
     * Polar Stereographic Projection charts.
     *
     * CSV line fields:
     *   0 : north latitude
     *   1 : west longitude
     *   2 : south latitude
     *   3 : east longitude
     *   4 : x pixel for point at north,west
     *   5 : y pixel for point at north,west
     *   6 : x pixel for point at north,east
     *   7 : y pixel for point at north,east
     *   8 : x pixel for point at south,west
     *   9 : y pixel for point at south,west
     *  10 : x pixel for point at south,east
     *  11 : y pixel for point at south,east
     *  12 : chart width
     *  13 : chart height
     *  14 : expiration date yyyymmdd
     *  15 : chart space name including revision number
     *
     * Example for ONC A-1 chart:
     *   psp:88,-36,80,24,4337,87,5737,199,1118,4690,8090,5279,9254,6693,99999999,ONC A-1 19690603
     *
     * The basic projection transformation is:
     *   beta = 90 - latitude
     *   pixels_from_north_pole = earth_diameter_in_pixels * tan (beta / 2)
     */
    private static class PSP extends AirChart {
        public final static String pfx = "psp:";

        private float earthDiameterPixels;
        private float tiltedCCWRadians;
        private int northPoleX, northPoleY;

        @Override  // AirChart
        protected String ParseParams (String csvLine)
        {
            String[] values = csvLine.substring (pfx.length ()).split (",", 13);
            // at latn = Float.parseFloat (values[ 0]);
            float lonw = Float.parseFloat (values[ 1]);
            float lats = Float.parseFloat (values[ 2]);
            // at lone = Float.parseFloat (values[ 3]);
            int    nwx = Integer.parseInt (values[ 4]);
            int    nwy = Integer.parseInt (values[ 5]);
            int    nex = Integer.parseInt (values[ 6]);
            int    ney = Integer.parseInt (values[ 7]);
            int    swx = Integer.parseInt (values[ 8]);
            int    swy = Integer.parseInt (values[ 9]);
            int    sex = Integer.parseInt (values[10]);
            int    sey = Integer.parseInt (values[11]);

            // find north pole pixel by intersecting west edge and east edge lines
            northPoleX = Math.round (Lib.lineIntersectX (nwx, nwy, swx, swy, nex, ney, sex, sey));
            northPoleY = Math.round (Lib.lineIntersectY (nwx, nwy, swx, swy, nex, ney, sex, sey));

            // find earth diameter in pixels by doing transform using south ring pixels from north pole
            float southRingRadius = Mathf.hypot (swx - northPoleX, swy - northPoleY);
            float beta = Mathf.toRadians (90.0F - lats);
            earthDiameterPixels = southRingRadius / Mathf.tan (beta / 2.0F);

            // find counter-clockwise tilt from west edge pixel angle minus west edge longitude
            tiltedCCWRadians = Mathf.atan2 (swx - northPoleX, swy - northPoleY) - Mathf.toRadians (lonw);

            return values[12];
        }

        @Override  // AirChart
        public boolean LatLon2ChartPixelExact (float lat, float lon, @NonNull Point p)
        {
            float beta = Mathf.toRadians (90.0F - lat);
            float pixelsFromNorthPole  = Mathf.tan (beta / 2.0F) * earthDiameterPixels;
            float angleCCWFromVertical = Mathf.toRadians (lon) + tiltedCCWRadians;
            int x = Math.round (pixelsFromNorthPole * Mathf.sin (angleCCWFromVertical) + northPoleX);
            int y = Math.round (pixelsFromNorthPole * Mathf.cos (angleCCWFromVertical) + northPoleY);
            p.x = x;
            p.y = y;
            return (x >= 0) && (x < chartwidth) && (y >= 0) && (y < chartheight);
        }

        @Override  // AirChart
        protected void ChartPixel2LatLonExact (float x, float y, @NonNull LatLon ll)
        {
            float pixelsFromNorthPole = Mathf.hypot (x - northPoleX, y - northPoleY);
            float beta = Mathf.atan (pixelsFromNorthPole / earthDiameterPixels) * 2.0F;
            ll.lat = 90.0F - Mathf.toDegrees (beta);

            float angleCCWFromVertical = Mathf.atan2 (x - northPoleX, y - northPoleY);
            ll.lon = Lib.NormalLon (Mathf.toDegrees (angleCCWFromVertical - tiltedCCWRadians));
        }
    }

    /**
     * Box Projection charts.
     *
     * Each degree of latitude and longitude, no matter where on the chart,
     * is the same number of pixels high and wide, thus forming a rectangular box.
     * Mercator except lines of latitude are equally spaced.
     *
     * Gives the most accurate mapping but slightly less visual quality
     * cuz the chart pixels have baked-in aliasing.
     *
     * CSV line fields:
     *   0 : north latitude (pixel row y=0)
     *   1 : west longitude (pixel col x=0)
     *   2 : south latitude (pixel row y=height)
     *   3 : east longitude (pixel col x=width)
     *   4 : chart width
     *   5 : chart height
     *   6 : expiration date yyyymmdd
     *   7 : chart space name including revision number
     */
    private static class Box extends AirChart {
        public final static String pfx = "box:";

        private float latn, lonw, lats, lone;

        @Override  // AirChart
        protected String ParseParams (String csvLine)
        {
            String[] values = csvLine.substring (pfx.length ()).split (",", 5);
            latn = Float.parseFloat (values[0]);
            lonw = Float.parseFloat (values[1]);
            lats = Float.parseFloat (values[2]);
            lone = Float.parseFloat (values[3]);

            lonw = Lib.NormalLon (lonw);
            lone = Lib.NormalLon (lone);
            if (lone < lonw) lone += 360.0F;

            return values[4];
        }

        @Override  // AirChart
        public boolean LatLon2ChartPixelExact (float lat, float lon, @NonNull Point p)
        {
            while (lon < lonw - 180.0F) lon += 360.0F;
            while (lon > lonw + 180.0F) lon -= 360.0F;

            int x = Math.round ((lon - lonw) / (lone - lonw) * chartwidth);
            int y = Math.round ((latn - lat) / (latn - lats) * chartheight);
            p.x = x;
            p.y = y;
            return (x >= 0) && (x < chartwidth) && (y >= 0) && (y < chartheight);
        }

        @Override  // AirChart
        protected void ChartPixel2LatLonExact (float x, float y, @NonNull LatLon ll)
        {
            ll.lat = y * (lats - latn) / chartheight + latn;
            ll.lon = x * (lone - lonw) / chartwidth  + lonw;
        }
    }
}
