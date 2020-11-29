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

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.NonNull;
import android.support.v4.util.LongSparseArray;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Single instance contains all the street map tiles for the whole world.
 */
public class OpenStreetMap {
    private final static String TAG = "WairToNow";

    public  static final int BitmapSize = 256;  // all OSM tiles are this size
    public  static final int MAXZOOM = 17;
    private static final long TILE_FILE_AGE_MS = 1000L*60*60*24*365;
    private static final long TILE_RETRY_MS = 1000L*10;

    private double log2pixperin;
    private MainTileDrawer mainTileDrawer;
    private RunwayDownloadThread runwayDownloadThread;
    private WairToNow wairToNow;

    public OpenStreetMap (WairToNow wtn)
    {
        wairToNow = wtn;

        // using 100 pix per inch works out for font size 1/7th inch
        // so scale pix per inch from that
        double fontHeightInches = wairToNow.textSize / wairToNow.dotsPerInch;
        log2pixperin = Math.log (14.0 / fontHeightInches) / Math.log (2);

        StartPrefetchingRunwayTiles ();

        mainTileDrawer = new MainTileDrawer ();
    }

    /**
     * Draw tiles to canvas corresponding to given lat/lons.
     * @param canvas = canvas that draws to the view
     * @param pmap = maps canvas/view pixels to lat/lon
     * @param inval = what to call in an arbitrary thread when a tile gets loaded
     * @param copyrty = where to put copyright message
     */
    public void Draw (@NonNull Canvas canvas, @NonNull PixelMapper pmap, @NonNull DisplayableChart.Invalidatable inval, float copyrtx, float copyrty)
    {
        mainTileDrawer.Draw (canvas, pmap, inval, copyrtx, copyrty);
    }

    /**
     * Screen is being closed, close all open bitmaps.
     */
    public void CloseBitmaps ()
    {
        mainTileDrawer.CloseBitmaps ();
    }

    /**
     * This class actually draws tiles on a canvas.
     */
    private class MainTileDrawer extends TileDrawer {
        private Canvas canvas;
        private DisplayableChart.Invalidatable redrawView;
        private float[] bitmappts = new float[8];
        private Matrix matrix = new Matrix ();
        private Paint copyrtBGPaint = new Paint ();
        private Paint copyrtTxPaint = new Paint ();
        private Path canvasclip = new Path ();

        private final LongSparseArray<TileBitmap> openedBitmaps = new LongSparseArray<> ();
        private final LongSparseArray<TileBitmap> neededBitmaps = new LongSparseArray<> ();
        private TileOpenerThread tileOpenerThread;

        private final LongSparseArray<DisplayableChart.Invalidatable> downloadBitmaps = new LongSparseArray<> ();
        private TileDownloaderThread tileDownloaderThread;

        public MainTileDrawer ()
        {
            copyrtBGPaint.setColor (Color.WHITE);
            copyrtBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
            copyrtBGPaint.setTextSize (wairToNow.textSize * 3 / 4);
            copyrtBGPaint.setStrokeWidth (wairToNow.thickLine);
            copyrtBGPaint.setTextAlign (Paint.Align.RIGHT);
            copyrtTxPaint.setColor (Color.BLACK);
            copyrtTxPaint.setTextSize (wairToNow.textSize * 3 / 4);
            copyrtTxPaint.setTextAlign (Paint.Align.RIGHT);
        }

        public void Draw (@NonNull Canvas canvas, @NonNull PixelMapper pmap, @NonNull DisplayableChart.Invalidatable inval, float copyrtx, float copyrty)
        {
            redrawView = inval;
            this.canvas = canvas;

            stopReadingTiles (false);

            synchronized (openedBitmaps) {
                for (int i = openedBitmaps.size (); -- i >= 0;) {
                    TileBitmap tbm = openedBitmaps.valueAt (i);
                    tbm.used = false;
                }
            }

            if (DrawTiles (wairToNow, pmap)) {
                String copyrtMessage = "[" + zoom + "]  Copyright OpenStreetMap contributors";
                canvas.drawText (copyrtMessage, copyrtx - 5, copyrty - 5, copyrtBGPaint);
                canvas.drawText (copyrtMessage, copyrtx - 5, copyrty - 5, copyrtTxPaint);
            }

            synchronized (openedBitmaps) {
                for (int i = openedBitmaps.size (); -- i >= 0;) {
                    TileBitmap tbm = openedBitmaps.valueAt (i);
                    if (! tbm.used) {
                        openedBitmaps.removeAt (i);
                        if (tbm.bm != null) tbm.bm.recycle ();
                    }
                }
            }
        }

