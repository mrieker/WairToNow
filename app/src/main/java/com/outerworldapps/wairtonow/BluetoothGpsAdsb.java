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
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/**
 * Bluetooth GPS/ADS-B receiver.
 * When enabled, reads GPS/ADS-B info from bluetooth and pushes to UI.
 */
public class BluetoothGpsAdsb extends GpsAdsbReceiver {
    public final static String TAG = "WairToNow";

    private final static int DISCONNECTED = 0;
    private final static int CONNECTING   = 1;
    private final static int CONNECTED    = 2;
    private final static int CONFAILED    = 3;
    private final static int READFAILED   = 4;

    private final static String SPPUUID = "00001101-0000-1000-8000-00805f9b34fb";

    private AdsbGpsRThread adsbContext;
    private BluetoothDevice btDevice;
    private BluetoothDevice[] btDeviceArray;
    private boolean btShowErrorAlerts;
    private volatile boolean closing;
    private volatile boolean pktReceivedQueued;
    private boolean displayOpen;
    private byte[] onebyte = new byte[1];
    private CheckBox btAdsbEnable;
    private CheckBox btAdsbSecure;
    private CheckBox btSendInit;
    private InputStream btIStream;
    private LinearLayout linearLayout;
    private OutputStream btOStream;
    private ParcelUuid[] btUUIDArray;
    private SendInitThread sendInitThread;
    private String prefKey;
    private TextArraySpinner btAdsbDevice;
    private TextArraySpinner btAdsbUUID;
    private TextView btAdsbStatus;
    private UUID btUUID;

    @SuppressLint("SetTextI18n")
    public BluetoothGpsAdsb (SensorsView sv, int n)
    {
        wairToNow = sv.wairToNow;

        prefKey = "blue" + n;

        btAdsbEnable = new CheckBox (wairToNow);
        btAdsbDevice = new TextArraySpinner (wairToNow);
        btAdsbUUID   = new TextArraySpinner (wairToNow);
        btAdsbSecure = new CheckBox (wairToNow);
        btSendInit   = new CheckBox (wairToNow);
        btAdsbStatus = new TextView (wairToNow);

        btAdsbEnable.setText ("Bluetooth GPS/ADS-B #" + n);
        btAdsbSecure.setText ("Secure");
        btSendInit.setText ("Send GDL-90 Inits");

        wairToNow.SetTextSize (btAdsbEnable);
        wairToNow.SetTextSize (btAdsbDevice);
        wairToNow.SetTextSize (btAdsbUUID);
        wairToNow.SetTextSize (btAdsbSecure);
        wairToNow.SetTextSize (btSendInit);
        wairToNow.SetTextSize (btAdsbStatus);

        linearLayout = new LinearLayout (wairToNow);
        linearLayout.setOrientation (LinearLayout.VERTICAL);
        linearLayout.addView (btAdsbEnable);
        linearLayout.addView (btAdsbDevice);
        linearLayout.addView (btAdsbUUID);
        linearLayout.addView (btAdsbSecure);
        linearLayout.addView (btSendInit);
        linearLayout.addView (btAdsbStatus);

        createLogElements (linearLayout);

        btAdsbSetup ();

        adsbStatusChangedUI (DISCONNECTED, "");
    }

    /***************************************************************\
     *  GpsAdsbReceiver implementation                             *
     *  Used by SensorsView to select this GPS/ADS-B data source.  *
    \***************************************************************/

    @Override  // GpsAdsbReceiver
    public String getPrefKey ()
    {
        return prefKey;
    }

