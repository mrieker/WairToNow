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

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedList;

/**
 * Use Android device's internal GPS for location source.
 */
public class InternalGps extends GpsAdsbReceiver implements GpsStatus.Listener, LocationListener {
    public final static String TAG = "WairToNow";

    private final static int gpsTimeout = 3000;

    private boolean lastReceivedQueued;
    private boolean selected;
    private boolean displayOpen;
    private CheckBox intGPSEnable;
    private GpsStatus gpsStatus;
    private int numLocations;
    private int numStatuses;
    private LinearLayout linearLayout;
    private LinkedList<MyGpsSatellite> satellites;
    private long lastReceivedTime;
    private TextView intGPSStatus;

    public InternalGps (WairToNow wtn)
    {
        wairToNow  = wtn;
        satellites = new LinkedList<> ();

        intGPSEnable = new CheckBox (wairToNow);
        intGPSStatus = new TextView (wairToNow);

        wairToNow.SetTextSize (intGPSEnable);
        wairToNow.SetTextSize (intGPSStatus);

        intGPSEnable.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view)
            {
                intGPSEnabClicked ();
            }
        });
        intGPSEnable.setText (getDisplay ());

        linearLayout = new LinearLayout (wairToNow);
        linearLayout.setOrientation (LinearLayout.VERTICAL);
        linearLayout.addView (intGPSEnable);
        linearLayout.addView (intGPSStatus);

        updateLastReceived ();
    }

    /*****************************************************************\
     *  GpsAdsbReceiver implementation                               *
     *  Used by SensorsView to select the Internal GPS data source.  *
    \*****************************************************************/

    @Override  // GpsAdsbReceiver
    public String getPrefKey ()
    {
        return "internal";
    }

    @Override  // GpsAdsbReceiver
    public String getDisplay ()
    {
        return "Internal GPS";
    }

    @Override  // GpsAdsbReceiver
    public View displayOpened ()
    {
        displayOpen = true;
        return linearLayout;
    }

    @Override  // GpsAdsbReceiver
    public void displayClosed ()
    {
        displayOpen = false;
    }

    @Override  // GpsAdsbReceiver
    public void startSensor ()
    {
        if (ContextCompat.checkSelfPermission (wairToNow, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions (wairToNow,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    WairToNow.RC_INTGPS);
            return;
        }

        LocationManager locationManager = getLocationManager ();
        if (locationManager != null) {
            try {
                Location lastLoc = locationManager.getLastKnownLocation (LocationManager.GPS_PROVIDER);
                if (lastLoc == null) {
                    lastLoc = new Location ("InternalGps.start");
                }
                onLocationChanged (lastLoc);
                locationManager.requestLocationUpdates (LocationManager.GPS_PROVIDER, 333L, 0.0F, this);
                locationManager.addGpsStatusListener (this);
            } catch (IllegalArgumentException iae) {
                Log.w (TAG, "exception starting internal GPS", iae);
                String newmsg = iae.getMessage ();
                if (newmsg == null) newmsg = iae.getClass ().getName ();
                SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
                if (! newmsg.equals (prefs.getString ("notifiednointernalgps", ""))) {
                    SharedPreferences.Editor editr = prefs.edit ();
                    editr.putString ("notifiednointernalgps", newmsg);
                    editr.apply ();
                    newmsg += "\ngoto Sensors->Internal->Internal GPS to retry";
                    AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                    adb.setTitle ("Error starting Internal GPS");
                    adb.setMessage (newmsg);
                    adb.setPositiveButton ("OK", null);
                    adb.show ();
                }
                intGPSEnable.setChecked (false);
                return;
            }
            selected = true;

            numLocations = 0;
            numStatuses  = 0;

            // we are the default so make sure checkbox is checked
            intGPSEnable.setChecked (true);
            getButton ().setRingColor (Color.GREEN);
        }
    }

    @Override  // GpsAdsbReceiver
    public void stopSensor ()
    {
        selected = false;
        LocationManager locationManager = getLocationManager ();
        if (locationManager != null) {
            locationManager.removeUpdates (this);
            locationManager.removeGpsStatusListener (this);
        }
    }

    @Override  // GpsAdsbReceiver
    public void refreshStatus ()
    {
        SetInternalGPSStatus ();
    }

    @Override  // GpsAdsbReceiver
    public boolean isSelected ()
    {
        return selected;
    }

    // callback when ACCESS_FINE_LOCATION granted or denied
    // check/uncheck enable checkbox
    public void onRequestPermissionsResult ()
    {
        boolean granted = ContextCompat.checkSelfPermission (wairToNow,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Internal GPS Access");
            adb.setMessage ("Internal GPS access permission not granted, internal GPS disabled.  " +
                    "To enable it later, go to Sensors page and enable Internal GPS.");
            adb.setPositiveButton ("OK", null);
            adb.show ();
        }
        intGPSEnable.setChecked (granted);
        intGPSEnabClicked ();
    }

    /**
     * User has checked/unchecked the enable internal GPS checkbox.
     */
    private void intGPSEnabClicked ()
    {
        /*
         * Write to preferences so we remember it.
         */
        selected = intGPSEnable.isChecked ();
        SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
        SharedPreferences.Editor editr = prefs.edit ();
        editr.putBoolean ("selectedGPSReceiverKey_" + getPrefKey (), selected);
        editr.putString ("notifiednointernalgps", "");
        editr.apply ();

        /*
         * Start or stop the sensor likewise.
         */
        if (selected) {
            getButton ().setRingColor (Color.GREEN);
            startSensor ();
        } else {
            getButton ().setRingColor (Color.TRANSPARENT);
            stopSensor ();
        }

        /*
         * Update status display saying which sensors are enabled.
         */
        wairToNow.sensorsView.SetGPSLocation ();

        /*
         * Fill in/Blank out center of compass dial display.
         */
        SetInternalGPSStatus ();
    }

    /**
     * Try to get location manager.  If not present, output alert dialog.
     */
    private LocationManager getLocationManager ()
    {
        LocationManager lm = (LocationManager) wairToNow.getSystemService (Context.LOCATION_SERVICE);
        if (lm == null) {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle ("Internal GPS Access");
            adb.setMessage ("No location manager available, cannot access internal GPS");
            adb.show ();
            if (!selected) {
                intGPSEnable.setChecked (false);
                intGPSEnabClicked ();
            }
        }
        return lm;
    }

    /**
     * Internal GPS receiver got a status update, post it on through to the display.
     */
    private void SetInternalGPSStatus ()
    {
        if (displayOpen) {
            if (selected && (gpsStatus != null)) {
                Iterable<GpsSatellite> sats = gpsStatus.getSatellites ();
                satellites.clear ();
                for (GpsSatellite sat : sats) {
                    MyGpsSatellite mysat = new MyGpsSatellite ();
                    mysat.azim = sat.getAzimuth ();
                    mysat.elev = sat.getElevation ();
                    mysat.prn  = sat.getPrn ();
                    mysat.snr  = sat.getSnr ();
                    mysat.used = sat.usedInFix ();
                    satellites.addLast (mysat);
                }
                wairToNow.sensorsView.gpsStatusView.SetGPSStatus (satellites);
            }
        }
    }

    /**********************************************\
     *  LocationListener implementation           *
     *  Receives incoming GPS location readings.  *
    \**********************************************/

    @Override  // LocationListener
    public void onLocationChanged (@NonNull Location loc)
    {
        updateLastReceived ();
        if (selected) {
            wairToNow.LocationReceived (
                    loc.getSpeed (), loc.getAltitude (), loc.getBearing (),
                    loc.getLatitude (), loc.getLongitude (), loc.getTime ()
            );
            numLocations ++;
            updateStats ();
        }
    }

    @Override  // LocationListener
    public void onProviderDisabled(@NonNull String arg0)
    { }

    @Override  // LocationListener
    public void onProviderEnabled(@NonNull String arg0)
    { }

    @Override  // LocationListener
    public void onStatusChanged(String provider, int status, Bundle extras)
    { }

    /*********************************************\
     *  GpsStatus.Listener implementation        *
     *  Receives incoming GPS satellite status.  *
    \*********************************************/

    @Override  // GpsStatus.Listener
    public void onGpsStatusChanged (int event)
    {
        switch (event) {
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS: {
                updateLastReceived ();
                if (selected) {
                    LocationManager locationManager = getLocationManager ();
                    if (locationManager != null) {
                        try {
                            gpsStatus = locationManager.getGpsStatus (gpsStatus);
                            SetInternalGPSStatus ();
                            numStatuses++;
                            updateStats ();
                        } catch (SecurityException se) {
                            Log.w (TAG, "error reading gps status", se);
                            intGPSEnable.setChecked (false);
                            intGPSEnabClicked ();
                        }
                    }
                }
            }
        }
    }

    /**
     * Update time something (location or status) from GPS was received.
     * If nothing in last 3 seconds, warn than GPS might be turned off.
     */
    private void updateLastReceived ()
    {
        lastReceivedTime = System.currentTimeMillis ();
        if (! lastReceivedQueued) {
            lastReceivedQueued = true;
            WairToNow.wtnHandler.runDelayed (gpsTimeout, gpsTimedOut);
        }
    }

    /**
     * Called 3 seconds after latest call to updateLastReceived().
     */
    private final Runnable gpsTimedOut = new Runnable () {
        @Override
        public void run ()
        {
            long now = System.currentTimeMillis ();
            long togo = lastReceivedTime + gpsTimeout - now;
            if (togo > 0) {
                WairToNow.wtnHandler.runDelayed (togo, gpsTimedOut);
            } else {
                lastReceivedQueued = false;
                updateStats ();
            }
        }
    };

    /**
     * Update the internal gps status string on the Sensors screen.
     */
    private void updateStats ()
    {
        SpannableStringBuilder sb = new SpannableStringBuilder ();

        // if selected and haven't heard from GPS in a while, alert user that it might be disabled
        if (selected && (System.currentTimeMillis () - lastReceivedTime >= gpsTimeout)) {
            sb.append ("  GPS may not be present or enabled\n" +
                    "  Check Android Settings \u2192\n        Location \u2192 On\n");
            sb.setSpan (new ForegroundColorSpan (Color.RED),
                    0, sb.length (), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // update status string on Sensors page
        sb.append ("Locations: ");
        sb.append (Integer.toString (numLocations));
        sb.append ("\nStatuses: ");
        sb.append (Integer.toString (numStatuses));
        intGPSStatus.setText (sb);
    }
}
