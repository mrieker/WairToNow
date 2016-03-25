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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.location.Location;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.Locale;

/**
 * Display an OBS-dial looking screen that can be set to a waypoint
 * and operated like an ADF/VOR/ILS/LOC receiver.
 */
public class VirtNavView extends LinearLayout
        implements NavDialView.DMEClickedListener, NavDialView.OBSChangedListener, WairToNow.CanBeMainView {

    private boolean open;
    private Button modeButton;
    private float currentAlt, currentHdg, currentLat, currentLon, currentSpd;
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

        LayoutInflater inflater = (LayoutInflater) wtn.getSystemService (Context.LAYOUT_INFLATER_SERVICE);

        inflater.inflate (R.layout.virtnav, this);

        wairToNow.SetDetentSize ((DetentHorizontalScrollView) findViewById (R.id.buttonscroller));
        wairToNow.SetDetentSize ((DetentHorizontalScrollView) findViewById (R.id.identscroller));

        modeButton = (Button) findViewById (R.id.modebutton);
        wairToNow.SetTextSize (modeButton);
        modeButton.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                modeButtonClicked ();
            }
        });

        Button useFAAWP1 = (Button) findViewById (R.id.use_faawp1);
        wairToNow.SetTextSize (useFAAWP1);
        useFAAWP1.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useWaypointButtonClicked (wairToNow.waypointView1.selectedWaypoint);
            }
        });

        Button useFAAWP2 = (Button) findViewById (R.id.use_faawp2);
        wairToNow.SetTextSize (useFAAWP2);
        useFAAWP2.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useWaypointButtonClicked (wairToNow.waypointView2.selectedWaypoint);
            }
        });

        Button useUserWP = (Button) findViewById (R.id.use_userwp);
        wairToNow.SetTextSize (useUserWP);
        useUserWP.setOnClickListener (new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                useWaypointButtonClicked (wairToNow.userWPView.selectedUserWP);
            }
        });

        wpIdent = (TextView) findViewById (R.id.wpident);
        wpStatus = (TextView) findViewById (R.id.wpstatus);
        navDial = (NavDialView) findViewById (R.id.navdial);

        wpIdent.setTextColor (Color.YELLOW);
        wpIdent.setTextSize (TypedValue.COMPLEX_UNIT_PX, wairToNow.textSize * 1.5F);
        wairToNow.SetTextSize (wpStatus);
        navDial.dmeClickedListener = this;
        navDial.obsChangedListener = this;

        useWaypointButtonClicked (null);
    }

    /**
     * Got a new GPS location, so update the needle.
     */
    public void SetGPSLocation (Location loc)
    {
        currentAlt = (float) loc.getAltitude ();
        currentHdg = loc.getBearing ();
        currentLat = (float) loc.getLatitude ();
        currentLon = (float) loc.getLongitude ();
        currentSpd = loc.getSpeed ();

        if (open) {
            // update needle(s)
            computeRadial ();
            // update the GPS blinking border status box
            // - causes dispatchDraw() to be called
            invalidate ();
        }
    }

    /** CanBeMainView **/
    public String GetTabName () { return label; }
    public void OpenDisplay () { open = true; computeRadial (); }
    public void CloseDisplay () { open = false; }
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
            case OFF: mode = NavDialView.Mode.VOR; break;
            case VOR: mode = NavDialView.Mode.ADF; break;
            case ADF: mode = NavDialView.Mode.LOC; break;
            case LOC: mode = NavDialView.Mode.ILS; break;
            case ILS: mode = NavDialView.Mode.OFF; break;
        }
        if (mode == NavDialView.Mode.LOC && !(waypoint instanceof Waypoint.Localizer)) {
            mode = NavDialView.Mode.OFF;
        }
        if (mode == NavDialView.Mode.ILS && (!(waypoint instanceof Waypoint.Localizer) || (((Waypoint.Localizer) waypoint).gs_elev == Waypoint.ELEV_UNKNOWN))) {
            mode = NavDialView.Mode.OFF;
        }
        navDial.setMode (mode);
        modeButton.setText (mode.toString ());
        computeRadial ();
    }

    /**
     * One of the "use" buttons was clicked to select a waypoint.
     * @param wp = waypoint active in that source
     */
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
                navDial.obsSetting = ((Waypoint.Localizer)waypoint).thdg + wpmagvar;
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
        showDMESelectDialog ((dmeWaypoint == null) ? waypoint.ident : dmeWaypoint.ident);
    }

    /**
     * Select a different waypoint for DME display.
     * @param oldid = value to put in text input box to start with
     */
    private void showDMESelectDialog (String oldid)
    {
        // set up a text box to enter the new identifier in
        // populate it with the current identifier
        final EditText ident = new EditText (wairToNow);
        ident.setInputType (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        ident.setSingleLine ();
        ident.setText (oldid);
        wairToNow.SetTextSize (ident);

        // set up and display an alert dialog box to enter new identifier
        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
        adb.setTitle ("Enter DME waypoint");
        adb.setView (ident);
        adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                updateDMEWaypoint (ident);
            }
        });
        adb.setNeutralButton ("OFF", new DialogInterface.OnClickListener () {
            @Override
            public void onClick (DialogInterface dialogInterface, int i)
            {
                ident.setText ("");
                updateDMEWaypoint (ident);
            }
        });
        adb.setNegativeButton ("Cancel", null);
        final AlertDialog dmeDialog = adb.show ();

        // set up listener for the DONE keyboard key
        ident.setOnEditorActionListener (new TextView.OnEditorActionListener () {
            @Override
            public boolean onEditorAction (TextView textView, int i, KeyEvent keyEvent)
            {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    dmeDialog.dismiss ();
                    updateDMEWaypoint (ident);
                }
                return false;
            }
        });
    }

    private void updateDMEWaypoint (EditText ident)
    {
        String newid = ident.getText ().toString ().replace (" ", "").toUpperCase (Locale.US);
        if (newid.equals ("")) {
            dmeWaypoint = null;
        } else {
            LinkedList<Waypoint> wplist = Waypoint.GetWaypointsByIdent (newid);
            Waypoint bestwp = null;
            float bestnm = 999999.0F;
            for (Waypoint wp : wplist) {
                float nm = Lib.LatLonDist (wp.lat, wp.lon, waypoint.lat, waypoint.lon);
                if (bestnm > nm) {
                    bestnm = nm;
                    bestwp = wp;
                }
            }
            if (bestwp == null) {
                showDMESelectDialog (newid);
                return;
            }
            dmeWaypoint = bestwp;
        }
        dmeDisplay ();
    }

    @Override  // ViewGroup
    protected void dispatchDraw (Canvas canvas)
    {
        super.dispatchDraw (canvas);
        wairToNow.drawGPSAvailable (canvas, this);
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
                navDial.setDistance (NavDialView.NODISTANCE, null);
                navDial.setHeading (NavDialView.NOHEADING);
                break;
            }

            // VOR mode - needle deflection is difference of magnetic course from waypoint to aircraft
            //            and the OBS setting
            case VOR: {
                float radial = Lib.LatLonTC (waypoint.lat, waypoint.lon, currentLat, currentLon);
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
                float radial = Lib.LatLonTC (waypoint.lat, waypoint.lon, currentLat, currentLon);
                radial -= currentHdg;
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
                float tctoloc = Lib.LatLonTC (currentLat, currentLon, loc.lat, loc.lon);
                float factor  = Mathf.cos (Mathf.toRadians (tctoloc - loc.thdg));
                float horizfromant_nm = Lib.LatLonDist (currentLat, currentLon, loc.gs_lat, loc.gs_lon) * factor;
                float aboveantenna_ft = currentAlt * Lib.FtPerM - loc.gs_elev;
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
        float tctoloc = Lib.LatLonTC (currentLat, currentLon, loc.lat, loc.lon);
        navDial.setDeflect (tctoloc - loc.thdg);
        dmeDisplay ();
        currentHeading ();
        return loc;
    }

    /**
     * Set up current heading marker (magenta triangle).
     */
    private void currentHeading ()
    {
        if (currentSpd > WairToNow.gpsMinSpeedMPS) {
            float currentMH = currentHdg + Lib.MagVariation (currentLat, currentLon, currentAlt);
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
            navDial.setDistance (NavDialView.NODISTANCE, null);
        } else {
            float elev = dmeWaypoint.GetDMEElev ();
            float lat  = dmeWaypoint.GetDMELat ();
            float lon  = dmeWaypoint.GetDMELon ();
            float dist = Lib.LatLonDist (lat, lon, currentLat, currentLon);
            if (elev != Waypoint.ELEV_UNKNOWN) {
                dist = Mathf.hypot (dist, (currentAlt - elev / Lib.FtPerM) / Lib.MPerNM);
            }
            navDial.setDistance (dist, dmeWaypoint.ident);
        }
    }
}
