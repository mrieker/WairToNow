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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.zip.GZIPInputStream;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * This view presents a menu so the user can select a FAA waypoint by name.
 * It can also pick a waypoint that is nearby a given lat/lon.
 */
public class WaypointView extends LinearLayout
        implements WairToNow.CanBeMainView {
    public final static String TAG = "WairToNow";
    private final static float nearbynm = 5.0F;

    private DestinationButton destinationButton;
    private DownloadButton downloadButton;
    private File oldSearchFile;
    private FindButton findButton;
    private InfoButton infoButton;
    private LinearLayout oldSearchRow;
    private LinearLayout waypointLinear;
    private LocationButton locationButton;
    private MetarButton metarButton;
    private OldSearchButtonListener oldSearchLis;
    private SearchTextView searchTextView;
    private String tabName;
    public  WairToNow wairToNow;
    public  Waypoint selectedWaypoint;
    public  Waypoint.Within waypointsWithin;

    public WaypointView (WairToNow ctx, String tn)
    {
        super (ctx);
        wairToNow = ctx;
        tabName = tn;

        setOrientation (VERTICAL);

        /*
         * Start with window title.
         */
        TextView tv1 = new TextView (ctx);
        tv1.setText ("FAA Waypoints");
        wairToNow.SetTextSize (tv1);
        addView (tv1);

        /*
         * This is a box the user can enter the waypoint name in.
         */
        LinearLayout ll0 = new LinearLayout (ctx);
        ll0.setOrientation (HORIZONTAL);

        searchTextView = new SearchTextView (ctx);
        searchTextView.setLayoutParams (new LinearLayout.LayoutParams (ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));
        ll0.addView (searchTextView);

        /*
         * Click the Find button to perform the search.
         */
        findButton = new FindButton (ctx);
        ll0.addView (findButton);

        addView (ll0);

        /*
         * Make a row of buttons, each containing previous searches.
         */
        oldSearchRow = new LinearLayout (ctx);
        oldSearchRow.setOrientation (HORIZONTAL);
        DetentHorizontalScrollView hs0 = new DetentHorizontalScrollView (ctx);
        ctx.SetDetentSize (hs0);
        hs0.addView (oldSearchRow);
        addView (hs0);
        LoadOldSearchButtons ();

        LinearLayout ll1 = new LinearLayout (ctx);
        ll1.setOrientation (HORIZONTAL);

        /*
         * This button can be clicked to set the waypoint as destination.
         */
        destinationButton = new DestinationButton (ctx);
        ll1.addView (destinationButton);

        /*
         * This button can be clicked to set the waypoint as center location in screen.
         */
        locationButton = new LocationButton (ctx);
        ll1.addView (locationButton);

        /*
         * This button can be clicked to get the waypoint's METAR.
         */
        metarButton = new MetarButton (ctx);
        ll1.addView (metarButton);

        /*
         * This button can be clicked to display the waypoint's information.
         */
        infoButton = new InfoButton (ctx);
        ll1.addView (infoButton);

        /*
         * This button can be clicked to download the airport's information.
         */
        downloadButton = new DownloadButton (ctx);
        ll1.addView (downloadButton);

        DetentHorizontalScrollView hs1 = new DetentHorizontalScrollView (ctx);
        ctx.SetDetentSize (hs1);
        hs1.addView (ll1);
        addView (hs1);

        /*
         * This box is used to describe the waypoint.
         */
        waypointLinear = new LinearLayout (ctx);
        waypointLinear.setOrientation (LinearLayout.VERTICAL);
        ScrollView sv1 = new ScrollView (ctx);
        sv1.addView (waypointLinear);
        addView (sv1);

        /*
         * Reset waypoint area search database.
         */
        waypointsWithin = new Waypoint.Within ();
    }

    /**
     * Fill the old search buttons row with a button for each old search string.
     */
    private void LoadOldSearchButtons ()
    {
        oldSearchLis = new OldSearchButtonListener ();
        oldSearchFile = new File (WairToNow.dbdir + "/oldsearches.txt");
        oldSearchRow.removeAllViews ();
        try {
            Lib.Ignored (oldSearchFile.createNewFile ());
            BufferedReader osrdr = new BufferedReader (new FileReader (oldSearchFile), 4096);
            try {
                String osline;
                while ((osline = osrdr.readLine ()) != null) {
                    AddOldSearchButton (osline, false);
                }
            } finally {
                osrdr.close ();
            }
        } catch (IOException ioe) {
            Log.e (TAG, "error reading oldsearches.txt", ioe);
        }
    }

    /**
     * Add string to list of old search string buttons.
     * @param key = string to add
     * @param update = true to also update the oldsearches.txt file
     */
    private void AddOldSearchButton (String key, boolean update)
    {
        /*
         * If there is already a button with the same string, remove it from list.
         */
        boolean dupfound = false;
        int nchilds = oldSearchRow.getChildCount ();
        for (int ichild = 0; ichild < nchilds; ichild ++) {
            View v = oldSearchRow.getChildAt (ichild);
            if (v instanceof Button) {
                CharSequence t = ((Button)v).getText ();
                if (t.equals (key)) {
                    oldSearchRow.removeViewAt (ichild);
                    -- nchilds;
                    dupfound = true;
                }
            }
        }

        /*
         * Insert new button at top of list.
         */
        Button osbut = new Button (wairToNow);
        osbut.setText (key);
        wairToNow.SetTextSize (osbut);
        osbut.setOnClickListener (oldSearchLis);
        osbut.setOnLongClickListener (oldSearchLis);
        oldSearchRow.addView (osbut, 0);
        nchilds ++;

        /*
         * Maybe stick new string at end of data file.
         * But if it was already in there somewhere, rewrite the whole file.
         */
        if (update) {
            try {
                if (dupfound) {
                    BufferedWriter osfw = new BufferedWriter (new FileWriter (oldSearchFile, false), 1024);
                    try {
                        for (int ichild = nchilds; -- ichild >= 0;) {
                            View v = oldSearchRow.getChildAt (ichild);
                            if (v instanceof Button) {
                                CharSequence t = ((Button)v).getText ();
                                osfw.write (t.toString () + "\n");
                            }
                        }
                    } finally {
                        osfw.close ();
                    }
                } else {
                    BufferedWriter osfw = new BufferedWriter (new FileWriter (oldSearchFile, true), 1024);
                    try {
                        osfw.write (key + "\n");
                    } finally {
                        osfw.close ();
                    }
                }
            } catch (IOException ioe) {
                Log.e (TAG, "error writing oldsearches.txt", ioe);
            }
        }
    }

    /**
     * Delete button from list of old search string buttons.
     * @param key = string to delete
     */
    private void DelOldSearchButton (String key)
    {
        /*
         * If there is a button with the same string, remove it from list.
         */
        boolean found = false;
        int nchilds = oldSearchRow.getChildCount ();
        for (int ichild = nchilds; -- ichild >= 0;) {
            View v = oldSearchRow.getChildAt (ichild);
            if (v instanceof Button) {
                CharSequence t = ((Button)v).getText ();
                if (t.equals (key)) {
                    oldSearchRow.removeViewAt (ichild);
                    -- nchilds;
                    found = true;
                }
            }
        }

        /*
         * Remove string from data file.
         */
        if (found) {
            try {
                BufferedWriter osfw = new BufferedWriter (new FileWriter (oldSearchFile, false), 1024);
                try {
                    for (int ichild = nchilds; -- ichild >= 0;) {
                        View v = oldSearchRow.getChildAt (ichild);
                        if (v instanceof Button) {
                            CharSequence t = ((Button)v).getText ();
                            osfw.write (t.toString () + "\n");
                        }
                    }
                } finally {
                    osfw.close ();
                }
            } catch (IOException ioe) {
                Log.e (TAG, "error writing oldsearches.txt", ioe);
            }
        }
    }

    /**
     * When user clicks an old search button, stuff the text in the search box
     * and make like the user clicked the Find button.
     */
    private class OldSearchButtonListener implements OnClickListener, OnLongClickListener {
        public void onClick (View v)
        {
            searchTextView.setText (((Button) v).getText ());
            findButton.onClick (findButton);
        }

        public boolean onLongClick (View v)
        {
            String key = ((Button) v).getText ().toString ();
            searchTextView.setText (key);
            DelOldSearchButton (key);
            return true;
        }
    }

    /**
     * WairToNow.CanBeMainView implementation
     */
    @Override
    public String GetTabName ()
    {
        return tabName;
    }
    @Override
    public void CloseDisplay ()
    { }
    @Override
    public void OpenDisplay ()
    {
        wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_USER);
        LoadOldSearchButtons ();  // reload buttons in case changed by other WaypointView instance
    }
    @Override
    public View GetBackPage ()
    {
        return wairToNow.chartView;
    }
    @Override
    public void ReClicked ()
    { }

    /**
     * Find waypoints near the given lat/lon and let user select one and open it.
     */
    public void OpenWaypointAtLatLon (final float lat, final float lon)
    {
        // get all waypoints within box from given lat/lon
        float del = nearbynm / wairToNow.chartView.scaling / Lib.NMPerDeg;
        Collection<Waypoint> wpcollection = new Waypoint.Within ().Get (lat - del, lat + del, lon - del, lon + del);

        // remove all waypoints greater than radius from the collection
        for (Iterator<Waypoint> it = wpcollection.iterator (); it.hasNext ();) {
            Waypoint wp = it.next ();
            float dist = Lib.LatLonDist (lat, lon, wp.lat, wp.lon);
            if (dist > nearbynm / wairToNow.chartView.scaling) {
                it.remove ();
            }
        }

        // sort collection by ascending distance from given lat/lon
        Waypoint[] wparray = new Waypoint[wpcollection.size()];
        wpcollection.toArray (wparray);
        Arrays.sort (wparray, new WPSorter (lat, lon));

        // present resultant list to user as a menu
        OpenWaypointFromList (Arrays.asList (wparray));
    }

    private static class WPSorter implements Comparator<Waypoint> {
        private float llat, llon;
        public WPSorter (float lat, float lon)
        {
            llat = lat;
            llon = lon;
        }
        public int compare (Waypoint a, Waypoint b)
        {
            float dista = Lib.LatLonDist (llat, llon, a.lat, a.lon);
            float distb = Lib.LatLonDist (llat, llon, b.lat, b.lon);
            if (dista < distb) return -1;
            if (dista > distb) return  1;
            return 0;
        }
    }

    /**
     * Present list for user to select a waypoint and open it.
     * @param waypoints = list of waypoints to choose from
     */
    private void OpenWaypointFromList (Collection<Waypoint> waypoints)
    {
        int npoints = waypoints.size ();
        if (npoints == 1) {

            /*
             * Just one waypoint within range, select it directly.
             */
            WaypointSelected (waypoints.iterator ().next ());
        } else if (npoints > 1) {

            /*
             * More than one waypoint within range, display selection dialog list.
             */
            CharSequence[] names = new CharSequence[npoints];
            int i = 0;
            for (Waypoint wp : waypoints) {
                names[i++] = wp.MenuKey ();
            }
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Select waypoint");
            adb.setItems (names, new OpenWaypointAtLatLonSelected (waypoints));
            adb.setNegativeButton ("Cancel", null);
            adb.create ().show ();
        }
    }

    /**
     * One of the waypoints from the selection dialog was picked.
     */
    private class OpenWaypointAtLatLonSelected implements DialogInterface.OnClickListener {
        private Collection<Waypoint> nearby;
        public OpenWaypointAtLatLonSelected (Collection<Waypoint>  ns)
        {
            nearby = ns;
        }
        @Override
        public void onClick (DialogInterface dialog, int which)
        {
            int n = -1;
            for (Waypoint wp : nearby) {
                if (++ n == which) {
                    WaypointSelected (wp);
                    break;
                }
            }
        }
    }

    /**
     * Set the given waypoint as being currently selected for viewing.
     * @param wp = waypoint to establish
     */
    public void WaypointSelected (Waypoint wp)
    {
        selectedWaypoint = wp;
        if (wp == null) {
            searchTextView.setText ("");
            waypointLinear.removeAllViews ();
            findButton.setEnabled (false);
            destinationButton.setEnabled (false);
            locationButton.setEnabled (false);
            metarButton.setEnabled (false);
            infoButton.setEnabled (false);
            downloadButton.setEnabled (false);
        } else {
            searchTextView.setText (wp.ident);
            waypointLinear.removeAllViews ();
            wp.GetDetailViews (this, waypointLinear);
            destinationButton.setEnabled (true);
            locationButton.setEnabled (true);
            metarButton.setEnabled (wp.HasMetar ());
            infoButton.setEnabled (wp.HasInfo ());
            downloadButton.setEnabled (wp.NeedsDwnld ());
        }
        wairToNow.SetCurrentTab (this);
    }

    /**
     * Text box used to enter the waypoint name in.
     */
    private class SearchTextView extends EditText implements TextView.OnEditorActionListener, TextWatcher {
        public SearchTextView (Context ctx)
        {
            super (ctx);
            setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            setSingleLine (true);
            wairToNow.SetTextSize (this);
            setOnEditorActionListener (this);
            addTextChangedListener (this);
        }

        @Override  // OnEditorActionListener
        public boolean onEditorAction (TextView v, int actionId, KeyEvent event)
        {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                findButton.onClick (findButton);
            }
            return false;
        }

        @Override  // TextWatcher
        public void afterTextChanged (Editable s)
        {
            findButton.setEnabled (s.toString ().length () > 0);
        }

        @Override  // TextWatcher
        public void beforeTextChanged (CharSequence s, int start, int count, int after) { }
        @Override  // TextWatcher
        public void onTextChanged (CharSequence s, int start, int before, int count) { }
    }

    /**
     * Button used to initiate search for waypoint
     * based on string entered in SearchTextView.
     */
    private class FindButton extends Button implements OnClickListener {
        public FindButton (Context ctx)
        {
            super (ctx);
            setText ("Find");
            wairToNow.SetTextSize (this);
            setEnabled (false);
            setOnClickListener (this);
        }

        /**
         * Called when the Find button is clicked.
         * Search the waypoint list for something with that name.
         * If more than one match, display dialog for selection.
         * Otherwise directly open the one match.
         */
        @Override
        public void onClick (View v)
        {
            selectedWaypoint = null;
            destinationButton.setEnabled (false);
            locationButton.setEnabled (false);
            metarButton.setEnabled (false);
            infoButton.setEnabled (false);
            downloadButton.setEnabled (false);
            setEnabled (false);

            String key = searchTextView.getText ().toString ();
            key = Waypoint.NormalizeKey (key);
            searchTextView.setText (key);
            LinkedList<Waypoint> matches = Waypoint.GetWaypointsMatchingKey (key);

            if (matches.size () == 0) {
                TextView dt = new TextView (wairToNow);
                wairToNow.SetTextSize (dt);
                dt.setText ("waypoint " + key + " not found in database");
                waypointLinear.removeAllViews ();
                waypointLinear.addView (dt);
            } else {
                AddOldSearchButton (key, true);
                OpenWaypointFromList (matches);
            }
        }
    }

    /**
     * Button used to set the waypoint's location as the course line destination.
     */
    private class DestinationButton extends Button implements OnClickListener {
        public DestinationButton (Context ctx)
        {
            super (ctx);
            setText ("GoTo");
            wairToNow.SetTextSize (this);
            setEnabled (false);
            setOnClickListener (this);
        }

        /**
         * Called when the destination button is clicked.
         * Sets the course line on the display to go from current
         * position to wherever the waypoint is.
         */
        @Override
        public void onClick (View v)
        {
            wairToNow.SetDestinationWaypoint (selectedWaypoint);
        }
    }

    /**
     * Button used to set the waypoint's location as the course line destination.
     */
    private class LocationButton extends Button implements OnClickListener {
        public LocationButton (Context ctx)
        {
            super (ctx);
            setText ("Locate");
            wairToNow.SetTextSize (this);
            setEnabled (false);
            setOnClickListener (this);
        }

        /**
         * Called when the location button is clicked.
         * Sets the waypoint in the middle of the screen.
         */
        @Override
        public void onClick (View v)
        {
            if (selectedWaypoint != null) {
                wairToNow.chartView.SetCenterLatLon (selectedWaypoint.lat, selectedWaypoint.lon);
                wairToNow.SetCurrentTab (wairToNow.chartView);
            }
        }
    }

    /**
     * Button used to get the METAR and TAF information for the current waypoint.
     */
    private class MetarButton extends Button implements OnClickListener {
        public MetarButton (Context ctx)
        {
            super (ctx);
            setText ("METAR");
            wairToNow.SetTextSize (this);
            setEnabled (false);
            setOnClickListener (this);
        }

        @Override
        public void onClick (View v)
        {
            try {
                Intent intent = new Intent (Intent.ACTION_VIEW);
                intent.setData (Uri.parse ("http://aviationweather.gov/adds/tafs/?station_ids=" + selectedWaypoint.ident +
                                           "&std_trans=translated&submit_both=Get+TAFs+and+METARs"));
                wairToNow.startActivity (intent);
            } catch (Throwable e) {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Error fetching METAR web page");
                adb.setMessage (e.getMessage ());
                adb.create ().show ();
            }
        }
    }

    /**
     * Button used to display the information file for the current waypoint.
     */
    private class InfoButton extends Button implements OnClickListener {

        public InfoButton (Context ctx)
        {
            super (ctx);
            setText ("Info");
            wairToNow.SetTextSize (this);
            setEnabled (false);
            setOnClickListener (this);
        }

        @Override
        public void onClick (View v)
        {
            if (selectedWaypoint.HasInfo ()) {
                try {

                    /*
                     * Display using a WebView.
                     */
                    int waypointexpdate = MaintView.GetWaypointExpDate ();
                    String faaident = ((Waypoint.Airport)selectedWaypoint).faaident;
                    char subdir = faaident.charAt (0);
                    String subnam = faaident.substring (1);
                    String name = WairToNow.dbdir + "/datums/aptinfo_" + waypointexpdate + '/' +
                            subdir + '/' + subnam + ".html.gz";
                    FileInputStream fis = new FileInputStream (name);
                    BufferedInputStream bis = new BufferedInputStream (fis, 4096);
                    GZIPInputStream zis = new GZIPInputStream (bis);
                    InputStreamReader isr = new InputStreamReader (zis);
                    StringBuilder sb = new StringBuilder ();
                    char[] buf = new char[4096];
                    int len;
                    while ((len = isr.read (buf)) > 0) {
                        sb.append (buf, 0, len);
                    }
                    isr.close ();
                    MyWebView webView = new MyWebView (sb.toString ());
                    wairToNow.SetCurrentTab (webView);
                } catch (Throwable e) {
                    AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                    adb.setTitle ("Error fetching info web page");
                    adb.setMessage (e.getMessage ());
                    adb.create ().show ();
                }
            }
        }
    }

    private class MyWebView extends WebView implements WairToNow.CanBeMainView {
        public MyWebView (String content)
        {
            super (wairToNow);

            getSettings ().setBuiltInZoomControls (true);
            getSettings ().setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
            getSettings ().setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
            getSettings ().setJavaScriptEnabled (true);

            loadData (content, "text/html", null);
        }

        // CanBeMainView implementation
        public void OpenDisplay () { }
        public void CloseDisplay () { }

        // say which tab button is active
        public String GetTabName ()
        {
            return WaypointView.this.GetTabName ();
        }

        // if back button clicked, show the waypoint search page
        public View GetBackPage ()
        {
            return WaypointView.this;
        }

        // if page button (at bottom of screen) clicked, show waypoint search page
        public void ReClicked ()
        {
            wairToNow.SetCurrentTab (WaypointView.this);
        }
    }

    /**
     * Button used to download information page and plates.
     */
    private class DownloadButton extends Button implements OnClickListener, Runnable {

        public DownloadButton (Context ctx)
        {
            super (ctx);
            setText ("Dwnld");
            wairToNow.SetTextSize (this);
            setEnabled (false);
            setOnClickListener (this);
        }

        // button clicked
        @Override
        public void onClick (View v)
        {
            setEnabled (false);
            selectedWaypoint.StartDwnld (wairToNow, this);
        }

        // download complete
        @Override
        public void run ()
        {
            WaypointSelected (selectedWaypoint);
        }
    }
}