        @Override  // TileDrawer
        public boolean DrawTile ()
        {
            /*
             * Try to draw the bitmap or start downloading it if we don't have it.
             * Meanwhile, try to draw zoomed out tile if we have one (but don't download them).
             */
            if (TryToDrawTile (canvas, zoom, tileX, tileY, true)) return true;
            int tileXOut = tileX;
            int tileYOut = tileY;
            for (int zoomOut = zoom; -- zoomOut >= 0;) {
                tileXOut /= 2;
                tileYOut /= 2;
                if (TryToDrawTile (canvas, zoomOut, tileXOut, tileYOut, false)) return true;
            }
            return false;
        }

        /**
         * Try to draw a single tile to the canvas, rotated, scaled and translated in place.
         * @param canvas = canvas to draw it on
         * @param zoomOut = zoom level to try to draw
         * @param tileXOut = which tile at that zoom level to draw
         * @param tileYOut = which tile at that zoom level to draw
         * @param startDownload = true: start downloading if not on flash; false: don't bother
         * @return true: tile drawn; false: not drawn
         */
        private boolean TryToDrawTile (Canvas canvas, int zoomOut, int tileXOut, int tileYOut, boolean startDownload)
        {
            TileBitmap tbm;
            long key = ((long) tileXOut << 36) | ((long) tileYOut << 8) | zoomOut;
            synchronized (openedBitmaps) {

                // see if we have the exact tile requested already opened and ready to display
                tbm = openedBitmaps.get (key);
                if (tbm == null) {

                    // if not, request only if it is the most zoomed-in level
                    // the TileOpenerThread will open an outer-zoom level if the zoomed-in one is not downloaded
                    if (startDownload) {
                        tbm = new TileBitmap ();
                        tbm.inval = redrawView;
                        neededBitmaps.put (key, tbm);
                        if (tileOpenerThread == null) {
                            tileOpenerThread = new TileOpenerThread ();
                            tileOpenerThread.start ();
                        }
                    }
                    return false;
                }
            }

            // it is opened, remember it is being used so it doesn't get recycled
            // also it might be null meaning the bitmap file is corrupt
            tbm.used = true;
            Bitmap tile = tbm.bm;
            if (tile == null) return false;

            /*
             * We might be using a zoomed-out tile while correct one downloads.
             * So adjust which pixels in the zoomed-out tile have what we want.
             */
            int outed    = zoom - zoomOut;
            int ww       = BitmapSize >> outed;
            int hh       = BitmapSize >> outed;
            int leftBmp  = (tileX * ww) % BitmapSize;
            int topBmp   = (tileY * hh) % BitmapSize;
            int riteBmp  = leftBmp + ww;
            int botBmp   = topBmp  + hh;
            bitmappts[0] = leftBmp;
            bitmappts[1] = topBmp;
            bitmappts[2] = riteBmp;
            bitmappts[3] = topBmp;
            bitmappts[4] = riteBmp;
            bitmappts[5] = botBmp;
            bitmappts[6] = leftBmp;
            bitmappts[7] = botBmp;

            /*
             * If zoomed out, make a clip so we only draw what is needed on the canvas so we don't overdraw zoomed-in tiles..
             * If not zoomed out, we can draw the whole bitmap as it just fits the canvas spot.
             */
            boolean saved = false;
            try {
                if (outed > 0) {
                    canvasclip.rewind ();
                    canvasclip.moveTo ((float) northwestcanpix.x, (float) northwestcanpix.y);
                    canvasclip.lineTo ((float) northeastcanpix.x, (float) northeastcanpix.y);
                    canvasclip.lineTo ((float) southeastcanpix.x, (float) southeastcanpix.y);
                    canvasclip.lineTo ((float) southwestcanpix.x, (float) southwestcanpix.y);
                    canvasclip.lineTo ((float) northwestcanpix.x, (float) northwestcanpix.y);
                    canvasclip.close ();

                    canvas.save ();
                    saved = true;
                    try {
                        canvas.clipPath (canvasclip);
                    } catch (UnsupportedOperationException uoe) {
                        Log.e (TAG, "error setting osm tile clip", uoe);
                        Log.e (TAG, "  northwestcanpix=" + northwestcanpix);
                        Log.e (TAG, "  northeastcanpix=" + northeastcanpix);
                        Log.e (TAG, "  southwestcanpix=" + southwestcanpix);
                        Log.e (TAG, "  southwestcanpix=" + southwestcanpix);
                        return false;
                    }
                }

                /*
                 * Draw the bitmap to the canvas using that transformation.
                 */
                if (!matrix.setPolyToPoly (bitmappts, 0, canvaspts, 0, 4)) {
                    return false;  // maybe zoomed in too far so ww or hh is zero
                }
                canvas.drawBitmap (tile, matrix, null);
            } finally {
                if (saved) canvas.restore ();
            }

            return true;
        }