    @Override
    public String getDisplay ()
    {
        return btAdsbEnable.getText ().toString ();
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
        /*
         * Look for device selected by preferences.
         */
        SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
        String devident = prefs.getString (prefKey + "ReceiverDev", "");
        boolean secure  = prefs.getBoolean ("bluetoothRcvrSecure_" + devident, false);
        boolean sendini = prefs.getBoolean ("bluetoothSendGdlIni_" + devident, false);

        for (int tries = 0; tries < 2; tries ++) {
            for (int i = btDeviceArray.length; -- i >= 0;) {
                BluetoothDevice dev = btDeviceArray[i];
                if (btIdentString (dev).equals (devident)) {

                    /*
                     * Set device selection box just as if user did it manually.
                     */
                    btAdsbDevice.setIndex (i);

                    /*
                     * Look for UUID selected by preferences.
                     */
                    String[] uuids = populateBtUUIDArray (dev);
                    String devuuid = prefs.getString  ("bluetoothRcvrUUID_"   + devident, SPPUUID);
                    for (int j = uuids.length; -- j >= 0;) {
                        if (uuids[j].equals (devuuid)) {

                            /*
                             * Set remainder of selection/checkboxes just as if user did it manually.
                             */
                            btAdsbUUID.setIndex (j);
                            btAdsbSecure.setChecked (secure);
                            btSendInit.setChecked (sendini);
                            btAdsbEnable.setChecked (true);
                            getButton ().setRingColor (Color.GREEN);
                            getLogFromPreferences (prefs);

                            /*
                             * Disable changing device name and secure mode while running.
                             */
                            btAdsbDevice.setEnabled (false);
                            btAdsbUUID.setEnabled (false);
                            btAdsbSecure.setEnabled (false);
                            btSendInit.setEnabled (false);
                            setLogChangeEnable (false);

                            /*
                             * Fork a thread to open and read from the connection.
                            */
                            btShowErrorAlerts = true;
                            btDevice    = dev;
                            btUUID      = UUID.fromString (devuuid);
                            closing     = false;
                            btIStream   = null;
                            btOStream   = null;
                            adsbContext = new AdsbGpsRThread (receiverStream, wairToNow);
                            adsbContext.setName (getPrefKey ());
                            adsbContext.startRecording (createLogFile ());
                            adsbContext.start ();
                            return;
                        }
                    }
                }
            }

            /*
             * Didn't find device, rebuild array and try again.
             */
            if (tries > 0) break;
            populateBtDeviceArray ();
        }

        errorMessage ("Cannot find device " + devident);
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
     * Set up spinner that selects which bluetooth device and UUID they want.
     */
    private void btAdsbSetup ()
    {
        btDeviceArray = new BluetoothDevice[0];
        btAdsbDevice.setTitle ("Select Bluetooth GPS/ADS-B device");
        btAdsbDevice.setLabels (new String[0], "Cancel", "(select)", "Refresh");
        btAdsbDevice.setIndex (TextArraySpinner.NOTHING);

        btAdsbDevice.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                btAdsbDeviceClicked ();
            }
        });

        btAdsbDevice.setOnItemSelectedListener (new TextArraySpinner.OnItemSelectedListener () {
            @Override
            public boolean onItemSelected (View view, int i) {
                BluetoothDevice dev = btDeviceArray[i];
                String devident = btIdentString (dev);

                // set secure and send-gdl-90 checkboxes from preferences for this device
                SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
                boolean secure = prefs.getBoolean ("bluetoothRcvrSecure_" + devident, false);
                boolean sendini = prefs.getBoolean ("bluetoothSendGdlIni_" + devident, false);
                btAdsbSecure.setChecked (secure);
                btSendInit.setChecked (sendini);

                // select UUID from preferences if so defined
                // if not, select the serial port UUID if the device supports it
                btAdsbUUID.setIndex (TextArraySpinner.NOTHING);
                final String uuid = prefs.getString ("bluetoothRcvrUUID_" + devident, SPPUUID);
                String[] uuids = populateBtUUIDArray (dev);
                for (i = 0; i < uuids.length; i ++) {
                    if (uuids[i].equals (uuid)) {
                        btAdsbUUID.setIndex (i);
                        break;
                    }
                }

                // set new index and change legend on button
                return true;
            }

            @Override
            public boolean onPositiveClicked (View view) {
                btAdsbDeviceRefresh ();
                return false;  // leave button alone; btAdsbDeviceRefresh() handled any changes
            }

            @Override
            public boolean onNegativeClicked (View view) {
                return false;  // cancel, don't change the index or the button
            }
        });

        btUUIDArray = new ParcelUuid[0];
        btAdsbUUID.setTitle ("Select Bluetooth UUID to connect to");
        btAdsbUUID.setLabels (new String[0], "Cancel", "(select)", "Refresh");
        btAdsbUUID.setIndex (TextArraySpinner.NOTHING);

        btAdsbUUID.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                btAdsbUUIDClicked ();
            }
        });

        btAdsbUUID.setOnItemSelectedListener (new TextArraySpinner.OnItemSelectedListener () {
            @Override
            public boolean onItemSelected (View view, int i) {
                return true;  // set new index and change legend on button
            }

            @Override
            public boolean onPositiveClicked (View view) {
                btAdsbUUIDRefresh ();
                return false;  // leave button alone; btAdsbUUIDRefresh() handled any changes
            }

            @Override
            public boolean onNegativeClicked (View view) {
                return false;  // cancel, don't change the index or the button
            }
        });

        btAdsbEnable.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                btAdsbEnabClicked ();
            }
        });
    }

    /**
     * Clicked on the GPS/ADS-B bluetooth device selection button.
     * If the device list is not populated, try to populate it.
     */
    private void btAdsbDeviceClicked ()
    {
        if (btDeviceArray.length == 0) {
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run () {
                    // clear existing dialog from screen
                    btAdsbDevice.setEnabled (false);
                    btAdsbDevice.setEnabled (true);
                    // populate the list and re-open dialog
                    btAdsbDeviceRefresh ();
                }
            });
        }
    }

    /**
     * Clicked on GPS/ADS-B bluetooth device refresh button.
     * Rebuild list of available bluetooth devices they can pick from.
     */
    private void btAdsbDeviceRefresh ()
    {
        // set spinner to indicate nothing selected
        // cuz we are about to rebuild the selection list
        btAdsbDevice.setIndex (TextArraySpinner.NOTHING);

        // scan for devices and get their names also write to button tops
        String[] names = populateBtDeviceArray ();
        if (names == null) return;

        // re-open the spinner so they can pick a device
        btAdsbDevice.onClick (null);
    }

    /**
     * Scan for all bluetooth devices.
     * Set up internal array of bluetooth devices.
     * @return list of names in an array (or null if error scanning)
     */
    private String[] populateBtDeviceArray ()
    {
        // get list of bluetooth devices that are currently paired
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter ();
        if (btAdapter == null) {
            errorMessage ("Bluetooth not supported on this device");
            return null;
        }
        if (!btAdapter.isEnabled ()) {
            errorMessage ("Bluetooth not enabled");
            return null;
        }
        Set<BluetoothDevice> devs = btAdapter.getBondedDevices ();
        if (devs == null) {
            errorMessage ("Unable to get list of paired devices");
            return null;
        }
        int ndevs = devs.size ();
        if (ndevs <= 0) {
            errorMessage ("No paired devices found");
            return null;
        }

        // set that list up as selections on the spinner button
        btDeviceArray = new BluetoothDevice[ndevs];
        String[] names = new String[ndevs];
        int i = 0;
        for (BluetoothDevice dev : devs) {
            btDeviceArray[i] = dev;
            names[i] = btIdentString (dev);
            i ++;
        }

        // write the names to the buttons
        btAdsbDevice.setLabels (names, "Cancel", "(select)", "Refresh");
        btAdsbDevice.setIndex (TextArraySpinner.NOTHING);

        return names;
    }

    /**
     * Get user-friendly name string for device that is also unique.
     */
    private static String btIdentString (BluetoothDevice dev)
    {
        return dev.getName () + " " + dev.getAddress ();
    }

    /**
     * Clicked on the GPS/ADS-B bluetooth UUID selection button.
     * If the UUID list is not populated, try to populate it.
     */
    private void btAdsbUUIDClicked ()
    {
        if (btUUIDArray.length == 0) {
            wairToNow.runOnUiThread (new Runnable () {
                @Override
                public void run ()
                {
                    // clear existing dialog from screen
                    btAdsbUUID.setEnabled (false);
                    btAdsbUUID.setEnabled (true);
                    // populate the list and re-open dialog
                    btAdsbUUIDRefresh ();
                }
            });
        }
    }

    /**
     * Clicked on GPS/ADS-B bluetooth UUID refresh button.
     * Query device then rebuild list of available bluetooth UUIDs they can pick from.
     */
    private void btAdsbUUIDRefresh ()
    {
        // set spinner to indicate nothing selected
        // cuz we are about to rebuild the selection list
        btAdsbUUID.setIndex (TextArraySpinner.NOTHING);

        // see which device they have selected
        int devindex = btAdsbDevice.getIndex ();
        if (devindex < 0) {
            errorMessage ("no device selected");
        } else {

            // query that device for UUIDs
            final BluetoothDevice btdev = btDeviceArray[devindex];
            new RefreshBtUUIDArray (btdev) {
                @Override
                public void finished () {
                    // write UUID list to button tops
                    populateBtUUIDArray (btdev);

                    // re-open the spinner so they can pick an UUID
                    btAdsbUUID.onClick (null);
                }
            };
        }
    }

    /**
     * Scan device for all UUIDs it advertises.
     * Android will remember the list, returnable by BluetoothDevice.getUuids().
     */
    private abstract class RefreshBtUUIDArray extends BroadcastReceiver implements Runnable {
        private AlertDialog pleaseWait;
        private boolean sdpComplete;
        private Class<? extends BluetoothDevice> btDevClass;
        private String btdevname;

        public abstract void finished ();

        public RefreshBtUUIDArray (BluetoothDevice btdev)
        {
            /*
             * Update local cache of UUIDs supported by the device.
             * It sometimes get stuck with out-of-date information.
             */
            btdevname = btIdentString (btdev);
            btDevClass = btdev.getClass ();
            try {
                Method fuws = btDevClass.getMethod ("fetchUuidsWithSdp");
                wairToNow.registerReceiver (this, new IntentFilter ("android.bluetooth.device.action.UUID"));

                boolean rc = (Boolean) fuws.invoke (btdev);
                Log.d (TAG, "BluetoothGpsAdsb: fetchUuidsWithSdp " + btdevname + ": " + rc);
                if (rc) {
                    AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                    adb.setTitle ("Scanning " + btdevname + " for UUIDs");
                    adb.setMessage ("...please wait");
                    adb.setCancelable (false);
                    pleaseWait = adb.show ();

                    // normally takes 2-5 sec so give it 20 sec
                    WairToNow.wtnHandler.runDelayed (20000, this);
                    return;
                }
            } catch (Exception e) {
                Log.w (TAG, "error calling fetchUuidsWithSdp() for " + btdevname, e);
            }
            continuing ();
        }

        /**
         * Receiver that listens for completion of fetchUuidsWithSdp() call,
         * indicating that we now have an updated list of UUIDs supported
         * by the device.
         *
         * Apparently requires android.permission.BLUETOOTH_ADMIN,
         * even though fetchUuidsWithSdp() returns true without it.
         */
        @Override  // BroadcastReceiver
        public void onReceive (Context context, Intent intent)
        {
            String action = intent.getAction ();
            Log.d (TAG, "BluetoothGpsAdsb: received action " + action);
            if (action.equals ("android.bluetooth.device.action.UUID") && !sdpComplete) {
                BluetoothDevice bd = intent.getParcelableExtra (BluetoothDevice.EXTRA_DEVICE);
                String bdid = btIdentString (bd);
                Log.d (TAG, "BluetoothGpsAdsb: device ident " + bdid);
                if (bdid.equals (btdevname)) {
                    continuing ();
                }
            }
        }

        // waited too long for list of UUIDs supported by device
        @Override  // Runnable
        public void run () {
            if (!sdpComplete) {
                Log.d (TAG, "BluetoothGpsAdsb: fetchUuidsWithSdp " + btdevname + " timed out");
                continuing ();
            }
        }

        // received new list of UUIDs or not...
        private void continuing ()
        {
            wairToNow.unregisterReceiver (this);
            if (pleaseWait != null) {
                pleaseWait.dismiss ();
                pleaseWait = null;
            }
            Log.d (TAG, "BluetoothGpsAdsb: continuing...");

            sdpComplete = true;
            finished ();
        }
    }

    /**
     * Set up array of UUIDs that the device supports.
     */
    private String[] populateBtUUIDArray (BluetoothDevice btdev)
    {
        /*
         * Get cached list of UUIDs known for the device.
         * This works if the device is currently paired,
         * it does not matter if it is in radio range or not.
         */
        String btdevname = btIdentString (btdev);
        Class<? extends BluetoothDevice> btDevClass = btdev.getClass ();
        btUUIDArray = null;
        try {
            Method gum = btDevClass.getMethod ("getUuids");
            btUUIDArray = (ParcelUuid[]) gum.invoke (btdev);
        } catch (Exception e) {
            Log.w (TAG, "exception calling getUuids() for " + btdevname, e);
        }

        /*
         * If no cache, use the default SPPUUID.
         */
        if ((btUUIDArray == null) || (btUUIDArray.length == 0)) {
            Log.w (TAG, "bt device " + btdevname + " has no uuids");
            btUUIDArray = new ParcelUuid[] { ParcelUuid.fromString (SPPUUID) };
        }

        /*
         * Set that list up as selections on the spinner button.
         * Mark the default SPPUUID with brackets.
         */
        int nuuids = btUUIDArray.length;
        String[] uuids = new String[nuuids];
        String[] uuidsWithDef = new String[nuuids];
        int i = 0;
        int defi = TextArraySpinner.NOTHING;
        for (ParcelUuid uuid : btUUIDArray) {
            String uuidstr = uuid.toString ();
            uuids[i] = uuidstr;
            if (uuidstr.equals (SPPUUID)) {
                uuidstr = "<" + SPPUUID + ">";
                defi = i;
            }
            uuidsWithDef[i] = uuidstr;
            i ++;
        }

        /*
         * Write the uuids to the buttons including <> around default.
         */
        btAdsbUUID.setTitle ("Select UUID for " + btdevname);
        btAdsbUUID.setLabels (uuidsWithDef, "Cancel", "(select)", "Refresh");
        btAdsbUUID.setIndex (defi);

        /*
         * Return strings without <> around default.
         */
        return uuids;
    }

    /**
     * User just clicked GPS/ADS-B bluetooth enable checkbox.
     * Turn bluetooth GPS/ADS-B on and off.
     */
    private void btAdsbEnabClicked ()
    {
        if (btAdsbEnable.isChecked ()) {
            getButton ().setRingColor (Color.GREEN);

            /*
             * Get selected bluetooth device and UUID.
             */
            int devindex = btAdsbDevice.getIndex ();
            if (devindex < 0) {
                btAdsbEnable.setChecked (false);
                getButton ().setRingColor (Color.TRANSPARENT);
                errorMessage ("No device selected");
            } else {
                int uuidindex = btAdsbUUID.getIndex ();
                if (uuidindex < 0) {
                    btAdsbEnable.setChecked (false);
                    getButton ().setRingColor (Color.TRANSPARENT);
                    errorMessage ("No UUID selected");
                } else {

                    /*
                     * Block changing device, UUID, send-gdl90-inits and secure mode.
                     */
                    btAdsbDevice.setEnabled (false);
                    btAdsbUUID.setEnabled (false);
                    btAdsbSecure.setEnabled (false);
                    btSendInit.setEnabled (false);

                    /*
                     * Write to preferences so we remember it.
                     */
                    BluetoothDevice dev = btDeviceArray[devindex];
                    SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
                    SharedPreferences.Editor editr = prefs.edit ();
                    editr.putBoolean ("selectedGPSReceiverKey_" + prefKey, true);
                    String devident = btIdentString (dev);
                    editr.putString  (prefKey + "ReceiverDev", devident);
                    editr.putString  ("bluetoothRcvrUUID_"   + devident, btUUIDArray[uuidindex].toString ());
                    editr.putBoolean ("bluetoothRcvrSecure_" + devident, btAdsbSecure.isChecked ());
                    editr.putBoolean ("bluetoothSendGdlIni_" + devident, btSendInit.isChecked ());
                    putLogToPreferences (editr);
                    editr.commit ();

                    /*
                     * Start this one going.
                     */
                    startSensor ();
                }
            }
        } else {
            getButton ().setRingColor (Color.TRANSPARENT);

            /*
             * Write to preferences so we remember it.
             */
            SharedPreferences prefs = wairToNow.getPreferences (Context.MODE_PRIVATE);
            SharedPreferences.Editor editr = prefs.edit ();
            editr.putBoolean ("selectedGPSReceiverKey_" + prefKey, false);
            editr.commit ();

            /*
             * Shut down whichever GPS receiver we are using now.
             */
            stopSensor ();

            /*
             * Allow changing device and secure mode.
             */
            btAdsbDevice.setEnabled (true);
            btAdsbUUID.setEnabled (true);
            btAdsbSecure.setEnabled (true);
            btSendInit.setEnabled (true);
            setLogChangeEnable (true);
        }

        /*
         * Update status display saying which sensors are enabled.
         */
        wairToNow.sensorsView.SetGPSLocation ();
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
                stmsg = "Connected";
                break;
            }

            // read from device failed, it will re-attempt connection
            case READFAILED: {
                stmsg = "Read failed: " + message;
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

        // display string
        btAdsbStatus.setText (stmsg);
    }

    /**
     * A packet was received from GPS/ADS-B receiver and this page is visible.
     * Update the on-screen text to show updated byte count.
     */
    private final Runnable adsbPacketReceivedUI = new Runnable () {
        @SuppressLint("SetTextI18n")
        @Override
        public void run ()
        {
            pktReceivedQueued = false;
            if (adsbContext != null) {
                String text = adsbContext.getConnectStatusText ("Connected");
                btAdsbStatus.setText (text);
            } else {
                btAdsbStatus.setText ("Shutdown");
            }
        }
    };

    /**********************************************************************************\
     *  InputStream implementation                                                    *
     *  Reads from the bluetooth device, reconnecting whenever the connection drops.  *
     *  Also updates on-screen status.                                                *
    \**********************************************************************************/

    private final InputStream receiverStream = new InputStream () {

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
            while (true) {
                if (btIStream == null) {
                    connect ();
                }
                try {
                    if (displayOpen && !pktReceivedQueued) {
                        pktReceivedQueued = true;
                        wairToNow.runOnUiThread (adsbPacketReceivedUI);
                    }
                    int rc = btIStream.read (buffer, offset, length);
                    if (closing) return 0;
                    return rc;
                } catch (IOException ioe) {
                    if (closing) return 0;
                    String msg = ioe.getMessage ();
                    if (msg == null) msg = ioe.getClass ().getSimpleName ();
                    adsbStatusChangedRT (READFAILED, msg);
                    disconnect ();
                    try { Thread.sleep (1000); } catch (InterruptedException ie) { Lib.Ignored (); }
                }
            }
        }

        /**
         * Close bluetooth connection.
         * Note that this will abort any read() in progress.
         */
        @Override  // InputStream
        public void close ()
        {
            closing = true;
            disconnect ();
            adsbStatusChangedRT (DISCONNECTED, "");
        }
    };

    /**
     * Connect to bluetooth device's serial port.
     */
    private void connect () throws IOException
    {
        adsbStatusChangedRT (CONNECTING, "");

        String btDevName = btIdentString (btDevice);
        Log.i (TAG, "using UUID " + btUUID.toString () + " for " + btDevName);

        try {

            /*
             * Make sure discovery is not in progress or the connect will fail.
             * Requires android.permission.BLUETOOTH_ADMIN.
             */
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter ();
            if (!btAdapter.isEnabled ()) {
                throw new IOException ("bluetooth not enabled");
            }
            if (btAdapter.isDiscovering () && !btAdapter.cancelDiscovery ()) {
                Log.w (TAG, "bluetooth cancel discovery failed");
            }

            /*
             * Get socket, secure or insecure according to checkbox.
             */
            BluetoothSocket socket;
            if (btAdsbSecure.isChecked ()) {
                socket = btDevice.createRfcommSocketToServiceRecord (btUUID);
            } else {
                Class<? extends BluetoothDevice> btDevClass = btDevice.getClass ();
                try {
                    Method cirstcr = btDevClass.getMethod (
                            "createInsecureRfcommSocketToServiceRecord",
                            btUUID.getClass ());
                    socket = (BluetoothSocket) cirstcr.invoke (btDevice, btUUID);
                } catch (NoSuchMethodException nsme) {
                    throw new IOException ("insecure mode not available - nsme");
                } catch (IllegalAccessException iae) {
                    throw new IOException ("insecure mode not available - iae");
                } catch (InvocationTargetException ite) {
                    throw new IOException ("insecure mode not available - ite");
                }
            }

            /*
             * Connect and get input stream.  This will block until it connects.
             */
            socket.connect ();

            /*
             * Open the streams and maybe start sending init messages.
             */
            btIStream = socket.getInputStream ();
            btOStream = socket.getOutputStream ();
            if (btSendInit.isChecked ()) {
                sendInitThread = new SendInitThread ();
                sendInitThread.start ();
            }
        } catch (IOException ioe) {
            disconnect ();
            String msg = ioe.getMessage ();
            if (msg == null) msg = ioe.getClass ().getSimpleName ();
            adsbStatusChangedRT (CONFAILED, msg);
            throw ioe;
        }

        /*
         * All connected.
         */
        adsbStatusChangedRT (CONNECTED, "");
    }

    /**
     * Drop any bluetooth connection we may have.
     */
    private void disconnect ()
    {
        try {
            sendInitThread.die = true;
            sendInitThread.interrupt ();
            sendInitThread.join ();
        } catch (Exception e) {
            Lib.Ignored ();
        }
        try { btIStream.close (); } catch (Exception e) { Lib.Ignored (); }
        try { btOStream.close (); } catch (Exception e) { Lib.Ignored (); }
        btIStream = null;
        btOStream = null;
        sendInitThread = null;
    }

    /**
     * Thread that sends a GDL-90 init message once a second.
     * It runs from when the connection is successfully opened until it is closed.
     */
    private class SendInitThread extends Thread {
        public boolean die;

        @Override
        public void run ()
        {
            byte[] initbuf = new byte[] { 0x7E, 0x02, 0x01, 0x00, 0, 0, 0, 0, 0 };
            int crc = com.apps4av.avarehelper.gdl90.Crc.calcCrc (initbuf, 1, 3);
            int initlen = putByte (initbuf, 4, (byte) crc);
            initlen = putByte (initbuf, initlen, (byte) (crc >> 8));
            initbuf[initlen++] = 0x7E;

            try {
                while (!die) {
                    long nowms = System.currentTimeMillis ();
                    Thread.sleep (1000 - nowms % 1000);
                    btOStream.write (initbuf, 0, initlen);
                }
            } catch (Exception e) {
                if (!die) Log.e (TAG, "sendInitThread exception", e);
            }
        }

        private int putByte (byte[] initbuf, int initlen, byte value)
        {
            if ((value == 0x7E) || (value == 0x7D)) {
                initbuf[initlen++] = 0x7D;
                value ^= 0x20;
            }
            initbuf[initlen++] = value;
            return initlen;
        }
    }

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
