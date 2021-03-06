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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.Iterator;
import java.util.TreeMap;

/**
 * Display chart and current position and a specified course line.
 */
@SuppressLint("ViewConstructor")
public class ChartView extends FrameLayout implements WairToNow.CanBeMainView {
    public interface Backing {
        void Activate ();
        View getView ();
        void ChartSelected ();
        void SetGPSLocation ();
        void ReCenter ();
        double GetCanvasTrueUpRads ();
        void recycle ();
        boolean LatLonAlt2CanPixExact (double lat, double lon, double alt, PointD pix);
        void drawOverlay (Canvas canvas);
    }

    private boolean forceRecen;
    private boolean reselectLastChart = true;
    private boolean use3DChart;
    public  AutoAirChart autoAirChartSEC;
    private AutoAirChart[] autoAirCharts;
    public  Backing backing;
    private ChartSelectDialog chartSelectDialog;
    private DisplayableChart waitingForChartDownload;
    public  DisplayableChart selectedChart;
    public  PixelMapper pmap;
    public  StateView stateView;
    private StreetChart streetChart;
    public  TFROutlines tfrOutlines;
    public  WairToNow wairToNow;

    public boolean holdPosition;          // gps updates centering screen are blocked (eg, when screen has been panned)
    public double centerLat, centerLon;   // lat/lon at center+displaceX,Y of canvas
    public double orgLat, orgLon;         // course origination lat/lon
    public double scaling = 1.0;          // < 1 : zoomed out; > 1 : zoomed in
    public Waypoint clDest = null;        // course line destination waypoint

    public ChartView (WairToNow na)
    {
        super (na);

        wairToNow = na;

        stateView = new StateView (this);

        autoAirChartSEC = new AutoAirChart (wairToNow, "SEC");
        autoAirCharts = new AutoAirChart[] {
            new AutoAirChart (wairToNow, "ENR"),
            new AutoAirChart (wairToNow, "OFM Street"),
            new AutoAirChart (wairToNow, "OFM White"),
            autoAirChartSEC
        };

        pmap = new PixelMapper ();

        /*
         * Gets populated from database whenever chart is displayed.
         */
        tfrOutlines = new TFROutlines ();

        /*
         * The street charts theoretically cover the whole world,
         * so there is just one chart object.
         */
        streetChart = new StreetChart (wairToNow);

        /*
         * Used to display menu that selects which chart to display.
         */
        chartSelectDialog = new ChartSelectDialog ();

        /*
         * See if we have a 2D or a 3D backing.
         */
        BackingChartSelected ();
    }

    /**
     * Just set either 2D or 3D mode, update which of Chart2DView or Chart3DView we want now.
     */
    private void BackingChartSelected ()
    {
        Backing newbacking = null;
        if (use3DChart) {
            if (!(backing instanceof Chart3DView)) {
                newbacking = new Chart3DView (this);
            }
        } else {
            if (!(backing instanceof Chart2DView)) {
                if (backing != null) backing.recycle ();
                newbacking = new Chart2DView (this);
            }
        }

        if (newbacking != null) {
            if (backing != null) backing.recycle ();
            backing = newbacking;
            removeAllViews ();
            newbacking.Activate ();
            newbacking.ChartSelected ();
            newbacking.SetGPSLocation ();
            addView (newbacking.getView ());
            addView (stateView);
        }
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Chart";
    }

    /**
     * The GPS has a new lat/lon and we want to put the arrow at that point.
     * Also, if we aren't displaced by the mouse, move that point to center screen.
     */
    public void SetGPSLocation ()
    {
        // if position hold not in force, update the center of screen lat/lon
        if (!holdPosition) {
            centerLat = wairToNow.currentGPSLat;
            centerLon = wairToNow.currentGPSLon;
            chartSelectDialog.Update ();
        }

        // tell other things to update what they are displaying
        stateView.invalidate ();
        if (backing != null) {
            backing.SetGPSLocation ();
        }
    }

    /**
     * Set the center point of display and block GPS updates from panning.
     * @param lat = latitude (degrees)
     * @param lon = longitude (degrees)
     */
    public void SetCenterLatLon (double lat, double lon)
    {
        // save the given lat/lon as the new screen center lat/lon
        // then don't allow GPS updates to change it
        centerLat = lat;
        centerLon = Lib.NormalLon (lon);
        holdPosition = true;

        // tell other things to re-draw themselves
        stateView.invalidate ();
        if (backing != null) {
            backing.getView ().invalidate ();
        }
    }

