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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Group of same-scaled air (eg, SEC or WAC) charts (that are downloaded) sown together.
 */
public class AutoAirChart implements DisplayableChart, Comparator<AirChart> {
    private HashSet<String> dontAskAboutDownloading = new HashSet<> ();
    private long viewDrawCycle;
    private String basename;
    private String category;
    private TreeMap<AirChart,Long> airChartsAsync = new TreeMap<> (this);
    private WairToNow wairToNow;

    public AutoAirChart (WairToNow wtn, String cat)
    {
        wairToNow = wtn;
        category  = cat;
        basename  = "Auto " + cat;
    }

    /**
     * See if the named chart belongs in this AutoAirChart cagegory.
     * @param spacename = space name, with or without revision number
     */
    public boolean Matches (String spacename)
    {
        if (category.equals ("ENR")) return spacename.startsWith ("ENR E");
        return spacename.contains (category);
    }

    @Override  // DisplayableChart
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
    @Override  // DisplayableChart
    public View GetMenuSelector (@NonNull ChartView chartView)
    {
        TextView tv = new TextView (chartView.wairToNow);
        tv.setText (basename);
        tv.setTextColor (Color.CYAN);
        tv.setTextSize (TypedValue.COMPLEX_UNIT_PX, chartView.wairToNow.textSize * 1.5F);
        return tv;
    }

    /**
     * User just clicked this chart in the chart selection menu.
     */
    @Override  // DisplayableChart
    public void UserSelected ()
    {
        // set up to re-prompt for any charts that aren't downloaded
        dontAskAboutDownloading.clear ();
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
        // fill white background for enroute charts cuz they sometimes leave gaps
        if (category.equals ("ENR")) canvas.drawColor (Color.WHITE);

        // select charts that are in view of the screen
        SelectCharts (airChartsAsync, pmap);

        // draw all downloaded selected charts
        for (AirChart ac : airChartsAsync.keySet ()) {
            if (ac.IsDownloaded ()) {
                ac.DrawOnCanvas (pmap, canvas, inval, false);
            }
        }
    }

    /**
     * Screen is being closed, close all open bitmaps.
     */
    @Override  // DisplayableChart
    public void CloseBitmaps ()
    {
        for (AirChart ac : airChartsAsync.keySet ()) {
            ac.CloseBitmaps ();
        }
        airChartsAsync.clear ();
    }

    @Override  // DisplayableChart
    public boolean LatLon2CanPixExact (float lat, float lon, @NonNull Point canpix)
    {
        // find last chart drawn that covers the given lat/lon
        boolean found = false;
        int lastx = 0;
        int lasty = 0;
        for (AirChart ac : airChartsAsync.keySet ()) {
            if (ac.IsDownloaded () &&
                    ac.LatLonIsCharted (lat, lon) &&
                    ac.LatLon2CanPixExact (lat, lon, canpix)) {
                found = true;
                lastx = canpix.x;
                lasty = canpix.y;
            }
        }
        if (found) {
            canpix.x = lastx;
            canpix.y = lasty;
        }
        return found;
    }

    @Override  // DisplayableChart
    public boolean CanPix2LatLonExact (float canpixx, float canpixy, @NonNull LatLon ll)
    {
        // find last chart drawn that covers the given pixel
        boolean found = false;
        float lastlat = 0.0F;
        float lastlon = 0.0F;
        for (AirChart ac : airChartsAsync.keySet ()) {
            // get what this chart thinks the lat/lon of the canvas pixel is
            // and see if that chart covers that lat/lon
            if (ac.IsDownloaded () &&
                    ac.CanPix2LatLonExact (canpixx, canpixy, ll) &&
                    ac.LatLonIsCharted (ll.lat, ll.lon)) {
                // if so, remember the point was converted
                found   = true;
                lastlat = ll.lat;
                lastlon = ll.lon;
            }
        }
        if (found) {
            // found one, return the converted lat/lon
            ll.lat = lastlat;
            ll.lon = lastlon;
        }
        return found;
    }

    /**
     * Select list of charts that are viewable and get rid of ones that aren't.
     */
    private void SelectCharts (TreeMap<AirChart,Long> airCharts, PixelMapper pmap)
    {
        // get list of the 2 or 3 charts we will be drawing this time
        long dcn = ++ viewDrawCycle;
        for (Iterator<AirChart> it = wairToNow.maintView.GetAirChartIterator (); it.hasNext ();) {
            AirChart ac = it.next ();
            if (Matches (ac.GetSpacenameSansRev ()) && ac.ContributesToCanvas (pmap)) {
                airCharts.put (ac, dcn);
            }
        }

        // go through them in autoOrder order
        // any that are picked this time get drawn
        // any that weren't picked get closed
        TreeSet<String> askAboutDownloading = null;
        for (Iterator<AirChart> it = airCharts.keySet ().iterator (); it.hasNext ();) {
            AirChart ac = it.next ();

            // if chart no longer needed, close its bitmaps to save memory
            long drawcycle = airCharts.get (ac);
            if (drawcycle < dcn) {
                ac.CloseBitmaps ();
                it.remove ();
                continue;
            }

            // if we haven't asked about downloading it since user selected 'Auto <category>',
            // prompt for downloading.
            if (!ac.IsDownloaded () && !dontAskAboutDownloading.contains (ac.spacenamenr)) {
                // haven't asked to download it before, queue to ask about it
                if (askAboutDownloading == null) askAboutDownloading = new TreeSet<> ();
                askAboutDownloading.add (ac.spacenamenr);
                // don't ask about it again
                dontAskAboutDownloading.add (ac.spacenamenr);
            }
        }

        // see if some charts cover the screen that aren't downloaded
        MaybePromptChartDownload (askAboutDownloading);
    }

    /**
     * Ask user if they want to download a chart that is needed for the screen.
     * @param aad = list of charts that need downloading
     */
    private void MaybePromptChartDownload (final TreeSet<String> aad)
    {
        if ((aad != null) && !aad.isEmpty ()) {

            // build dialog box
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Auto " + category);

            // make prompt string listing all the charts (like 2, 3 maybe 4)
            StringBuilder prompt = new StringBuilder ();
            prompt.append ("Download ");
            boolean first = true;
            for (String snnr : aad) {
                if (!first) prompt.append ("\nand ");
                prompt.append (snnr);
                first = false;
            }
            prompt.append ('?');
            adb.setMessage (prompt);

            // start downloading each chart if OK clicked
            adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    for (String snnr : aad) {
                        wairToNow.maintView.StartDownloadingChart (snnr);
                    }
                }
            });

            // just ignore if Cancel clicked
            adb.setNegativeButton ("Cancel", null);

            // display the dialog
            adb.show ();
        }
    }

    /**
     * Sort such that northeastern charts are drawn first,
     * as the north and east edges should be on top when
     * there is any overlapping.
     *
     * Lower numbers get drawn behind higher numbers.
     */
    @Override  // Comparator
    public int compare (AirChart a, AirChart b)
    {
        int aao = a.autoOrder;
        int bao = b.autoOrder;
        return aao - bao;
    }
}
