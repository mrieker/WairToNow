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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

/**
 * Single instance contains all the street map tiles for the whole world.
 */
public class OpenStreetMap {
    private final static String TAG = "WairToNow";

    private static final int BitmapSize = 256;  // all OSM tiles are this size
    private static final int MAXZOOM = 16;      // openstreepmap.org doesn't like serving zoom 17
    private static final float MinPixIn = 0.24F / Lib.MMPerIn;  // min inches on screen for a tile pixel
    private static final long TILE_FILE_AGE_MS = 1000L*60*60*24*365;
    private static final long TILE_RETRY_MS = 1000L*10;

    private boolean prefetchRunwayTiles;
    private DisplayableChart.Invalidatable redrawView;
    private DownloadThread downloadThread;
    private MainTileDrawer mainTileDrawer;
    private Paint copyrtBGPaint = new Paint ();
    private Paint copyrtTxPaint = new Paint ();
    private final TreeSet<String> downloadTilenames = new TreeSet<> ();
    private WairToNow wairToNow;

    public OpenStreetMap (WairToNow wtn)
    {
        wairToNow = wtn;

        copyrtBGPaint.setColor (Color.WHITE);
        copyrtBGPaint.setStyle (Paint.Style.FILL_AND_STROKE);
        copyrtBGPaint.setTextSize (wairToNow.textSize * 3 / 4);
        copyrtBGPaint.setStrokeWidth (30);
        copyrtTxPaint.setColor (Color.BLACK);
        copyrtTxPaint.setTextSize (wairToNow.textSize * 3 / 4);

        /*
         * Maybe we need to prefetch some runway diagram tiles.
         */
        StartPrefetchingRunwayTiles ();

        mainTileDrawer = new MainTileDrawer ();
    }

    /**
     * Draw tiles to canvas corresponding to given lat/lons.
     * @param canvas = canvas that draws to the view
     * @param pmap = maps canvas/view pixels to lat/lon
     * @param inval = what to call in an arbitrary thread when a tile gets loaded
     * @param canvasHdgRads = 'up' heading on the canvas
     */
    public void Draw (@NonNull Canvas canvas, @NonNull PixelMapper pmap, @NonNull DisplayableChart.Invalidatable inval, float canvasHdgRads)
    {
        /*
         * If thread is busy working on a queue of tiles, tell it not to bother,
         * as it is very possible the user just zoomed or panned the map and we
         * don't want those tiles any more.
         * If we still want those tiles, we will requeue them below.
         */
        redrawView = null;
        StopDownloadingTileFiles ();
        redrawView = inval;

        /*
         * Draw tiles.
         */
        mainTileDrawer.canvas = canvas;
        boolean gotatile = mainTileDrawer.DrawTiles (wairToNow, pmap, canvasHdgRads);

        /*
         * Copyright message in lower left corner.
         */
        if (gotatile) {
            int h = pmap.canvasHeight;
            String copyrtMessage = "Copyright OpenStreetMap contributors";
            canvas.drawText (copyrtMessage, 5, h - 5, copyrtBGPaint);
            canvas.drawText (copyrtMessage, 5, h - 5, copyrtTxPaint);
        }
    }

    /**
     * Screen is being closed, close all open bitmaps.
     */
    public void CloseBitmaps ()
    {
        redrawView = null;
        mainTileDrawer.CloseBitmaps ();
    }

    /**
     * This class actually draws tiles on a canvas.
     */
    private class MainTileDrawer extends TileDrawer {
        public Canvas canvas;
        private float[] bitmappts = new float[8];
        private HashMap<String,Bitmap> openBitmaps = new HashMap<> ();
        private Matrix matrix = new Matrix ();
        private Path canvasclip = new Path ();

