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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This view presents a menu so the user can select a FAA waypoint by name.
 * It can also pick a waypoint that is nearby a given lat/lon.
 */
@SuppressLint({ "SetTextI18n", "ViewConstructor" })
public class WaypointView extends LinearLayout
        implements WairToNow.CanBeMainView {
    public final static String TAG = "WairToNow";

    private DestinationButton destinationButton;
    private DetentHorizontalScrollView oldSearchScroll;
    private File oldSearchFile;
    private FindButton findButton;
    private LinearLayout inputBoxLine;
    private LinearLayout oldSearchRow;
    private LocationButton locationButton;
    private OldSearchButtonListener oldSearchLis;
    public  PlateView selectedPlateView;
    private RNavOffsetButton rnavOffsetButton;
    private SearchTextView searchTextView;
    private String tabName;
    private TextView titleText;
    public  WairToNow wairToNow;
    public  Waypoint selectedWaypoint;
    public  Waypoint.Within waypointsWithin;
    private WebView waypointWebView;

    @SuppressLint("SetJavaScriptEnabled")
    public WaypointView (WairToNow ctx, String tn)
    {
        super (ctx);
        wairToNow = ctx;
        tabName = tn;

        setOrientation (VERTICAL);

        /*
         * Start with window title.
         */
        titleText = new TextView (ctx);
        titleText.setText ("FAA Waypoints");
        wairToNow.SetTextSize (titleText);

        /*
         * This is a box the user can enter the waypoint name in.
         * Consists of the box and a Find button.
         */
        searchTextView = new SearchTextView (ctx);
        searchTextView.setLayoutParams (new LinearLayout.LayoutParams (ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));
        findButton = new FindButton (ctx);

        inputBoxLine = new LinearLayout (ctx);
        inputBoxLine.setOrientation (HORIZONTAL);
        inputBoxLine.addView (searchTextView);
        inputBoxLine.addView (findButton);

        /*
         * Make a row of buttons, each containing previous searches.
         */
        oldSearchRow = new LinearLayout (ctx);
        oldSearchRow.setOrientation (HORIZONTAL);
        LoadOldSearchButtons ();
        oldSearchScroll = new DetentHorizontalScrollView (ctx);
        ctx.SetDetentSize (oldSearchScroll);
        oldSearchScroll.addView (oldSearchRow);

        /*
         * This button can be clicked to set the waypoint as destination.
         */
        destinationButton = new DestinationButton (ctx);

        /*
         * This button can be clicked to set the waypoint as center location in screen.
         */
        locationButton = new LocationButton (ctx);

        /*
         * This button can be clicked to add an RNAV-style offset to the waypoint.
         */
        rnavOffsetButton = new RNavOffsetButton ();

        /*
         * This box is used to describe the waypoint.
         */
        waypointWebView = new WebView (ctx);
        WebSettings settings = waypointWebView.getSettings ();
        settings.setBuiltInZoomControls (true);
        settings.setDomStorageEnabled (true);
        settings.setJavaScriptEnabled (true);
        settings.setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
        settings.setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
        settings.setSupportZoom (true);

        /*
         * Reset waypoint area search database.
         */
        waypointsWithin = new Waypoint.Within (wairToNow);
    }

    /**
     * For portrait orientation:
     *   Title
     *   InputBox Find
     *   OldSearchButtons
     *   Goto Locate RNAV
     *   WebView
     *
     * For landscape orientation:
     *   Title
     *   InputBox Find
     *   Goto Locate RNAV * OldSearchButtons
     *   WebView
     */
    @Override  // CanBeMainView
    public void OrientationChanged ()
    {
        removeAllViews ();

        addView (titleText);
        addView (inputBoxLine);

        clearParent (oldSearchScroll);
        clearParent (destinationButton);
        clearParent (locationButton);
        clearParent (rnavOffsetButton);

        if (wairToNow.displayHeight > wairToNow.displayWidth) {
            // put old search buttons on a line by themselves
            addView (oldSearchScroll);
            // make a scrollable line just for the function buttons by themselves
            LinearLayout ll1 = new LinearLayout (wairToNow);
            ll1.setOrientation (HORIZONTAL);
            ll1.addView (destinationButton);
            ll1.addView (locationButton);
            ll1.addView (rnavOffsetButton);
            DetentHorizontalScrollView hs1 = new DetentHorizontalScrollView (wairToNow);
            wairToNow.SetDetentSize (hs1);
            hs1.addView (ll1);
            addView (hs1);
        } else {
            // make a single line with function buttons and old search buttons
            // put a diamond between function buttons and old search buttons
            TextView diamond = new TextView (wairToNow);
            wairToNow.SetTextSize (diamond);
            diamond.setText (" \u25C6 ");
            LinearLayout ll1 = new LinearLayout (wairToNow);
            ll1.setOrientation (HORIZONTAL);
            ll1.addView (destinationButton);
            ll1.addView (locationButton);
            ll1.addView (rnavOffsetButton);
            ll1.addView (diamond);
            ll1.addView (oldSearchScroll);
            addView (ll1);
        }

        addView (waypointWebView);
    }

    private static void clearParent (View v)
    {
        ViewParent p = v.getParent ();
        if (p instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) p;
            g.removeView (v);
        }
    }

    /**
     * New GPS location received, update any georef'd plate.
     */
    public void SetGPSLocation ()
    {
        if (selectedPlateView != null) {
            selectedPlateView.SetGPSLocation ();
        }
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
        OrientationChanged ();
        LoadOldSearchButtons ();  // reload buttons in case changed by other WaypointView instance
    }
    @Override
    public View GetBackPage ()
    {
        if ((waypointWebView != null) && (waypointWebView.canGoBack ())) {
            waypointWebView.goBack ();
            return this;
        }
        return wairToNow.chartView;
    }
    @Override
    public void ReClicked ()
    {
        if (selectedWaypoint != null) {
            selectedWaypoint.GetDetailViews (WaypointView.this, waypointWebView);
        }
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    /**
     * Find waypoints near the given lat/lon and let user select one and open it.
     */
    public void OpenWaypointAtLatLon (final double lat, final double lon, double radiusnm)
    {
        // get all waypoints within box from given lat/lon
        double delat = radiusnm / Lib.NMPerDeg;
        double delon = delat / Math.cos (Math.toRadians (lat));
        Collection<Waypoint> wpcollection = new Waypoint.Within (wairToNow).Get (lat - delat, lat + delat, lon - delon, lon + delon);

        // remove all waypoints greater than radius from the collection
        for (Iterator<Waypoint> it = wpcollection.iterator (); it.hasNext ();) {
            Waypoint wp = it.next ();
            double dist = Lib.LatLonDist (lat, lon, wp.lat, wp.lon);
            if (dist > radiusnm) {
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
        private double llat, llon;
        public WPSorter (double lat, double lon)
        {
            llat = lat;
            llon = lon;
        }
        public int compare (Waypoint a, Waypoint b)
        {
            double dista = Lib.LatLonDist (llat, llon, a.lat, a.lon);
            double distb = Lib.LatLonDist (llat, llon, b.lat, b.lon);
            return Double.compare (dista, distb);
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
            noWayptSelected ();
        } else {
            searchTextView.setText (wp.ident);
            wp.GetDetailViews (this, waypointWebView);
            destinationButton.setEnabled (true);
            locationButton.setEnabled (true);
            rnavOffsetButton.setEnabled (true);
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
            if ((actionId == EditorInfo.IME_ACTION_DONE) ||
                    (actionId == EditorInfo.IME_ACTION_GO) ||
                    (actionId == EditorInfo.IME_ACTION_NEXT) ||
                    (actionId == EditorInfo.IME_ACTION_SEARCH)) {
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
            noWayptSelected ();

            // get what user typed in to search for
            String key = searchTextView.getText ().toString ();

            // check for ident[rnavoffset string
            // if so, try searching for that specially
            // cuz NormalizeKey() will munge it
            LinkedList<Waypoint> matches = null;
            try {
                Waypoint.RNavParse rnavParse = new Waypoint.RNavParse (key);
                if (rnavParse.rnavsuffix != null) {
                    matches = Waypoint.GetWaypointsByIdent (key, wairToNow);
                }
            } catch (Exception e) {
                Lib.Ignored ();
            }

            // if not ident[rnavoffset, do a general search
            if (matches == null) {
                key = Waypoint.NormalizeKey (key);
                searchTextView.setText (key);
                matches = Waypoint.GetWaypointsMatchingKey (key, wairToNow);
            }

            // let user select from the matches, if any
            if (matches.size () == 0) {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setMessage ("waypoint " + key + " not found in database");
                adb.setPositiveButton ("OK", null);
                adb.show ();
                waypointWebView.loadUrl ("file:///android_asset/wpviewblank.html");
            } else {
                AddOldSearchButton (key, true);
                OpenWaypointFromList (matches);
            }
        }
    }

    // no waypoint selected, disable a bunch of buttons etc.
    private void noWayptSelected ()
    {
        waypointWebView.loadUrl ("file:///android_asset/wpviewblank.html");
        findButton.setEnabled (false);
        destinationButton.setEnabled (false);
        locationButton.setEnabled (false);
        rnavOffsetButton.setEnabled (false);
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
     * Button used to add/remove an RNAV offset from the current waypoint.
     */
    private class RNavOffsetButton extends Button implements OnClickListener {
        public RNavOffsetButton ()
        {
            super (wairToNow);
            setText ("RNAV");
            wairToNow.SetTextSize (this);
            setEnabled (false);
            setOnClickListener (this);
        }

        /**
         * Called when the RNAV button is clicked.
         */
        @Override
        public void onClick (View v)
        {
            if (selectedWaypoint != null) {
                if (selectedWaypoint instanceof Waypoint.RNavOffset) {
                    remRNavOffset ();
                } else {
                    addRNavOffset ();
                }
            }
        }

        /**
         * Add an RNAV offset to currently selected waypoint.
         */
        private void addRNavOffset ()
        {
            Context ctx = getContext ();

            // create data entry boxes
            final EditText radial = new EditText (ctx);
            final MagTrueSpinner magnetic = new MagTrueSpinner (ctx);
            final EditText distance = new EditText (ctx);

            // three digits each should be plenty
            radial.setInputType (InputType.TYPE_CLASS_NUMBER);
            distance.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            radial.setSingleLine ();
            distance.setSingleLine ();
            radial.setEms (3);
            distance.setEms (3);

            // make a linear layout for the boxes
            LinearLayout ll = new LinearLayout (ctx);
            ll.setOrientation (HORIZONTAL);
            ll.addView (radial);
            ll.addView (new TextViewString (ctx, "\u00B0"));
            ll.addView (magnetic);
            ll.addView (new TextViewString (ctx, "@"));
            ll.addView (distance);
            ll.addView (new TextViewString (ctx, "nm"));

            // wrap in an horizontal scroll in case of small screen
            DetentHorizontalScrollView sv = new DetentHorizontalScrollView (ctx);
            wairToNow.SetDetentSize (sv);
            sv.addView (ll);

            // create dialog box
            AlertDialog.Builder adb = new AlertDialog.Builder (ctx);
            adb.setTitle ("Add RNAV offset to " + selectedWaypoint.ident);
            adb.setView (sv);
            adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    // parse numeric radial and distance values
                    // don't bother if distance is zero
                    double rnavradl, rnavdist;
                    try {
                        rnavradl = Double.parseDouble (radial.getText ().toString ());
                        rnavdist = Double.parseDouble (distance.getText ().toString ());
                        if (rnavdist == 0) return;
                    } catch (NumberFormatException nfe) {
                        return;
                    }

                    // make new waypoint at an offset from the current waypoint
                    boolean mag = magnetic.getMag ();
                    String suffix = Lib.DoubleNTZ (rnavradl) + (mag ? "@" : "T@") + Lib.DoubleNTZ (rnavdist);
                    Waypoint.RNavOffset rnavwp = new Waypoint.RNavOffset (selectedWaypoint,
                            rnavdist, rnavradl, mag, suffix);

                    // select it
                    AddOldSearchButton (rnavwp.ident, true);
                    WaypointSelected (rnavwp);
                }
            });
            adb.setNegativeButton ("Cancel", null);

            // display dialog
            adb.show ();
        }

        /**
         * Remove the RNAV offset from the currently selected waypoint.
         */
        private void remRNavOffset ()
        {
            Context ctx = getContext ();

            final Waypoint.RNavOffset rnavOffset = (Waypoint.RNavOffset) selectedWaypoint;

            // create dialog box
            AlertDialog.Builder adb = new AlertDialog.Builder (ctx);
            adb.setTitle ("Remove RNAV offset from " + selectedWaypoint.ident);
            adb.setMessage ("...and return to plain " + rnavOffset.basewp.ident);
            adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i)
                {
                    WaypointSelected (rnavOffset.basewp);
                }
            });
            adb.setNegativeButton ("Cancel", null);

            // display dialog
            adb.show ();
        }
    }
}
