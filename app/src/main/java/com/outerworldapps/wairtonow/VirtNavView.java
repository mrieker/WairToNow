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
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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
    private float wpmagvar;
    private NavDialView navDial;
    private String label;
    private TextView wpIdent;
    private TextView wpStatus;
    private WairToNow wairToNow;
    private Waypoint dmeWaypoint;
    private Waypoint waypoint;

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
        setNavDialMode (mode);
    }

    /**
     * One of the "use" buttons was clicked to select a waypoint.
     *
     * @param wp = waypoint active in that source
     */
    @SuppressLint("SetTextI18n")
    private void useWaypointButtonClicked (Waypoint wp)
    {
        dmeWaypoint = waypoint = wp;
        if (waypoint == null) {
            wpIdent.setText ("[OFF]");
            navDial.setMode (NavDialView.Mode.OFF);
        } else {
            String wpstr = wp.MenuKey ();
            wpIdent.setText (wpstr);
            wpmagvar = waypoint.GetMagVar (0);
            if (waypoint instanceof Waypoint.Localizer) {
                navDial.setMode (NavDialView.Mode.LOC);
                if (((Waypoint.Localizer) waypoint).gs_elev != Waypoint.ELEV_UNKNOWN) {
                    navDial.setMode (NavDialView.Mode.ILS);
                }
                navDial.obsSetting = ((Waypoint.Localizer) waypoint).thdg + wpmagvar;
            } else if (wpstr.startsWith ("NDB")) {
                navDial.setMode (NavDialView.Mode.ADF);
            } else {
                navDial.setMode (NavDialView.Mode.VOR);
            }
        }

        modeButton.setText (navDial.getMode ().toString ());
        computeRadial ();
    }

    @Override  // OBSChangedListener
    public void obsChanged (float obs)
    {
        computeRadial ();
    }

    @Override  // DMEClickedListener
    public void dmeClicked ()
    {
        Waypoint.ShowWaypointDialog (wairToNow, "Enter DME Waypoint",
                waypoint.lat, waypoint.lon,
                (dmeWaypoint == null) ? waypoint.ident : dmeWaypoint.ident,
                new Waypoint.Selected () {
                    @Override
                    public void wpSeld (Waypoint wp)
                    {
                        dmeWaypoint = wp;
                        dmeDisplay ();
                    }
                }
        );
    }

    @Override  // ViewGroup
    protected void dispatchDraw (Canvas canvas)
    {
        boolean landscape = getWidth () > getHeight ();
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
        super.dispatchDraw (canvas);
        wairToNow.drawGPSAvailable (canvas, this);
    }

    /**
     * Screen orientaiton (portrait/landscape) just changed,
     * reconfigure layout accordingly.
     */
    private void orientationChanged ()
    {
        LayoutInflater inflater = (LayoutInflater) wairToNow.getSystemService (Context.LAYOUT_INFLATER_SERVICE);

        /*
         * Save current settings.
         */
        String oldIdent = (wpIdent == null) ? "" : wpIdent.getText ().toString ();
        NavDialView.Mode oldMode = (navDial == null) ? NavDialView.Mode.OFF : navDial.getMode ();
        float oldObsSetting = (navDial == null) ? 0.0F : navDial.obsSetting;

        /*
         * Load layout for portrait or landscape orientation.
         * Get all the widget pointers for whichever we load.
         * Only landscape orientation has the plate view frame.
         */
        Button useFAAWP1, useFAAWP2, useUserWP;
        FrameLayout plateViewFrame;
        if (oldLandscape) {
            @SuppressLint("InflateParams")
            View mainView = inflater.inflate (R.layout.virtnavland, null);

            removeAllViews ();
            addView (mainView);

            wairToNow.SetDetentSize ((DetentHorizontalScrollView) findViewById (R.id.identscrollerland));
            wairToNow.SetDetentSize ((DetentHorizontalScrollView) findViewById (R.id.statusscrollerland));

            modeButton = (Button) findViewById (R.id.modebuttonland);
            orientButton = (Button) findViewById (R.id.orientland);
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
            orientButton = (Button) findViewById (R.id.orientport);
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
            public void onClick (View view)
            {
                wpIdentClicked ();
            }
        });

        wairToNow.SetTextSize (modeButton);
        modeButton.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                modeButtonClicked ();
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

        wairToNow.SetTextSize (useFAAWP1);
        useFAAWP1.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useWaypointButtonClicked (wairToNow.waypointView1.selectedWaypoint);
            }
        });

        wairToNow.SetTextSize (useFAAWP2);
        useFAAWP2.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useWaypointButtonClicked (wairToNow.waypointView2.selectedWaypoint);
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
         * If landscape mode, load most recent IAP plate on right-hand side, if any.
         */
        if ((plateViewFrame != null) && (wairToNow.lastPlate_aw != null)) {
            plateViewFrame.removeAllViews ();
            PlateView pv = new PlateView (wairToNow, wairToNow.lastPlate_fn,
                    wairToNow.lastPlate_aw, wairToNow.lastPlate_pd, wairToNow.lastPlate_ex, false);
            plateViewFrame.addView (pv);
        }

        /*
         * Restore settings.
         */
        wpIdent.setText (oldIdent);
        setNavDialMode (oldMode);
        navDial.obsSetting = oldObsSetting;
        navDial.invalidate ();
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
                    public void wpSeld (Waypoint wp)
                    {
                        useWaypointButtonClicked (wp);
                    }
                }
        );
    }

    /**
     * Set the nav dial to the given mode and update its values correspondingly.
     */
    private void setNavDialMode (NavDialView.Mode mode)
    {
        navDial.setMode (mode);
        modeButton.setText (mode.toString ());
        computeRadial ();
    }

    /**
     * Compute radial we are on from the selected waypoint.
     * Update nav needles and status text accordingly.
     */
    private void computeRadial ()
    {
        // start building status string with the OBS setting (ie, what dial is actually set to)
        SpannableStringBuilder status = new SpannableStringBuilder ();
        status.append ("OBS: ");
        hdgString (status, navDial.obsSetting);

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
                float radial = Lib.LatLonTC (waypoint.lat, waypoint.lon, wairToNow.currentGPSLat, wairToNow.currentGPSLon);
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
                float radial = Lib.LatLonTC (waypoint.lat, waypoint.lon, wairToNow.currentGPSLat, wairToNow.currentGPSLon);
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
                status.append (Float.toString (loc.gs_tilt));
                status.append ('\u00B0');
                float tctoloc = Lib.LatLonTC (wairToNow.currentGPSLat, wairToNow.currentGPSLon, loc.lat, loc.lon);
                float factor  = Mathf.cos (Mathf.toRadians (tctoloc - loc.thdg));
                float horizfromant_nm = Lib.LatLonDist (wairToNow.currentGPSLat, wairToNow.currentGPSLon, loc.gs_lat, loc.gs_lon) * factor;
                float aboveantenna_ft = wairToNow.currentGPSAlt * Lib.FtPerM - loc.gs_elev;
                float degaboveantenna = Mathf.toDegrees (Mathf.atan2 (aboveantenna_ft, horizfromant_nm * Lib.MPerNM * Lib.FtPerM));
                navDial.setSlope (loc.gs_tilt - degaboveantenna);
                break;
            }
        }

        // update status text box
        wpStatus.setText (status);
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
        float tctoloc = Lib.LatLonTC (wairToNow.currentGPSLat, wairToNow.currentGPSLon, loc.lat, loc.lon);
        navDial.setDeflect (tctoloc - loc.thdg);
        dmeDisplay ();
        currentHeading ();
        return loc;
    }

    /**
     * Set up current heading marker (red triangle).
     */
    private void currentHeading ()
    {
        if (wairToNow.currentGPSSpd > WairToNow.gpsMinSpeedMPS) {
            float currentMH = wairToNow.currentGPSHdg + Lib.MagVariation (wairToNow.currentGPSLat,
                    wairToNow.currentGPSLon, wairToNow.currentGPSAlt);
            navDial.setHeading (currentMH - navDial.obsSetting);
        } else {
            navDial.setHeading (NavDialView.NOHEADING);
        }
    }

    /**
     * Format the given value to proper nnn degrees.
     */
    private static void hdgString (SpannableStringBuilder sb, float degs)
    {
        int idegs = Math.round (degs);
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
            float elev = dmeWaypoint.GetDMEElev ();
            float lat  = dmeWaypoint.GetDMELat ();
            float lon  = dmeWaypoint.GetDMELon ();
            float dist = Lib.LatLonDist (lat, lon, wairToNow.currentGPSLat, wairToNow.currentGPSLon);
            boolean slant = elev != Waypoint.ELEV_UNKNOWN;
            if (slant) {
                dist = Mathf.hypot (dist, (wairToNow.currentGPSAlt - elev / Lib.FtPerM) / Lib.MPerNM);
            }
            navDial.setDistance (dist, dmeWaypoint.ident, slant);
        }
    }
}