        @Override  // TileDrawer
        public boolean DrawTile ()
        {
            /*
             * Try to draw the bitmap or start downloaded it if we don't have it.
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
            /*
             * See if we have tile's bitmap already loaded in memory.
             */
            String tilename = zoomOut + "/" + tileXOut + "/" + tileYOut + ".png";
            String pathname = WairToNow.dbdir + "/streets/" + tilename;
            Bitmap tile = openBitmaps.get (tilename);
            if (tile == null) {

                /*
                 * See if we have tile file on flash.
                 * Maybe start downloading if we don't or if the existing file is old.
                 * (DrawOnCanvas() will be re-triggered when download completes).
                 */
                File pathfile = new File (pathname);
                long lastmod = pathfile.lastModified ();
                if (lastmod < System.currentTimeMillis () - TILE_FILE_AGE_MS) {
                    if (startDownload) StartDownloadingTileFile (tilename);
                }

                /*
                 * If the file doesn't exist, all done for now.
                 */
                if (lastmod == 0) return false;

                /*
                 * We have the tile file on flash, open it as a bitmap.
                 */
                try {
                    try {
                        tile = BitmapFactory.decodeFile (pathname);
                    } catch (OutOfMemoryError oome) {
                        CloseBitmaps ();  // closes all our bitmaps
                        try {
                            tile = BitmapFactory.decodeFile (pathname);
                        } catch (OutOfMemoryError oome2) {
                            Log.w (TAG, "error decoding bitmap streets/" + tilename, oome2);
                            return false;
                        }
                    }
                    if (tile == null) throw new IOException ("corrupt file");
                    int w = tile.getWidth ();
                    int h = tile.getHeight ();
                    if ((w != BitmapSize) || (h != BitmapSize)) {
                        throw new IOException ("tile size " + w + " x " + h);
                    }
                } catch (Exception e) {
                    Log.w (TAG, "error decoding bitmap streets/" + tilename, e);
                    Lib.Ignored (pathfile.delete ());
                    return false;
                }
                openBitmaps.put (tilename, tile);
            }

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
                    canvasclip.moveTo (northwestcanpix.x, northwestcanpix.y);
                    canvasclip.lineTo (northeastcanpix.x, northeastcanpix.y);
                    canvasclip.lineTo (southeastcanpix.x, southeastcanpix.y);
                    canvasclip.lineTo (southwestcanpix.x, southwestcanpix.y);
                    canvasclip.lineTo (northwestcanpix.x, northwestcanpix.y);

                    canvas.save ();
                    saved = true;
                    canvas.clipPath (canvasclip);
                }

                /*
                 * Draw the bitmap to the canvas using that transformation.
                 */
                if (!matrix.setPolyToPoly (bitmappts, 0, canvaspts, 0, 4)) {
                    throw new RuntimeException ("setPolyToPoly failed");
                }
                canvas.drawBitmap (tile, matrix, null);
            } finally {
                if (saved) canvas.restore ();
            }