        public void CloseBitmaps ()
        {
            redrawView = null;
            stopReadingTiles (true);
            synchronized (openedBitmaps) {
                for (int i = openedBitmaps.size (); -- i >= 0;) {
                    TileBitmap tbm = openedBitmaps.valueAt (i);
                    if (tbm.bm != null) tbm.bm.recycle ();
                }
                openedBitmaps.clear ();
            }
        }

        private void stopReadingTiles (boolean wait)
        {
            Thread t;
            synchronized (openedBitmaps) {
                neededBitmaps.clear ();
                t = tileOpenerThread;
            }
            if (wait && (t != null)) {
                try { t.join (); } catch (InterruptedException ignored) { }
            }
            synchronized (downloadBitmaps) {
                downloadBitmaps.clear ();
                t = tileDownloaderThread;
            }
            if (wait && (t != null)) {
                try { t.join (); } catch (InterruptedException ignored) { }
            }
        }

        /**
         * Get tiles to open from neededBitmaps and put in openedBitmaps.
         * If any need downloading from the server, put them in downloadBitmaps.
         */
        private class TileOpenerThread extends Thread {
            @Override
            public void run ()
            {
                setName ("OpenStreetMap tile opener");

                long key = 0;
                TileBitmap tbm = null;
                while (true) {

                    // queue previously opened bitmap into openedBitmaps
                    // ...and dequeued needed bitmap from neededBitmaps
                    // if nothing to dequeue, terminate thread
                    synchronized (openedBitmaps) {
                        if (tbm != null) {
                            openedBitmaps.put (key, tbm);
                            tbm.inval.postInvalidate ();
                        }
                        do {
                            if (neededBitmaps.size () == 0) {
                                tileOpenerThread = null;
                                return;
                            }
                            key = neededBitmaps.keyAt (0);
                            tbm = neededBitmaps.valueAt (0);
                            neededBitmaps.removeAt (0);
                        } while (openedBitmaps.indexOfKey (key) >= 0);
                    }

                    // open the requested tile or one at an outer zoom level
                    // do not request any tile be downloaded from server yet
                    int tileIX = (int) (key >> 36) & 0x0FFFFFFF;
                    int tileIY = (int) (key >>  8) & 0x0FFFFFFF;
                    int zoomLevel = (int) key & 0xFF;
                    int zl;
                    for (zl = zoomLevel; zl >= 0; -- zl) {
                        tbm.bm = ReadTileBitmap (tileIX, tileIY, zl, false);
                        if (tbm.bm != null) break;
                        tileIX /= 2;
                        tileIY /= 2;
                    }

                    // if an outer tile was found, ie, the inner tile not found on flash,
                    // request that it be downloaded from server
                    if (zl < zoomLevel) {
                        synchronized (downloadBitmaps) {
                            downloadBitmaps.put (key, tbm.inval);
                            if (tileDownloaderThread == null) {
                                tileDownloaderThread = new TileDownloaderThread ();
                                tileDownloaderThread.start ();
                            }
                        }
                    }

                    // mark the possibly zoomed-out tile as now being opened
                    // and prevent it from being recycled right away
                    if (zl < 0) {
                        tbm = null;
                    } else {
                        key = (((long) tileIX) << 36) | (((long) tileIY) << 8) | zl;
                        tbm.used = true;
                    }
                }
            }
        }

