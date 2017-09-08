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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.TreeMap;

import static android.widget.LinearLayout.VERTICAL;

@SuppressLint("ViewConstructor")
public class PlanView extends ScrollView implements WairToNow.CanBeMainView {
    private final static String TAG = "WairToNow";
    private final static long pretendInterval = 1000;

    private boolean displayOpen;
    private boolean pretendEnabled;
    private boolean ptendTimerPend;
    private Button ptendCapCen;
    private CheckBox ptendCheckbox;
    private EditText fromAirport;
    private EditText fromDistEdit;
    private EditText fromHdgEdit;
    private EditText ptendAltitude;
    private EditText ptendClimbRt;
    private EditText ptendHeading;
    private EditText ptendSpeed;
    private EditText ptendTurnRt;
    private EditText toDistEdit;
    private EditText toHdgEdit;
    private double lastPtendLat;
    private double lastPtendLon;
    private LatLonView ptendLat;
    private LatLonView ptendLon;
    private LinearLayout linearLayout;
    private LinearLayout lp1, lp2, lp3, lp4, lp5, lp6;
    private LinearLayout lv0, lv1, lv2, lv3;
    private long ptendTime;
    private OnClickListener locateListener;
    private Runnable pretendStepper;
    private TextView tp0;
    private TextView tv0;
    private WairToNow wairToNow;

