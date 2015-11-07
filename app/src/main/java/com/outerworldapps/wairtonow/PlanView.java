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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.TreeMap;

public class PlanView extends ScrollView implements WairToNow.CanBeMainView {
    private static final long pretendInterval = 1000;

    private boolean displayOpen;
    public  boolean pretendEnabled;
    private Button ptendCapCen;
    private CheckBox ptendCheckbox;
    private EditText fromAirport;
    private EditText fromDistEdit;
    private EditText fromHdgEdit;
    private EditText ptendAltitude;
    private EditText ptendHeading;
    private EditText ptendSpeed;
    private EditText ptendTurnRt;
    private EditText toDistEdit;
    private EditText toHdgEdit;
    private LatLonView ptendLat;
    private LatLonView ptendLon;
    private LinearLayout linearLayout;
    private LinearLayout lp1, lp2, lp3, lp4, lp5;
    private LinearLayout lv0, lv1, lv2, lv3;
    private long ptendTime;
    private OnClickListener locateListener;
    private Runnable pretendStepper;
    private TextView tp0;
    private TextView tv0;
    private WairToNow wairToNow;

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
            public void onClick (View view)
            {
                ptendLat.setVal (wairToNow.chartView.centerLat);
                ptendLon.setVal (wairToNow.chartView.centerLon);
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
        linearLayout.setOrientation (LinearLayout.VERTICAL);

        Reset ();

        addView (linearLayout);
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Plan";
    }

    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        displayOpen = true;
        String format = wairToNow.optionsView.LatLonString (10.0F/3600.0F, 'N', 'S');
        ptendLat.setFormat (format);
        ptendLon.setFormat (format);
    }

    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        displayOpen = false;
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
     * The Pretend checkbox was just checked/unchecked.
     */
    private void SetPretendEnabled (boolean enab)
    {
        pretendEnabled = enab;
        if (enab) {
            ptendTime = System.currentTimeMillis ();
            WairToNow.wtnHandler.runDelayed (pretendInterval, pretendStepper);
        }
    }

    /**
     * The Find button was just clicked.
     * Search database for airports that match the given criteria
     * and display the list with a "locate" button for each that
     * shows where the airport is on the chart.
     */
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
            int aptinfoexpdate = MaintView.GetWaypointExpDate ();
            String dbname = "cycles56_" + aptinfoexpdate + ".db";
            SQLiteDatabase sqldb = SQLiteDBs.GetOrCreate (dbname);

            /*
             * Find starting point airport's lat/lon.
             */
            Cursor result1 = sqldb.query (
                    Waypoint.Airport.dbtable, Waypoint.Airport.dbcols,
                    Waypoint.Airport.dbkeyid + "=?", new String[] { fromApt },
                    null, null, null, null);
            float fromLat, fromLon;
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
                    float distNM  = Lib.LatLonDist (fromLat, fromLon, wp.lat, wp.lon);
                    float trueDeg = Lib.LatLonTC (fromLat, fromLon, wp.lat, wp.lon);
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

    private void PretendStep ()
    {
        if (pretendEnabled) {

            /*
             * Get lat/lon and time from previous interval.
             */
            long  oldnow = ptendTime;
            float oldlat = ptendLat.getVal ();
            float oldlon = ptendLon.getVal ();

            //if (oldlon > 1) throw new RuntimeException ("fetched eastern longitude");

            /*
             * Get speed and heading input by the user.
             */
            float spdkts = 0.0F;
            float hdgdeg = 0.0F;
            float altft  = 0.0F;
            float turnrt = 0.0F;
            try { spdkts = Float.parseFloat (ptendSpeed.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { hdgdeg = Float.parseFloat (ptendHeading.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { altft  = Float.parseFloat (ptendAltitude.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { turnrt = Float.parseFloat (ptendTurnRt.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }

            /*
             * If display is open, user might be editing values.
             * So just pretend speed and turn rate are zero so we
             * don't try to update anything based on bogus values.
             */
            if (displayOpen) {
                spdkts = 0.0F;
                turnrt = 0.0F;
            }

            /*
             * Get updated lat/lon, heading and time.
             */
            long  newnow = System.currentTimeMillis ();
            float distnm = spdkts * (newnow - oldnow) / 1000.0F / 3600.0F;
            float newhdg = hdgdeg + (newnow - oldnow) / 1000.0F * turnrt;
            float newlat = Lib.LatHdgDist2Lat (oldlat, newhdg, distnm);
            float newlon = Lib.LatLonHdgDist2Lon (oldlat, oldlon, newhdg, distnm);

            while (newhdg <=  0.0F) newhdg += 360.0F;
            while (newhdg > 360.0F) newhdg -= 360.0F;

            //if (newlon > 1) throw new RuntimeException ("writing eastern longitude");

            /*
             * Save the new values in the on-screen boxes.
             */
            ptendTime = newnow;
            if (!displayOpen) {
                ptendLat.setVal (newlat);
                ptendLon.setVal (newlon);
                ptendHeading.setText (Float.toString (newhdg));
            }

            /*
             * Send the values in the form of a GPS reading to the active screen.
             */
            Location loc = new Location ("PlanPretend");
            loc.setAltitude (altft / Lib.FtPerM);
            loc.setBearing (newhdg);
            loc.setLatitude (newlat);
            loc.setLongitude (newlon);
            loc.setSpeed (spdkts / Lib.KtPerMPS);
            loc.setTime (newnow);
            wairToNow.SetCurrentLocation (loc);

            /*
             * Start the timer to do next interval.
             */
            WairToNow.wtnHandler.runDelayed (pretendInterval, pretendStepper);
        }
    }
}
