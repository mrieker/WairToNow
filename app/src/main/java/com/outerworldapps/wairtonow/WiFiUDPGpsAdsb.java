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
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * WiFi UDP GPS/ADS-B receiver.
 * When enabled, reads GPS/ADS-B info from UDP and pushes to UI.
 * Packets are assumed to be NMEA ('$'...<CR><LF>) or GDL-90 (0x7E...0x7E) format (can be a mix).
 */
public class WiFiUDPGpsAdsb extends GpsAdsbReceiver {
    public final static String TAG = "WairToNow";
    public final static int DEFAULT_PORT = 1269;
    public final static int MIN_PORT = 1024;
    public final static int MAX_PORT = 65535;

    private final static int DISCONNECTED = 0;
    private final static int CONNECTING   = 1;
    private final static int CONNECTED    = 2;
    private final static int CONFAILED    = 3;

    private AdsbGpsRThread adsbContext;
    private boolean btShowErrorAlerts;
    private boolean displayOpen;
    private volatile boolean pktReceivedQueued;
    private byte[] onebyte = new byte[1];
    private CheckBox btAdsbEnable;
    private DatagramSocket udpSocket;
    private EditText btAdsbPortNo;
    private int btAdsbLastStatus;
    private LinearLayout linearLayout;
    private String prefKey;
    private TextView btAdsbStatus;
    private TextView netIntfsView;

