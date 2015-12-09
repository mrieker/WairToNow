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

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Group of same-scaled air (eg, SEC or WAC) charts (that are downloaded) sown together.
 */
public class AutoAirChart implements DisplayableChart, Comparator<AirChart> {
    private long viewDrawCycle;
    private String basename;
    private String category;
    private TreeMap<AirChart,Long> airCharts = new TreeMap<> (this);

    public AutoAirChart (String cat)
    {
        category = cat;
        basename = "Auto " + cat;
    }

    @Override  // Chart
    public String GetSpacenameSansRev ()
    {
        return basename;
    }

    @Override  // DisplayableChart
    public boolean IsDownloaded ()
    {
        return true;
    }

    /**
     * Get entry for chart selection menu.
     */
    @Override  // Chart
    public View GetMenuSelector (ChartView chartView)
    {
        TextView tv = new TextView (chartView.wairToNow);
        tv.setText (basename);
        tv.setTextColor (Color.CYAN);
        tv.setTextSize (TypedValue.COMPLEX_UNIT_PX, chartView.wairToNow.textSize * 1.5F);
        return tv;
    }

    /**
     * Draw chart on canvas possibly scaled and/or rotated.
     * @param canvas = canvas to draw on
     */
    @Override  // Chart
    public void DrawOnCanvas (ChartView chartView, Canvas canvas)
    {
        // get list of the 2 or 3 charts we will be drawing this time
        long dcn = ++ viewDrawCycle;
        for (Iterator<AirChart> it = chartView.wairToNow.maintView.GetAirChartIterator (); it.hasNext ();) {
            AirChart ac = it.next ();
            if (ac.IsDownloaded () && ac.GetSpacenameSansRev ().contains (category)) {
                for (LatLon cmll : chartView.canvasMappingLatLons) {
                    if (ac.LatLonIsCharted (cmll.lat, cmll.lon)) {
                        airCharts.put (ac, dcn);
                        break;
                    }
                }
            }
        }

        // go through them in autoOrder order
        // any that are picked this time get drawn
        // any that weren't picked get closed
        for (Iterator<AirChart> it = airCharts.keySet ().iterator (); it.hasNext ();) {
            AirChart ac = it.next ();
            long drawcycle = airCharts.get (ac);
            if (drawcycle < dcn) {
                ac.CloseBitmaps ();
                it.remove ();
            } else {
                ac.DrawOnCanvas (chartView, canvas, false);
            }
        }
    }

    /**
     * Screen is being closed, close all open bitmaps.
     */
    @Override  // DisplayableChart
    public void CloseBitmaps ()
    {
        for (AirChart ac : airCharts.keySet ()) {
            ac.CloseBitmaps ();
        }
        airCharts.clear ();
    }

    @Override  // DisplayableChart
    public boolean LatLon2CanPixExact (float lat, float lon, @NonNull Point canpix)
    {
        // find last chart drawn that covers the given lat/lon
        AirChart lastac = null;
        for (AirChart ac : airCharts.keySet ()) {
            if (ac.LatLonIsCharted (lat, lon)) lastac = ac;
        }
        // if none, tell caller to use approximate mapping
        if (lastac == null) return false;
        // found one, use one drawn on top to convert
        lastac.LatLon2CanPixExact (lat, lon, canpix);
        return true;
    }

    @Override  // DisplayableChart
    public boolean CanPix2LatLonExact (float canpixx, float canpixy, @NonNull LatLon ll)
    {
        // find last chart drawn that covers the given pixel
        AirChart lastac = null;
        float lastlat = 0.0F;
        float lastlon = 0.0F;
        for (AirChart ac : airCharts.keySet ()) {
            // get what this chart thinks the lat/lon of the canvas pixel is
            ac.ChartPixel2LatLonExact (canpixx, canpixy, ll);
            // see if that chart covers that lat/lon
            if (ac.LatLonIsCharted (ll.lat, ll.lon)) {
                // if so, remember the point was converted
                lastac = ac;
                lastlat = ll.lat;
                lastlon = ll.lon;
            }
        }
        // no chart covers the canvax pixel, tell caller to use approximate mapping
        if (lastac == null) return false;
        // found one, return the converted lat/lon
        ll.lat = lastlat;
        ll.lon = lastlon;
        return true;
    }

    /**
     * Sort such that northeastern charts are drawn first,
     * as the north and east edges should be on top when
     * there is any overlapping.
     */
    @Override  // Comparator
    public int compare (AirChart a, AirChart b)
    {
        int aao = a.autoOrder;
        int bao = b.autoOrder;
        return aao - bao;
    }
}
