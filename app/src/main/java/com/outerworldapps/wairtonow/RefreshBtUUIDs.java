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
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Scan device for all UUIDs it advertises.
 * Android will remember the list, returnable by BluetoothDevice.getUuids().
 *
 *  new RefreshBtUUIDs (ctx, btdev) {
 *      public void finished ()
 *      {
 *          all done...
 *      }
 *  };
 */
public abstract class RefreshBtUUIDs extends BroadcastReceiver implements Runnable {
    private final static String TAG = "RefreshBtUUIDArray";

    private AlertDialog pleaseWait;
    private boolean sdpComplete;
    private Context ctx;
    private String btdevname;

    protected abstract void finished ();

    public RefreshBtUUIDs (Context ctx, BluetoothDevice btdev)
    {
        this.ctx = ctx;

        /*
         * Update local cache of UUIDs supported by the device.
         * It sometimes get stuck with out-of-date information.
         */
        btdevname = btIdentString (btdev);
        Class<? extends BluetoothDevice> btDevClass = btdev.getClass ();
        try {
            Method fuws = btDevClass.getMethod ("fetchUuidsWithSdp");
            ctx.registerReceiver (this, new IntentFilter ("android.bluetooth.device.action.UUID"));

            boolean rc = (Boolean) fuws.invoke (btdev);
            Log.d (TAG, "BluetoothGpsAdsb: fetchUuidsWithSdp " + btdevname + ": " + rc);
            if (rc) {
                AlertDialog.Builder adb = new AlertDialog.Builder (ctx);
                adb.setTitle ("Scanning " + btdevname + " for UUIDs");
                adb.setMessage ("...please wait");
                adb.setCancelable (false);
                pleaseWait = adb.show ();

                // normally takes 2-5 seconds
                // call run() after 20 seconds
                Looper looper = Looper.myLooper ();
                Handler handler = new Handler (looper);
                handler.postDelayed (this, 20000);
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
        if ("android.bluetooth.device.action.UUID".equals (action) && !sdpComplete) {
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
        ctx.unregisterReceiver (this);
        Lib.dismiss (pleaseWait);
        pleaseWait = null;

        Log.d (TAG, "BluetoothGpsAdsb: continuing...");

        sdpComplete = true;
        finished ();
    }

    private static String btIdentString (BluetoothDevice btdev)
    {
        return btdev.getName () + " " + btdev.getAddress ();
    }
}
