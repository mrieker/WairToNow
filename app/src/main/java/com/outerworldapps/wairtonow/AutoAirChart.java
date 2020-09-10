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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * Group of same-scaled air (eg, SEC) charts (that are downloaded) sown together.
 */
public class AutoAirChart implements DisplayableChart, Comparator<AirChart> {
    private double macroNLat, macroSLat;
    private double macroELon, macroWLon;
    private HashSet<String> dontAskAboutDownloading = new HashSet<> ();
    private long viewDrawCycle;
    private NNTreeMap<AirChart,Long> airChartsAsync = new NNTreeMap<> (this);
    private NNTreeMap<AirChart,Long> airChartsSync = new NNTreeMap<> (this);
    private String basename;
    private String category;
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
    public void DrawOnCanvas (@NonNull PixelMapper pmap, @NonNull Canvas canvas, @NonNull Invalidatable inval, double canvasHdgRads)
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

        for (AirChart ac : airChartsSync.keySet ()) {
            ac.CloseBitmaps ();
        }
        airChartsSync.clear ();
    }

    @Override  // DisplayableChart
    public boolean LatLon2CanPixExact (double lat, double lon, @NonNull PointD canpix)
    {
        // find last chart drawn that covers the given lat/lon
        boolean found = false;
        double lastx = 0.0;
        double lasty = 0.0;
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
    public boolean CanPix2LatLonExact (double canpixx, double canpixy, @NonNull LatLon ll)
    {
        // find last chart drawn that covers the given pixel
        boolean found = false;
        double lastlat = 0.0;
        double lastlon = 0.0;
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
        macroSLat = slat;
        macroNLat = nlat;
        macroWLon = wlon;
        macroELon = elon;

        Bitmap abm = Bitmap.createBitmap (EarthSector.MBMSIZE, EarthSector.MBMSIZE, Bitmap.Config.RGB_565);
        Canvas can = new Canvas (abm);

        // fill white background for enroute charts cuz they sometimes leave gaps
        if (category.equals ("ENR")) can.drawColor (Color.WHITE);

        // select charts that are part of the requested macrobitmap area
        PixelMapper pmap = new PixelMapper ();
        pmap.setup (EarthSector.MBMSIZE, EarthSector.MBMSIZE, nlat, wlon, nlat, elon, slat, wlon, slat, elon);
        SelectCharts (airChartsSync, pmap);

        // draw all downloaded selected charts
        // charts will return transparent pixels where they don't cover an area
        float[] floats = new float[16];
        floats[0] = 0;
        floats[1] = 0;
        floats[2] = EarthSector.MBMSIZE;
        floats[3] = 0;
        floats[4] = 0;
        floats[5] = EarthSector.MBMSIZE;
        floats[6] = EarthSector.MBMSIZE;
        floats[7] = EarthSector.MBMSIZE;
        Matrix matrix = new Matrix ();
        PointD srctlpix = new PointD ();
        PointD srctrpix = new PointD ();
        PointD srcblpix = new PointD ();
        PointD srcbrpix = new PointD ();
        for (AirChart ac : airChartsSync.keySet ()) {
            if (ac.IsDownloaded ()) {
                Bitmap cbm = ac.GetMacroBitmap (slat, nlat, wlon, elon, false);
                ac.LatLon2MacroBitmap (nlat, wlon, srctlpix);
                ac.LatLon2MacroBitmap (nlat, elon, srctrpix);
                ac.LatLon2MacroBitmap (slat, wlon, srcblpix);
                ac.LatLon2MacroBitmap (slat, elon, srcbrpix);
                floats[ 8] = (float) srctlpix.x;
                floats[ 9] = (float) srctlpix.y;
                floats[10] = (float) srctrpix.x;
                floats[11] = (float) srctrpix.y;
                floats[12] = (float) srcblpix.x;
                floats[13] = (float) srcblpix.y;
                floats[14] = (float) srcbrpix.x;
                floats[15] = (float) srcbrpix.y;
                matrix.setPolyToPoly (floats, 8, floats, 0, 4);
                can.drawBitmap (cbm, matrix, null);
                cbm.recycle ();
            }
        }

        return abm;
    }

    @Override  // DisplayableChart
    public void LatLon2MacroBitmap (double lat, double lon, @NonNull PointD mbmpix)
    {
        double x;
        if (macroELon < macroWLon) {
            if (lon < (macroELon + macroWLon) / 2.0) lon += 360.0;
            x = (lon - macroWLon) / (macroELon - macroWLon + 360.0);
        } else {
            x = (lon - macroWLon) / (macroELon - macroWLon);
        }
        mbmpix.x = x * EarthSector.MBMSIZE;
        mbmpix.y = (macroNLat - lat) / (macroNLat - macroSLat) * EarthSector.MBMSIZE;
    }

    /**
     * Select list of charts that are viewable and get rid of ones that aren't.
     */
    private void SelectCharts (NNTreeMap<AirChart,Long> airCharts, PixelMapper pmap)
    {
        // get list of the 2 or 3 charts we will be drawing this time
        long dcn = ++ viewDrawCycle;
        for (Iterator<AirChart> it = wairToNow.maintView.GetCurentAirChartIterator (); it.hasNext ();) {
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
            long drawcycle = airCharts.nnget (ac);
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
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
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
            });
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