    /**
     * Re-center the arrow at the center of the screen and turn tracking back on
     * so center of screen follows future GPS updates.
     */
    public void ReCenter ()
    {
        // allow GPS updates to change center of screen
        // and set current center of screen to latest GPS lat/lon
        holdPosition = false;
        centerLat = wairToNow.currentGPSLat;
        centerLon = wairToNow.currentGPSLon;

        // tell other things to re-draw themselves
        stateView.invalidate ();
        if (backing != null) {
            backing.ReCenter ();
        }
    }

    /**
     * Set course line to be drawn on charts.
     * Use dest=null to disable.
     * @param oLat = origination latitude
     * @param oLon = origination longitude
     * @param dest = destination
     */
    public void SetCourseLine (double oLat, double oLon, Waypoint dest)
    {
        orgLat = oLat;
        orgLon = Lib.NormalLon (oLon);
        clDest = dest;

        stateView.showCourseInfo = true;
        stateView.showCourseLine = true;
        stateView.invalidate ();
        if (backing != null) {
            backing.getView ().invalidate ();
        }
    }

    /**
     * What to show when the back button is pressed
     */
    @Override  // WairToNow.CanBeMainView
    public View GetBackPage ()
    {
        return this;
    }

    /**
     * This screen is about to become active.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        // update TFRs
        tfrOutlines.populate (wairToNow);

        // set up either 2D or 3D backing (Chart2DView or Chart3DView)
        BackingChartSelected ();

        // if first time since app started, re-select chart from preferences
        MaybeReselectLastChart ();

        // tell Chart2DView or Chart3DView to draw selected chart
        if (backing != null) {
            backing.ChartSelected ();
        }
    }

    @Override  // CanBeMainView
    public void OrientationChanged ()
    { }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return wairToNow.optionsView.powerLockOption.checkBox.isChecked ();
    }

    /**
     * This screen is no longer current.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        for (Iterator<AirChart> it = wairToNow.maintView.GetCurentAirChartIterator (); it.hasNext ();) {
            it.next ().CloseBitmaps ();
        }
        for (AutoAirChart aac : autoAirCharts) aac.CloseBitmaps ();
        streetChart.CloseBitmaps ();
    }

    /**
     * Chart tab re-clicked when already active.
     * Show selection dialog.
     */
    @Override  // CanBeMainView
    public void ReClicked ()
    {
        chartSelectDialog.Show ();
    }