        private class TileDownloaderThread extends Thread {
            @Override
            public void run ()
            {
                setName ("OpenStreetMap tile downloader");
                while (true) {
                    long key;
                    DisplayableChart.Invalidatable inval;
                    synchronized (downloadBitmaps) {
                        if (downloadBitmaps.size () == 0) {
                            tileDownloaderThread = null;
                            return;
                        }
                        key = downloadBitmaps.keyAt (0);
                        inval = downloadBitmaps.valueAt (0);
                        downloadBitmaps.removeAt (0);
                    }

                    int tileIX = (int) (key >> 36) & 0x0FFFFFFF;
                    int tileIY = (int) (key >>  8) & 0x0FFFFFFF;
                    int zoomLevel = (int) key & 0xFF;
                    DownloadTileBitmap (tileIX, tileIY, zoomLevel, true);
                    inval.postInvalidate ();
                }
            }
        }
    }

    private static class TileBitmap {
        public DisplayableChart.Invalidatable inval;  // callback when tile gets loaded
        public Bitmap bm;                             // bitmap (or null if not on flash or corrupt)
        public boolean used;                          // was used this cycle
    }

    /**
     * This class simply scans the tiles needed to draw to a canvas.
     * It does a callback to DrawTile() for each tile needed.
     */
    private abstract class TileDrawer {
        protected float[] canvaspts = new float[8];
        protected int tileX, tileY, zoom;
        protected PointD northwestcanpix = new PointD ();
        protected PointD northeastcanpix = new PointD ();
        protected PointD southwestcanpix = new PointD ();
        protected PointD southeastcanpix = new PointD ();

        // draw tile zoom/tileX/tileY.png
        public abstract boolean DrawTile ();

