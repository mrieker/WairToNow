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
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.TreeMap;

/**
 * Display Sensors panel, used for viewing and selecting sensor input sources.
 */
@SuppressLint("ViewConstructor")
public class SensorsView
        extends FrameLayout
        implements WairToNow.CanBeMainView {
    public final static String TAG = "WairToNow";
    public final static int GPS_OFF_DELAY_MS = 30 * 1000;

    private boolean displayOpen;
    private DetentHorizontalScrollView buttonScrollView;
    private DetentHorizontalScrollView statusScrollView;
    public  InternalGps internalGPS;
    private LinearLayout mainLinearLayout;
    private DecimalFormat formatter = new DecimalFormat ("#.#");
    public  GpsStatusView gpsStatusView;
    private NNHashMap<String,RingedButton> selectButtons = new NNHashMap<> ();
    private TextView gpsStatusText;
    private TreeMap<String,GpsAdsbReceiver> selectableGPSs = new TreeMap<> ();
    public  WairToNow wairToNow;

    //    0: turning all GPSs off (or they are off now)
    //  MAX: GPS is on and should stay on indefinitely
    // else: GPS is going to be turned off at this time
    private long turnGPSOffAt;

    @SuppressLint("InflateParams")
    public SensorsView (WairToNow wtn)
    {
        super (wtn);
        wairToNow = wtn;

        // internal GPS receiver
        internalGPS = new InternalGps (wairToNow);
        selectableGPSs.put (internalGPS.getPrefKey (), internalGPS);

        // bluetooth GPS/ADS-B receiver #1
        BluetoothGpsAdsb bluetoothGpsAdsb1 = new BluetoothGpsAdsb (this, 1);
        selectableGPSs.put (bluetoothGpsAdsb1.getPrefKey (), bluetoothGpsAdsb1);

        // bluetooth GPS/ADS-B receiver #2
        BluetoothGpsAdsb bluetoothGpsAdsb2 = new BluetoothGpsAdsb (this, 2);
        selectableGPSs.put (bluetoothGpsAdsb2.getPrefKey (), bluetoothGpsAdsb2);

        // wifi UDP GPS/ADS-B receiver #1
        WiFiUDPGpsAdsb wifiUDPGpsAdsb1 = new WiFiUDPGpsAdsb (this, 1);
        selectableGPSs.put (wifiUDPGpsAdsb1.getPrefKey (), wifiUDPGpsAdsb1);

        // wifi UDP GPS/ADS-B receiver #2
        WiFiUDPGpsAdsb wifiUDPGpsAdsb2 = new WiFiUDPGpsAdsb (this, 2);
        selectableGPSs.put (wifiUDPGpsAdsb2.getPrefKey (), wifiUDPGpsAdsb2);

        // wifi TCP GPS/ADS-B receiver #1
        WiFiTCPGpsAdsb wifiTCPGpsAdsb1 = new WiFiTCPGpsAdsb (this, 1);
        selectableGPSs.put (wifiTCPGpsAdsb1.getPrefKey (), wifiTCPGpsAdsb1);

        // wifi TCP GPS/ADS-B receiver #2
        WiFiTCPGpsAdsb wifiTCPGpsAdsb2 = new WiFiTCPGpsAdsb (this, 2);
        selectableGPSs.put (wifiTCPGpsAdsb2.getPrefKey (), wifiTCPGpsAdsb2);

        // log playback
        if (GpsAdsbReceiver.LOGGING_ENABLED) {
            LogPlayGpsAdsb logPlayGpsAdsb = new LogPlayGpsAdsb (this);
            selectableGPSs.put (logPlayGpsAdsb.getPrefKey (), logPlayGpsAdsb);
        }

        // compass-like dial that shows where GPS satellites are
        gpsStatusView = new GpsStatusView (wtn);
        int gsvSize = Math.round (wtn.dotsPerInch * 1.5F);
        LinearLayout.LayoutParams gpsStatusViewLayoutParams =
                new LinearLayout.LayoutParams (gsvSize, gsvSize);
        gpsStatusView.setLayoutParams (gpsStatusViewLayoutParams);

        // text box at top of screen
        // shows which sensors are active
        // shows latest GPS location and date/time
        gpsStatusText = new TextView (wtn);
        wairToNow.SetTextSize (gpsStatusText);
        LinearLayout.LayoutParams gpsStatusTextLayoutParams =
                new LinearLayout.LayoutParams (ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        gpsStatusText.setLayoutParams (gpsStatusTextLayoutParams);

        // bundle them together in a horizontally scrollable linear layout
        LinearLayout statusLinearLayout = new LinearLayout (wtn);
        statusLinearLayout.setOrientation (LinearLayout.HORIZONTAL);
        statusLinearLayout.addView (gpsStatusView);
        statusLinearLayout.addView (gpsStatusText);

        statusScrollView = new DetentHorizontalScrollView (wtn);
        wtn.SetDetentSize (statusScrollView);
        statusScrollView.setLayoutParams (new ViewGroup.LayoutParams (
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        statusScrollView.addView (statusLinearLayout);

        // make horizontally scrollable linear layout to hold all the sensor selection buttons
        LinearLayout buttonLinearLayout = new LinearLayout (wtn);
        buttonLinearLayout.setOrientation (LinearLayout.HORIZONTAL);
        buttonScrollView = new DetentHorizontalScrollView (wtn);
        wtn.SetDetentSize (buttonScrollView);
        buttonScrollView.addView (buttonLinearLayout);

        // set up listener to display the sensor-specific page below the button row
        OnClickListener receiverButtonClicked = new OnClickListener () {
            @Override
            public void onClick (View view)
            {
                for (GpsAdsbReceiver rcvr : selectableGPSs.values ()) {
                    rcvr.displayClosed ();
                    selectButtons.nnget (rcvr.getPrefKey ()).setTextColor (Color.BLACK);
                }
                GpsAdsbReceiver receiver = (GpsAdsbReceiver) view.getTag ();
                View receiverView = receiver.displayOpened ();
                buildMainLayout (receiverView);
                selectButtons.nnget (receiver.getPrefKey ()).setTextColor (Color.RED);
            }
        };

        // fill it in with a button for each possible sensor
        for (GpsAdsbReceiver receiver : selectableGPSs.values ()) {
            RingedButton button = receiver.getButton ();
            buttonLinearLayout.addView (button);
            button.setOnClickListener (receiverButtonClicked);
            selectButtons.put (receiver.getPrefKey (), button);
        }

        // make a vertical linear layout to hold all that and fill it in
        mainLinearLayout = new LinearLayout (wtn);
        mainLinearLayout.setOrientation (LinearLayout.VERTICAL);
        mainLinearLayout.setLayoutParams (new ViewGroup.LayoutParams (
                ViewGroup.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        buildMainLayout (null);

        // make all that scrollable in case it overflows screen
        ScrollView mainScroller = new ScrollView (wtn);
        mainScroller.addView (mainLinearLayout);
        addView (mainScroller);
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Sensors";
    }

    @Override  // CanBeMainView
    public int GetOrientation ()
    {
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    /**
     * What to show when the back button is pressed
     */
    @Override
    public View GetBackPage ()
    {
        return wairToNow.chartView;
    }

    /**
     * The sensors screen is about to be made visible so tell all the sensors.
     */
    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay ()
    {
        displayOpen = true;
        gpsStatusView.Startup (wairToNow);
        for (GpsAdsbReceiver gps : selectableGPSs.values ()) {
            gps.displayOpened ();
            gps.refreshStatus ();
        }
        SetGPSLocation ();
    }

    /**
     * The sensors screen is no longer current.
     */
    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay ()
    {
        for (GpsAdsbReceiver gps : selectableGPSs.values ()) {
            gps.displayClosed ();
        }
        gpsStatusView.Shutdown ();
        displayOpen = false;
    }

    /**
     * Tab is being re-clicked when already active.
     */
    @Override  // WairToNow.CanBeMainView
    public void ReClicked ()
    { }

    /**
     * Intercept drawing to draw red/yellow box around the edge.
     */
    @Override  // ViewGroup
    protected void dispatchDraw (Canvas canvas)
    {
        super.dispatchDraw (canvas);
        wairToNow.drawGPSAvailable (canvas, this);
    }

    /**
     * The app is now visible so start receiving GPS updates.
     */
    public void startGPSReceiver ()
    {
        // if still in grace period where user clicked home button then came back in,
        // just disable the shutoff timer and don't restart anything.
        if (SystemClock.uptimeMillis () >= turnGPSOffAt) {

            // it is past the grace period so the GPSs are probably shut down
            // but make sure they are by stopping them all
            stopGPSReceiverNow ();

            // see which ones are selected in the preferences
            // default to the internal GPS receiver
            SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
            for (GpsAdsbReceiver gps : selectableGPSs.values ()) {
                boolean selected = prefs.getBoolean ("selectedGPSReceiverKey_" + gps.getPrefKey (),
                        (gps == internalGPS));
                if (selected) {
                    gps.startSensor ();
                }
            }
        }

        // now that app is in foreground and GPS is running,
        // tell timer not to shut the GPSs off
        turnGPSOffAt = Long.MAX_VALUE;

        // update on-screen status, first the common status then the receiver-specific status
        SetGPSLocation ();
        for (GpsAdsbReceiver gps : selectableGPSs.values ()) {
            gps.refreshStatus ();
        }
    }

    /**
     * App is going to background, don't waste battery on receiving GPS/ADS-B updates.
     * But wait a few seconds so they don't have to wait for resync just for pushing wrong button.
     */
    public void stopGPSReceiverDelayed ()
    {
        turnGPSOffAt = SystemClock.uptimeMillis () + GPS_OFF_DELAY_MS;
        WairToNow.wtnHandler.runDelayed (GPS_OFF_DELAY_MS, new Runnable () {
            @Override
            public void run () {
                //    0: GPS is already shut off, so don't bother
                //  MAX: GPS is turned on and must stay on indefinitely
                // else: shut GPS off if it is at or after that time
                long when = turnGPSOffAt;
                if ((when > 0) && (when < Long.MAX_VALUE)) {
                    when -= SystemClock.uptimeMillis ();
                    if (when > 0) {
                        WairToNow.wtnHandler.runDelayed (when, this);
                    } else {
                        stopGPSReceiverNow ();
                    }
                }
            }
        });
    }

    /**
     * Stop all GPS/ADS-B receivers now.
     */
    public void stopGPSReceiverNow ()
    {
        // indicate that we are turning off all the GPSs
        turnGPSOffAt = 0;

        // turn them all off
        for (GpsAdsbReceiver gps : selectableGPSs.values ()) {
            gps.stopSensor ();
        }
    }

    /**
     * Update GPS common text on screen.
     */
    public void SetGPSLocation ()
    {
        if (displayOpen) {
            StringBuilder sb = new StringBuilder ();

            boolean selected = false;
            for (GpsAdsbReceiver gps : selectableGPSs.values ()) {
                if (gps.isSelected ()) {
                    sb.append (gps.getDisplay ());
                    sb.append (" selected\n");
                    selected = true;
                }
            }
            if (!selected) {
                sb.append ("no GPS selected\n");
            }

            if (wairToNow.currentGPSTime > 0) {
                sb.append (Lib.TimeStringUTC (wairToNow.currentGPSTime));
            } else {
                sb.append ("no GPS co-ordinate received");
            }

            sb.append ('\n');

            double latitude  = wairToNow.currentGPSLat;
            double longitude = wairToNow.currentGPSLon;
            sb.append (wairToNow.optionsView.LatLonString (latitude,  'N', 'S'));
            sb.append ("    ");
            sb.append (wairToNow.optionsView.LatLonString (longitude, 'E', 'W'));

            sb.append ('\n');

            double altitude = wairToNow.currentGPSAlt;
            double heading  = wairToNow.currentGPSHdg;
            double speed    = wairToNow.currentGPSSpd;
            sb.append (Math.round (altitude * Lib.FtPerM));
            sb.append (" ft MSL    ");
            sb.append (wairToNow.optionsView.HdgString (heading, latitude, longitude, altitude));
            sb.append ("    ");
            if (wairToNow.optionsView.ktsMphOption.getAlt ()) {
                sb.append (formatter.format (speed * 3600 / Lib.MPerNM * Lib.SMPerNM));
                sb.append (" mph");
            } else {
                sb.append (formatter.format (speed * 3600 / Lib.MPerNM));
                sb.append (" kts");
            }

            gpsStatusText.setText (sb);
        }
    }

    /**
     * Fill in main layout with or without a sensor-specific page.
     */
    private void buildMainLayout (View receiverView)
    {
        mainLinearLayout.removeAllViews ();
        mainLinearLayout.addView (statusScrollView);
        mainLinearLayout.addView (buttonScrollView);
        if (receiverView != null) {
            mainLinearLayout.addView (receiverView);
        }
    }
}
