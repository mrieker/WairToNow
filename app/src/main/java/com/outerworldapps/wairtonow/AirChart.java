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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.support.annotation.NonNull;
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

    private final static Object airTileLoaderLock = new Object ();
    private static AirTileLoader airTileLoaderThread;

    private static ThreadLocal<float[]> flt4PerThread = new ThreadLocal<float[]> () {
        @Override protected float[] initialValue () { return new float[4]; }
    };
    private static ThreadLocal<LatLon> llPerThread = new ThreadLocal<LatLon> () {
        @Override protected LatLon initialValue ()
        {
            return new LatLon ();
        }
    };
    private static ThreadLocal<PointD> ptPerThread = new ThreadLocal<PointD> () {
        @Override protected PointD initialValue ()
        {
            return new PointD ();
        }
    };

    private AirTile[] tilez;    // list of tiles that make up the chart at the given scaling and with/without legends
    private boolean legends;    // whether or not tilez contains tiles with legend pixels
    private float[] drawOnCanvasPoints = new float[16];
    private int hstep;          // heightwise pixel step for each tile
    private int ntilez;         // number of entries in tilez that are valid
    private int scaling;        // 1=full scale, 2=half sized, 3=third sized, etc
    private int wstep;          // widthwise pixel step for each tile
    private int[] expiredpixels;
    private LinkedList<AirTile>  loadedBitmaps = new LinkedList<> ();
    private long viewDrawCycle = 0;  // incremented each drawing cycle
    private MaintView maintView;
    private Matrix drawOnCanvasChartMat   = new Matrix ();
    private Matrix drawOnCanvasTileMat    = new Matrix ();
    private Matrix undrawOnCanvasChartMat = new Matrix ();
    private Paint expiredpaint;
    private Paint stnbgpaint;   // "showtilenames" background paint
    private Paint stntxpaint;   // "showtilenames" foreground paint
    private PointD drawOnCanvasPoint = new PointD ();
    private Rect canvasBounds       = new Rect (0, 0, 0, 0);
    public  String spacenamewr; // eg, "New York SEC 92"
    public  String spacenamenr; // eg, "New York SEC"
    private String revision;    // eg, "92"
    private WairToNow wairToNow;

    private int leftMacroChartPix;
    private int riteMacroChartPix;
    private int topMacroChartPix;
    private int botMacroChartPix;

    private double chartedEastLon;  // lat/lon limits of charted (non-legend) area
    private double chartedWestLon;
    private double chartedNorthLat;
    private double chartedSouthLat;
    public  int autoOrder;
    public  int begdate;
    public  int enddate;
    private int chartedBotPix, chartedLeftPix;  // pixel limits of charted (non-legend) area
    private int chartedRitePix, chartedTopPix;
    public  int chartheight, chartwidth;  // pixel width & height of this chart including legend areas

    private PointD[] outline;

    // methods a projection-specific implementation must provide
    protected abstract String ParseParams (String csvLine);
    public abstract boolean LatLon2ChartPixelExact (double lat, double lon, @NonNull PointD p);
    protected abstract void ChartPixel2LatLonExact (double x, double y, @NonNull LatLon ll);

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

        expiredpaint = new Paint ();
        expiredpaint.setColor (Color.RED);
        expiredpaint.setStrokeWidth (2);

        // set up paints used to show tile names at top center of each tile
        if (showtilenames) {
            stnbgpaint = new Paint ();
            stnbgpaint.setColor (Color.WHITE);
            stnbgpaint.setStyle (Paint.Style.STROKE);
            stnbgpaint.setStrokeWidth (wairToNow.thickLine);
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
     * See if the non-legend area of chart could contribute something to the canvas.
     * @param pmap = lat/lon mapping to the canvas
     */
    public boolean ContributesToCanvas (PixelMapper pmap)
    {
        return Lib.LatOverlap (pmap.canvasSouthLat, pmap.canvasNorthLat, chartedSouthLat, chartedNorthLat) &&
                Lib.LonOverlap (pmap.canvasWestLon, pmap.canvasEastLon, chartedWestLon, chartedEastLon);
    }

    /**
     * User just clicked this chart in the chart selection menu.
     */
    @Override  // DisplayableChart
    public void UserSelected ()
    { }

    /**
     * Get entry for chart selection menu.
     */
    @SuppressLint("SetTextI18n")
    @Override  // DisplayableChart
    public View GetMenuSelector (@NonNull ChartView chartView)
    {
        PixelMapper pmap = chartView.pmap;
        double arrowLat = wairToNow.currentGPSLat;
        double arrowLon = wairToNow.currentGPSLon;

        double arrowradpix = wairToNow.dotsPerInch / 32.0;
        double canvasradpix = arrowradpix * 1.5;

        // ratio of chart pixels / icon pixels
        // - size to make chart appear same size as text
        gms_ratio = (double) chartheight / (wairToNow.textSize * 1.5);

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

        PointD pt    = new PointD ();
        Paint  paint = new Paint  ();
        paint.setStyle (Paint.Style.FILL_AND_STROKE);

        // if we have an icon image, use it to fill in background
        // otherwise fill background with white
        Rect bmrect  = new Rect ();
        pt.x = 0;
        pt.y = 0;
        ChartPixel2GMSPixel (pt);
        bmrect.left   = (int) Math.round (pt.x);
        bmrect.top    = (int) Math.round (pt.y);
        pt.x = chartwidth;
        pt.y = chartheight;
        ChartPixel2GMSPixel (pt);
        bmrect.right  = (int) Math.round (pt.x);
        bmrect.bottom = (int) Math.round (pt.y);
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
        rectpath.addCircle ((float) pt.x, (float) pt.y, (float) canvasradpix, Path.Direction.CCW);
        paint.setColor (Color.CYAN);
        can.drawPath (rectpath, paint);

        rectpath.reset ();
        LatLon2GMSPixel (pmap.lastTlLat, pmap.lastTlLon, pt);
        rectpath.moveTo ((float) pt.x, (float) pt.y);
        LatLon2GMSPixel (pmap.lastTrLat, pmap.lastTrLon, pt);
        rectpath.lineTo ((float) pt.x, (float) pt.y);
        LatLon2GMSPixel (pmap.lastBrLat, pmap.lastBrLon, pt);
        rectpath.lineTo ((float) pt.x, (float) pt.y);
        LatLon2GMSPixel (pmap.lastBlLat, pmap.lastBlLon, pt);
        rectpath.lineTo ((float) pt.x, (float) pt.y);
        LatLon2GMSPixel (pmap.lastTlLat, pmap.lastTlLon, pt);
        rectpath.lineTo ((float) pt.x, (float) pt.y);
        can.drawPath (rectpath, paint);

        // draw a red dot where the current GPS location is
        LatLon2GMSPixel (arrowLat, arrowLon, pt);
        paint.setColor (Color.RED);
        can.drawCircle ((float) pt.x, (float) pt.y, (float) arrowradpix, paint);

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

    private double gms_ratio;
    private int gms_bitmapcenterx, gms_bitmapcentery;
    private int gms_chartcenterx, gms_chartcentery;

    private void LatLon2GMSPixel (double lat, double lon, PointD pt)
    {
        LatLon2ChartPixelExact (lat, lon, pt);
        ChartPixel2GMSPixel (pt);
    }
    private void ChartPixel2GMSPixel (PointD pt)
    {
        pt.x = (pt.x - gms_chartcenterx) / gms_ratio + gms_bitmapcenterx;
        pt.y = (pt.y - gms_chartcentery) / gms_ratio + gms_bitmapcentery;
    }

    /**
     * Draw chart on canvas possibly scaled and/or rotated.
     * @param pmap = mapping of lat/lons to canvas
     * @param canvas = canvas to draw on
     * @param inval = what to call in an arbitrary thread when a tile gets loaded
     * @param canvasHdgRads = 'up' heading on canvas
     */
    @Override  // DisplayableChart
    public void DrawOnCanvas (@NonNull PixelMapper pmap, @NonNull Canvas canvas, @NonNull Invalidatable inval, double canvasHdgRads)
    {
        DrawOnCanvas (pmap, canvas, inval, true);
    }
    public void DrawOnCanvas (@NonNull PixelMapper pmap, @NonNull Canvas canvas, @NonNull Invalidatable inval, boolean includeLegends)
    {
        viewDrawCycle ++;

        int tileScaling = ComputeChartMapping (pmap);
        if (tileScaling <= 0) return;

        /*
         * Scan through each tile that composes the chart.
         */
        GetAirTiles (inval, tileScaling, includeLegends);
        for (int i = 0; i < ntilez; i ++) {
            AirTile tile = tilez[i];

            /*
             * Attempt to draw tile iff it intersects the part of canvas we are drawing.
             * Otherwise, don't waste time and memory loading the bitmap, and certainly
             * don't mark the tile as referenced so we don't keep it in memory.
             */
            if (MapTileToCanvas (tile)) {
                try {
                    Bitmap bm = tile.GetScaledBitmap ();
                    if (bm != null) {
                        canvas.drawBitmap (bm, drawOnCanvasTileMat, null);
                        if (tile.tileDrawCycle == 0) loadedBitmaps.add (tile);
                        tile.tileDrawCycle = viewDrawCycle;

                        // maybe show tile names at top center of each tile
                        if (showtilenames) {
                            canvas.save ();
                            try {
                                canvas.concat (drawOnCanvasTileMat);
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
    public boolean LatLon2CanPixExact (double lat, double lon, @NonNull PointD canpix)
    {
        LatLon2ChartPixelExact (lat, lon, canpix);

        float[] flt = flt4PerThread.get ();
        flt[0] = (float) canpix.x;
        flt[1] = (float) canpix.y;
        drawOnCanvasChartMat.mapPoints (flt, 2, flt, 0, 1);
        canpix.x = flt[2];
        canpix.y = flt[3];
        return true;
    }

    /**
     * Use chart projection to map canvas pixel to lat/lon.
     */
    @Override  // DisplayableChart
    public boolean CanPix2LatLonExact (double canpixx, double canpixy, @NonNull LatLon ll)
    {
        float[] flt = flt4PerThread.get ();
        flt[0] = (float) canpixx;
        flt[1] = (float) canpixy;
        undrawOnCanvasChartMat.mapPoints (flt, 2, flt, 0, 1);

        double chartpixx = flt[2];
        double chartpixy = flt[3];
        ChartPixel2LatLonExact (chartpixx, chartpixy, ll);
        return true;
    }

    /**
     * Draw tile filename string top center on the tile for debugging.
     */
    private void DrawTileName (Canvas canvas, AirTile tile, Paint paint)
    {
        double  x = tile.width / 2.0;
        double  y =  0;
        int    d = 15;
        String s = tile.pngName;
        int j;
        for (j = 0; j + d < s.length (); j += d) {
            y += paint.getTextSize ();
            canvas.drawText (s.substring (j, j + d), (float) x, (float) y, paint);
        }
        y += paint.getTextSize ();
        canvas.drawText (s.substring (j), (float) x, (float) y, paint);
    }

    /**
     * Get list of tiles in this chart.
     * Does not actually open the image tile files.
     * @param s = scaling : 1: normal size
     *                      2: undersampled (1/4 as many original pixels)
     *                      3: 1/9 as many original pixels, etc
     * @param l = include legend pixels
     */
    private void GetAirTiles (Invalidatable inval, int s, boolean l)
    {
        if (!MakeSureWeHaveTileSize ()) return;

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
                    AirTile at = new AirTile (inval, w, h, s, l);
                    if (l || !at.isLegendOnly) tilez[ntilez++] = at;
                }
            }
            scaling = s;
            legends = l;
        }
    }

    /**
     * Get macro bitmap lat/lon step size limits.
     * @param limits = where to return min,max limits.
     */
    @Override  // DisplayableChart
    public void GetL2StepLimits (int[] limits)
    {
        // all we do is 16min x 16min blocks
        // log2(16) = 4, so return 4.
        limits[1] = limits[0] = 4;
    }

    /**
     * Create a bitmap that fits the given pixel mapping.
     * This should be called in a non-UI thread as it is synchronous.
     * @param slat = southern latitude
     * @param nlat = northern latitude
     * @param wlon = western longiture
     * @param elon = eastern longitude
     * @return corresponding bitmap
     */
    @Override  // DisplayableChart
    public Bitmap GetMacroBitmap (double slat, double nlat, double wlon, double elon)
    {
        return GetMacroBitmap (slat, nlat, wlon, elon, true);
    }

    public Bitmap GetMacroBitmap (double slat, double nlat, double wlon, double elon, boolean legends)
    {
        /*
         * Find range of canvas pixels that cover the requested lat/lon range.
         */
        PointD lastTlChartPix = new PointD ();
        PointD lastTrChartPix = new PointD ();
        PointD lastBlChartPix = new PointD ();
        PointD lastBrChartPix = new PointD ();
        LatLon2ChartPixelExact (nlat, wlon, lastTlChartPix);
        LatLon2ChartPixelExact (nlat, elon, lastTrChartPix);
        LatLon2ChartPixelExact (slat, wlon, lastBlChartPix);
        LatLon2ChartPixelExact (slat, elon, lastBrChartPix);

        leftMacroChartPix = (int) Math.round (Math.min (Math.min (lastTlChartPix.x, lastTrChartPix.x), Math.min (lastBlChartPix.x, lastBrChartPix.x)));
        riteMacroChartPix = (int) Math.round (Math.max (Math.max (lastTlChartPix.x, lastTrChartPix.x), Math.max (lastBlChartPix.x, lastBrChartPix.x)));
        topMacroChartPix  = (int) Math.round (Math.min (Math.min (lastTlChartPix.y, lastTrChartPix.y), Math.min (lastBlChartPix.y, lastBrChartPix.y)));
        botMacroChartPix  = (int) Math.round (Math.max (Math.max (lastTlChartPix.y, lastTrChartPix.y), Math.max (lastBlChartPix.y, lastBrChartPix.y)));

        /*
         * See if we already have a suitable .png file image.
         */
        StringBuilder sb = new StringBuilder ();
        sb.append (WairToNow.dbdir);
        sb.append ("/charts/");
        sb.append (spacenamewr.replace (' ', '_'));
        sb.append ("/3D/");
        sb.append (leftMacroChartPix);
        sb.append ('-');
        sb.append (riteMacroChartPix);
        sb.append ('/');
        sb.append (topMacroChartPix);
        sb.append ('-');
        sb.append (botMacroChartPix);
        if (!legends) sb.append ('@');
        sb.append (".png");
        String mbmPngName = sb.toString ();

        File mbmPngFile = new File (mbmPngName);
        if (mbmPngFile.exists ()) {
            Bitmap mbm = BitmapFactory.decodeFile (mbmPngName);
            if ((mbm != null) &&
                    (mbm.getWidth  () == EarthSector.MBMSIZE) &&
                    (mbm.getHeight () == EarthSector.MBMSIZE)) {
                return mbm;
            }
            Log.e (TAG, "bitmap corrupt " + mbmPngName);
            Lib.Ignored (mbmPngFile.delete ());
        }

        /*
         * Make sure we know the tile bitmap size.
         */
        if (!MakeSureWeHaveTileSize ()) return null;

        /*
         * Create an empty bitmap that we will map that range of canvas pixels to.
         */
        Bitmap mbm = Bitmap.createBitmap (EarthSector.MBMSIZE, EarthSector.MBMSIZE,
                legends ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas (mbm);

        // Conversion between chart pixels and macrobitmap pixels

        // leftMacroChartPix  <=>  0
        // riteMacroChartPix  <=>  mbmWidth
        //  topMacroChartPix  <=>  0
        //  botMacroChartPix  <=>  mbmHeight

        // (someChartXPix - leftMacroChartPix) / (riteMacroChartPix - leftMacroChartPix) = someMbmXPix / macroBitmapWidth
        // (someChartYPix - topMacroChartPix)  / (botMacroChartPix  - topMacroChartPix)  = someMbmYPix / macroBitmapHeight

        /*
         * Loop through all possible tiles that can go on that macrobitmap.
         */
        Rect dst = new Rect ();
        for (int chartYIdx = topMacroChartPix / hstep; chartYIdx <= botMacroChartPix / hstep; chartYIdx ++) {
            for (int chartXIdx = leftMacroChartPix / wstep; chartXIdx <= riteMacroChartPix / wstep; chartXIdx ++) {
                int leftTilePix = chartXIdx * wstep;
                int topTilePix  = chartYIdx * hstep;
                int riteTilePix = leftTilePix + hstep;
                int botTilePix  = topTilePix  + wstep;

                if ((leftTilePix >= 0) && (leftTilePix < chartwidth) &&
                        (topTilePix >= 0) && (topTilePix < chartheight)) {
                    dst.left   = (leftTilePix - leftMacroChartPix) * EarthSector.MBMSIZE / (riteMacroChartPix - leftMacroChartPix);
                    dst.top    = (topTilePix  - topMacroChartPix)  * EarthSector.MBMSIZE / (botMacroChartPix  - topMacroChartPix);
                    dst.right  = (riteTilePix - leftMacroChartPix) * EarthSector.MBMSIZE / (riteMacroChartPix - leftMacroChartPix);
                    dst.bottom = (botTilePix  - topMacroChartPix)  * EarthSector.MBMSIZE / (botMacroChartPix  - topMacroChartPix);

                    AirTile tile = new AirTile (null, leftTilePix, topTilePix, 1, legends);
                    Bitmap tbm = tile.LoadScaledBitmap ();
                    if (tbm != null) can.drawBitmap (tbm, null, dst, null);
                    tile.recycle ();
                }
            }
        }

        /*
         * Write to a .png file in case we need it again.
         */
        try {
            WritePngFile (mbmPngFile, mbm);
            Log.i (TAG, "wrote " + mbmPngName);
        } catch (IOException ioe) {
            Log.e (TAG, "error writing " + mbmPngName, ioe);
        }

        return mbm;
    }

    /**
     * Get what pixel is for a given lat/lon in the most recent GetMacroBitmap() call bitmap.
     * @param lat = latitude (degrees)
     * @param lon = longitude (degrees)
     * @param mbmpix = where to return the corresponding macro bitmap pixel number
     */
    @Override  // DisplayableChart
    public void LatLon2MacroBitmap (double lat, double lon, @NonNull PointD mbmpix)
    {
        LatLon2ChartPixelExact (lat, lon, mbmpix);
        mbmpix.x = (mbmpix.x - leftMacroChartPix) * EarthSector.MBMSIZE / (riteMacroChartPix - leftMacroChartPix);
        mbmpix.y = (mbmpix.y - topMacroChartPix)  * EarthSector.MBMSIZE / (botMacroChartPix  - topMacroChartPix);
    }

    /**
     * Compate mapping of the chart as a whole onto the canvas.
     * @param pmap = canvas mapping
     * @return 0: mapping invalid; else: tile scaling factor
     */
    private int ComputeChartMapping (PixelMapper pmap)
    {
        /*
         * Create transformation matrix that scales and rotates the unscaled chart
         * as a whole such that the given points match up.
         *
         *    [matrix] * [some_xy_in_chart] => [some_xy_in_canvas]
         */
        float[] points = drawOnCanvasPoints;
        PointD point = drawOnCanvasPoint;

        LatLon2ChartPixelExact (pmap.lastTlLat, pmap.lastTlLon, point);
        points[ 0] = (float) point.x;  points[ 8] = 0;
        points[ 1] = (float) point.y;  points[ 9] = 0;

        LatLon2ChartPixelExact (pmap.lastTrLat, pmap.lastTrLon, point);
        points[ 2] = (float) point.x;  points[10] = pmap.canvasWidth;
        points[ 3] = (float) point.y;  points[11] = 0;

        LatLon2ChartPixelExact (pmap.lastBlLat, pmap.lastBlLon, point);
        points[ 4] = (float) point.x;  points[12] = 0;
        points[ 5] = (float) point.y;  points[13] = pmap.canvasHeight;

        LatLon2ChartPixelExact (pmap.lastBrLat, pmap.lastBrLon, point);
        points[ 6] = (float) point.x;  points[14] = pmap.canvasWidth;
        points[ 7] = (float) point.y;  points[15] = pmap.canvasHeight;

        if (!drawOnCanvasChartMat.setPolyToPoly (points, 0, points, 8, 4)) {
            Log.e (TAG, "can't position chart");
            return 0;
        }
        if (!drawOnCanvasChartMat.invert (undrawOnCanvasChartMat)) {
            Log.e (TAG, "can't unposition chart");
            return 0;
        }

        /*
         * Get size of the canvas that we are drawing on.
         * Don't use clip bounds because we want all tiles for the canvas marked
         * as referenced so we don't keep loading and unloading the bitmaps.
         */
        canvasBounds.right  = pmap.canvasWidth;
        canvasBounds.bottom = pmap.canvasHeight;

        /*
         * When zoomed out, scaling < 1.0 and we want to undersample the bitmaps by that
         * much.  Eg, if scaling = 0.5, we want to sample half**2 the bitmap pixels and
         * scale the bitmap back up to twice its size.  But when zoomed in, ie, scaling
         * > 1.0, we don't want to oversample the bitmaps, just sample them normally, so
         * we don't run out of memory doing so.  Also, using a Math.ceil() on it seems to
         * use up a lot less memory than using arbitrary real numbers.
         */
        double ts = Math.hypot (points[6] - points[0], points[7] - points[1])
                / Math.hypot (pmap.canvasWidth, pmap.canvasHeight);
        return (int) Math.ceil (ts);
    }

    /**
     * Make sure we have the size of the unscaled tile bitmaps from server.
     */
    private boolean MakeSureWeHaveTileSize ()
    {
        // get dimensions of unscaled tile 0,0 and assume that all tiles are the same
        // (except that the ones on the right and bottom edges may be smaller)
        if ((wstep == 0) || (hstep == 0)) {
            AirTile at0 = new AirTile (null, 0, 0, 1, true);
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
                return false;
            }
        }
        return true;
    }

    /**
     * Map a chart tile to the canvas.
     * @param tile = tile to be mapped onto canvas
     * @return whether any part of it appears at all
     */
    private boolean MapTileToCanvas (AirTile tile)
    {
        Matrix tileMat = drawOnCanvasTileMat;
        float[] points = drawOnCanvasPoints;

        /*
         * Set up a matrix that maps an unscaled tile onto the canvas.
         * If tileScaling=1, the unscaled tile is 256x256 plus overlap
         * If tileScaling=2, the unscaled tile is 512x512 plus overlap
         * If tileScaling=3, the unscaled tile is 768X768 plus overlap
         * ...etc
         */
        tileMat.setTranslate (tile.leftPixel * scaling, tile.topPixel * scaling);
        // translate tile into position within whole unscaled chart
        tileMat.postConcat (drawOnCanvasChartMat);  // position tile onto canvas

        /*
         * Compute the canvas points corresponding to the four corners of an unscaled tile.
         */
        int tws = scaling * tile.width;
        int ths = scaling * tile.height;
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
        if (!canvasBounds.intersects (testLeft, testTop, testRite, testBot)) return false;
        if (scaling != 1) {
            tileMat.preScale ((float) scaling, (float) scaling);
        }
        return true;
    }

    /**
     * Write an internally generated bitmap to a .png file.
     * @param pngFile = where to write the file
     * @param bm = bitmap to be written
     * @throws IOException = error writing file
     */
    private void WritePngFile (File pngFile, Bitmap bm)
            throws IOException
    {
        // write .png file
        String pngName = pngFile.getPath ();
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
        private File pngFile;          // name of bitmap file
        private int scaling;           // scaling factor used to fetch bitmap with
        private Invalidatable inval;   // callback when tile gets loaded
        private String pngName;        // name of bitmap file

        public AirTile (Invalidatable inv, int lp, int tp, int sf, boolean lg)
        {
            inval     = inv;
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
            synchronized (airTileLoaderLock) {
                bm = bitmap;
                if ((bm == null) && !queued) {

                    // push this as first cuz there might be a lot of tiles already
                    // queued that we don't really need any more so queue this one
                    // to be next to be processed cuz this one is needed asap
                    AirTileLoader.tilesToLoad.addFirst (this);

                    // don't queue the same tile more than once
                    queued = true;

                    // wake the AirTileLoader thread
                    if (airTileLoaderThread == null) {
                        airTileLoaderThread = new AirTileLoader ();
                        airTileLoaderThread.start ();
                    }
                }
            }
            return bm;
        }

        /**
         * Called in AirTileThread to read bitmap into memorie.
         */
        public Bitmap LoadScaledBitmap ()
        {
            // see if all legend pixels and caller doesn't want legends
            if (!legends && isLegendOnly) return null;

            // we need some pixels from a bitmap file
            try {
                return ReadScaledBitmap (null);
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
        private Bitmap ReadScaledBitmap (BitmapFactory.Options bfo) throws IOException
        {
            // make image filename if we haven't already
            getPngName ();

            // see if we already have exact tile needed
            Bitmap bm = null;
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
                bm = Bitmap.createBitmap (pixels, width, height, Bitmap.Config.ARGB_8888);
                WritePngFile (pngFile, bm);

                // caller wants undersampling, throw this one away
                if (bfo != null) {
                    bm.recycle ();
                    bm = null;
                }
            }

            // read tile, possibly undersampling
            if (bm == null) bm = BitmapFactory.decodeFile (pngName, bfo);

            // draw hash if expired
            if (enddate < MaintView.deaddate) {
                int bmw = bm.getWidth ();
                int bmh = bm.getHeight ();
                if (!bm.isMutable ()) {
                    if ((expiredpixels == null) || (expiredpixels.length < bmw * bmh)) {
                        expiredpixels = new int[bmw*bmh];
                    }
                    bm.getPixels (expiredpixels, 0, bmw, 0, 0, bmw, bmh);
                    bm.recycle ();
                    bm = Bitmap.createBitmap (bmw, bmh, Bitmap.Config.ARGB_8888);
                    bm.setPixels (expiredpixels, 0, bmw, 0, 0, bmw, bmh);
                }
                Canvas bmc = new Canvas (bm);
                for (int i = -bmh; i < bmw; i += bmw / 4) {
                    bmc.drawLine (i, 0, bmh + i, bmh, expiredpaint);
                    bmc.drawLine (bmh + i, 0, i, bmh, expiredpaint);
                }
            }

            return bm;
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
            AirTile unclippedAirTile = new AirTile (inval, leftPixel, topPixel, 1, true);
            Bitmap bm = unclippedAirTile.ReadScaledBitmap (null);
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
         * Called in AirTileThread via LoadScaledBitmap() to read scaled bitmap into memorie.
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
                    int scaledpixelsoverfromscaledtileleft = (int) Math.round ((double) xstep * wstep / scaling);
                    int scaledpixelsdownfromscaledtiletop  = (int) Math.round ((double) ystep * hstep / scaling);

                    // read the unscaled sub-tile into memory, undersampling it to scale it down
                    AirTile unscaledTile = new AirTile (inval,
                            unscaledpixelsoverfromchartleft,
                            unscaledpixelsdownfromcharttop,
                            1, legends);
                    Bitmap bm = unscaledTile.ReadScaledBitmap (bfo);

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
            inval.postInvalidate ();
        }
    }

    /**
     * Load air tiles in a background thread to keep GUI responsive.
     */
    private static class AirTileLoader extends Thread {
        public final static LinkedList<AirTile> tilesToLoad = new LinkedList<> ();

        @Override
        public void run ()
        {
            setName ("AirTile loader");

            /*
             * Once started, we keep going forever.
             */
            while (true) {

                /*
                 * Get a tile to load.  If none, we are all done.
                 */
                AirTile tile;
                synchronized (airTileLoaderLock) {
                    if (tilesToLoad.isEmpty ()) {
                        SQLiteDBs.CloseAll ();
                        airTileLoaderThread = null;
                        break;
                    }
                    tile = tilesToLoad.removeFirst ();
                }

                /*
                 * Got something to do, try to load the tile in memory.
                 */
                Bitmap bm = tile.LoadScaledBitmap ();

                /*
                 * Mark that we are done trying to load the bitmap
                 * and trigger re-drawing the screen.
                 */
                synchronized (airTileLoaderLock) {
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
        begdate         = Integer.parseInt (values[2]);
        enddate         = Integer.parseInt (values[3]);
        spacenamewr     = values[4];                      // eg, "New York SEC 87"
        int i           = spacenamewr.lastIndexOf (' ');  // remove revision number from the end
        revision        = spacenamewr.substring (i + 1);
        spacenamenr     = spacenamewr.substring (0, i);

        /*
         * Most helicopter charts don't have a published expiration date,
         * so we get a zero for enddate.  Make it something non-zero so
         * we don't confuse it with an unloaded chart.
         */
        if (enddate == 0) enddate = MaintView.INDEFINITE;

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
                    double val = ParseLatLon (limPart.substring (0, limPart.length () - 1));
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
         * Normalize longitude limits to -180.0..+179.999999999.
         */
        chartedWestLon = Lib.NormalLon (chartedWestLon);
        chartedEastLon = Lib.NormalLon (chartedEastLon);

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
                        outline = new PointD[parts.length+1];
                        int j = 0;
                        for (String part : parts) {
                            String[] xystrs = part.split (",");
                            if (xystrs.length != 2) throw new Exception ("bad x,y " + part);
                            PointD p = new PointD ();
                            p.x = Double.parseDouble (xystrs[0].trim ());
                            p.y = Double.parseDouble (xystrs[1].trim ());
                            outline[j++] = p;
                        }
                        if (j == 2) {
                            chartedLeftPix = (int) Math.round (Math.min (outline[0].x, outline[1].x));
                            chartedRitePix = (int) Math.round (Math.max (outline[0].x, outline[1].x));
                            chartedTopPix  = (int) Math.round (Math.min (outline[0].y, outline[1].y));
                            chartedBotPix  = (int) Math.round (Math.max (outline[0].y, outline[1].y));
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
    private static double ParseLatLon (String str)
    {
        int i = str.indexOf ('^');
        if (i < 0) {
            return Double.parseDouble (str);
        }
        double deg = Double.parseDouble (str.substring (0, i));
        return deg + Double.parseDouble (str.substring (++ i)) / 60.0;
    }

    /**
     * See if the given lat,lon is within the chart area (not legend)
     */
    public boolean LatLonIsCharted (double lat, double lon)
    {
        /*
         * If lat/lon out of lat/lon limits, point is not in chart area
         */
        if (!TestLatLonIsCharted (lat, lon)) return false;

        /*
         * It is within lat/lon limits, check pixel limits too.
         */
        PointD pt = ptPerThread.get ();
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
        return TestLatLonIsCharted (ll.lat, ll.lon);
    }

    /**
     * Test given lat/lon against chart lat/lon limits.
     */
    private boolean TestLatLonIsCharted (double lat, double lon)
    {
        return Lib.LatOverlap (chartedSouthLat, chartedNorthLat, lat) &&
                Lib.LonOverlap (chartedWestLon, chartedEastLon, lon);
    }

    /**
     * Test given pixel against chart pixel limits.
     * Fortunately most charts have simple rectangle limits.
     */
    private boolean TestPixelIsCharted (double pixx, double pixy)
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
        private Lambert lcc;

        @Override  // AirChart
        protected String ParseParams (String csvLine)
        {
            /*
             * Set up LCC projection transform.
             */
            String[] values = csvLine.split (",", 15);
            double centerLat = Double.parseDouble (values[0]);
            double centerLon = Double.parseDouble (values[1]);
            double stanPar1  = Double.parseDouble (values[2]);
            double stanPar2  = Double.parseDouble (values[3]);
            double rada      = Double.parseDouble (values[6]);
            double radb      = Double.parseDouble (values[7]);
            double tfws[] = new double[] {
                Double.parseDouble (values[ 8]),
                Double.parseDouble (values[ 9]),
                Double.parseDouble (values[10]),
                Double.parseDouble (values[11]),
                Double.parseDouble (values[12]),
                Double.parseDouble (values[13])
            };
            lcc = new Lambert (centerLat, centerLon, stanPar1, stanPar2, rada, radb, tfws);

            /*
             * Return common parameter portion of csvLine.
             */
            return values[4] + ',' + values[5] + ',' + values[14];
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
        public boolean LatLon2ChartPixelExact (double lat, double lon, @NonNull PointD p)
        {
            lcc.LatLon2ChartPixelExact (lat, lon, p);
            return (p.x >= 0) && (p.x < chartwidth) && (p.y >= 0) && (p.y < chartheight);
        }

        /**
         * Given a pixel within the chart, compute corresponding lat/lon
         *
         * @param x  = pixel within the entire side of the sectional
         * @param y  = pixel within the entire side of the sectional
         * @param ll = where to return corresponding lat/lon
         */
        @Override  // AirChart
        protected void ChartPixel2LatLonExact (double x, double y, @NonNull LatLon ll)
        {
            lcc.ChartPixel2LatLonExact (x, y, ll);
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
     *  14 : effective date yyyymmdd
     *  15 : expiration date yyyymmdd
     *  16 : chart space name including revision number
     *
     * Example for ONC A-1 chart:
     *   psp:88,-36,80,24,4337,87,5737,199,1118,4690,8090,5279,9254,6693,19690603,99999999,ONC A-1 19690603
     *
     * The basic projection transformation is:
     *   beta = 90 - latitude
     *   pixels_from_north_pole = earth_diameter_in_pixels * tan (beta / 2)
     */
    private static class PSP extends AirChart {
        public final static String pfx = "psp:";

        private double earthDiameterPixels;
        private double tiltedCCWRadians;
        private int northPoleX, northPoleY;

        @Override  // AirChart
        protected String ParseParams (String csvLine)
        {
            String[] values = csvLine.substring (pfx.length ()).split (",", 13);
            // at latn = Double.parseDouble (values[ 0]);
            double lonw = Double.parseDouble (values[ 1]);
            double lats = Double.parseDouble (values[ 2]);
            // at lone = Double.parseDouble (values[ 3]);
            int    nwx = Integer.parseInt (values[ 4]);
            int    nwy = Integer.parseInt (values[ 5]);
            int    nex = Integer.parseInt (values[ 6]);
            int    ney = Integer.parseInt (values[ 7]);
            int    swx = Integer.parseInt (values[ 8]);
            int    swy = Integer.parseInt (values[ 9]);
            int    sex = Integer.parseInt (values[10]);
            int    sey = Integer.parseInt (values[11]);

            // find north pole pixel by intersecting west edge and east edge lines
            northPoleX = (int) Math.round (Lib.lineIntersectX (nwx, nwy, swx, swy, nex, ney, sex, sey));
            northPoleY = (int) Math.round (Lib.lineIntersectY (nwx, nwy, swx, swy, nex, ney, sex, sey));

            // find earth diameter in pixels by doing transform using south ring pixels from north pole
            double southRingRadius = Math.hypot (swx - northPoleX, swy - northPoleY);
            double beta = Math.toRadians (90.0 - lats);
            earthDiameterPixels = southRingRadius / Math.tan (beta / 2.0);

            // find counter-clockwise tilt from west edge pixel angle minus west edge longitude
            tiltedCCWRadians = Math.atan2 (swx - northPoleX, swy - northPoleY) - Math.toRadians (lonw);

            return values[12];
        }

        @Override  // AirChart
        public boolean LatLon2ChartPixelExact (double lat, double lon, @NonNull PointD p)
        {
            double beta = Math.toRadians (90.0 - lat);
            double pixelsFromNorthPole  = Math.tan (beta / 2.0) * earthDiameterPixels;
            double angleCCWFromVertical = Math.toRadians (lon) + tiltedCCWRadians;
            double x = pixelsFromNorthPole * Math.sin (angleCCWFromVertical) + northPoleX;
            double y = pixelsFromNorthPole * Math.cos (angleCCWFromVertical) + northPoleY;
            p.x = x;
            p.y = y;
            return (x >= 0) && (x < chartwidth) && (y >= 0) && (y < chartheight);
        }

        @Override  // AirChart
        protected void ChartPixel2LatLonExact (double x, double y, @NonNull LatLon ll)
        {
            double pixelsFromNorthPole = Math.hypot (x - northPoleX, y - northPoleY);
            double beta = Math.atan (pixelsFromNorthPole / earthDiameterPixels) * 2.0;
            ll.lat = 90.0 - Math.toDegrees (beta);

            double angleCCWFromVertical = Math.atan2 (x - northPoleX, y - northPoleY);
            ll.lon = Lib.NormalLon (Math.toDegrees (angleCCWFromVertical - tiltedCCWRadians));
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
     *   6 : effective date yyyymmdd
     *   7 : expiration date yyyymmdd
     *   8 : chart space name including revision number
     */
    private static class Box extends AirChart {
        public final static String pfx = "box:";

        private double latn, lonw, lats, lone;

        @Override  // AirChart
        protected String ParseParams (String csvLine)
        {
            String[] values = csvLine.substring (pfx.length ()).split (",", 5);
            latn = Double.parseDouble (values[0]);
            lonw = Double.parseDouble (values[1]);
            lats = Double.parseDouble (values[2]);
            lone = Double.parseDouble (values[3]);

            lonw = Lib.NormalLon (lonw);
            lone = Lib.NormalLon (lone);
            if (lone < lonw) lone += 360.0;

            return values[4];
        }

        @Override  // AirChart
        public boolean LatLon2ChartPixelExact (double lat, double lon, @NonNull PointD p)
        {
            while (lon < lonw - 180.0) lon += 360.0;
            while (lon > lonw + 180.0) lon -= 360.0;

            double x = (lon - lonw) / (lone - lonw) * chartwidth;
            double y = (latn - lat) / (latn - lats) * chartheight;
            p.x = x;
            p.y = y;
            return (x >= 0) && (x < chartwidth) && (y >= 0) && (y < chartheight);
        }

        @Override  // AirChart
        protected void ChartPixel2LatLonExact (double x, double y, @NonNull LatLon ll)
        {
            ll.lat = y * (lats - latn) / chartheight + latn;
            ll.lon = x * (lone - lonw) / chartwidth  + lonw;
        }
    }
}
