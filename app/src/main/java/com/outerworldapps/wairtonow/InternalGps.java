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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.*;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedList;

/**
 * Use Android device's internal GPS for location source.
 */
public class InternalGps extends GpsAdsbReceiver implements GpsStatus.Listener, LocationListener {
    private boolean selected;
    private boolean displayOpen;
    private CheckBox intGPSEnable;
    private GpsStatus gpsStatus;
    private int numLocations;
    private int numStatuses;
    private LinearLayout linearLayout;
    private LinkedList<MyGpsSatellite> satellites;
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
            public void onClick (View view) {
                intGPSEnabClicked ();
            }
        });
        intGPSEnable.setText (getDisplay ());

        linearLayout = new LinearLayout (wairToNow);
        linearLayout.setOrientation (LinearLayout.VERTICAL);
        linearLayout.addView (intGPSEnable);
        linearLayout.addView (intGPSStatus);
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
        LocationManager locationManager = (LocationManager) wairToNow.getSystemService (Context.LOCATION_SERVICE);
        Location lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastLoc == null) {
            lastLoc = new Location ("InternalGps.start");
        }
        onLocationChanged (lastLoc);
        locationManager.requestLocationUpdates (LocationManager.GPS_PROVIDER, 333L, 0.0F, this);
        locationManager.addGpsStatusListener (this);
        selected = true;

        numLocations = 0;
        numStatuses  = 0;

        // we are the default so make sure checkbox is checked
        intGPSEnable.setChecked (true);
        getButton ().setRingColor (Color.GREEN);
    }

    @Override  // GpsAdsbReceiver
    public void stopSensor ()
    {
        selected = false;
        LocationManager locationManager = (LocationManager) wairToNow.getSystemService (Context.LOCATION_SERVICE);
        locationManager.removeUpdates (this);
        locationManager.removeGpsStatusListener (this);
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
        editr.commit ();

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
     * Internal GPS receiver got a status update, post it on through to the display.
     * But if bluetooth selected instead, blank out the display.
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

    private void updateStats ()
    {
        StringBuilder sb = new StringBuilder ();
        sb.append ("Locations: ");
        sb.append (numLocations);
        sb.append ("\nStatuses: ");
        sb.append (numStatuses);
        intGPSStatus.setText (sb);
    }

    /**********************************************\
     *  LocationListener implementation           *
     *  Receives incoming GPS location readings.  *
    \**********************************************/

    @Override  // LocationListener
    public void onLocationChanged (Location loc)
    {
        if (selected) {
            wairToNow.LocationReceived (
                    loc.getSpeed (), (float) loc.getAltitude (), loc.getBearing (),
                    (float) loc.getLatitude (), (float) loc.getLongitude (),
                    loc.getTime ()
            );
            numLocations ++;
            updateStats ();
        }
    }

    @Override  // LocationListener
    public void onProviderDisabled(String arg0)
    { }

    @Override  // LocationListener
    public void onProviderEnabled(String arg0)
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
                if (selected) {
                    LocationManager locationManager = (LocationManager) wairToNow.getSystemService (Context.LOCATION_SERVICE);
                    gpsStatus = locationManager.getGpsStatus (gpsStatus);
                    SetInternalGPSStatus ();
                    numStatuses ++;
                    updateStats ();
                }
            }
        }
    }
}