            return true;
        }

        public void CloseBitmaps ()
        {
            for (Iterator<String> it = openBitmaps.keySet ().iterator (); it.hasNext ();) {
                String tx = it.next ();
                Bitmap bm = openBitmaps.get (tx);
                bm.recycle ();
                it.remove ();
            }
        }
    }

    /**
     * This class simply scans the tiles needed to draw to a canvas.
     * It does a callback to DrawTile() for each tile needed.
     */
    private static abstract class TileDrawer {
        private DisplayMetrics metrics = new DisplayMetrics ();
        protected float[] canvaspts = new float[8];
        protected int tileX, tileY, zoom;
        protected Point northwestcanpix = new Point ();
        protected Point northeastcanpix = new Point ();
        protected Point southwestcanpix = new Point ();
        protected Point southeastcanpix = new Point ();

        // draw tile zoom/tileX/tileY.png
        public abstract boolean DrawTile ();

        /**
         * Draw OpenStreetMap tiles that fill the given pmap area.
         * Calls DrawTile() for each tile needed.
         * @param wairToNow = our activity
         * @param pmap = what pixels to draw to and the corresponding lat/lon mapping
         * @param canvasHdgRads = heading for 'up'
         */
        public boolean DrawTiles (WairToNow wairToNow, PixelMapper pmap, float canvasHdgRads)
        {
            boolean gotatile = false;

            /*
             * Read display metrics each time in case of orientation change.
             */
            wairToNow.getWindowManager ().getDefaultDisplay ().getMetrics (metrics);

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

            // get canvas size in inches
            float canvasWidthIn  = w / metrics.xdpi;
            float canvasHeightIn = h / metrics.ydpi;

            // width of rotated canvas in inches
            float canvasWholeLonIn = canvasWidthIn * Math.abs (Mathf.cos (canvasHdgRads)) +
                    canvasHeightIn * Math.abs (Mathf.sin (canvasHdgRads));

            // longitude degrees spanned by the canvas
            float canvasWholeLonDeg = pmap.canvasEastLon - pmap.canvasWestLon;

            // how many deg longitude are on a tile at zoom=MAXZOOM
            float tileWidthLonDeg = 360.0F / (1 << MAXZOOM);

            // how many zoom=MINZOOM tiles fit across the whole canvas
            float tilesPerCanvasWidthWhole = canvasWholeLonDeg / tileWidthLonDeg;

            // how many canvas inches the whole zoom=MINZOOM tile is wide
            float tileWholeWidthCanvasIn = canvasWholeLonIn / tilesPerCanvasWidthWhole;

            // how many canvas inches each zoom=MINZOOM tile pixel is wide
            float tilePixWidthCanvasIn = tileWholeWidthCanvasIn / BitmapSize;

            // check each zoom level starting with zoomed-in tiles until we have tile pixel big enough
            // eg, if the user has zoomed way out, the MAXZOOM tile pixels will be very small and so we
            // end up stepping out a few zoom levels to get something reasonable
            for (zoom = MAXZOOM; zoom > 0; -- zoom) {

                // if tile pixels at this zoom level will be displayed big enough, stop scanning
                if (tilePixWidthCanvasIn >= MinPixIn) break;

                // after zooming out another level, the zoomed-out tile
                // pixels will display twice the size as previous level
                tilePixWidthCanvasIn *= 2.0F;
            }

            /*
             * See what range of tile numbers are needed to cover the canvas.
             */
            int maxTileY = lat2TileY (pmap.canvasSouthLat);
            int minTileY = lat2TileY (pmap.canvasNorthLat);
            int minTileX = lon2TileX (pmap.canvasWestLon);
            int maxTileX = lon2TileX (pmap.canvasEastLon);

            /*
             * Loop through all the possible tiles to cover the canvas.
             */
            for (tileY = minTileY; tileY <= maxTileY; tileY ++) {
                float northlat = tileY2Lat (tileY);
                float southlat = tileY2Lat (tileY + 1);
                for (int rawTileX = minTileX; rawTileX <= maxTileX; rawTileX ++) {
                    float westlon = tileX2Lon (rawTileX);
                    float eastlon = tileX2Lon (rawTileX + 1);
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
                    canvaspts[0] = northwestcanpix.x;
                    canvaspts[1] = northwestcanpix.y;
                    canvaspts[2] = northeastcanpix.x;
                    canvaspts[3] = northeastcanpix.y;
                    canvaspts[4] = southeastcanpix.x;
                    canvaspts[5] = southeastcanpix.y;
                    canvaspts[6] = southwestcanpix.x;
                    canvaspts[7] = southwestcanpix.y;
                    gotatile |= DrawTile ();
                }
            }
            return gotatile;
        }

        /**
         * Convert lat,lon to x,y tile numbers
         * http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
         */
        private int lon2TileX (float lon)
        {
            float n = 1 << zoom;
            return (int) (n * (lon + 180.0F) / 360.0F);
        }
        private int lat2TileY (float lat)
        {
            float n = 1 << zoom;
            float latrad = lat / 180.0F * Mathf.PI;
            return (int) (n * (1.0 - (Math.log (Math.tan (latrad) + 1.0 / Math.cos (latrad)) / Math.PI)) / 2.0);
        }
        private float tileX2Lon (int xTile)
        {
            float n = 1 << zoom;
            return xTile * 360.0F / n - 180.0F;
        }
        private float tileY2Lat (int yTile)
        {
            float n = 1 << zoom;
            return (float) (Math.atan (Math.sinh (Mathf.PI * (1.0 - 2.0 * yTile / n))) / Math.PI * 180.0);
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
        synchronized (downloadTilenames) {
            prefetchRunwayTiles = true;
            if (downloadThread == null) {
                downloadThread = new DownloadThread ();
                downloadThread.start ();
            }
        }
    }

    /**
     * We need a tile that we don't have the file on flash for.
     * So start downloading it in the background then invalidate when we got it.
     */
    private void StartDownloadingTileFile (String tilename)
    {
        synchronized (downloadTilenames) {
            downloadTilenames.add (tilename);
            if (downloadThread == null) {
                downloadThread = new DownloadThread ();
                downloadThread.start ();
            }
        }
    }
    private void StopDownloadingTileFiles ()
    {
        synchronized (downloadTilenames) {
            downloadTilenames.clear ();
        }
    }

    private final static String[] rpcolumns = new String[] { "rp_faaid", "rp_lastry" };
    private class DownloadThread extends Thread {
        private HashSet<String> bulkDownloads = new HashSet<> ();

        private long downloadStatus;  // =0: last download succeeded
                                      // >0: time of last download failure

        private RWYPrefecthTileDrawer rwyPrefecthTileDrawer = new RWYPrefecthTileDrawer ();

        @Override  // Thread
        public void run ()
        {
            setName ("OpenStreetMap download");

            try {
                int haveRwyPrefetches = 1;

                /*
                 * Download needed tile files one-by-one.
                 */
                while (true) {

                    /*
                     * Get name of tile on server to download.
                     * We might also be prefetching runway diagram tiles.
                     */
                    String tilename = null;
                    synchronized (downloadTilenames) {

                        // maybe some specific tile needed right away
                        if (!downloadTilenames.isEmpty ()) {
                            tilename = downloadTilenames.first ();
                            downloadTilenames.remove (tilename);
                        }

                        // maybe there are some bulk downloads to do
                        else if (!bulkDownloads.isEmpty ()) {
                            Lib.Ignored ();
                        }

                        // maybe there are some new prefetches or some recent ones
                        else if (prefetchRunwayTiles || (haveRwyPrefetches > 0)) {
                            prefetchRunwayTiles = false;
                            haveRwyPrefetches = 1;
                        }

                        // maybe there are some old prefetches that we should retry in a little while
                        else if (haveRwyPrefetches < 0) {
                            try {
                                downloadTilenames.wait (TILE_RETRY_MS);
                            } catch (InterruptedException ie) {
                                downloadThread = null;
                                break;
                            }
                            continue;
                        }

                        // nothing to do, exit thread
                        else {
                            downloadThread = null;
                            break;
                        }
                    }

                    /*
                     * If specific tile needed right away, try to download then redraw.
                     */
                    if (tilename != null) {
                        bulkDownloads.add (tilename);
                        continue;
                    }

                    /*
                     * Maybe there are some bulk downloads to do.
                     */
                    if (!bulkDownloads.isEmpty ()) {
                        FetchTiles ();
                        DisplayableChart.Invalidatable v = redrawView;
                        if (v != null) v.postInvalidate ();
                        continue;
                    }

                    /*
                     * No specific tile needed right away, prefetch some runway diagram tiles.
                     */
                    int platesexpdate = MaintView.GetPlatesExpDate ();
                    String dbname = "plates_" + platesexpdate + ".db";
                    try {
                        // get a few airports that haven't been processed yet
                        SQLiteDBs sqldb = SQLiteDBs.open (dbname);
                        if ((sqldb == null) || !sqldb.tableExists ("rwypreloads")) {
                            haveRwyPrefetches = 0;
                            continue;
                        }

                        Cursor result = sqldb.query (
                                "rwypreloads", rpcolumns,
                                null, null, null, null,
                                "rp_lastry", "25");
                        try {
                            // if no records at all, we're done prefetching
                            if (!result.moveToFirst ()) {
                                haveRwyPrefetches = 0;
                                continue;
                            }

                            do {
                                // if recently tried this one, don't keep pounding on it,
                                // retry in a little while
                                long lastry = result.getLong (1);
                                long now = System.currentTimeMillis ();
                                if (lastry > now - TILE_RETRY_MS) {
                                    haveRwyPrefetches = -1;
                                    break;
                                }

                                rwyPrefecthTileDrawer.successful = true;

                                String faaid = result.getString (0);

                                // get airport information
                                Waypoint.Airport apt = Waypoint.Airport.GetByFaaID (faaid);
                                if (apt != null) {

                                    // get mapping of the runway to the default canvas size
                                    PixelMapper pmap = PlateView.GetRWYPlateImageDefaultOSMMapping (apt, wairToNow);

                                    // gather the tiles needed into a bulk download request
                                    rwyPrefecthTileDrawer.DrawTiles (wairToNow, pmap, 0.0F);

                                    Log.i (TAG, "preloading runway diagram background for " + apt.ident);
                                }

                                // if successfully downloaded all the tiles, mark that airport as complete
                                if (FetchTiles ()) {
                                    sqldb.execSQL ("DELETE FROM rwypreloads WHERE rp_faaid='" + faaid + "'");
                                    if (wairToNow.maintView != null) {
                                        wairToNow.maintView.UpdateRunwayDiagramDownloadStatus ();
                                    }
                                } else {
                                    bulkDownloads.clear ();
                                    now = System.currentTimeMillis ();
                                    sqldb.execSQL ("UPDATE rwypreloads SET rp_lastry=" + now + " WHERE rp_faaid='" + faaid + "'");
                                }

                                // stop this if there are some in the downloadTilenames queue
                                // cuz the user is waiting for those right now
                                synchronized (downloadTilenames) {
                                    if (!downloadTilenames.isEmpty ()) break;
                                }

                                // try to get another airport's runway tiles
                            } while (result.moveToNext ());
                        } finally {
                            result.close ();
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
                        httpCon.setRequestMethod ("POST");
                        httpCon.setDoOutput (true);
                        httpCon.setChunkedStreamingMode (0);
                        PrintWriter os = new PrintWriter (httpCon.getOutputStream ());
                        os.print (q.toString ());
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
}