    /**
     * Display a list of buttons to select currently displayed chart
     * from list of eligible charts.
     */
    private class ChartSelectDialog implements ListAdapter,
            DialogInterface.OnClickListener {
        private AlertDialog openDialog;
        private NNTreeMap<String,DisplayableChart> displayableCharts;
        private View[] chartViews;

        /**
         * Build button list based on what charts cover the current position
         * and display them.
         */
        @SuppressLint("SetTextI18n")
        public void Show ()
        {
            reselectLastChart = false;
            waitingForChartDownload = null;
            forceRecen = false;

            /*
             * Find charts that cover each of the corners and edges.
             * Street chart coverts the whole world so it is always downloaded.
             */
            NNTreeMap<String,DisplayableChart> chartlist = new NNTreeMap<> ();
            chartlist.put (streetChart.GetSpacenameSansRev (), streetChart);

            boolean[] autoAirChartHits = new boolean[autoAirCharts.length];
            for (Iterator<AirChart> it = wairToNow.maintView.GetCurentAirChartIterator (); it.hasNext ();) {
                AirChart ac = it.next ();
                if (ac.ContributesToCanvas (pmap)) {
                    String spn = ac.GetSpacenameSansRev ();
                    chartlist.put (spn, ac);

                    for (int i = 0; i < autoAirCharts.length; i ++) {
                        AutoAirChart aac = autoAirCharts[i];
                        if (!autoAirChartHits[i] && aac.Matches (spn)) {
                            chartlist.put (aac.GetSpacenameSansRev (), aac);
                            autoAirChartHits[i] = true;
                        }
                    }
                }
            }

            /*
             * If menu already open showing the same charts, don't do anything.
             */
            int ncharts = chartlist.size ();
            if (openDialog != null) {
                if (displayableCharts.size () == ncharts) {
                    boolean allthere = true;
                    for (String spn : displayableCharts.keySet ()) {
                        allthere &= chartlist.containsKey (spn);
                    }
                    if (allthere) return;
                }
                openDialog.dismiss ();
                openDialog = null;
            }
            displayableCharts = chartlist;

            /*
             * Make a button for each chart found.
             * List them in alphabetical order.
             */
            int nviews = ncharts;
            TextView awaitgps = null;
            if ((ncharts < 2) && ! wairToNow.gpsAvailable) {
                nviews ++;
                awaitgps = new TextView (wairToNow);
                wairToNow.SetTextSize (awaitgps);
                awaitgps.setBackgroundColor (Color.BLACK);
                awaitgps.setTextColor (Color.WHITE);
                awaitgps.setText ("You may need to wait for GPS positioning to " +
                        "get a list of available charts for this area.\n\n" +
                        "You can always select Street, however it may just show blue " +
                        "ocean until a proper GPS position is received.  Then when it " +
                        "receives GPS position, and shows your local area with Street " +
                        "map, click Chart again to select an aviation chart.");
                forceRecen = true;
            }
            chartViews = new View[nviews];
            int i = 0;
            if (awaitgps != null) chartViews[i++] = awaitgps;
            for (String spacename : chartlist.keySet ()) {
                DisplayableChart dc = chartlist.nnget (spacename);

                View ms = dc.GetMenuSelector (ChartView.this);

                View as = new AirplaneSymbol ();
                as.setVisibility ((dc == selectedChart) ? VISIBLE : INVISIBLE);

                LinearLayout ll = new LinearLayout (wairToNow);
                ll.setTag (spacename);
                ll.setOrientation (LinearLayout.HORIZONTAL);
                ll.addView (as);
                ll.addView (ms);

                chartViews[i++] = ll;
            }

            /*
             * Build dialog box to select chart from.
             */
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Chart Select");
            adb.setAdapter (this, this);
            adb.setPositiveButton ("None", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    openDialog = null;
                    selectedChart = null;
                    backing.ChartSelected ();
                }
            });
            adb.setNeutralButton (use3DChart ? "2D View" : "3D View", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    openDialog = null;
                    use3DChart = !use3DChart;
                    if (use3DChart && (MaintView.GetCurentTopographyExpDate () == 0)) {
                        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                        adb.setTitle ("Topography");
                        adb.setMessage ("Topography database should be downloaded or you will get flat terrain.");
                        adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                            @Override
                            public void onClick (DialogInterface dialogInterface, int i)
                            {
                                wairToNow.maintView.StartDownloadingChart ("Topography");
                            }
                        });
                        adb.setNegativeButton ("NotNow", null);
                        adb.show ();
                    }
                    BackingChartSelected ();
                }
            });
            adb.setNegativeButton ("Cancel", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    openDialog = null;
                }
            });
            openDialog = adb.show ();
            openDialog.setOnDismissListener (new DialogInterface.OnDismissListener () {
                @Override
                public void onDismiss (DialogInterface dialogInterface)
                {
                    openDialog = null;
                }
            });
        }

        // new GPS position received, update menu of available charts
        public void Update ()
        {
            if (openDialog != null) Show ();
        }

        /********************************\
         *  ListAdapter implementation  *
        \********************************/

        @Override
        public boolean areAllItemsEnabled ()
        {
            return true;
        }

        @Override
        public boolean isEnabled (int position)
        {
            return true;
        }

        @Override
        public int getCount ()
        {
            return chartViews.length;
        }

        @Override
        public Object getItem (int position)
        {
            return chartViews[position].getTag ();
        }

        @Override
        public long getItemId (int position)
        {
            return position;
        }

        @Override
        public int getItemViewType (int position)
        {
            return 0;
        }

        @Override
        public View getView (int position, View convertView, ViewGroup parent)
        {
            // AlertController.RecycleListView
            parent.setBackgroundColor (Color.BLACK);
            return chartViews[position];
        }

        @Override
        public int getViewTypeCount ()
        {
            return 1;
        }

        @Override
        public boolean hasStableIds ()
        {
            return true;
        }

        @Override
        public boolean isEmpty ()
        {
            return chartViews.length == 0;
        }

        @Override
        public void registerDataSetObserver (DataSetObserver observer)
        { }

        @Override
        public void unregisterDataSetObserver (DataSetObserver observer)
        { }

        /****************************************************\
         *  DialogInterface.OnClickListener implementation  *
        \****************************************************/

        @Override
        public void onClick (DialogInterface dialog, int which)
        {
            openDialog = null;
            View tv = chartViews[which];
            String spacename = (String) tv.getTag ();
            if (spacename != null) {
                if (forceRecen) ReCenter ();
                waitingForChartDownload = null;
                final DisplayableChart dc = displayableCharts.nnget (spacename);
                if (dc.IsDownloaded ()) {
                    SelectChart (dc);
                } else {
                    AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                    adb.setTitle ("Chart Select");
                    adb.setMessage ("Download " + spacename + "?");
                    adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                        @Override
                        public void onClick (DialogInterface dialog, int which)
                        {
                            waitingForChartDownload = dc;
                            wairToNow.maintView.StartDownloadingChart (dc.GetSpacenameSansRev ());
                        }
                    });
                    adb.setNegativeButton ("Cancel", null);
                    adb.show ();
                }
            }
        }
    }

    /**
     * Draws little red airplane symbol indicating which chart is current.
     * Set to INVISIBLE if the charts isn't selected so it takes up the space.
     */
    private class AirplaneSymbol extends View {
        private int size;

        public AirplaneSymbol ()
        {
            super (wairToNow);
            size = Math.round (wairToNow.textSize) * 2;
        }

        @Override
        protected final void onMeasure (int widthMeasureSpec, int heightMeasureSpec)
        {
            setMeasuredDimension (size, size);
        }

        @Override
        protected int getSuggestedMinimumHeight ()
        {
            return size;
        }

        @Override
        protected int getSuggestedMinimumWidth ()
        {
            return size;
        }

        @Override
        public void onDraw (Canvas canvas)
        {
            canvas.save ();
            try {
                float hs = size / 2.0F;
                canvas.translate (hs, hs);
                canvas.rotate (90.0F);
                wairToNow.DrawAirplaneSymbol (canvas, hs);
            } finally {
                canvas.restore ();
            }
        }
    }

    /**
     * Download completed, redraw screen with newly downloaded chart.
     */
    public void DownloadComplete ()
    {
        if ((waitingForChartDownload != null) && waitingForChartDownload.IsDownloaded ()) {
            SelectChart (waitingForChartDownload);
            waitingForChartDownload = null;
        }
    }

    /**
     * If this is our first time displaying stuff, re-select the chart last selected by the user.
     */
    private void MaybeReselectLastChart ()
    {
        if (reselectLastChart) {
            reselectLastChart = false;
            wairToNow.maintView.callbackWhenChartsLoaded (new Runnable () {
                @Override
                public void run ()
                {
                    SharedPreferences prefs = wairToNow.getPreferences (Activity.MODE_PRIVATE);
                    String spacenamesansrev = prefs.getString ("selectedChart", null);
                    if (spacenamesansrev != null) {
                        TreeMap<String,DisplayableChart> charts = new TreeMap<> ();
                        charts.put (streetChart.GetSpacenameSansRev (), streetChart);
                        for (AutoAirChart aac : autoAirCharts) charts.put (aac.GetSpacenameSansRev (), aac);
                        for (Iterator<AirChart> it = wairToNow.maintView.GetCurentAirChartIterator (); it.hasNext ();) {
                            AirChart ac = it.next ();
                            charts.put (ac.GetSpacenameSansRev (), ac);
                        }
                        DisplayableChart dc = charts.get (spacenamesansrev);
                        if ((dc != null) && dc.IsDownloaded ()) {
                            selectedChart = dc;
                            if (backing != null) backing.ChartSelected ();
                        }
                    }
                }
            });
        }
    }

    /**
     * Select the given chart.  Remember it in case we are restarted so we can reselect it.
     * @param dc = null: show splash screen; else: show given chart if it is in range
     */
    public void SelectChart (DisplayableChart dc)
    {
        if (selectedChart != null) {
            selectedChart.CloseBitmaps ();
        }
        selectedChart = dc;
        if (selectedChart != null) {
            selectedChart.UserSelected ();
            SharedPreferences prefs = wairToNow.getPreferences (Activity.MODE_PRIVATE);
            SharedPreferences.Editor editr = prefs.edit ();
            editr.putString ("selectedChart", dc.GetSpacenameSansRev ());
            editr.apply ();
        }
        if (backing != null) backing.ChartSelected ();
    }
}
