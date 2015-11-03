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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

/**
 * Single instance contains all the street map tiles for the whole world.
 */
public class OpenStreetMap {
    private final static String TAG = "WairToNow";

    private static final int BitmapWidth = 256;
    private static final int MINZOOM =  7;        // major cities and highways
    private static final int MAXZOOM = 16;        // openstreepmap.org doesn't like serving zoom 17
    private static final float MinPixIn = 0.24F / Lib.MMPerIn;  // .lt. half MaxPixIn
    private static final float MaxPixIn = 0.50F / Lib.MMPerIn;  // .gt. twice MinPixIn
    private static final long TILE_FILE_AGE_MS = 1000L*60*60*24*365;

    private boolean downloadFailed;
    private DisplayMetrics metrics = new DisplayMetrics ();
    private DownloadThread downloadThread;
    private float[] bitmappts = new float[8];
    private float[] canvaspts = new float[8];
    private int tileX, tileY, zoom;
    private HashMap<String,Bitmap> openBitmaps = new HashMap<> ();
    private Matrix matrix = new Matrix ();
    private Paint copyrtBGPaint = new Paint ();
    private Paint copyrtTxPaint = new Paint ();
    private Path canvasclip = new Path ();
    private Point topleftcanpix = new Point ();
    private Point topritecanpix = new Point ();
    private Point botleftcanpix = new Point ();
    private Point botritecanpix = new Point ();
    private final TreeSet<String> downloadTilenames = new TreeSet<> ();
    private View redrawView;
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
    }

    /**
     * Draw tiles to canvas corresponding to given lat/lons.
     * @param canvas = canvas that draws to the view
     * @param pmap = maps canvas/view pixels to lat/lon
     * @param view = view that the canvas draws to
     * @param canvasHdgRads = heading that is "up" (radians)
     */
    public void Draw (Canvas canvas, PixelMapper pmap, View view, float canvasHdgRads)
    {
        /*
         * If thread is busy working on a queue of tiles, tell it not to bother,
         * as it is very possible the user just zoomed or panned the map and we
         * don't want those tiles any more.
         * If we still want those tiles, we will requeue them below.
         */
        redrawView = null;
        StopDownloadingTileFiles ();
        redrawView = view;

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
        float canvasWidthIn  = w / metrics.xdpi;
        float canvasHeightIn = h / metrics.ydpi;
        float canvasWholeLonDeg = pmap.canvasEastLon - pmap.canvasWestLon;  // longitude degrees spanned by the canvas
        float canvasWholeLonIn = canvasWidthIn * Math.abs (Mathf.cos (canvasHdgRads)) +  // width of rotated canvas
                canvasHeightIn * Math.abs (Mathf.sin (canvasHdgRads));
        float tileWidthLonDeg = 360.0F / (1 << MINZOOM);  // how many deg longitude on whole zoom=MINZOOM tile width
        float tilesPerCanvasWidthWhole = canvasWholeLonDeg / tileWidthLonDeg;  // how many zoom=MINZOOM tiles fit across the whole canvas
        float tileWholeWidthCanvasIn = canvasWholeLonIn / tilesPerCanvasWidthWhole;  // how many canvas MMs the whole zoom=MINZOOM tile is wide
        float tilePixWidthCanvasIn = tileWholeWidthCanvasIn / BitmapWidth;  // how many canvas MMs each zoom=MINZOOM tile pixel is wide
        for (zoom = MINZOOM; zoom < MAXZOOM; zoom ++) {
            // if tile pixels display within acceptable size range, stop scanning
            if ((tilePixWidthCanvasIn >= MinPixIn) && (tilePixWidthCanvasIn <= MaxPixIn)) break;

            // after zooming in another level, those tile pixels will display as half the size as previous level
            tilePixWidthCanvasIn /= 2.0;
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
            float toplat = tileY2Lat (tileY);
            float botlat = tileY2Lat (tileY + 1);
            for (tileX = minTileX; tileX <= maxTileX; tileX ++) {
                float leftlon = tileX2Lon (tileX);
                float ritelon = tileX2Lon (tileX + 1);

                /*
                 * Get rectangle outlining where the entire bitmap goes on canvas.
                 * It's quite possible that some of the bitmap is off the canvas.
                 */
                pmap.LatLonToCanvasPix (toplat, leftlon, topleftcanpix);
                pmap.LatLonToCanvasPix (toplat, ritelon, topritecanpix);
                pmap.LatLonToCanvasPix (botlat, leftlon, botleftcanpix);
                pmap.LatLonToCanvasPix (botlat, ritelon, botritecanpix);
                canvaspts[0] = topleftcanpix.x;
                canvaspts[1] = topleftcanpix.y;
                canvaspts[2] = topritecanpix.x;
                canvaspts[3] = topritecanpix.y;
                canvaspts[4] = botritecanpix.x;
                canvaspts[5] = botritecanpix.y;
                canvaspts[6] = botleftcanpix.x;
                canvaspts[7] = botleftcanpix.y;

                /*
                 * If tile completely off the canvas, don't bother with it.
                 */
                if ((topleftcanpix.x < 0) && (topritecanpix.x < 0) && (botleftcanpix.x < 0) && (botritecanpix.x < 0)) continue;
                if ((topleftcanpix.y < 0) && (topritecanpix.y < 0) && (botleftcanpix.y < 0) && (botritecanpix.y < 0)) continue;
                if ((topleftcanpix.x > w) && (topritecanpix.x > w) && (botleftcanpix.x > w) && (botritecanpix.x > w)) continue;
                if ((topleftcanpix.y > h) && (topritecanpix.y > h) && (botleftcanpix.y > h) && (botritecanpix.y > h)) continue;

                /*
                 * Try to draw the bitmap or start downloaded it if we don't have it.
                 * Meanwhile, try to draw zoomed out tile if we have one.
                 */
                if (!TryToDrawTile (canvas, zoom, tileX, tileY, true)) {
                    int tileXOut = tileX;
                    int tileYOut = tileY;
                    for (int zoomOut = zoom; -- zoomOut >= MINZOOM;) {
                        tileXOut /= 2;
                        tileYOut /= 2;
                        if (TryToDrawTile (canvas, zoomOut, tileXOut, tileYOut, false)) break;
                    }
                }
            }
        }

        /*
         * Copyright message in lower left corner.
         */
        String copyrtMessage = "Copyright OpenStreetMap contributors";
        canvas.drawText (copyrtMessage, 5, h - 5, copyrtBGPaint);
        canvas.drawText (copyrtMessage, 5, h - 5, copyrtTxPaint);
    }

    /**
     * Screen is being closed, close all open bitmaps.
     */
    public void CloseBitmaps ()
    {
        redrawView = null;
        closeBitmaps ();
    }

    private void closeBitmaps ()
    {
        for (Iterator<String> it = openBitmaps.keySet ().iterator (); it.hasNext ();) {
            String tx = it.next ();
            Bitmap bm = openBitmaps.get (tx);
            bm.recycle ();
            it.remove ();
        }
    }

    /**
     * Try to draw a single tile to the canvas, scaled and translated in place.
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
             * We have the tile file on disk, open it as a bitmap.
             */
            try {
                try {
                    tile = BitmapFactory.decodeFile (pathname);
                } catch (OutOfMemoryError oome) {
                    closeBitmaps ();  // closes all our bitmaps
                    try {
                        tile = BitmapFactory.decodeFile (pathname);
                    } catch (OutOfMemoryError oome2) {
                        Log.w (TAG, "error decoding bitmap streets/" + tilename, oome2);
                        return false;
                    }
                }
                if (tile == null) throw new IOException ("corrupt file");
            } catch (Exception e) {
                Log.w (TAG, "error decoding bitmap streets/" + tilename, e);
                Lib.Ignored (pathfile.delete ());
                return false;
            }
            openBitmaps.put (tilename, tile);
        }

        /*
         * Get rectangle outlining entire bitmap in bitmap pixels.
         */
        int w = tile.getWidth ();
        int h = tile.getHeight ();

        /*
         * We might be using a zoomed-out tile while correct one downloads.
         * So adjust which pixels in the zoomed-out tile have what we want.
         */
        int outed    = zoom - zoomOut;
        int leftBmp  = ((tileX * w) >> outed) % w;
        int topBmp   = ((tileY * h) >> outed) % h;
        int riteBmp  = leftBmp + (w >> outed);
        int botBmp   = topBmp  + (h >> outed);
        bitmappts[0] = leftBmp;
        bitmappts[1] = topBmp;
        bitmappts[2] = riteBmp;
        bitmappts[3] = topBmp;
        bitmappts[4] = riteBmp;
        bitmappts[5] = botBmp;
        bitmappts[6] = leftBmp;
        bitmappts[7] = botBmp;

        /*
         * If zoomed out, make a clip so we only draw what is needed on the canvas.
         * If not zoomed out, we can draw the whole bitmap as it just fits the canvas spot.
         */
        boolean saved = false;
        try {
            if (outed > 0) {
                canvasclip.rewind ();
                canvasclip.moveTo (topleftcanpix.x, topleftcanpix.y);
                canvasclip.lineTo (topritecanpix.x, topritecanpix.y);
                canvasclip.lineTo (botritecanpix.x, botritecanpix.y);
                canvasclip.lineTo (botleftcanpix.x, botleftcanpix.y);
                canvasclip.lineTo (topleftcanpix.x, topleftcanpix.y);

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

    private class DownloadThread extends Thread {
        @Override  // Thread
        public void run ()
        {
            /*
             * Download needed tile files one-by-one.
             */
            while (true) {

                /*
                 * Get name of tile on server to download.
                 */
                String tilename;
                synchronized (downloadTilenames) {
                    if (downloadTilenames.isEmpty ()) {
                        downloadThread = null;
                        break;
                    }
                    tilename = downloadTilenames.first ();
                    downloadTilenames.remove (tilename);
                }

                /*
                 * See if we already have it and it is sufficiently new.  If not, download it.
                 */
                String pathname = WairToNow.dbdir + "/streets/" + tilename;
                File pathfile = new File (pathname);
                long lastmod = pathfile.lastModified ();
                if (lastmod < System.currentTimeMillis () - TILE_FILE_AGE_MS) {
                    String tempname = pathname + ".tmp";
                    try {
                        MaintView.DownloadFile ("streets.php?tile=" + tilename, tempname);
                        MaintView.RenameFile (tempname, pathname);
                        downloadFailed = false;
                    } catch (final IOException ioe) {
                        Log.e (TAG, "error downloading " + tilename, ioe);
                        if (!downloadFailed) {
                            downloadFailed = true;
                            wairToNow.runOnUiThread (new Runnable () {
                                    public void run ()
                                    {
                                        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                                        adb.setMessage ("StreetChart download failed: " + ioe.getMessage ());
                                        adb.setPositiveButton ("OK", null);
                                        adb.show ();
                                    }
                            });
                        }
                    }
                }

                /*
                 * Now that we have a file, trigger re-drawing the screen.
                 */
                View v = redrawView;
                if (v != null) v.postInvalidate ();
            }
        }
    }
}