        /**
         * Draw OpenStreetMap tiles that fill the given pmap area.
         * Calls DrawTile() for each tile needed.
         * @param wairToNow = our activity
         * @param pmap = what pixels to draw to and the corresponding lat/lon mapping
         */
        public boolean DrawTiles (WairToNow wairToNow, PixelMapper pmap)
        {
            boolean gotatile = false;

            /*
             * Get drawing area size in pixels.
             */
            int w = pmap.canvasWidth;
            int h = pmap.canvasHeight;

            /*
             * Calculate zoom level.
             * We find lowest zoom level that makes each tile pixel
             * within a certain size range on physical screen.
             */

            // get canvas area in square inches
            double canvasPixPerSqIn = wairToNow.dotsPerInchX * wairToNow.dotsPerInchY;
            double canvasAreaSqIn   = (double) w * (double) h / canvasPixPerSqIn;

            // see how many zoom=MAXZOOM canvas pixels would be displayed on canvas
            zoom = MAXZOOM;
            double tlTileX = lon2TileX (pmap.lastTlLon);
            double tlTileY = lat2TileY (pmap.lastTlLat);
            double trTileX = lon2TileX (pmap.lastTrLon);
            double trTileY = lat2TileY (pmap.lastTrLat);
            double blTileX = lon2TileX (pmap.lastBlLon);
            double blTileY = lat2TileY (pmap.lastBlLat);
            double brTileX = lon2TileX (pmap.lastBrLon);
            double brTileY = lat2TileY (pmap.lastBrLat);
            double topEdge = Math.hypot (tlTileX - trTileX, tlTileY - trTileY);
            double botEdge = Math.hypot (blTileX - brTileX, blTileY - brTileY);
            double leftEdg = Math.hypot (tlTileX - blTileX, tlTileY - blTileY);
            double riteEdg = Math.hypot (trTileX - brTileX, trTileY - brTileY);
            double horEdge = (topEdge + botEdge) / 2.0;
            double verEdge = (leftEdg + riteEdg) / 2.0;
            double canvasAreaTilePixels = horEdge * verEdge * BitmapSize * BitmapSize;

            // see how many zoom=MAXZOOM tile pixels per canvas square inch
            double tilePixelsPerCanvasSqIn = canvasAreaTilePixels / canvasAreaSqIn;

            // each zoom level has 4 times the pixels as the lower level

            double log4TilePixelsPerCanvasSqIn = Math.log (tilePixelsPerCanvasSqIn) / Math.log (4);

            // tiles look good at 128 pixels per inch, so LOG2PIXPERIN = 7
            // assuming MAXZOOM = 17:
            // tilePixelsPerCanvasSqIn@MAXZOOM  log4TilePixelsPerCanvasSqIn@MAXZOOM  zoom to get 128x128 pix per sq in
            //     16M                                  12                               12 = MAXZOOM-5
            //      4M                                  11                               13 = MAXZOOM-4
            //      1M                                  10                               14 = MAXZOOM-3
            //    256K                                   9                               15 = MAXZOOM-2
            //     64K                                   8                               16 = MAXZOOM-1
            //     16K                                   7                               17 = MAXZOOM-0

            zoom = MAXZOOM - (int) Math.round (log4TilePixelsPerCanvasSqIn - log2pixperin);
            if (zoom < 0) zoom = 0;
            if (zoom > MAXZOOM) zoom = MAXZOOM;

            /*
             * See what range of tile numbers are needed to cover the canvas.
             */
            int maxTileY = (int) lat2TileY (pmap.canvasSouthLat);
            int minTileY = (int) lat2TileY (pmap.canvasNorthLat);
            int minTileX = (int) lon2TileX (pmap.canvasWestLon);
            int maxTileX = (int) lon2TileX (pmap.canvasEastLon);

            /*
             * Loop through all the possible tiles to cover the canvas.
             */
            for (tileY = minTileY; tileY <= maxTileY; tileY ++) {
                double northlat = tileY2Lat (tileY);
                double southlat = tileY2Lat (tileY + 1);
                for (int rawTileX = minTileX; rawTileX <= maxTileX; rawTileX ++) {
                    double westlon = tileX2Lon (rawTileX);
                    double eastlon = tileX2Lon (rawTileX + 1);
                    tileX = rawTileX & ((1 << zoom) - 1);

                    /*
                     * Get rectangle outlining where the entire bitmap goes on canvas.
                     * It's quite possible that some of the bitmap is off the canvas.
                     * It's also quite possible that it is flipped around on the canvas.
                     */
                    pmap.LatLon2CanPixAprox (northlat, westlon, northwestcanpix);
                    pmap.LatLon2CanPixAprox (northlat, eastlon, northeastcanpix);
                    pmap.LatLon2CanPixAprox (southlat, westlon, southwestcanpix);
                    pmap.LatLon2CanPixAprox (southlat, eastlon, southeastcanpix);

                    /*
                     * If tile completely off the canvas, don't bother with it.
                     */
                    if ((northwestcanpix.x < 0) && (northeastcanpix.x < 0) &&
                            (southwestcanpix.x < 0) && (southeastcanpix.x < 0)) continue;
                    if ((northwestcanpix.y < 0) && (northeastcanpix.y < 0) &&
                            (southwestcanpix.y < 0) && (southeastcanpix.y < 0)) continue;
                    if ((northwestcanpix.x > w) && (northeastcanpix.x > w) &&
                            (southwestcanpix.x > w) && (southeastcanpix.x > w)) continue;
                    if ((northwestcanpix.y > h) && (northeastcanpix.y > h) &&
                            (southwestcanpix.y > h) && (southeastcanpix.y > h)) continue;

                    /*
                     * At least some part of tile is on canvas, draw it.
                     */
                    canvaspts[0] = (float) northwestcanpix.x;
                    canvaspts[1] = (float) northwestcanpix.y;
                    canvaspts[2] = (float) northeastcanpix.x;
                    canvaspts[3] = (float) northeastcanpix.y;
                    canvaspts[4] = (float) southeastcanpix.x;
                    canvaspts[5] = (float) southeastcanpix.y;
                    canvaspts[6] = (float) southwestcanpix.x;
                    canvaspts[7] = (float) southwestcanpix.y;
                    gotatile |= DrawTile ();
                }
            }
            return gotatile;
        }

