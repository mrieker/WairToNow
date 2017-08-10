//    Copyright (C) 2016, Mike Rieker, Beverly, MA USA
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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Display an OBS-dial looking screen that can be set to a waypoint
 * and operated like an ADF/VOR/ILS/LOC receiver.
 */
@SuppressLint("ViewConstructor")
public class VirtNavView extends LinearLayout
        implements NavDialView.DMEClickedListener, NavDialView.OBSChangedListener, WairToNow.CanBeMainView {

    private boolean oldLandscape;
    private boolean open;
    private boolean orientLocked;
    private Button modeButton;
    private Button orientButton;
    private CheckBox hsiCheckBox;
    private double locObsSetting;   // for ILS/LOC waypoint, corresponding OBS setting
    private double wpmagvar;
    private NavDialView navDial;
    private PlateCIFP selectedPlateCIFP;  // non-null: CIFP tracking mode
    private String label;
    private TextView wpIdent;
    private TextView wpStatus;
    private View rightHalfView;     // plate to display in right-hand half of landscape mode
    private WairToNow wairToNow;
    private Waypoint dmeWaypoint;   // what waypoint to display in DME window
    private Waypoint waypoint;      // what waypoint we are navigating to

    public VirtNavView (WairToNow wtn, String lbl)
    {
        super (wtn);
        wairToNow = wtn;
        label = lbl;

        orientationChanged ();

        useWaypointButtonClicked (null);
    }

    /**
     * Got a new GPS location, so update the needle.
     */
    public void SetGPSLocation ()
    {
        // open or not, keep updating CIFP state
        if (selectedPlateCIFP != null) {
            selectedPlateCIFP.SetGPSLocation ();
        }

        if (open) {
            // update needle(s)
            computeRadial ();
            // update the GPS blinking border status box
            // - causes dispatchDraw() to be called
            invalidate ();
        }
    }

    /**
     * CanBeMainView
     **/
    public String GetTabName () { return label; }

    @Override  // CanBeMainView
    public int GetOrientation ()
    {
        /*
         * Set screen orientation to match whatever button currently says.
         */
        switch (orientButton.getText ().toString ()) {
            default: {  // also for "Unlkd"
                return ActivityInfo.SCREEN_ORIENTATION_USER;
            }
            case "Land": {
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            }
            case "Port": {
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            }
        }
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return wairToNow.optionsView.powerLockOption.checkBox.isChecked ();
    }

    public void OpenDisplay ()
    {
        /*
         * Tell anyone who cares that this screen is open.
         */
        open = true;

        /*
         * Rebuild the screen in case orientation changed.
         */
        orientationChanged ();
    }

    public void CloseDisplay ()
    {
        open = false;
    }

    public void ReClicked () { }

    public View GetBackPage () { return this; }

    /**
     * The mode button was clicked.
     * Increment the mode.
     */
    private void modeButtonClicked ()
    {
        NavDialView.Mode mode = navDial.getMode ();
        if (selectedPlateCIFP == null) {
            if (waypoint == null) mode = NavDialView.Mode.OFF;
            else switch (mode) {
                case OFF:
                    mode = NavDialView.Mode.VOR;
                    break;
                case VOR:
                    mode = NavDialView.Mode.ADF;
                    break;
                case ADF:
                    mode = NavDialView.Mode.LOC;
                    break;
                case LOC:
                    mode = NavDialView.Mode.ILS;
                    break;
                case ILS:
                    mode = NavDialView.Mode.OFF;
                    break;
            }
            if (mode == NavDialView.Mode.LOC && !(waypoint instanceof Waypoint.Localizer)) {
                mode = NavDialView.Mode.OFF;
            }
            if (mode == NavDialView.Mode.ILS && (!(waypoint instanceof Waypoint.Localizer) ||
                    (((Waypoint.Localizer) waypoint).gs_elev == Waypoint.ELEV_UNKNOWN))) {
                mode = NavDialView.Mode.OFF;
            }
        } else {
            if (mode == NavDialView.Mode.OFF) {
                mode = NavDialView.Mode.LOC;
            } else {
                mode = NavDialView.Mode.OFF;
            }
        }
        setNavDialMode (mode);
    }

    /**
     * The Chart button was clicked to use the destination point shown on the chart page.
     */
    private void useChartViewButtonClicked ()
    {
        // set dial to navigate to destination waypoint
        // if landscape mode, put chart in right half
        ChartView chartView = wairToNow.chartView;
        rightHalfView = chartView;
        useWaypointButtonClicked (chartView.clDest);

        // set OBS setting to course line going into destination waypoint
        switch (navDial.getMode ()) {
            case ADF:
            case VOR: {
                double tcfrom = Lib.LatLonTC (waypoint.lat, waypoint.lon, chartView.orgLat, chartView.orgLon);
                double magvar = waypoint.GetMagVar (wairToNow.currentGPSAlt);
                navDial.obsSetting = tcfrom + magvar + 180.0;
            }
        }

        computeRadial ();
    }

    /**
     * One of the FAAWP1, FAAWP2 buttons was clicked to select
     * the waypoint selected by that page.
     *
     * If it is an airport, ask the user if they want the airport,
     * or one of the nearby navaids.  Also, if an IAP plate is
     * selected, ask if they want the plate's navaid or if a CIFP
     * is current, do they want the CIFP route.
     *
     * @param wpv = FAAWP1 or FAAWP2
     */
    @SuppressLint("SetTextI18n")
    private void useWaypointViewButtonClicked (WaypointView wpv)
    {
        // assume we don't want a plate displayed in the right-hand part of landscape mode
        rightHalfView = null;

        // turn dial OFF so it doesn't have an incorrect mode selected for new waypoint
        // eg, old was in LOC mode when navving to a new VOR
        // whilst in landscape:
        //  FAAWP1 -> KSFM -> VOR-25 -> VirtNav1 -> FAAWP1 -> CIFP -> ENE -> FAAWP1
        //    CIFP(long click) -> DISCONTINUE -> FAAWP1 (used to crash here)
        useWaypointButtonClicked (null);

        // if FAAWP1,2 doesn't have an airport waypoint selected,
        // just select whatever waypoint it has selected (VOR, NDB, etc)
        Waypoint wp = wpv.selectedWaypoint;
        if (!(wp instanceof Waypoint.Airport)) {
            orientationChanged ();  // remove old selected plate if any
            useWaypointButtonClicked (wp);
            return;
        }

        // FAAWP1,2 has an airport selected, see if it is displaying an IAP plate
        // update this screen (right-hand part of landscape mode) with plate if so
        rightHalfView = wpv.selectedPlateView;
        orientationChanged ();  // update right-hand part of landscape mode

        // if it is an IAP plate, get associated navaid or selected CIFP route
        if ((wpv.selectedPlateView != null) &&
                (wpv.selectedPlateView.plateImage instanceof PlateView.IAPPlateImage)) {
            PlateView.IAPPlateImage iappi = (PlateView.IAPPlateImage) wpv.selectedPlateView.plateImage;
            PlateCIFP plateCIFP = iappi.plateCIFP;

            // if CIFP selected, track the CIFP route
            if (plateCIFP.cifpSelected != null) {

                // ok, put us in CIFP tracking mode
                selectedPlateCIFP = plateCIFP;

                // have mode button show we are in CIFP tracking mode
                modeButton.setText ("CIFP");

                // set waypoint title string
                wpIdent.setText ("CIFP: " + plateCIFP.getApproachName ());

                // get magnetic variation in the neighborhood
                Waypoint.Airport apt = (Waypoint.Airport) wp;
                wpmagvar = Lib.MagVariation (apt.lat, apt.lon, apt.elev);

                // set dial initial values
                navDial.setMode (NavDialView.Mode.LOC);
                computeRadial ();
                return;
            }

            // if CIFP knows the reference navaid of the approach, select that navaid
            Waypoint refnavwp = iappi.plateCIFP.getRefNavWaypt ();
            if (refnavwp != null) {
                useWaypointButtonClicked (refnavwp);
                return;
            }
        }

        // airport itself without any IAP plate selected, select the airport
        //TODO maybe give menu to select airport or one of nearby navaids
        useWaypointButtonClicked (wp);
    }

    /**
     * One of the "use" buttons was clicked to select a waypoint.
     * @param wp = waypoint active in that source
     */
    @SuppressLint("SetTextI18n")
    private void useWaypointButtonClicked (Waypoint wp)
    {
        // not in CIFP tracking mode
        selectedPlateCIFP = null;

        // set waypoint for DME and nav measurement
        dmeWaypoint = waypoint = wp;

        // set NavDialView to initial settings appropriate to the waypoint
        if (waypoint == null) {

            // null waypoint turns the thing off
            wpIdent.setText ("[OFF]");
            wpStatus.setText ("");
            navDial.setMode (NavDialView.Mode.OFF);
            navDial.obsSetting = 0.0;
        } else {

            // set up big yellow text so user can see what navaid is selected
            String wpstr = wp.MenuKey ();
            wpIdent.setText (wpstr);

            // for localizer, set the mode to LOC or ILS
            // and set the obs to the localizer's course
            wpmagvar = waypoint.GetMagVar (0);
            if (waypoint instanceof Waypoint.Localizer) {
                navDial.setMode (NavDialView.Mode.LOC);
                if (((Waypoint.Localizer) waypoint).gs_elev != Waypoint.ELEV_UNKNOWN) {
                    navDial.setMode (NavDialView.Mode.ILS);
                }
                navDial.obsSetting = locObsSetting = ((Waypoint.Localizer) waypoint).thdg + wpmagvar;
            }

            // NDBs get the dial set to ADF mode
            else if (wpstr.startsWith ("NDB")) {
                navDial.setMode (NavDialView.Mode.ADF);
                navDial.obsSetting = wairToNow.currentGPSHdg +
                        waypoint.GetMagVar (wairToNow.currentGPSAlt);
            }

            // VORs get the dial set to VOR mode
            else {
                navDial.setMode (NavDialView.Mode.VOR);
                navDial.obsSetting = Lib.LatLonTC (
                        wairToNow.currentGPSLat, wairToNow.currentGPSLon,
                        waypoint.lat, waypoint.lon) +
                    waypoint.GetMagVar (wairToNow.currentGPSAlt);
            }
        }

        // hide or show the HSI checkbox based on nav dial capability
        hsiCheckBox.setVisibility (navDial.isHSIAble () ? VISIBLE : GONE);

        // update button to show what mode was selected above
        modeButton.setText (navDial.getMode ().toString ());

        // re-layout screen
        orientationChanged ();

        // update the needles and DME
        computeRadial ();
    }

    @Override  // OBSChangedListener
    public void obsChanged (double obs)
    {
        computeRadial ();
    }

    @Override  // DMEClickedListener
    public void dmeClicked ()
    {
        // don't allow manually changing DME when in CIFP tracking mode
        if (selectedPlateCIFP == null) {

            // normal navigating to waypoint, allow separate DME waypoint
            Waypoint.ShowWaypointDialog (wairToNow, "Enter DME Waypoint",
                    waypoint.lat, waypoint.lon,
                    (dmeWaypoint == null) ? waypoint.ident : dmeWaypoint.ident,
                    new Waypoint.Selected () {
                        @Override
                        public void wpSeld (Waypoint wp) {
                            dmeWaypoint = wp;
                            dmeDisplay ();
                        }
                        @Override
                        public void noWPSeld () { }
                    }
            );
        }
    }

    @Override  // ViewGroup
    protected void dispatchDraw (Canvas canvas)
    {
        // see if in landscape mode or not
        // re-draw screen if it has changed
        boolean landscape = (getResources().getConfiguration().orientation ==  Configuration.ORIENTATION_LANDSCAPE);
        if (oldLandscape != landscape) {
            oldLandscape = landscape;
            post (new Runnable () {
                @Override
                public void run ()
                {
                    orientationChanged ();
                }
            });
        }

        // update screen contents with current position
        super.dispatchDraw (canvas);
        wairToNow.drawGPSAvailable (canvas, this);
    }

    /**
     * Screen orientaiton (portrait/landscape) just changed,
     * reconfigure layout accordingly.
     * Or rebuild layout for any reason.
     */
    private void orientationChanged ()
    {
        LayoutInflater inflater = (LayoutInflater) wairToNow.getSystemService (Context.LAYOUT_INFLATER_SERVICE);

        /*
         * Save current settings.
         */
        String oldIdent = (wpIdent == null) ? "" : wpIdent.getText ().toString ();
        NavDialView.Mode oldMode = (navDial == null) ? NavDialView.Mode.OFF : navDial.getMode ();
        double oldObsSetting = (navDial == null) ? 0.0 : navDial.obsSetting;
        boolean oldHSIEnable = (navDial == null) ? getHSIPreference () : navDial.hsiEnable;

        /*
         * Load layout for portrait or landscape orientation.
         * Get all the widget pointers for whichever we load.
         * Only landscape orientation has the plate view frame.
         */
        Button useChart, useFAAWP1, useFAAWP2, useUserWP;
        FrameLayout plateViewFrame;
        if (oldLandscape) {
            @SuppressLint("InflateParams")
            View mainView = inflater.inflate (R.layout.virtnavland, null);

            removeAllViews ();
            addView (mainView);

            wairToNow.SetDetentSize ((DetentHorizontalScrollView) findViewById (R.id.identscrollerland));
            wairToNow.SetDetentSize ((DetentHorizontalScrollView) findViewById (R.id.statusscrollerland));

            modeButton = (Button) findViewById (R.id.modebuttonland);
            hsiCheckBox = (CheckBox) findViewById (R.id.hsicheckboxland);
            orientButton = (Button) findViewById (R.id.orientland);
            useChart  = (Button) findViewById (R.id.use_chartland);
            useFAAWP1 = (Button) findViewById (R.id.use_faawp1land);
            useFAAWP2 = (Button) findViewById (R.id.use_faawp2land);
            useUserWP = (Button) findViewById (R.id.use_userwpland);

            wpIdent = (TextView) findViewById (R.id.wpidentland);
            wpStatus = (TextView) findViewById (R.id.wpstatusland);
            navDial = (NavDialView) findViewById (R.id.navdialland);

            plateViewFrame = (FrameLayout) findViewById (R.id.plateviewland);
        } else {
            @SuppressLint("InflateParams")
            View mainView = inflater.inflate (R.layout.virtnavport, null);

            removeAllViews ();
            addView (mainView);

            wairToNow.SetDetentSize ((DetentHorizontalScrollView) findViewById (R.id.buttonscrollerport));
            wairToNow.SetDetentSize ((DetentHorizontalScrollView) findViewById (R.id.identscrollerport));

            modeButton = (Button) findViewById (R.id.modebuttonport);
            hsiCheckBox = (CheckBox) findViewById (R.id.hsicheckboxport);
            orientButton = (Button) findViewById (R.id.orientport);
            useChart  = (Button) findViewById (R.id.use_chartport);
            useFAAWP1 = (Button) findViewById (R.id.use_faawp1port);
            useFAAWP2 = (Button) findViewById (R.id.use_faawp2port);
            useUserWP = (Button) findViewById (R.id.use_userwpport);

            wpIdent = (TextView) findViewById (R.id.wpidentport);
            wpStatus = (TextView) findViewById (R.id.wpstatusport);
            navDial = (NavDialView) findViewById (R.id.navdialport);

            plateViewFrame = null;
        }

        /*
         * Set up button listeners and a few other attributes.
         */
        wpIdent.setTextColor (Color.YELLOW);
        wpIdent.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize * 1.5F);
        wpIdent.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view) {
                wpIdentClicked ();
            }
        });

        wairToNow.SetTextSize (modeButton);
        modeButton.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view) {
                modeButtonClicked ();
            }
        });

        wairToNow.SetTextSize (hsiCheckBox);
        hsiCheckBox.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view) {
                navDial.hsiEnable = hsiCheckBox.isChecked ();
                setHSIPreference (navDial.hsiEnable);
                navDial.invalidate ();
            }
        });

        wairToNow.SetTextSize (orientButton);
        setOrientButtonText ();
        orientButton.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                orientButtonClicked ();
            }
        });

        wairToNow.SetTextSize (useChart);
        useChart.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useChartViewButtonClicked ();
            }
        });

        wairToNow.SetTextSize (useFAAWP1);
        useFAAWP1.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useWaypointViewButtonClicked (wairToNow.waypointView1);
            }
        });

        wairToNow.SetTextSize (useFAAWP2);
        useFAAWP2.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useWaypointViewButtonClicked (wairToNow.waypointView2);
            }
        });

        wairToNow.SetTextSize (useUserWP);
        useUserWP.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useWaypointButtonClicked (wairToNow.userWPView.selectedUserWP);
            }
        });

        wairToNow.SetTextSize (wpStatus);
        navDial.dmeClickedListener = this;
        navDial.obsChangedListener = this;

        /*
         * Highlight button showing where right-hand view comes from.
         */
        String rhvtn = (rightHalfView instanceof WairToNow.CanBeMainView) ? ((WairToNow.CanBeMainView)rightHalfView).GetTabName () : null;
        useChart.setTextColor  ("Chart".equals  (rhvtn) ? Color.RED : Color.BLACK);
        useFAAWP1.setTextColor ("FAAWP1".equals (rhvtn) ? Color.RED : Color.BLACK);
        useFAAWP2.setTextColor ("FAAWP2".equals (rhvtn) ? Color.RED : Color.BLACK);
        useUserWP.setTextColor ("UserWP".equals (rhvtn) ? Color.RED : Color.BLACK);

        /*
         * If landscape mode, maybe display chart or IAP on right-hand side.
         */
        if ((plateViewFrame != null) && (rightHalfView != null)) {
            ViewParent p = rightHalfView.getParent ();
            if (p instanceof ViewGroup) {
                ((ViewGroup) p).removeView (rightHalfView);
            }
            plateViewFrame.addView (rightHalfView);
        }

        /*
         * Restore settings.
         */
        wpIdent.setText (oldIdent);
        setNavDialMode (oldMode);
        navDial.obsSetting = oldObsSetting;
        navDial.hsiEnable = oldHSIEnable;
        navDial.invalidate ();
        hsiCheckBox.setChecked (oldHSIEnable);
        hsiCheckBox.setVisibility (navDial.isHSIAble () ? VISIBLE : GONE);
    }

    /**
     * Save HSI enable checkbox in preferences.
     */
    private boolean getHSIPreference ()
    {
        SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
        return prefs.getBoolean (label + ".hsiEnable", false);
    }
    private void setHSIPreference (boolean enab)
    {
        SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
        SharedPreferences.Editor editr = prefs.edit ();
        editr.putBoolean (label + ".hsiEnable", enab);
        editr.commit ();
    }

    /**
     * Step orientation each time button is clicked.
     */
    private void orientButtonClicked ()
    {
        /*
         * Step orientation and set screen accordingly.
         */
        orientLocked = !orientLocked;
        if (orientLocked) {
            wairToNow.setRequestedOrientation (oldLandscape ?
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_USER);
        }

        /*
         * Update text on button to indicate new setting.
         */
        setOrientButtonText ();
    }

    /**
     * Set text on button to match what the screen orientation currently is.
     */
    @SuppressLint("SetTextI18n")
    private void setOrientButtonText ()
    {
        int orientation = wairToNow.getRequestedOrientation ();
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_USER: {
                orientButton.setText ("Unlkd");
                break;
            }
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE: {
                orientButton.setText ("Land");
                break;
            }
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT: {
                orientButton.setText ("Port");
                break;
            }
            default: {
                orientButton.setText ("<<" + orientation + ">>");
                break;
            }
        }
    }

    /**
     * If ident box clicked, prompt user to enter new waypoint.
     */
    private void wpIdentClicked ()
    {
        Waypoint.ShowWaypointDialog (wairToNow, "Enter Nav Waypoint",
                wairToNow.currentGPSLat, wairToNow.currentGPSLon,
                (waypoint == null) ? "" : waypoint.ident,
                new Waypoint.Selected () {
                    @Override
                    public void wpSeld (Waypoint wp) {
                        useWaypointButtonClicked (wp);
                    }
                    @Override
                    public void noWPSeld () { }
                }
        );
    }

    /**
     * Set the nav dial to the given mode and update its values correspondingly.
     */
    @SuppressLint("SetTextI18n")
    private void setNavDialMode (NavDialView.Mode mode)
    {
        if (selectedPlateCIFP == null) {
            if ((mode == NavDialView.Mode.ILS) || (mode == NavDialView.Mode.LOC)) {
                navDial.obsSetting = locObsSetting;
            }
            navDial.setMode (mode);
            modeButton.setText (mode.toString ());
            computeRadial ();
        } else {
            navDial.setMode (mode);
            if (mode == NavDialView.Mode.OFF) {
                modeButton.setText ("OFF");
            } else {
                modeButton.setText ("CIFP");
            }
            computeRadial ();
        }

        // hide or show the HSI checkbox based on nav dial capability
        hsiCheckBox.setVisibility (navDial.isHSIAble () ? VISIBLE : GONE);
    }

    /**
     * Compute radial we are on and distance from the selected waypoint.
     * Update nav needles, DME and status text accordingly.
     */
    @SuppressLint("SetTextI18n")
    private void computeRadial ()
    {
        // CIFP mode tracking is its own thing
        if ((selectedPlateCIFP != null) && (navDial.getMode () != NavDialView.Mode.OFF)) {

            // see where we are at on the course
            String status = selectedPlateCIFP.getVNTracking (navDial);

            // maybe CIFP has been disabled
            if (status == null) {
                wpStatus.setText ("[OFF]");
                navDial.setMode (NavDialView.Mode.OFF);
                hsiCheckBox.setVisibility (GONE);
                return;
            }

            // update status text at top to say what we are doing
            wpStatus.setText (status);

            // hide or show the HSI checkbox based on nav dial capability
            hsiCheckBox.setVisibility (navDial.isHSIAble () ? VISIBLE : GONE);

            // draw airplane symbol showing where we are currently headed
            currentHeading ();
        } else if (waypoint != null) {

            // normal (non-CIFP) nav to single waypoint...
            SpannableStringBuilder status = new SpannableStringBuilder ();

            // start building status string with the OBS setting (ie, what dial is actually set to)
            if (navDial.getMode () != NavDialView.Mode.OFF) {
                status.append ("OBS: ");
                hdgString (status, navDial.obsSetting);
            }

            // update display based on what mode we are in
            switch (navDial.getMode ()) {
                case OFF: {
                    navDial.setDistance (NavDialView.NODISTANCE, null, false);
                    navDial.setHeading (NavDialView.NOHEADING);
                    break;
                }

                // VOR mode - needle deflection is difference of magnetic course from waypoint to aircraft
                //            and the OBS setting
                case VOR: {
                    double radial = Lib.LatLonTC (waypoint.lat, waypoint.lon, wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                    radial += wpmagvar;
                    navDial.setDeflect (navDial.obsSetting - radial);
                    status.append ("  Radial From: ");
                    hdgString (status, radial);
                    status.append ("  To: ");
                    hdgString (status, radial + 180);
                    dmeDisplay ();
                    currentHeading ();
                    break;
                }

                // ADF mode - needle deflection is difference of true course from waypoint to aircraft
                //            and true heading
                case ADF: {
                    double radial = Lib.LatLonTC (waypoint.lat, waypoint.lon, wairToNow.currentGPSLat, wairToNow.currentGPSLon);
                    radial -= wairToNow.currentGPSHdg;
                    navDial.setDeflect (radial + 180);
                    status.append ("  Rel Brng From: ");
                    hdgString (status, radial);
                    status.append ("  To: ");
                    hdgString (status, radial + 180);
                    dmeDisplay ();
                    navDial.setHeading (NavDialView.NOHEADING);
                    break;
                }

                // LOC mode - needle deflection is difference of true course from aircraft to waypoint
                //            and alignment of localizer antenna
                case LOC: {
                    computeLocRadial (status);
                    break;
                }

                // ILS mode - needle deflection is difference of true course from aircraft to waypoint
                //            and alignment of localizer antenna
                //            gs needle deflection is difference of angle from aircraft to gs antenna
                //            and tilt angle of gs antenna
                case ILS: {
                    Waypoint.Localizer loc = computeLocRadial (status);
                    status.append ("  GS Tilt: ");
                    status.append (Double.toString (loc.gs_tilt));
                    status.append ('\u00B0');
                    double descrate = Math.tan (Math.toRadians (loc.gs_tilt)) * wairToNow.currentGPSSpd * Lib.FtPerM * 60.0;
                    navDial.gsfpm   = (int) - Math.round (descrate);
                    double tctoloc  = Lib.LatLonTC (wairToNow.currentGPSLat, wairToNow.currentGPSLon, loc.lat, loc.lon);
                    double factor   = Math.cos (Math.toRadians (tctoloc - loc.thdg));
                    double horizfromant_nm = Lib.LatLonDist (wairToNow.currentGPSLat, wairToNow.currentGPSLon, loc.gs_lat, loc.gs_lon) * factor;
                    double aboveantenna_ft = wairToNow.currentGPSAlt * Lib.FtPerM - loc.gs_elev;
                    double degaboveantenna = Math.toDegrees (Math.atan2 (aboveantenna_ft, horizfromant_nm * Lib.MPerNM * Lib.FtPerM));
                    navDial.setSlope (loc.gs_tilt - degaboveantenna);
                    break;
                }
            }

            // update status text box
            wpStatus.setText (status);
        }
    }

    /**
     * Assuming we are in localizer/ILS mode, set needle deflection accordingly.
     * Also append info to status string.
     */
    private Waypoint.Localizer computeLocRadial (SpannableStringBuilder status)
    {
        Waypoint.Localizer loc = (Waypoint.Localizer) waypoint;
        status.append ("  Loc Hdg: ");
        hdgString (status, loc.thdg + wpmagvar);
        double tctoloc = Lib.LatLonTC (wairToNow.currentGPSLat, wairToNow.currentGPSLon, loc.lat, loc.lon);
        navDial.setDeflect (tctoloc - loc.thdg);
        dmeDisplay ();
        currentHeading ();
        return loc;
    }

    /**
     * Set up current heading marker (red airplane).
     */
    private void currentHeading ()
    {
        if (wairToNow.currentGPSSpd > WairToNow.gpsMinSpeedMPS) {
            double currentMH = wairToNow.currentGPSHdg + Lib.MagVariation (wairToNow.currentGPSLat,
                    wairToNow.currentGPSLon, wairToNow.currentGPSAlt);
            navDial.setHeading (currentMH - navDial.obsSetting);
        } else {
            navDial.setHeading (NavDialView.NOHEADING);
        }
    }

    /**
     * Format the given value to proper nnn degrees.
     */
    private static void hdgString (SpannableStringBuilder sb, double degs)
    {
        int idegs = (int) Math.round (degs);
        while (idegs <=  0) idegs += 360;
        while (idegs > 360) idegs -= 360;
        sb.append (Integer.toString (idegs + 1000).substring (1));
        sb.append ('\u00B0');
    }

    /**
     * Update the DME display.
     */
    private void dmeDisplay ()
    {
        if (dmeWaypoint == null) {
            navDial.setDistance (NavDialView.NODISTANCE, null, false);
        } else {
            double elev = dmeWaypoint.GetDMEElev ();
            double lat  = dmeWaypoint.GetDMELat ();
            double lon  = dmeWaypoint.GetDMELon ();
            double dist = Lib.LatLonDist (lat, lon, wairToNow.currentGPSLat, wairToNow.currentGPSLon);
            boolean slant = elev != Waypoint.ELEV_UNKNOWN;
            if (slant) {
                dist = Math.hypot (dist, (wairToNow.currentGPSAlt - elev / Lib.FtPerM) / Lib.MPerNM);
            }
            navDial.setDistance (dist, dmeWaypoint.ident, slant);
        }
    }
}
