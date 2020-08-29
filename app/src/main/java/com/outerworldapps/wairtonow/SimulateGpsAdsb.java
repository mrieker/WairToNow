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
    private CheckBox trackVirtNav1;
    private CheckBox trackVirtNav2;
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
    private VirtNavView trackVirtNav;
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

        trackVirtNav1 = TrackVirtNav (1);
        trackVirtNav2 = TrackVirtNav (2);
        LinearLayout lp7 = new LinearLayout (wairToNow);
        lp7.setOrientation (LinearLayout.HORIZONTAL);
        lp7.addView (trackVirtNav1);
        lp7.addView (TextString ("  "));
        lp7.addView (trackVirtNav2);

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
        linearLayout.addView (lp7, llpwc);

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

    // create a track nav dial checkbox with text label
    @SuppressLint("SetTextI18n")
    private CheckBox TrackVirtNav (int n)
    {
        CheckBox cb = new CheckBox (wairToNow);
        cb.setTag (n);
        cb.setText ("track VirtNav" + n);
        wairToNow.SetTextSize (cb);
        cb.setOnClickListener (trackClicked);
        return cb;
    }

    // one of the track nav dial #n buttons was clicked
    // enable/disable nav dial tracking
    private final View.OnClickListener trackClicked = new View.OnClickListener () {
        @Override
        public void onClick (View view) {
            switch ((Integer) view.getTag ())
            {
                case 1: {
                    trackVirtNav2.setChecked (false);
                    trackVirtNav = trackVirtNav1.isChecked () ? wairToNow.virtNav1View : null;
                    break;
                }
                case 2: {
                    trackVirtNav1.setChecked (false);
                    trackVirtNav = trackVirtNav2.isChecked () ? wairToNow.virtNav2View : null;
                    break;
                }
            }
        }
    };

    // create a text view and set the text string
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
            double oldhdg = 0.0;    // degrees
            double oldalt = 0.0;    // feet
            double turnrt = 0.0;    // degrees per second
            double climrt = 0.0;    // feet per minute
            try { spdkts = Double.parseDouble (ptendSpeed.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { oldhdg = Double.parseDouble (ptendHeading.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { oldalt = Double.parseDouble (ptendAltitude.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { turnrt = Double.parseDouble (ptendTurnRt.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }
            try { climrt = Double.parseDouble (ptendClimbRt.getText ().toString ()); } catch (NumberFormatException nfe) { Lib.Ignored (); }

            /*
             * Overwrite turnrt and climrt if tracking a virt nav dial.
             */
            if (trackVirtNav != null) {
                // magcourse = truecourse + magvar
                double magvar = Lib.MagVariation (oldlat, oldlon, oldalt / Lib.FtPerM, ptendTime);
                //uble oldth  = ptendHdgFrame.getVal () ? oldhdg : oldhdg - magvar;
                double oldmh  = ptendHdgFrame.getVal () ? oldhdg + magvar : oldhdg;

                NavDialView.Mode mode = trackVirtNav.navDial.getMode ();

                if (trackVirtNav.selectedPlateCIFP != null) {
                    switch (mode) {

                        // OFF
                        default: break;

                        // control climb/descent if ILS mode
                        case ILS: {
                            climrt = calcTrackingILSClimbRate ();
                            turnrt = calcTrackingVORTurnRate (oldmh, oldlat, oldlon);
                            break;
                        }

                        // LOC mode
                        case LOC: {

                            // get step's target altitude and climb/descend toward that altitude
                            double targetalt = trackVirtNav.selectedPlateCIFP.getTargetAltitude ();
                            if (! Double.isNaN (targetalt)) {
                                // pitch max of +- 5 degrees until within 50 ft of target
                                double gsfpm = spdkts * Lib.FtPerNM / 60.0;
                                double climbby = targetalt - oldalt;
                                if (climbby < -50.0) climbby = -50.0;
                                if (climbby >  50.0) climbby =  50.0;
                                climrt = Math.tan (Math.toRadians (climbby / 10.0)) * gsfpm;
                            }

                            // compute turn rate to track OBS heading to waypoint
                            // if beacon is behind aircraft, track OBS heading away from waypoint
                            turnrt = calcTrackingVORTurnRate (oldmh, oldlat, oldlon);
                            break;
                        }
                    }
                } else {
                    switch (mode) {

                        // OFF
                        default: break;

                        // control climb/descent if ILS mode
                        case ILS: {
                            climrt = calcTrackingILSClimbRate ();
                            // fall through
                        }

                        // VOR, LOC, LOCBC, ILS modes
                        case VOR:
                        case LOC:
                        case LOCBC: {
                            // compute turn rate to track OBS heading to waypoint
                            // if beacon is behind aircraft, track OBS heading away from waypoint
                            turnrt = calcTrackingVORTurnRate (oldmh, oldlat, oldlon);
                            break;
                        }

                        // ADF mode
                        case ADF: {
                            // compute turn rate to track OBS heading to waypoint
                            // but if beacon is behind aircraft, track OBS heading away from waypoint
                            turnrt = calcTrackingADFTurnRate (oldmh);
                            break;
                        }
                    }
                }
            }

            /*
             * Get updated lat/lon, heading, altitude and time.
             */
            long   newnow = System.currentTimeMillis ();
            long   dtms   = newnow - ptendTime;
            double distnm = spdkts * dtms / 3600000.0;
            double newhdg = oldhdg + dtms / 1000.0 * turnrt;
            double newth  = ptendHdgFrame.getVal () ? newhdg : newhdg - Lib.MagVariation (oldlat, oldlon, oldalt / Lib.FtPerM, ptendTime);
            double newlat = Lib.LatHdgDist2Lat (oldlat, newth, distnm);
            double newlon = Lib.LatLonHdgDist2Lon (oldlat, oldlon, newth, distnm);
            double newalt = oldalt + dtms / 60000.0 * climrt;

            while (newhdg <=  0.0) newhdg += 360.0;
            while (newhdg > 360.0) newhdg -= 360.0;

            while (newth < -180.0) newth += 360.0;
            while (newth >= 180.0) newth -= 360.0;

            /*
             * Save the new values in the on-screen boxes.
             */
            ptendTime = newnow;
            ptendLat.setVal (newlat);
            ptendLon.setVal (newlon);
            ptendHeading.setText (Lib.DoubleNTZ (newhdg, 2));
            ptendAltitude.setText (Lib.DoubleNTZ (newalt, 2));

            /*
             * Send the values in the form of a GPS reading to the active screen.
             */
            wairToNow.SetCurrentLocation (
                    spdkts / Lib.KtPerMPS, oldalt / Lib.FtPerM,
                    newth, newlat, newlon, newnow);

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
     * Calculate climb rate to intercept/maintain glide slope.
     */
    private double calcTrackingILSClimbRate ()
    {
        double gsdots = trackVirtNav.navDial.slope * (5.0 / NavDialView.GSDEFLECT);

        // level off if needle above the bubble
        double climrt;
        if (gsdots >= 1.0) climrt = 0.0;

        // otherwise if in top half of bubble, descend a little bit
        // if needle says we need to go down, descend steeper
        else climrt = trackVirtNav.navDial.gsfpm * Math.sqrt (1.0 - gsdots);

        // display what we are doing
        ptendClimbRt.setText (Lib.DoubleNTZ (climrt, 2));

        return climrt;
    }

    /**
     * Calculate turn rate to intercept track.
     */
    private double calcTrackingVORTurnRate (double oldmh, double oldlat, double oldlon)
    {
        // see what magnetic heading the user wants to follow
        double oncoursemh = trackVirtNav.navDial.getObs ();

        // see what deflection the needle is showing, without pegging, ie, -90..+90
        // <0 : airplane is right of course (assuming OBS pointing up)
        // >0 : airplane is left of course (assuming OBS pointing up)
        double offcourseby = trackVirtNav.navDial.degdiffnp;

        // fudge factor: inflate it the farther away from the waypoint we are
        // it makes us turn sharper when far away from waypoint so correction works
        // if FF is big, the needle deflects very little when tracking a VOR radial over long distance
        //               but it oscillates trying to track a VOR radial outbound
        //        small, the needle deflects larger to maintain tracking VOR radial over long distance
        // eventually settles on airplane heading = great circle heading to VOR at current position
        //                                          but with needle offset enough to make up difference
        //                                          between OBS and the gc heading at current position
        // example:
        //  starting at KPAO head directly to GDM VOR
        //  GDM radial is 294 giving inbound heading of 114 at the GDM end of the route
        //  near KPAO the initial GC heading is 053
        //  so it steers southeast to begin with until needle is displaced sufficiently to maintain GC heading
        //  ...although the airplane is displaced to the south
        //  displacement reduces to zero by the time it gets to GDM VOR
        //  ...cuz the GC heading and the inbound radial converge on 114
        if (trackVirtNav.waypoint != null) {
            final double FF = 0.001;
            double distnm = Lib.LatLonDist (oldlat, oldlon, trackVirtNav.waypoint.lat, trackVirtNav.waypoint.lon);
            offcourseby *= (1.0 + distnm * FF);
        }

        // limit correction angle to +- 80 degrees
        // see scaling factor below
        if (offcourseby < -15.0) offcourseby = -15.0;
        if (offcourseby >  15.0) offcourseby =  15.0;

        // this is how much to correct into oncoursemh to center the needle
        // scale it to use max of 80deg correction for max 15deg off course
        // cuz dial shows about 80deg heading correction when needle is displaced 15deg
        // should put red airplane at top of needle like a pilot would fly it
        final double SCALING = Math.sin (Math.toRadians (80.0)) / 15.0;
        double correction = Math.toDegrees (Math.asin (offcourseby * SCALING));

        // this is the heading we want to be flying right now
        double interceptmh = oncoursemh + correction;

        // this is how much we need to turn from current heading
        double needtoturn = interceptmh - oldmh;
        while (needtoturn < -180.0) needtoturn += 360.0;
        while (needtoturn >= 180.0) needtoturn -= 360.0;

        // take a half second to turn that far but never more than standard rate
        double turnrt = needtoturn * 2.0;
        if (turnrt < -3.0) turnrt = -3.0;
        if (turnrt >  3.0) turnrt =  3.0;
        ptendTurnRt.setText (Lib.DoubleNTZ (turnrt, 2));

        return turnrt;
    }

    /**
     * Calculate turn rate to intercept track.
     */
    private double calcTrackingADFTurnRate (double oldmh)
    {
        // see what magnetic heading the user wants to follow
        double oncoursemh = trackVirtNav.navDial.getObs ();

        // see what deflection the needle is showing
        double offcourseby = trackVirtNav.navDial.deflect;

        // maybe it is pointing behind, so we assume tracking away from beacon
        if (offcourseby < -90.0) offcourseby = -180.0 - offcourseby;
        if (offcourseby >  90.0) offcourseby =  180.0 - offcourseby;

        // this is how much to correct into oncoursemh to center the needle
        double correction = offcourseby * 2.0;
        if (correction < -80.0) correction = -80.0;
        if (correction >  80.0) correction =  80.0;

        // this is the heading we want to turn to now to center the needle
        double interceptheading = oncoursemh + correction;

        // this is how much we need to turn from current heading
        double needtoturn = interceptheading - oldmh;
        while (needtoturn < -180.0) needtoturn += 360.0;
        while (needtoturn >= 180.0) needtoturn -= 360.0;

        // take one second to turn that far but never more than standard rate
        double turnrt = needtoturn;
        if (turnrt < -3.0) turnrt = -3.0;
        if (turnrt >  3.0) turnrt =  3.0;
        ptendTurnRt.setText (Lib.DoubleNTZ (turnrt, 2));

        return turnrt;
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