    @SuppressLint("SetTextI18n")
    public PlanView (WairToNow ctx)
    {
        super (ctx);
        wairToNow = ctx;
        pretendStepper = new Runnable () {
            @Override
            public void run ()
            {
                PretendStep ();
            }
        };

        // Pretend ...

        tp0 = TextString ("----------------------------");

        ptendCheckbox = new CheckBox (ctx);
        ptendCheckbox.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view) {
                SetPretendEnabled (ptendCheckbox.isChecked ());
            }
        });

        lp1 = new LinearLayout (ctx);
        lp1.setOrientation (LinearLayout.HORIZONTAL);
        lp1.addView (ptendCheckbox);
        lp1.addView (TextString ("Pretend to be at "));

        ptendLat = new LatLonView (ctx, false);
        ptendLon = new LatLonView (ctx, true);
        ptendLat.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize);
        ptendLon.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize);

        ptendCapCen = new Button (ctx);
        ptendCapCen.setText ("(capture center)");
        wairToNow.SetTextSize (ptendCapCen);
        ptendCapCen.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view) {
                PixelMapper pmap = wairToNow.chartView.pmap;
                ptendLat.setVal (pmap.centerLat);
                ptendLon.setVal (pmap.centerLon);
            }
        });

        ptendSpeed = new EditText (ctx);
        ptendSpeed.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ptendSpeed.setEms (5);
        wairToNow.SetTextSize (ptendSpeed);

        lp2 = new LinearLayout (ctx);
        lp2.setOrientation (LinearLayout.HORIZONTAL);
        lp2.addView (TextString ("speed "));
        lp2.addView (ptendSpeed);
        lp2.addView (TextString ("kts"));

        ptendHeading = new EditText (ctx);
        ptendHeading.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ptendHeading.setEms (5);
        wairToNow.SetTextSize (ptendHeading);

        lp3 = new LinearLayout (ctx);
        lp3.setOrientation (LinearLayout.HORIZONTAL);
        lp3.addView (TextString ("heading "));
        lp3.addView (ptendHeading);
        lp3.addView (TextString ("deg true"));

        ptendAltitude = new EditText (ctx);
        ptendAltitude.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ptendAltitude.setEms (5);
        wairToNow.SetTextSize (ptendAltitude);

        lp5 = new LinearLayout (ctx);
        lp5.setOrientation (LinearLayout.HORIZONTAL);
        lp5.addView (TextString ("altitude "));
        lp5.addView (ptendAltitude);
        lp5.addView (TextString ("feet"));

        ptendTurnRt = new EditText (ctx);
        ptendTurnRt.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        ptendTurnRt.setEms (3);
        wairToNow.SetTextSize (ptendTurnRt);

        lp4 = new LinearLayout (ctx);
        lp4.setOrientation (LinearLayout.HORIZONTAL);
        lp4.addView (TextString ("turn rate "));
        lp4.addView (ptendTurnRt);
        lp4.addView (TextString ("deg per sec"));

        ptendClimbRt = new EditText (ctx);
        ptendClimbRt.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        ptendClimbRt.setEms (6);
        wairToNow.SetTextSize (ptendClimbRt);

        lp6 = new LinearLayout (ctx);
        lp6.setOrientation (LinearLayout.HORIZONTAL);
        lp6.addView (TextString ("climb rate "));
        lp6.addView (ptendClimbRt);
        lp6.addView (TextString ("ft per min"));

        // Find ...

        tv0 = TextString ("----------------------------");

        Button findButton = new Button (ctx);
        findButton.setText ("Find");
        wairToNow.SetTextSize (findButton);
        findButton.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                FindButtonClicked ();
            }
        });

        lv0 = new LinearLayout (ctx);
        lv0.setOrientation (LinearLayout.HORIZONTAL);
        lv0.addView (findButton);
        lv0.addView (TextString (" airports"));

        fromDistEdit = new EditText (ctx);
        fromDistEdit.setInputType (InputType.TYPE_CLASS_NUMBER);
        fromDistEdit.setEms (5);
        wairToNow.SetTextSize (fromDistEdit);

        toDistEdit = new EditText (ctx);
        toDistEdit.setInputType (InputType.TYPE_CLASS_NUMBER);
        toDistEdit.setEms (5);
        wairToNow.SetTextSize (toDistEdit);

        lv1 = new LinearLayout (ctx);
        lv1.setOrientation (LinearLayout.HORIZONTAL);
        lv1.addView (TextString ("between distances "));
        lv1.addView (fromDistEdit);
        lv1.addView (TextString (" and "));
        lv1.addView (toDistEdit);
        lv1.addView (TextString ("nm"));

        fromHdgEdit = new EditText (ctx);
        fromHdgEdit.setInputType (InputType.TYPE_CLASS_NUMBER);
        fromHdgEdit.setEms (3);
        wairToNow.SetTextSize (fromHdgEdit);

        toHdgEdit = new EditText (ctx);
        toHdgEdit.setInputType (InputType.TYPE_CLASS_NUMBER);
        toHdgEdit.setEms (3);
        wairToNow.SetTextSize (toHdgEdit);

        lv2 = new LinearLayout (ctx);
        lv2.setOrientation (LinearLayout.HORIZONTAL);
        lv2.addView (TextString ("between headings "));
        lv2.addView (fromHdgEdit);
        lv2.addView (TextString (" and "));
        lv2.addView (toHdgEdit);
        lv2.addView (TextString ("deg true"));

        fromAirport = new EditText (ctx);
        fromAirport.setEms (5);
        fromAirport.setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        fromAirport.setSingleLine ();
        wairToNow.SetTextSize (fromAirport);

        lv3 = new LinearLayout (ctx);
        lv3.setOrientation (LinearLayout.HORIZONTAL);
        lv3.addView (TextString ("from airport icao id "));
        lv3.addView (fromAirport);

        locateListener = new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                LocateListener (view);
            }
        };

        /*
         * Layout the screen and display it.
         */
        linearLayout = new LinearLayout (ctx);
        linearLayout.setOrientation (VERTICAL);

        Reset ();

        addView (linearLayout);

        /*// set up a course for debugging
        ptendCheckbox.setChecked (true);
        ptendAltitude.setText ("3000");
        ptendHeading.setText ("212");
        ptendSpeed.setText ("360");
        ptendTurnRt.setText ("0");
        lastPtendLat =  41.724;    //   38.8; // 42.5;
        lastPtendLon = -71.428224; // -104.7; // -71.0;
        ptendLat.setVal (lastPtendLat);
        ptendLon.setVal (lastPtendLon);
        SetPretendEnabled (true);
        */
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Plan";
    }

    @Override  // CanBeMainView
    public int GetOrientation ()
    {
        return ActivityInfo.SCREEN_ORIENTATION_USER;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        displayOpen = true;
        String format = wairToNow.optionsView.LatLonString (10.0/3600.0, 'N', 'S');
        ptendLat.setFormat (format);
        ptendLon.setFormat (format);

        // if PlanView display is open, next simulated lat/lon is to be taken
        // from the ptendLat,Lon boxes
        lastPtendLat = -99999.0;
        lastPtendLon = -99999.0;
    }

    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        displayOpen = false;
        if (pretendEnabled && !ptendTimerPend) {
            ptendTimerPend = true;
            WairToNow.wtnHandler.runDelayed (pretendInterval, pretendStepper);
        }
    }

    @Override  // WairToNow.CanBeMainView
    public void ReClicked ()
    { }

    @Override  // WairToNow.CanBeMainView
    public View GetBackPage ()
    {
        return this;
    }

    private void Reset ()
    {
        LinearLayout.LayoutParams llpwc = new LinearLayout.LayoutParams (LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        linearLayout.removeAllViews ();

        // Links ...

        linearLayout.addView (TextString ("----------------------------"), llpwc);
        linearLayout.addView (TextString ("Opens page in web browser."), llpwc);
        linearLayout.addView (TextString ("Internet access required."), llpwc);
        TreeMap<String,String> alllines = ReadWholeLinkFile ();
        for (String title : alllines.keySet ()) {
            linearLayout.addView (new OpenLinkButton (title, alllines.get (title)), llpwc);
        }
        linearLayout.addView (TextString ("Long-click above button to customize."), llpwc);
        linearLayout.addView (new AddLinkButton (), llpwc);

        // Pretend ...

        linearLayout.addView (tp0, llpwc);
        linearLayout.addView (lp1, llpwc);
        linearLayout.addView (ptendLat, llpwc);
        linearLayout.addView (ptendLon, llpwc);
        linearLayout.addView (ptendCapCen, llpwc);
        linearLayout.addView (lp2, llpwc);
        linearLayout.addView (lp3, llpwc);
        linearLayout.addView (lp5, llpwc);
        linearLayout.addView (lp4, llpwc);
        linearLayout.addView (lp6, llpwc);

        // Find ...

        linearLayout.addView (tv0, llpwc);
        linearLayout.addView (lv0, llpwc);
        linearLayout.addView (lv1, llpwc);
        linearLayout.addView (lv2, llpwc);
        linearLayout.addView (lv3, llpwc);
    }

    private TextView TextString (String str)
    {
        TextView tv = new TextView (wairToNow);
        tv.setText (str);
        wairToNow.SetTextSize (tv);
        return tv;
    }

    /**
     * Click on the link button and it opens the corresponding web page.
     * Long click allows editing link button.
     */
    private class OpenLinkButton extends Button implements OnClickListener, OnLongClickListener {
        private String link;
        private String title;

        public OpenLinkButton (String title, String link)
        {
            super (wairToNow);

            this.link = link;
            this.title = title;

            setOnClickListener (this);
            setOnLongClickListener (this);
            setText (title);
            wairToNow.SetTextSize (this);
        }

        @Override
        public void onClick (View v)
        {
            try {
                Intent intent = new Intent (Intent.ACTION_VIEW);
                intent.setData (Uri.parse (link));
                wairToNow.startActivity (intent);
            } catch (Throwable e) {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Error fetching " + title + " web page " + link);
                adb.setMessage (e.getMessage ());
                adb.create ().show ();
            }
        }

        @Override
        public boolean onLongClick (View v)
        {
            final EditText editTitle = new EditText (wairToNow);
            editTitle.setSingleLine ();
            editTitle.setText (title);

            final EditText editLink  = new EditText (wairToNow);
            editLink.setSingleLine ();
            editLink.setText (link);

            LinearLayout ll = new LinearLayout (wairToNow);
            ll.setOrientation (VERTICAL);
            ll.addView (editTitle);
            ll.addView (editLink);

            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Customize " + title);
            adb.setView (ll);

            adb.setPositiveButton ("Apply", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    TreeMap<String,String> alllines = ReadWholeLinkFile ();
                    alllines.remove (title);
                    alllines.put (editTitle.getText ().toString (), editLink.getText ().toString ());
                    WriteWholeLinkFile (alllines);
                    Reset ();
                }
            });

            adb.setNeutralButton ("Cancel", null);

            adb.setNegativeButton ("Delete", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    TreeMap<String,String> alllines = ReadWholeLinkFile ();
                    alllines.remove (title);
                    WriteWholeLinkFile (alllines);
                    Reset ();
                }
            });

            adb.show ();

            return true;
        }
    }

    /**
     * Click on add link button allows addition of a new button.
     */
    private class AddLinkButton extends Button implements OnClickListener {
        @SuppressLint("SetTextI18n")
        public AddLinkButton ()
        {
            super (wairToNow);

            setOnClickListener (this);
            setText ("Add Button");
            wairToNow.SetTextSize (this);
        }

        @Override
        public void onClick (View v)
        {
            final EditText editTitle = new EditText (wairToNow);
            editTitle.setSingleLine ();
            editTitle.setHint ("button title");

            final EditText editLink  = new EditText (wairToNow);
            editLink.setSingleLine ();
            editLink.setHint ("web page link");

            LinearLayout ll = new LinearLayout (wairToNow);
            ll.setOrientation (VERTICAL);
            ll.addView (editTitle);
            ll.addView (editLink);

            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Add Button");
            adb.setView (ll);

            adb.setPositiveButton ("Apply", new DialogInterface.OnClickListener () {
                @Override
                public void onClick (DialogInterface dialogInterface, int i) {
                    TreeMap<String,String> alllines = ReadWholeLinkFile ();
                    alllines.put (editTitle.getText ().toString (), editLink.getText ().toString ());
                    WriteWholeLinkFile (alllines);
                    Reset ();
                }
            });

            adb.setNeutralButton ("Cancel", null);

            adb.show ();
        }
    }

    /**
     * Read link buttons file.
     * If not present, fill in default buttons.
     */
    private static TreeMap<String,String> ReadWholeLinkFile ()
    {
        TreeMap<String,String> alllines = new TreeMap<> ();
        try {
            BufferedReader br = new BufferedReader (new FileReader (WairToNow.dbdir + "/linkbuttons.txt"), 4096);
            try {
                for (String line; (line = br.readLine ()) != null;) {
                    String[] parts = line.split (",,,,,");
                    alllines.put (parts[0], parts[1]);
                }
            } finally {
                br.close ();
            }
        } catch (FileNotFoundException fnfe) {
            alllines.put ("ADDS - Aviation Weather", "https://www.aviationweather.gov/adds/");
            alllines.put ("AirNav - Fuel Prices", "https://www.airnav.com/fuel/local.html");
            alllines.put ("DUATS - File Flight Plan", "https://www.duats.com/index.php?mobile=true");
            alllines.put ("SkyVector - Planning", "https://skyvector.com/");
        } catch (Exception e) {
            Log.w (TAG, "error reading linkbuttons.txt", e);
        }
        return alllines;
    }

    /**
     * Write modified link buttons to text file.
     */
    private static void WriteWholeLinkFile (TreeMap<String,String> alllines)
    {
        try {
            BufferedWriter bw = new BufferedWriter (new FileWriter (WairToNow.dbdir + "/linkbuttons.txt.tmp"));
            try {
                for (String title : alllines.keySet ()) {
                    bw.write (title + ",,,,," + alllines.get (title) + "\n");
                }
            } finally {
                bw.close ();
            }
            Lib.RenameFile (WairToNow.dbdir + "/linkbuttons.txt.tmp", WairToNow.dbdir + "/linkbuttons.txt");
        } catch (IOException ioe) {
            Log.w (TAG, "error writing linkbuttons.txt", ioe);
        }
    }

    /**
     * The Pretend checkbox was just checked/unchecked.
     */
    private void SetPretendEnabled (boolean enab)
    {
        if (pretendEnabled != enab) {
            pretendEnabled = enab;
            if (enab) {
                ptendTime = System.currentTimeMillis ();
                wairToNow.gpsDisabled ++;
                if (!ptendTimerPend) {
                    ptendTimerPend = true;
                    WairToNow.wtnHandler.runDelayed (pretendInterval, pretendStepper);
                }
            } else {
                wairToNow.gpsDisabled --;
            }
        }
    }

    /**
     * The Find button was just clicked.
     * Search database for airports that match the given criteria
     * and display the list with a "locate" button for each that
     * shows where the airport is on the chart.
     */
    @SuppressLint("SetTextI18n")
    private void FindButtonClicked ()
    {
        try {
            int fromDist = Integer.parseInt (fromDistEdit.getText ().toString ());
            int toDist   = Integer.parseInt (toDistEdit.getText ().toString ());
            int fromHdg  = Integer.parseInt (fromHdgEdit.getText ().toString ());
            int toHdg    = Integer.parseInt (toHdgEdit.getText ().toString ());

            if ((fromDist < 0) || (toDist < fromDist)) {
                throw new Exception ("bad distance range");
            }
            fromDistEdit.setText (Integer.toString (fromDist));
            toDistEdit.setText   (Integer.toString (toDist));

            while (fromHdg < 0) fromHdg += 360;
            while (toHdg   < 0) toHdg   += 360;
            fromHdg %= 360;
            toHdg   %= 360;
            fromHdgEdit.setText (Integer.toString (fromHdg));
            toHdgEdit.setText   (Integer.toString (toHdg));
            if (toHdg <= fromHdg) toHdg += 360;

            String fromApt = fromAirport.getText ().toString ().toUpperCase (Locale.US).trim ();
            fromAirport.setText (fromApt);

            /*
             * Clear off any previous results.
             */
            Reset ();

            /*
             * Open database.
             */
            int waypointexpdate = MaintView.GetWaypointExpDate ();
            String dbname = "waypoints_" + waypointexpdate + ".db";
            SQLiteDBs sqldb = SQLiteDBs.create (dbname);

            /*
             * Find starting point airport's lat/lon.
             */
            Cursor result1 = sqldb.query (
                    Waypoint.Airport.dbtable, Waypoint.Airport.dbcols,
                    Waypoint.Airport.dbkeyid + "=?", new String[] { fromApt },
                    null, null, null, null);
            double fromLat, fromLon;
            try {
                if (!result1.moveToFirst ()) {
                    throw new Exception ("starting airport not defined");
                }
                Waypoint.Airport wp = new Waypoint.Airport (result1);
                fromLat = wp.lat;
                fromLon = wp.lon;
            } finally {
                result1.close ();
            }

            /*
             * Read list of airports into waypoint list that are within the given distance range
             * and that are within the given heading range.
             */
            TreeMap<String,Waypoint.Airport> waypoints = new TreeMap<> ();
            Cursor result2 = sqldb.query (
                    "airports", Waypoint.Airport.dbcols,
                    null, null, null, null, null, null);
            try {
                if (result2.moveToFirst ()) do {
                    Waypoint.Airport wp = new Waypoint.Airport (result2);
                    double distNM  = Lib.LatLonDist (fromLat, fromLon, wp.lat, wp.lon);
                    double trueDeg = Lib.LatLonTC (fromLat, fromLon, wp.lat, wp.lon);
                    while (trueDeg < fromHdg) trueDeg += 360;
                    if ((distNM >= fromDist) && (distNM <= toDist) && (trueDeg <= toHdg)) {
                        waypoints.put (wp.ident, wp);
                    }
                } while (result2.moveToNext ());
            } finally {
                result2.close ();
            }

            /*
             * Add the airports to the display.
             */
            for (Waypoint.Airport wp : waypoints.values ()) {
                int distNM  = (int) Lib.LatLonDist (fromLat, fromLon, wp.lat, wp.lon);
                int trueDeg = (int) Lib.LatLonTC (fromLat, fromLon, wp.lat, wp.lon);
                if (trueDeg <= 0) trueDeg += 360;

                String detail = wp.GetDetailText ();
                int i = detail.indexOf ('\n');
                if (i < 0) i = detail.length ();

                Button locateButton = new Button (wairToNow);
                locateButton.setOnClickListener (locateListener);
                locateButton.setTag (wp);
                locateButton.setText (detail.subSequence (0, i) + " ("+ distNM + "nm " + trueDeg + (char)0xB0 + "true)");
                wairToNow.SetTextSize (locateButton);
                linearLayout.addView (locateButton);

                if (++ i < detail.length ()) {
                    detail = detail.substring (i).trim ();
                    linearLayout.addView (TextString (detail));
                }
            }
        } catch (Exception e) {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setMessage (e.getMessage ());
            adb.setPositiveButton ("OK", null);
            adb.show ();
        }
    }

    /**
     * One of the locate airport buttons was just clicked.
     * Show where the airport is on the chart.
     */
    private void LocateListener (View v)
    {
        Waypoint.Airport wp = (Waypoint.Airport) v.getTag ();
        wairToNow.chartView.SetCenterLatLon (wp.lat, wp.lon);
        wairToNow.SetCurrentTab (wairToNow.chartView);
    }

    @SuppressLint("SetTextI18n")
    private void PretendStep ()
    {
        ptendTimerPend = false;
        if (pretendEnabled && !displayOpen) {

            /*
             * Get lat/lon and time from previous interval.
             * If display is open, we just keep taking position from the ptendLat,Lon boxes.
             * If display is closed, take first point from the ptendLat,Lon boxes, then
             * use lastPtendLat,Lon from then on.
             */
            long  oldnow = ptendTime;
            double oldlat = (lastPtendLat != -99999.0) ? lastPtendLat : ptendLat.getVal ();
            double oldlon = (lastPtendLon != -99999.0) ? lastPtendLon : ptendLon.getVal ();

            /*
             * Get speed and heading input by the user.
             */
            double spdkts = 0.0;
            double hdgdeg = 0.0;
            double altft  = 0.0;
            double turnrt = 0.0;
            double climrt = 0.0;
            try { spdkts = Double.parseDouble (ptendSpeed.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { hdgdeg = Double.parseDouble (ptendHeading.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { altft  = Double.parseDouble (ptendAltitude.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { turnrt = Double.parseDouble (ptendTurnRt.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { climrt = Double.parseDouble (ptendClimbRt.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }

            /*
             * Get updated lat/lon, heading and time.
             */
            long  newnow = oldnow + pretendInterval;
            double distnm = spdkts * (newnow - oldnow) / 1000.0 / 3600.0;
            double newhdg = hdgdeg + (newnow - oldnow) / 1000.0 * turnrt;
            double newlat = Lib.LatHdgDist2Lat (oldlat, newhdg, distnm);
            double newlon = Lib.LatLonHdgDist2Lon (oldlat, oldlon, newhdg, distnm);
            double newalt = altft + (newnow - oldnow) / 60000.0 * climrt;

            while (newhdg <=  0.0) newhdg += 360.0;
            while (newhdg > 360.0) newhdg -= 360.0;

            /*
             * Save the new values in the on-screen boxes.
             */
            ptendTime = newnow;
            ptendLat.setVal (newlat);
            ptendLon.setVal (newlon);
            ptendHeading.setText (Double.toString (newhdg));
            ptendAltitude.setText (Double.toString (newalt));
            lastPtendLat = newlat;
            lastPtendLon = newlon;

            /*
             * Send the values in the form of a GPS reading to the active screen.
             */
            wairToNow.SetCurrentLocation (
                    spdkts / Lib.KtPerMPS, altft / Lib.FtPerM,
                    newhdg, newlat, newlon, newnow);

            /*
             * Start the timer to do next interval.
             */
            ptendTimerPend = true;
            WairToNow.wtnHandler.runDelayed (pretendInterval, pretendStepper);
        }
    }
}
