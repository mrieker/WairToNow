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
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Locale;

/**
 * WiFi TCP GPS/ADS-B receiver.
 * When enabled, reads GPS/ADS-B info from TCP and pushes to UI.
 * Packets are assumed to be NMEA ('$'...<CR><LF>) or GDL-90 (0x7E...0x7E) format (can be a mix).
 */
public class WiFiTCPGpsAdsb extends GpsAdsbReceiver {
    public final static String TAG = "WairToNow";
    public final static int DEFAULT_PORT = 1269;
    public final static int MIN_PORT = 1024;
    public final static int MAX_PORT = 65535;

    private boolean displayOpen;
    private boolean pktReceivedQueued;
    private boolean stopping;
    private CheckBox btAdsbEnable;
    private EditText btAdsbPortNo;
    private final HashSet<ReceiveStream> receiveStreams = new HashSet<> ();
    private LinearLayout linearLayout;
    private ListenThread listenThread;
    private ServerSocket serverSocket;
    private String prefKey;
    private TextView btAdsbStatus;
    private TextView netIntfsView;

    @SuppressLint("SetTextI18n")
    public WiFiTCPGpsAdsb (SensorsView sv, int n)
    {
        wairToNow = sv.wairToNow;

        prefKey = "tcp" + n;

        btAdsbEnable = new CheckBox (wairToNow);
        btAdsbPortNo = new EditText (wairToNow);
        btAdsbStatus = new TextView (wairToNow);
        netIntfsView = new TextView (wairToNow);

        btAdsbEnable.setText ("WiFi TCP GPS/ADS-B #" + n);

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

        btAdsbStatus.setText ("Disconnected");
    }

    /*************************************************************\
     *  GpsAdsbReceiver implementation                           *
     *  Used by SensorsView to enable the WiFi TCP data source.  *
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
        int tcpPort = prefs.getInt (prefKey + "PortNo", DEFAULT_PORT);
        getLogFromPreferences (prefs);

        /*
         * Set selection/checkboxes just as if user did it manually.
         */
        btAdsbPortNo.setText (Integer.toString (tcpPort));
        btAdsbEnable.setChecked (true);
        getButton ().setRingColor (Color.GREEN);

        /*
         * Try to start listening on the TCP socket.
         */
        try {
            serverSocket = new ServerSocket (tcpPort);
        } catch (IOException ioe) {
            serverSocket = null;
            String msg = ioe.getMessage ();
            if (msg == null) msg = ioe.getClass ().getSimpleName ();
            errorMessage ("error listening on " + tcpPort + ": " + msg);
            btAdsbEnable.setChecked (false);
            getButton ().setRingColor (Color.TRANSPARENT);
            btAdsbPortNo.setEnabled (true);
            setLogChangeEnable (true);
            writePreferences ();
            return;
        }
        Log.d (TAG, "listening on tcp port " + tcpPort);

        /*
         * Disable changing port number while running.
         */
        btAdsbPortNo.setEnabled (false);
        setLogChangeEnable (false);

        /*
         * Start the listening thread going.
         */
        stopping = false;
        listenThread = new ListenThread ();
        listenThread.start ();

        btAdsbStatus.setText ("Listening");
    }

    @SuppressLint("SetTextI18n")
    @Override  // GpsAdsbReceiver
    public void stopSensor ()
    {
        // tell listening and receiving threads that we are stopping
        stopping = true;

        // this should break listening thread out of the accept() call if it is stuck there
        try { serverSocket.close (); } catch (Exception e) { Lib.Ignored (); }
        // wait for thread to exit
        try { listenThread.join (); } catch (Exception e) { Lib.Ignored (); }

        // we are no longer listening for more inbound connections
        serverSocket = null;
        listenThread = null;

        // abort all existing connections
        synchronized (receiveStreams) {
            // this should break all the receiving threads out of their read() call
            for (ReceiveStream rs : receiveStreams) {
                try { rs.wrapped.close (); } catch (Exception e) { Lib.Ignored (); }
            }
            // wait for all those threads to exit
            while (!receiveStreams.isEmpty ()) {
                try { receiveStreams.wait (); } catch (Exception e) { Lib.Ignored (); }
            }
        }

        // update status display
        btAdsbStatus.setText ("Disconnected");
    }

    @Override  // GpsAdsbReceiver
    public void refreshStatus ()
    { }

    @Override  // GpsAdsbReceiver
    public boolean isSelected ()
    {
        return serverSocket != null;
    }

    /**
     * User just clicked GPS/ADS-B wifi TCP enable checkbox.
     * Turn wifi TCP GPS/ADS-B on and off.
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
     * Thread that listens for inbound connections and spawns a receiver thread for each one.
     */
    private class ListenThread extends Thread {
        @Override  // Thread
        public void run ()
        {
            while (!stopping) {
                try {
                    Socket connectionSocket = serverSocket.accept ();
                    Log.d (TAG, "accepted inbound connection");
                    ReceiveStream rs = new ReceiveStream ();
                    rs.wrapped = connectionSocket.getInputStream ();
                    rs.rthread = new AdsbGpsRThread (rs, wairToNow);
                    rs.rthread.setName (getPrefKey ());
                    rs.rthread.startRecording (createLogFile ());
                    synchronized (receiveStreams) {
                        receiveStreams.add (rs);
                        triggerStatUpdate ();
                    }
                    rs.rthread.start ();
                } catch (Exception e) {
                    if (!stopping) {
                        Log.w (TAG, "error accepting connection", e);
                    }
                }
            }
        }
    }

    /**
     * Wrapper around inbound receiver streams to update stats.
     */
    private class ReceiveStream extends InputStream {
        public AdsbGpsRThread rthread;
        public InputStream wrapped;

        @Override  // InputStream
        public int read () throws IOException
        {
            return wrapped.read ();
        }

        @Override  // InputStream
        public int read (@NonNull byte[] buffer, int offset, int length) throws IOException
        {
            synchronized (receiveStreams) {
                triggerStatUpdate ();
            }
            return wrapped.read (buffer, offset, length);
        }

        @Override  // InputStream
        public void close ()
        {
            try { wrapped.close (); } catch (Exception e) { Lib.Ignored (); }
            synchronized (receiveStreams) {
                receiveStreams.remove (this);
                closeLogFile (rthread.rawLogStream);
                triggerStatUpdate ();
                receiveStreams.notifyAll ();
            }
        }
    }

    /**
     * Trigger a status update if not already triggered.
     * ** Caller must have receiveStreams locked. **
     */
    private void triggerStatUpdate ()
    {
        if (displayOpen && !pktReceivedQueued && !stopping) {
            pktReceivedQueued = true;
            wairToNow.runOnUiThread (adsbPacketReceivedUI);
        }
    }

    /**
     * Update the status box.
     */
    private final Runnable adsbPacketReceivedUI = new Runnable () {
        @Override
        public void run ()
        {
            String text;
            synchronized (receiveStreams) {
                pktReceivedQueued = false;
                int numcons = receiveStreams.size ();
                switch (numcons) {
                    case 0: {
                        text = "Listening";
                        break;
                    }
                    case 1: {
                        text = receiveStreams.iterator ().next ().rthread.getConnectStatusText ("Connected");
                        break;
                    }
                    default: {
                        long total = 0;
                        for (ReceiveStream rs : receiveStreams) {
                            total += rs.rthread.lengthReceived;
                        }
                        text = String.format (Locale.US, "Connections: %d; Bytes: %,d", numcons, total);
                        break;
                    }
                }
            }
            btAdsbStatus.setText (text);
        }
    };
}
