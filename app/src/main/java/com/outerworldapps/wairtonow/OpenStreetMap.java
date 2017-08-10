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
    public  static final int MAXZOOM = 16;      // openstreepmap.org doesn't like serving zoom 17
    private static final double MAXPIXPERSQIN = 14400;  // max tile pixels per square inch
    private static final long TILE_FILE_AGE_MS = 1000L*60*60*24*365;
    private static final long TILE_RETRY_MS = 1000L*10;

    private RunwayDownloadThread runwayDownloadThread;
    private MainTileDrawer mainTileDrawer;
    private WairToNow wairToNow;

    public OpenStreetMap (WairToNow wtn)
    {
        wairToNow = wtn;

        StartPrefetchingRunwayTiles ();

        mainTileDrawer = new MainTileDrawer ();
    }

    /**
     * Draw tiles to canvas corresponding to given lat/lons.
     * @param canvas = canvas that draws to the view
     * @param pmap = maps canvas/view pixels to lat/lon
     * @param inval = what to call in an arbitrary thread when a tile gets loaded
     */
    public void Draw (@NonNull Canvas canvas, @NonNull PixelMapper pmap, @NonNull DisplayableChart.Invalidatable inval)
    {
        mainTileDrawer.Draw (canvas, pmap, inval);
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
        private long drawCycle;
        private Matrix matrix = new Matrix ();
        private Paint copyrtBGPaint = new Paint ();
        private Paint copyrtTxPaint = new Paint ();
        private Path canvasclip = new Path ();

        private final LongSparseArray<TileBitmap> loadedBitmaps = new LongSparseArray<> ();
        private final LongSparseArray<TileBitmap> neededBitmaps = new LongSparseArray<> ();
        private TileLoaderThread tileLoaderThread;

        public MainTileDrawer ()
        {
            copyrtBGPaint.setColor (Color.WHITE);
            copyrtBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
            copyrtBGPaint.setTextSize (wairToNow.textSize * 3 / 4);
            copyrtBGPaint.setStrokeWidth (wairToNow.thickLine);
            copyrtTxPaint.setColor (Color.BLACK);
            copyrtTxPaint.setTextSize (wairToNow.textSize * 3 / 4);
        }

        public void Draw (@NonNull Canvas canvas, @NonNull PixelMapper pmap, @NonNull DisplayableChart.Invalidatable inval)
        {
            redrawView = inval;
            this.canvas = canvas;

            stopReadingTiles (false);

            ++ drawCycle;
            if (DrawTiles (wairToNow, pmap)) {
                int h = pmap.canvasHeight;
                String copyrtMessage = "Copyright OpenStreetMap contributors";
                canvas.drawText (copyrtMessage, 5, h - 5, copyrtBGPaint);
                canvas.drawText (copyrtMessage, 5, h - 5, copyrtTxPaint);
            }

            synchronized (loadedBitmaps) {
                for (int i = loadedBitmaps.size (); -- i >= 0;) {
                    TileBitmap tbm = loadedBitmaps.valueAt (i);
                    if (tbm.used < drawCycle) {
                        loadedBitmaps.removeAt (i);
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
             * Meanwhile, try to draw zoomed out tile if we have one.
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
            synchronized (loadedBitmaps) {
                tbm = loadedBitmaps.get (key);
                if (tbm == null) {
                    if (startDownload) {
                        tbm = new TileBitmap ();
                        tbm.inval = redrawView;
                        tbm.dwnld = true;
                        neededBitmaps.put (key, tbm);
                        if (tileLoaderThread == null) {
                            tileLoaderThread = new TileLoaderThread ();
                            tileLoaderThread.start ();
                        }
                    }
                    return false;
                }
            }

            tbm.used = drawCycle;
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

                    canvas.save ();
                    saved = true;
                    canvas.clipPath (canvasclip);
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
            synchronized (loadedBitmaps) {
                for (int i = loadedBitmaps.size (); -- i >= 0;) {
                    TileBitmap tbm = loadedBitmaps.valueAt (i);
                    if (tbm.bm != null) tbm.bm.recycle ();
                }
                loadedBitmaps.clear ();
            }
        }

        private void stopReadingTiles (boolean wait)
        {
            synchronized (loadedBitmaps) {
                neededBitmaps.clear ();
            }
            if (wait && (tileLoaderThread != null)) {
                try { tileLoaderThread.join (); } catch (InterruptedException ie) { Lib.Ignored (); }
            }
        }

        /**
         * Get tiles to load from neededBitmaps and put in loadedBitmaps.
         */
        private class TileLoaderThread extends Thread {
            @Override
            public void run ()
            {
                setName ("OpenStreetMap tile loader");

                long key = 0;
                TileBitmap tbm = null;
                while (true) {
                    synchronized (loadedBitmaps) {
                        if (tbm != null) {
                            loadedBitmaps.put (key, tbm);
                            tbm.inval.postInvalidate ();
                        }
                        do {
                            if (neededBitmaps.size () == 0) {
                                tileLoaderThread = null;
                                return;
                            }
                            key = neededBitmaps.keyAt (0);
                            tbm = neededBitmaps.valueAt (0);
                            neededBitmaps.removeAt (0);
                        } while (loadedBitmaps.indexOfKey (key) >= 0);
                    }
                    int tileIX = (int) (key >> 36) & 0x0FFFFFFF;
                    int tileIY = (int) (key >>  8) & 0x0FFFFFFF;
                    int zoomLevel = (int) key & 0xFF;
                    tbm.bm = ReadTileBitmap (tileIX, tileIY, zoomLevel, tbm.dwnld);
                    tbm.used = Long.MAX_VALUE;
                }
            }
        }
    }

    private static class TileBitmap {
        public DisplayableChart.Invalidatable inval;  // callback when tile gets loaded
        public Bitmap bm;                             // bitmap (or null if not on flash or corrupt)
        public boolean dwnld;                         // download if not on flash
        public long used;                             // cycle it was used on
    }

    /**
     * This class simply scans the tiles needed to draw to a canvas.
     * It does a callback to DrawTile() for each tile needed.
     */
    private static abstract class TileDrawer {
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
        @SuppressWarnings("ConstantConditions")
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

            // check each zoom level starting with zoomed-in tiles until we have tile pixel big enough
            // eg, if the user has zoomed way out, the MAXZOOM tile pixels will be very small and so we
            // end up stepping out a few zoom levels to get something reasonable
            double maxPixPerSqIn = Math.min (MAXPIXPERSQIN, canvasPixPerSqIn * 1.5);
            for (; zoom > 0; -- zoom) {

                // if tile pixels at this zoom level will be displayed big enough, stop scanning
                if (tilePixelsPerCanvasSqIn <= maxPixPerSqIn) break;

                // if we use next level zoomed-out tiles to cover the same lat/lon ranges,
                // the pixels will be bigger and so there will only be 1/4th as many per
                // canvas square inch
                tilePixelsPerCanvasSqIn *= 0.25;
            }

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

        private long downloadStatus;  // =0: last download succeeded
                                      // >0: time of last download failure

        private RWYPrefecthTileDrawer rwyPrefecthTileDrawer = new RWYPrefecthTileDrawer ();

        @Override  // Thread
        public void run ()
        {
            setName ("OpenStreetMap runway prefetch");

            try {

                /*
                 * Download needed tile files one-by-one.
                 */
                while (true) {
                    boolean delay = false;

                    /*
                     * Prefetch some runway diagram tiles.
                     */
                    int platesexpdate = MaintView.GetPlatesExpDate ();
                    String dbname = "plates_" + platesexpdate + ".db";
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
                                // if recently tried this one, don't keep pounding on it,
                                // retry in a little while
                                long lastry = result.getLong (1);
                                long now = System.currentTimeMillis ();
                                if (lastry > now - TILE_RETRY_MS) {
                                    delay = true;
                                    break;
                                }

                                rwyPrefecthTileDrawer.successful = true;

                                String faaid = result.getString (0);

                                // get airport information
                                Waypoint.Airport apt = Waypoint.Airport.GetByFaaID (faaid);
                                if (apt != null) {
                                    Log.i (TAG, "preloading runway diagram background for " + apt.ident);

                                    // get mapping of the runway to the default canvas size
                                    PixelMapper pmap = PlateView.GetRWYPlateImageDefaultOSMMapping (apt, wairToNow);

                                    // gather the tiles needed into a bulk download request
                                    bulkDownloads.clear ();
                                    rwyPrefecthTileDrawer.DrawTiles (wairToNow, pmap);

                                    // if successfully downloaded all the tiles, mark that airport as complete
                                    if (FetchTiles ()) {
                                        sqldb.execSQL ("DELETE FROM rwypreloads WHERE rp_faaid='" + faaid + "'");
                                        if (wairToNow.maintView != null) {
                                            wairToNow.maintView.UpdateRunwayDiagramDownloadStatus ();
                                        }
                                    } else {
                                        now = System.currentTimeMillis ();
                                        sqldb.execSQL ("UPDATE rwypreloads SET rp_lastry=" + now + " WHERE rp_faaid='" + faaid + "'");
                                        delay = true;
                                    }
                                }

                                // try to get another airport's runway tiles
                            } while (result.moveToNext ());
                        } finally {
                            result.close ();
                        }
                    } catch (Exception e) {
                        Log.e (TAG, "error reading " + dbname, e);
                    }

                    if (delay) try { Thread.sleep (TILE_RETRY_MS); } catch (InterruptedException ie) { Lib.Ignored (); }
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
        private boolean FetchTiles ()
        {
            StringBuilder q = new StringBuilder ();
            int n = 0;
            for (Iterator<String> it = bulkDownloads.iterator (); it.hasNext ();) {
                String tilename = it.next ();
                String pathname = WairToNow.dbdir + "/streets/" + tilename;
                File pathfile = new File (pathname);
                long lastmod = pathfile.lastModified ();
                long now = System.currentTimeMillis ();
                if (lastmod > now - TILE_FILE_AGE_MS) {

                    // we already have a recent copy of that file
                    it.remove ();
                } else {

                    // if we recently failed (probably no internet connection),
                    // don't keep pounding away at it, just fail
                    if (downloadStatus > now - TILE_RETRY_MS) return false;

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
                try {
                    URL url = new URL (MaintView.dldir + "/streets.php");
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
                            String tilename = tiletag.substring (7);
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
                        }
                        downloadStatus = 0;
                    } finally {
                        httpCon.disconnect ();
                    }
                } catch (Exception e) {
                    Log.e (TAG, "error downloading tiles: " + e.getMessage (), e);
                    downloadStatus = System.currentTimeMillis ();
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
                        Lib.Ignored (permfile.getParentFile ().mkdirs ());
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
            Log.e (TAG, "error reading tile: " + tilename, e);
            Lib.Ignored (permfile.delete ());
            return null;
        }
    }
}
