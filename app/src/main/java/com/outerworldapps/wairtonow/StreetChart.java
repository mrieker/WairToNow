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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/**
 * Single instance contains all the street map tiles for the whole world.
 * The tiles dynamically download as needed as we can't store all zoom levels
 * for the whole world.
 */
public class StreetChart implements DisplayableChart {

    private OpenStreetMap osm;
    private WairToNow wairToNow;

    public StreetChart (WairToNow wtn)
    {
        wairToNow = wtn;
    }

    @Override  // DisplayableChart
    public String GetSpacenameSansRev ()
    {
        return "Street";
    }

    @Override  // DisplayableChart
    public boolean IsDownloaded ()
    {
        return true;
    }

    /**
     * Get entry for chart selection menu.
     */
    @Override  // DisplayableChart
    public View GetMenuSelector (@NonNull ChartView chartView)
    {
        TextView tv = new TextView (chartView.wairToNow);
        tv.setText (GetSpacenameSansRev ());
        tv.setTextColor (Color.CYAN);
        tv.setTextSize (TypedValue.COMPLEX_UNIT_PX, chartView.wairToNow.textSize * 1.5F);
        return tv;
    }

    /**
     * User just clicked this chart in the chart selection menu.
     */
    @Override  // DisplayableChart
    public void UserSelected ()
    { }

    @Override  // DisplayableChart
    public boolean LatLon2CanPixExact (float lat, float lon, @NonNull Point canpix)
    {
        return false;
    }

    @Override  // DisplayableChart
    public boolean CanPix2LatLonExact (float canpixx, float canpixy, @NonNull LatLon ll)
    {
        return false;
    }

    /**
     * Draw chart on canvas possibly scaled and/or rotated.
     * @param pmap = mapping of lat/lons to canvas
     * @param canvas = canvas to draw on
     * @param inval = what to call in an arbitrary thread when a tile gets loaded
     * @param canvasHdgRads = 'up' heading on canvas
     */
    @Override  // DisplayableChart
    public void DrawOnCanvas (@NonNull PixelMapper pmap, @NonNull Canvas canvas, @NonNull Invalidatable inval, float canvasHdgRads)
    {
        osm = wairToNow.openStreetMap;
        osm.Draw (canvas, pmap, inval, canvasHdgRads);
    }

    /**
     * Screen is being closed, close all open bitmaps.
     */
    @Override  // DisplayableChart
    public void CloseBitmaps ()
    {
        if (osm != null) {
            osm.CloseBitmaps ();
            osm = null;
        }
    }