        /**
         * Convert lat,lon to x,y tile numbers
         * http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
         */
        private double lon2TileX (double lon)
        {
            double n = 1 << zoom;
            return n * (lon + 180.0) / 360.0;
        }
        private double lat2TileY (double lat)
        {
            double n = 1 << zoom;
            double latrad = lat / 180.0 * Math.PI;
            return n * (1.0 - (Math.log (Math.tan (latrad) + 1.0 / Math.cos (latrad)) / Math.PI)) / 2.0;
        }
        private double tileX2Lon (int xTile)
        {
            double n = 1 << zoom;
            return xTile * 360.0 / n - 180.0;
        }
        private double tileY2Lat (int yTile)
        {
            double n = 1 << zoom;
            return Math.toDegrees (Math.atan (Math.sinh (Math.PI * (1.0 - 2.0 * yTile / n))));
        }
    }

    /**
     * The runway diagrams use our tiles for backing,
     * so prefetch them as the user probably doesn't
     * have internet access when they are needed.
     * See PlateView.RWYPlateImage.
     */
    public void StartPrefetchingRunwayTiles ()
    {
        synchronized (runwayDownloadThreadLock) {
            if (runwayDownloadThread == null) {
                runwayDownloadThread = new RunwayDownloadThread ();
                runwayDownloadThread.start ();
            }
        }
    }

    private final Object runwayDownloadThreadLock = new Object ();
    private final static String[] rpcolumns = new String[] { "rp_faaid", "rp_lastry" };
    private class RunwayDownloadThread extends Thread {
        private HashSet<String> bulkDownloads = new HashSet<> ();

        private RWYPrefecthTileDrawer rwyPrefecthTileDrawer = new RWYPrefecthTileDrawer ();

        @Override  // Thread
        public void run ()
        {
            setName ("OpenStreetMap runway prefetch");

            // crude but effective during startup
            while (wairToNow.maintView == null) {
                try { Thread.sleep (100); } catch (InterruptedException ignored) { }
            }

            try {

                /*
                 * Download needed tile files one-by-one.
                 */
                while (true) {

                    /*
                     * Prefetch some runway diagram tiles.
                     */
                    int platesexpdate = MaintView.GetLatestPlatesExpDate ();
                    String dbname = "nobudb/plates_" + platesexpdate + ".db";
                    try {

                        // get a few airports that haven't been processed yet
                        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                        Cursor result;
                        synchronized (runwayDownloadThreadLock) {
                            if ((sqldb == null) || !sqldb.tableExists ("rwypreloads")) {
                                runwayDownloadThread = null;
                                break;
                            }
                            result = sqldb.query (
                                    "rwypreloads", rpcolumns,
                                    null, null, null, null,
                                    "rp_lastry", "25");
                            if (!result.moveToFirst ()) {
                                result.close ();
                                runwayDownloadThread = null;
                                break;
                            }
                        }
                        try {
                            do {
                                rwyPrefecthTileDrawer.successful = true;

                                String faaid = result.getString (0);

                                // get airport information
                                Waypoint.Airport apt = Waypoint.Airport.GetByFaaID (faaid, wairToNow);
                                if (apt != null) {
                                    Log.i (TAG, "preloading runway diagram background for " + apt.ident);
                                    if (wairToNow.maintView != null) {
                                        wairToNow.maintView.downloadingRunwayDiagram = apt;
                                        wairToNow.maintView.UpdateRunwayDiagramDownloadStatus ();
                                    }

                                    // get mapping of the runway to the default canvas size
                                    PixelMapper pmapland = RWYPlateImage.GetRWYPlateImageDefaultOSMMapping (apt, wairToNow, true);
                                    PixelMapper pmapport = RWYPlateImage.GetRWYPlateImageDefaultOSMMapping (apt, wairToNow, false);

                                    // gather the tiles needed into a bulk download request
                                    bulkDownloads.clear ();
                                    rwyPrefecthTileDrawer.DrawTiles (wairToNow, pmapland);
                                    rwyPrefecthTileDrawer.DrawTiles (wairToNow, pmapport);

                                    // if successfully downloaded all the tiles, mark that airport as complete
                                    if (FetchTiles (apt.ident)) {
                                        sqldb.execSQL ("DELETE FROM rwypreloads WHERE rp_faaid='" + faaid + "'");
                                    } else {
                                        long now = System.currentTimeMillis ();
                                        sqldb.execSQL ("UPDATE rwypreloads SET rp_lastry=" + now + " WHERE rp_faaid='" + faaid + "'");
                                    }
                                }

                                // try to get another airport's runway tiles
                            } while (result.moveToNext ());
                        } finally {
                            result.close ();
                            if (wairToNow.maintView != null) {
                                wairToNow.maintView.downloadingRunwayDiagram = null;
                                wairToNow.maintView.UpdateRunwayDiagramDownloadStatus ();
                            }
                        }
                    } catch (Exception e) {
                        Log.e (TAG, "error reading " + dbname, e);
                    }
                }
            } finally {
                SQLiteDBs.CloseAll ();
            }
        }

