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
import android.graphics.Color;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SimulateGpsAdsb extends GpsAdsbReceiver implements Runnable {
    public final static String TAG = "WairToNow";

    private boolean displayOpen;
    private boolean pretendEnabled;
    private boolean ptendTimerPend;
    private EditText ptendAltitude;
    private EditText ptendClimbRt;
    private EditText ptendHeading;
    private EditText ptendSpeed;
    private EditText ptendTurnRt;
    private LatLonView ptendLat;
    private LatLonView ptendLon;
    private LinearLayout linearLayout;
    private long ptendTime;
    private long simtimerstarted;
    private TrueMag ptendHdgFrame;

    public SimulateGpsAdsb (WairToNow wtn)
    {
        wairToNow = wtn;
    }

    @Override
    public String getPrefKey ()
    {
        return "simulator";
    }

    @Override
    public String getDisplay ()
    {
        return "Simulated GPS";
    }

    @Override
    public View displayOpened ()
    {
        displayOpen = true;
        if (linearLayout == null) {
            Initialize ();
        }
        return linearLayout;
    }

    @Override
    public void displayClosed ()
    {
        displayOpen = false;
        if (!ptendTimerPend) {
            ptendTime = simtimerstarted = System.currentTimeMillis ();
            PretendStep ();
        }
    }

    // app is now visible (in foreground)
    @Override
    public void startSensor ()
    { }

    // app is now invisible (in background)
    @Override
    public void stopSensor ()
    { }

    @Override
    public void refreshStatus ()
    { }

    @Override
    public boolean isSelected ()
    {
        return pretendEnabled;
    }

    // pretendInterval timer expired
    // runs in GUI thread
    @Override  // Runnable
    public void run ()
    {
        ptendTimerPend = false;
        PretendStep ();
    }

    @SuppressLint("SetTextI18n")
    private void Initialize ()
    {
        final CheckBox ptendCheckbox = new CheckBox (wairToNow);
        ptendCheckbox.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view)
            {
                boolean enab = ptendCheckbox.isChecked ();
                if (pretendEnabled != enab) {
                    pretendEnabled = enab;
                    if (enab) {
                        wairToNow.gpsDisabled ++;
                        getButton ().setRingColor (Color.GREEN);
                        if (!ptendTimerPend) {
                            ptendTime = simtimerstarted = System.currentTimeMillis ();
                            PretendStep ();
                        }
                    } else {
                        wairToNow.gpsDisabled --;
                        getButton ().setRingColor (Color.TRANSPARENT);
                    }
                }
            }
        });

        LinearLayout lp1 = new LinearLayout (wairToNow);
        lp1.setOrientation (LinearLayout.HORIZONTAL);
        lp1.addView (ptendCheckbox);
        lp1.addView (TextString ("Pretend to be at "));

        ptendLat = new LatLonView (wairToNow, false);
        ptendLon = new LatLonView (wairToNow, true);
        ptendLat.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize);
        ptendLon.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize);

        Button ptendCapCen = new Button (wairToNow);
        ptendCapCen.setText ("(capture center)");
        wairToNow.SetTextSize (ptendCapCen);
        ptendCapCen.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                PixelMapper pmap = wairToNow.chartView.pmap;
                ptendLat.setVal (pmap.centerLat);
                ptendLon.setVal (pmap.centerLon);
            }
        });

        ptendSpeed = new EditText (wairToNow);
        ptendSpeed.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ptendSpeed.setEms (5);
        wairToNow.SetTextSize (ptendSpeed);

        LinearLayout lp2 = new LinearLayout (wairToNow);
        lp2.setOrientation (LinearLayout.HORIZONTAL);
        lp2.addView (TextString ("speed "));
        lp2.addView (ptendSpeed);
        lp2.addView (TextString ("kts"));

        ptendHeading = new EditText (wairToNow);
        ptendHeading.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ptendHeading.setEms (5);
        wairToNow.SetTextSize (ptendHeading);

        ptendHdgFrame = new TrueMag ();

        LinearLayout lp3 = new LinearLayout (wairToNow);
        lp3.setOrientation (LinearLayout.HORIZONTAL);
        lp3.addView (TextString ("heading "));
        lp3.addView (ptendHeading);
        lp3.addView (TextString ("deg "));
        lp3.addView (ptendHdgFrame);

        ptendAltitude = new EditText (wairToNow);
        ptendAltitude.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        ptendAltitude.setEms (5);
        wairToNow.SetTextSize (ptendAltitude);

        LinearLayout lp5 = new LinearLayout (wairToNow);
        lp5.setOrientation (LinearLayout.HORIZONTAL);
        lp5.addView (TextString ("altitude "));
        lp5.addView (ptendAltitude);
        lp5.addView (TextString ("feet"));

        ptendTurnRt = new EditText (wairToNow);
        ptendTurnRt.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        ptendTurnRt.setEms (3);
        wairToNow.SetTextSize (ptendTurnRt);

        LinearLayout lp4 = new LinearLayout (wairToNow);
        lp4.setOrientation (LinearLayout.HORIZONTAL);
        lp4.addView (TextString ("turn rate "));
        lp4.addView (ptendTurnRt);
        lp4.addView (TextString ("deg per sec"));

        ptendClimbRt = new EditText (wairToNow);
        ptendClimbRt.setInputType (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        ptendClimbRt.setEms (6);
        wairToNow.SetTextSize (ptendClimbRt);

        LinearLayout lp6 = new LinearLayout (wairToNow);
        lp6.setOrientation (LinearLayout.HORIZONTAL);
        lp6.addView (TextString ("climb rate "));
        lp6.addView (ptendClimbRt);
        lp6.addView (TextString ("ft per min"));

        /*
         * Layout the screen and display it.
         */
        linearLayout = new LinearLayout (wairToNow);
        linearLayout.setOrientation (LinearLayout.VERTICAL);

        LinearLayout.LayoutParams llpwc = new LinearLayout.LayoutParams (LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        linearLayout.addView (lp1, llpwc);
        linearLayout.addView (ptendLat, llpwc);
        linearLayout.addView (ptendLon, llpwc);
        linearLayout.addView (ptendCapCen, llpwc);
        linearLayout.addView (lp2, llpwc);
        linearLayout.addView (lp3, llpwc);
        linearLayout.addView (lp5, llpwc);
        linearLayout.addView (lp4, llpwc);
        linearLayout.addView (lp6, llpwc);

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

    private TextView TextString (String str)
    {
        TextView tv = new TextView (wairToNow);
        tv.setText (str);
        wairToNow.SetTextSize (tv);
        return tv;
    }

    /**
     * Called every pretendInterval milliseconds to step simulation.
     */
    @SuppressLint("SetTextI18n")
    private void PretendStep ()
    {
        if (pretendEnabled && !displayOpen) {

            /*
             * Get values input by the user.
             */
            double oldlat = ptendLat.getVal ();
            double oldlon = ptendLon.getVal ();
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
            long   newnow = System.currentTimeMillis ();
            long   dtms   = newnow - ptendTime;
            double distnm = spdkts * dtms / 3600000.0;
            double newhdg = hdgdeg + dtms / 1000.0 * turnrt;
            double hdgtru = ptendHdgFrame.getVal () ? newhdg : newhdg - Lib.MagVariation (oldlat, oldlon, altft / Lib.FtPerM);
            double newlat = Lib.LatHdgDist2Lat (oldlat, hdgtru, distnm);
            double newlon = Lib.LatLonHdgDist2Lon (oldlat, oldlon, hdgtru, distnm);
            double newalt = altft + dtms / 60000.0 * climrt;

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

            /*
             * Send the values in the form of a GPS reading to the active screen.
             */
            wairToNow.SetCurrentLocation (
                    spdkts / Lib.KtPerMPS, altft / Lib.FtPerM,
                    hdgtru, newlat, newlon, newnow);

            /*
             * Start the timer to do next interval.
             */
            ptendTimerPend = true;
            int    simsteppersec   = wairToNow.optionsView.gpsUpdateOption.getVal ();
            double secsincestart   = (newnow - simtimerstarted) / 1000.0;
            double stepssincestart = secsincestart * simsteppersec;
            double tinyfraction    = stepssincestart - Math.floor (stepssincestart);
            long   mstonextstep    = Math.round ((1.0 - tinyfraction) * 1000.0 / simsteppersec);
            WairToNow.wtnHandler.runDelayed (mstonextstep, this);
        }
    }

    /**
     * Box for editing true/magnetic indicator value
     */
    private class TrueMag extends TextArraySpinner {
        public TrueMag ()
        {
            super (wairToNow);
            wairToNow.SetTextSize (this);

            String[] labels = new String[] { "True", "Mag" };
            setLabels (labels, null, null, null);

            setIndex (wairToNow.optionsView.magTrueOption.getAlt () ? 0 : 1);
        }

        /**
         * Get currently selected value
         * @return true: "True"
         *        false: "Mag"
         */
        public boolean getVal ()
        {
            return getIndex () == 0;
        }
    }
}
