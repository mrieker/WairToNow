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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
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
}