        /**
         * Called for each tile needed for runway diagram prefetching.
         * It attempts to download the file if we don't already have it.
         */
        private class RWYPrefecthTileDrawer extends TileDrawer {
            public boolean successful;

            @Override
            public boolean DrawTile ()
            {
                String tilename = zoom + "/" + tileX + "/" + tileY + ".png";
                bulkDownloads.add (tilename);
                return false;
            }
        }

        /**
         * Try to fetch the bulkDownload files.
         */
        private boolean FetchTiles (String ident)
        {
            StringBuilder q = new StringBuilder ();
            int n = 0;
            for (Iterator<String> it = bulkDownloads.iterator (); it.hasNext ();) {
                String tilename = it.next ();
                String pathname = WairToNow.dbdir + "/streets/" + tilename;
                File pathfile = new File (pathname);
                long lastmod = pathfile.lastModified ();
                long now = System.currentTimeMillis ();
                if (now - lastmod < TILE_FILE_AGE_MS) {

                    // we already have a recent copy of that file
                    it.remove ();
                } else {

                    // last download was successful or we are retrying,
                    // save the tilename to be downloaded
                    if (n > 0) q.append ('&');
                    q.append ("tile_");
                    q.append (++ n);
                    q.append ('=');
                    q.append (tilename);
                }
            }

            if (n > 0) {
                String tilename = null;
                try {
                    URL url = new URL (MaintView.dldir + "/streets.php/" + ident);
                    HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
                    try {
                        byte[] qbytes = q.toString ().getBytes ();
                        httpCon.setRequestMethod ("POST");
                        httpCon.setDoOutput (true);
                        httpCon.setFixedLengthStreamingMode (qbytes.length);
                        OutputStream os = httpCon.getOutputStream ();
                        os.write (qbytes);
                        os.flush ();

                        int rc = httpCon.getResponseCode ();
                        if (rc != HttpURLConnection.HTTP_OK) {
                            throw new IOException ("http response code " + rc + " on bulk download");
                        }

                        BufferedInputStream is = new BufferedInputStream (httpCon.getInputStream (), 32768);
                        while (true) {
                            String tiletag  = Lib.ReadStreamLine (is);
                            if (tiletag.equals ("@@done")) break;
                            String sizetag  = Lib.ReadStreamLine (is);
                            if (!tiletag.startsWith ("@@tile=")) throw new IOException ("bad download tile tag " + tiletag);
                            if (!sizetag.startsWith ("@@size=")) throw new IOException ("bad download size tag " + sizetag);
                            tilename = tiletag.substring (7);
                            String permname = WairToNow.dbdir + "/streets/" + tilename;
                            String tempname = permname + ".tmp";
                            int bytelen;
                            try {
                                bytelen = Integer.parseInt (sizetag.substring (7));
                            } catch (NumberFormatException nfe) {
                                throw new IOException ("bad download size tag " + sizetag);
                            }
                            Lib.WriteStreamToFile (is, bytelen, tempname);
                            String eoftag = Lib.ReadStreamLine (is);
                            if (!eoftag.equals ("@@eof")) throw new IOException ("bad end-of-file tag " + eoftag);
                            Lib.RenameFile (tempname, permname);
                            bulkDownloads.remove (tilename);
                            tilename = null;
                        }
                    } finally {
                        httpCon.disconnect ();
                    }
                } catch (Exception e) {
                    Log.e (TAG, "error downloading osm tiles" + ((tilename == null) ? "" : (" (" + tilename + ")")), e);
                    try { Thread.sleep (TILE_RETRY_MS); } catch (InterruptedException ie) { Lib.Ignored (); }
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Synchronously read a tile's bitmap file.
     * Maybe download from server if we don't have it on flash.
     */
    public static Bitmap ReadTileBitmap (int tileIX, int tileIY, int zoomLevel, boolean download)
    {
        String permname = DownloadTileBitmap (tileIX, tileIY, zoomLevel, download);
        if (permname == null) return null;
        try {

            /*
             * Read flash file into memorie.
             */
            Bitmap bm = BitmapFactory.decodeFile (permname);
            if (bm == null) throw new IOException ("bitmap corrupt");
            if ((bm.getWidth () != BitmapSize) || (bm.getHeight () != BitmapSize)) {
                throw new IOException ("bitmap bad size " + bm.getWidth () + "," + bm.getHeight ());
            }
            return bm;
        } catch (Exception e) {
            Log.e (TAG, "error reading tile: " + permname, e);
            Lib.Ignored (new File (permname).delete ());
            return null;
        }
    }

    /**
     * Synchronously download a tile's bitmap file from server onto flash.
     * @param tileIX = x coord left edge of tile 0..(1<<zoomLevel)-1
     * @param tileIY = y coord top edge of tile 0..(1<<zoomLevel)-1
     * @param zoomLevel = tile zoom level
     * @param download = false: return null if not on flash; true: download if not on flash
     * @return null if not on flash; else: name of flash file
     */
    public static String DownloadTileBitmap (int tileIX, int tileIY, int zoomLevel, boolean download)
    {
        String tilename = zoomLevel + "/" + tileIX + "/" + tileIY + ".png";
        String permname = WairToNow.dbdir + "/streets/" + tilename;
        String tempname = permname + ".tmp";
        File permfile = new File (permname);
        try {

            /*
             * See if flash file exists.
             */
            if (!permfile.exists ()) {
                if (!download) return null;

                /*
                 * If not, open connection to the server to fetch it.
                 */
                URL url = new URL (MaintView.dldir + "/streets.php?tile=" + tilename);
                HttpURLConnection httpCon = (HttpURLConnection)url.openConnection ();
                try {

                    /*
                     * Check HTTP status and open reading stream.
                     */
                    httpCon.setRequestMethod ("GET");
                    int rc = httpCon.getResponseCode ();
                    if (rc != HttpURLConnection.HTTP_OK) {
                        throw new IOException ("http response code " + rc);
                    }
                    InputStream is = httpCon.getInputStream ();
                    try {

                        /*
                         * Read stream into temp file.
                         */
                        File permparent = permfile.getParentFile ();
                        assert permparent != null;
                        Lib.Ignored (permparent.mkdirs ());
                        OutputStream os = new FileOutputStream (tempname);
                        try {
                            byte[] buff = new byte[4096];
                            while (true) {
                                rc = is.read (buff);
                                if (rc <= 0) break;
                                os.write (buff, 0, rc);
                            }
                        } finally {
                            os.close ();
                        }
                    } finally {
                        is.close ();
                    }
                } finally {
                    httpCon.disconnect ();
                }

                /*
                 * Successfully wrote complete temp file,
                 * rename temp file to permanant file.
                 */
                Lib.RenameFile (tempname, permname);
            }
            return permname;
        } catch (Exception e) {
            Log.e (TAG, "error downloading osm tile: " + tilename, e);
            Lib.Ignored (permfile.delete ());
            try { Thread.sleep (TILE_RETRY_MS); } catch (InterruptedException ie) { Lib.Ignored (); }
            return null;
        }
    }
}