    @SuppressLint("SetTextI18n")
    public WiFiUDPGpsAdsb (SensorsView sv, int n)
    {
        wairToNow = sv.wairToNow;

        prefKey = "udp" + n;

        btAdsbEnable = new CheckBox (wairToNow);
        btAdsbPortNo = new EditText (wairToNow);
        btAdsbStatus = new TextView (wairToNow);
        netIntfsView = new TextView (wairToNow);

        btAdsbEnable.setText ("WiFi UDP GPS/ADS-B #" + n);

        btAdsbPortNo.setEms (5);
        btAdsbPortNo.setInputType (InputType.TYPE_CLASS_NUMBER);
        btAdsbPortNo.setText (Integer.toString (DEFAULT_PORT));

        wairToNow.SetTextSize (btAdsbEnable);
        wairToNow.SetTextSize (btAdsbPortNo);
        wairToNow.SetTextSize (btAdsbStatus);
        wairToNow.SetTextSize (netIntfsView);

        TextView portnotv = new TextView (wairToNow);
        portnotv.setText (" Port");
        wairToNow.SetTextSize (portnotv);
        LinearLayout portnoll = new LinearLayout (wairToNow);
        portnoll.setOrientation (LinearLayout.HORIZONTAL);
        portnoll.addView (btAdsbPortNo);
        portnoll.addView (portnotv);

        linearLayout = new LinearLayout (wairToNow);
        linearLayout.setOrientation (LinearLayout.VERTICAL);
        linearLayout.addView (btAdsbEnable);
        linearLayout.addView (portnoll);
        linearLayout.addView (btAdsbStatus);

        createLogElements (linearLayout);
        linearLayout.addView (netIntfsView);

        SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
        int portno = prefs.getInt (prefKey + "PortNo", DEFAULT_PORT);
        btAdsbPortNo.setText (Integer.toString (portno));

        btAdsbEnable.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                btAdsbEnabClicked ();
            }
        });

        adsbStatusChangedUI (DISCONNECTED, "");
    }

    /*************************************************************\
     *  GpsAdsbReceiver implementation                           *
     *  Used by SensorsView to enable the WiFi UDP data source.  *
    \*************************************************************/

    @Override  // GpsAdsbReceiver
    public String getPrefKey ()
    {
        return prefKey;
    }

    @Override  // GpsAdsbReceiver
    public String getDisplay ()
    {
        return btAdsbEnable.getText ().toString ();
    }

    @Override  // GpsAdsbReceiver
    public View displayOpened ()
    {
        netIntfsView.setText (NetworkInterfaces.get ());
        displayOpen = true;
        return linearLayout;
    }

    @Override  // GpsAdsbReceiver
    public void displayClosed ()
    {
        displayOpen = false;
    }

    @SuppressLint("SetTextI18n")
    @Override  // GpsAdsbReceiver
    public void startSensor ()
    {
        /*
         * Look for device selected by preferences.
         */
        SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
        int udpPort = prefs.getInt (prefKey + "PortNo", DEFAULT_PORT);
        getLogFromPreferences (prefs);

        /*
         * Set selection/checkboxes just as if user did it manually.
         */
        btAdsbPortNo.setText (Integer.toString (udpPort));
        btAdsbEnable.setChecked (true);
        getButton ().setRingColor (Color.GREEN);

        /*
         * Try to start listening on the UDP socket.
         */
        btAdsbLastStatus = DISCONNECTED;
        try {
            udpSocket = new DatagramSocket (udpPort);
        } catch (IOException ioe) {
            udpSocket = null;
            String msg = ioe.getMessage ();
            if (msg == null) msg = ioe.getClass ().getSimpleName ();
            adsbStatusChangedRT (CONFAILED, msg);
            btAdsbEnable.setChecked (false);
            getButton ().setRingColor (Color.TRANSPARENT);
            btAdsbPortNo.setEnabled (true);
            setLogChangeEnable (true);
            writePreferences ();
            return;
        }
        adsbStatusChangedRT (CONNECTED, "");

        /*
         * Disable changing port number while running.
         */
        btAdsbPortNo.setEnabled (false);
        setLogChangeEnable (false);

        /*
         * Fork a thread to open and read from the connection.
         */
        btShowErrorAlerts = true;
        adsbContext = new AdsbGpsRThread (receiverStream, wairToNow);
        adsbContext.setName (getPrefKey ());
        adsbContext.startRecording (createLogFile ());
        adsbContext.start ();
    }

    @Override  // GpsAdsbReceiver
    public void stopSensor ()
    {
        // don't show any error alerts during or after close
        btShowErrorAlerts = false;
        if (adsbContext != null) {
            adsbContext.close ();
            closeLogFile (adsbContext.rawLogStream);
            adsbContext = null;
        }
    }

    @Override  // GpsAdsbReceiver
    public void refreshStatus ()
    {
        adsbPacketReceivedUI.run ();
    }

    @Override  // GpsAdsbReceiver
    public boolean isSelected ()
    {
        return adsbContext != null;
    }

    /**
     * User just clicked GPS/ADS-B wifi UDP enable checkbox.
     * Turn wifi UDP GPS/ADS-B on and off.
     */
    private void btAdsbEnabClicked ()
    {
        if (btAdsbEnable.isChecked ()) {
            getButton ().setRingColor (Color.GREEN);

            /*
             * Get selected port number.
             */
            int portno;
            try {
                portno = Integer.parseInt (btAdsbPortNo.getText ().toString ());
                if ((portno < MIN_PORT) || (portno > MAX_PORT)) {
                    throw new NumberFormatException ("range " + MIN_PORT + " .. " + MAX_PORT);
                }
            } catch (NumberFormatException nfe) {
                errorMessage ("bad port number");
                btAdsbEnable.setChecked (false);
                getButton ().setRingColor (Color.TRANSPARENT);
                return;
            }

            /*
             * Write to preferences so we remember it.
             */
            writePreferences ();

            /*
             * Start this one going.
             */
            startSensor ();
        } else {
            getButton ().setRingColor (Color.TRANSPARENT);

            /*
             * Write to preferences so we remember it.
             */
            writePreferences ();

            /*
             * Shut down whichever GPS receiver we are using now.
             */
            stopSensor ();

            /*
             * Allow changing port number.
             */
            btAdsbPortNo.setEnabled (true);
            setLogChangeEnable (true);
        }

        /*
         * Update status display saying which sensors are enabled.
         */
        wairToNow.sensorsView.SetGPSLocation ();
    }

    /**
     * Write values in the selection boxes out to the preferences.
     */
    private void writePreferences ()
    {
        SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
        SharedPreferences.Editor editr = prefs.edit ();
        editr.putBoolean ("selectedGPSReceiverKey_" + prefKey, btAdsbEnable.isChecked ());
        int portno = Integer.parseInt (btAdsbPortNo.getText ().toString ());
        editr.putInt (prefKey + "PortNo", portno);
        putLogToPreferences (editr);
        editr.apply ();
    }

    /**
     * Update GPS/ADS-B status message.
     */
    private void adsbStatusChangedUI (int status, String message)
    {
        String stmsg;
        switch (status) {

            // attempting to connect to the device
            case CONNECTING: {
                stmsg = "Connecting";
                break;
            }

            // failed to connect to device, thread is terminating
            case CONFAILED: {
                if (btShowErrorAlerts) {
                    errorMessage ("Failed to connect: " + message);
                    btAdsbEnable.setChecked (false);
                    btAdsbEnabClicked ();
                }
                stmsg = "Connect failed: " + message;
                break;
            }

            // device is now successfully connected
            case CONNECTED: {
                stmsg = "Listening";
                break;
            }

            // thread has terminated unexpectedly, uncheck the checkbox
            case DISCONNECTED: {
                if (btShowErrorAlerts) {
                    errorMessage ("Disconnected from device");
                    btAdsbEnable.setChecked (false);
                    btAdsbEnabClicked ();
                }
                stmsg = "Disconnected";
                break;
            }

            // dunno what the status is
            default: {
                stmsg = "status " + status;
                if (message.length () > 0) stmsg += ": " + message;
                break;
            }
        }

        // save status and display string
        btAdsbLastStatus = status;
        btAdsbStatus.setText (stmsg);
    }

    /**
     * A packet was received from GPS/ADS-B receiver and this page is visible.
     * Update the on-screen text to show updated byte count.
     */
    private final Runnable adsbPacketReceivedUI = new Runnable () {
        @Override
        public void run ()
        {
            pktReceivedQueued = false;
            if (btAdsbLastStatus == CONNECTED) {
                String text = adsbContext.getConnectStatusText ("Listening");
                btAdsbStatus.setText (text);
            }
        }
    };

    /***************************************************\
     *  InputStream implementation                     *
     *  Reads NMEA/GDL-90 messages from the UDP port.  *
     *  Also updates on-screen status.                 *
     \**************************************************/

    private final InputStream receiverStream = new InputStream () {
        private boolean first = true;

        @Override  // InputStream
        public int read () throws IOException
        {
            int rc = read (onebyte, 0, 1);
            if (rc <= 0) return -1;
            return onebyte[0] & 0xFF;
        }

        @Override  // InputStream
        public int read (@NonNull byte[] buffer, int offset, int length) throws IOException
        {
            if (displayOpen && !pktReceivedQueued) {
                pktReceivedQueued = true;
                wairToNow.runOnUiThread (adsbPacketReceivedUI);
            }

            // read a packet from ADS-B receiver
            DatagramPacket pkt = new DatagramPacket (buffer, offset, length);
            udpSocket.receive (pkt);

            // if time being requested, tell receiver what we think the current time is
            // also send time in case we missed the request message
            int pktlen = pkt.getLength ();
            if ((pktlen > 9) && (buffer[offset] == 'W') && (buffer[offset+1] == 'T') &&
                    (buffer[offset+2] == 'N') && (buffer[offset+3] == '-') &&
                    (buffer[offset+4] == 'U') && (buffer[offset+5] == 'T') &&
                    (buffer[offset+6] == 'I') && (buffer[offset+7] == 'M') &&
                    (buffer[offset+8] == 'E') && (buffer[offset+9] == '?')) {
                first = true;
                buffer[offset] = 0;
                pktlen = 1;
            }
            if (first) {
                first = false;
                long now = System.currentTimeMillis ();
                String utimestr = "wtn-utime=" + (now / 1000) + "." + (now % 1000) + "000";
                byte[] utimebyt = utimestr.getBytes ();
                DatagramPacket utimepkt = new DatagramPacket (utimebyt, utimebyt.length);
                utimepkt.setSocketAddress (pkt.getSocketAddress ());
                udpSocket.send (utimepkt);
            }

            // tell caller how long the received packet is
            return pktlen;
        }

        /**
         * Close UDP socket and tell thread to terminate.
         */
        @Override  // InputStream
        public void close ()
        {
            try { udpSocket.close (); } catch (Exception e) { Lib.Ignored (); }
            udpSocket = null;
            adsbStatusChangedRT (DISCONNECTED, "");
        }
    };

    /**
     * Post GPS/ADS-B receiver connection status to UI thread.
     */
    private void adsbStatusChangedRT (final int status, final String message)
    {
        wairToNow.runOnUiThread (new Runnable () {
            @Override
            public void run ()
            {
                adsbStatusChangedUI (status, message);
            }
        });
    }
}