    /**
     * Get macro bitmap lat/lon step size limits.
     * @param limits = where to return min,max limits.
     */
    @Override  // DisplayableChart
    public void GetL2StepLimits (int[] limits)
    {
        // given: tile = (1 << zoomLevel) * (lon + 180) / 360
        // find delta lon for delta tile = 1 at MAXZOOM:
        //     1 = (1 << MAXZOOM) * (lon) / 360
        //   360 = (1 << MAXZOOM) * (lon)
        //   360 / (1 << MAXZOOM) = lon

        // 0.33 mins per osm tile width at maxzoom
        float mins_per_osm_tile_of_maxzoom = 360.0F * 60.0F / (1 << OpenStreetMap.MAXZOOM);

        // 0.66 mins per mbm tile width at maxzoom
        float mins_per_mbm_tile_of_maxzoom = mins_per_osm_tile_of_maxzoom * EarthSector.MBMSIZE / OpenStreetMap.BitmapSize;

        // so tell caller we can do 1min x 1min tiles (by returning a 0)
        limits[0] = (int) Math.ceil (Math.log (mins_per_mbm_tile_of_maxzoom) / Math.log (2));

        // tell caller we can zoom out as far as 64min x 64min
        limits[1] = 6;
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
    public Bitmap GetMacroBitmap (float slat, float nlat, float wlon, float elon)
    {
        /*
         * See how many tiles needed to span the macro bitmap.
         * One tile width => TILEWIDTH macro bitmap pixels.
         */
        float numXTiles = (float) EarthSector.MBMSIZE / (float) TILEWIDTH;
        float numYTiles = (float) EarthSector.MBMSIZE / (float) TILEHEIGHT;

        /*
         * Find least zoomed-out level that requires fewer tiles
         * than that to fill the requested area.
         *
         * For example, if numXTiles is 2, zoomLevel 16 might
         * require 20.3 tiles on X to fill the area.  So try 15,
         * which would require 10.2, 14 would require 5.1, 13
         * requires 2.6, and level 12 requires 1.3.  So use
         * level 12 cuz since numXTiles is 2, 2 zoom 12 tiles
         * will more than cover the longitude range requested.
         */
        float northTileY, southTileY;
        float eastTileX, westTileX;
        for (zoomLevel = OpenStreetMap.MAXZOOM;; -- zoomLevel) {
            northTileY = lat2MacroTileY (nlat);
            southTileY = lat2MacroTileY (slat);
            eastTileX  = lon2MacroTileX (elon);
            westTileX  = lon2MacroTileX (wlon);
            if (zoomLevel <= 0) break;
            float diffTileX = eastTileX - westTileX;
            if (diffTileX < 0.0F) diffTileX += 1 << zoomLevel;
            if ((diffTileX <= numXTiles) &&
                    (southTileY - northTileY <= numYTiles)) break;
        }

        /*
         * Compute tile X,Y needed at top left of macro bitmap.
         */
        macroLeftTileX = lon2MacroTileX (wlon);
        macroTopTileY  = lat2MacroTileY (nlat);

        /*
         * Get integer tile numbers that cover the whole macro bitmap.
         */
        int northTileIY = (int) Math.floor (northTileY);
        int southTileIY = (int) Math.ceil  (southTileY);
        int eastTileIX  = (int) Math.ceil  (eastTileX);
        int westTileIX  = (int) Math.floor (westTileX);

        /*
         * Create blank macro bitmap of the requested size.
         */
        Bitmap mbm = Bitmap.createBitmap (EarthSector.MBMSIZE, EarthSector.MBMSIZE, Bitmap.Config.RGB_565);
        Canvas can = new Canvas (mbm);

        /*
         * Loop through all OSM tiles needed to fill the macro bitmap.
         */
        int zoommask = 1 << zoomLevel;
        if (eastTileIX < westTileIX) eastTileIX += zoommask;
        Rect dst = new Rect ();
        for (int tileIY = northTileIY; tileIY < southTileIY; tileIY ++) {
            for (int tileIX = westTileIX; tileIX < eastTileIX; tileIX ++) {
                int tileJX = tileIX & (zoommask - 1);
                Bitmap tbm = OpenStreetMap.ReadTileBitmap (tileJX, tileIY, zoomLevel);
                if (tbm != null) {

                    /*
                     * Calculate the macro bitmap X,Y of the northwest corner of the tile.
                     */
                    // (someTileX - macroLeftTileX) * TILEWIDTH  = someBitmapX
                    // (someTileY - macroTopTileY)  * TILEHEIGHT = someBitmapY
                    int bmX = Math.round ((tileIX - macroLeftTileX) * TILEWIDTH);
                    int bmY = Math.round ((tileIY - macroTopTileY) * TILEHEIGHT);

                    /*
                     * Draw tile bitmap to macro bitmap, stretching it in X,Y to TILEWIDTH,HEIGHT.
                     */
                    dst.top    = bmY;
                    dst.left   = bmX;
                    dst.bottom = bmY + TILEHEIGHT;
                    dst.right  = bmX + TILEWIDTH;
                    can.drawBitmap (tbm, null, dst, null);
                    tbm.recycle ();
                }
            }
        }

        return mbm;
    }

    private final static int FUDGEFACTOR = 1;  // one osm tile pixel = FUDGEFACTOR**2 macro pixels
    private final static int TILEWIDTH   = OpenStreetMap.BitmapSize * FUDGEFACTOR;
    private final static int TILEHEIGHT  = OpenStreetMap.BitmapSize * FUDGEFACTOR;
    private float macroLeftTileX;    // tile X at left edge of latest macro bitmap (might contain fractional part)
    private float macroTopTileY;     // tile Y at top edge of latest macro bitmap (might contain fractional part)
    private int zoomLevel;           // zoom level used for this mapping

    /**
     * Return mapping of lat/lon => bitmap pixel for most recent GetMacroBitmap() call.
     * @param lat = latitude (degrees)
     * @param lon = longitude (degrees)
     * @param mbmpix = macro bitmap pixel number (might be out of range)
     */
    @Override  // DisplayableChart
    public void LatLon2MacroBitmap (float lat, float lon, @NonNull Point mbmpix)
    {
        float tileX = lon2MacroTileX (lon) - macroLeftTileX;  // how many tiles east of left edge of macro bitmap
        float tileY = lat2MacroTileY (lat) - macroTopTileY;   // how many tiles south of top edge of macro bitmap

        tileX *= TILEWIDTH;   // how many macro bitmap pixels east of left edge
        tileY *= TILEHEIGHT;  // how many macro bitmap pixels south of top edge

        mbmpix.x = Math.round (tileX);
        mbmpix.y = Math.round (tileY);
    }

    /**
     * Return tile X number for the given longitude.
     * @param lon = longitude
     * @return tile X number (might contain fractional part)
     */
    private float lon2MacroTileX (float lon)
    {
        float n = 1 << zoomLevel;
        return n * (lon + 180.0F) / 360.0F;
    }

    /**
     * Return tile Y number for the given latitude.
     * @param lat = latitude
     * @return tile Y number (might contain fractional part)
     */
    private float lat2MacroTileY (float lat)
    {
        float n = 1 << zoomLevel;
        float latrad = lat / 180.0F * Mathf.PI;
        return (float) (n * (1.0 - (Math.log (Math.tan (latrad) + 1.0 / Math.cos (latrad)) / Math.PI)) / 2.0);
    }
}
