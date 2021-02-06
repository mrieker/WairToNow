//    Copyright (C) 2021, Mike Rieker, Beverly, MA USA
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
import android.content.ContentValues;
import android.content.DialogInterface;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Try to register username so this user can make contributions.
 */
public abstract class RegisterContributionUser extends Thread {
    public String username;
    public String password;
    public String emailaddr;

    private WairToNow wairToNow;

    public abstract void reprompt ();
    public abstract void success ();

    public RegisterContributionUser (WairToNow wtn)
    {
        wairToNow = wtn;
    }

    @Override
    public void run ()
    {
        setName ("RegisterContributionUser");

        int unlen = username.length ();
        if ((unlen < 3) || (unlen > 16)) {
            username = null;
        } else {
            for (int i = 0; i < unlen; i ++) {
                char c = username.charAt (i);
                if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z') && (c < '0' || c > '9') && (c != '_') && (c != '.')) {
                    username = null;
                    break;
                }
            }
        }
        if (username == null) {
            alertMessage ("username 3-16 characters, A-Z, a-z, 0-9, underscore, period");
            return;
        }

        SQLiteDBs sqldb = SQLiteDBs.create ("manualiapgeorefs.db");

        try {
            String _un = URLEncoder.encode (username);
            String _pw = URLEncoder.encode (password);
            String _em = URLEncoder.encode (emailaddr);
            URL url = new URL (MaintView.dldir +
                    "/manualgeoref.php?func=newuser&username=" + _un +
                    "&password=" + _pw + "&emailaddr=" + _em);
            HttpURLConnection httpcon = (HttpURLConnection) url.openConnection ();
            try {
                int rc = httpcon.getResponseCode ();
                if (rc != HttpURLConnection.HTTP_OK) {
                    throw new IOException ("http response code " + rc);
                }
                BufferedReader br = new BufferedReader (new InputStreamReader (httpcon.getInputStream ()));
                String line = br.readLine ();
                if (line == null) line = "";
                line = line.trim ();
                if (line.equals ("OK")) {
                    createRegisterTable (sqldb, username, password, emailaddr);
                    wairToNow.runOnUiThread (new Runnable () {
                        @Override
                        public void run ()
                        {
                            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                            adb.setTitle ("Contribution Registration Confirmation");
                            adb.setMessage ("Check your email (including spam folder) for " +
                                            "confirmation message then click link therein.");
                            adb.setPositiveButton ("OK", null);
                            adb.show ();
                            success ();
                        }
                    });
                } else if (line.equals ("")) {
                    alertMessage ("empty server reply");
                } else {
                    alertMessage (line);
                }
            } finally {
                httpcon.disconnect ();
            }
        } catch (final Exception e) {
            Log.w (ManualGeoRef.TAG, "exception registering username", e);
            alertMessage (e.getMessage ());
        }

        SQLiteDBs.CloseAll ();
    }

    /**
     * Display error message dialog and re-prompt for registration.
     */
    private void alertMessage (final String msg)
    {
        wairToNow.runOnUiThread (new Runnable () {
            @Override
            public void run ()
            {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Contribution Registration");
                adb.setMessage ("Error registering username: " + msg);
                adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialogInterface, int i)
                    {
                        reprompt ();
                    }
                });
                adb.show ();
            }
        });
    }

    /**
     * Write record to registration table
     * Options page will show section if it sees this record present
     */
    public static void createRegisterTable (SQLiteDBs sqldb, final String un, final String pw, final String em)
    {
        sqldb.execSQL ("BEGIN");
        sqldb.execSQL ("CREATE TABLE IF NOT EXISTS register (mr_username TEXT NOT NULL, mr_password TEXT NOT NULL, mr_emailaddr TEXT NOT NULL)");
        sqldb.execSQL ("DELETE FROM register");
        ContentValues values = new ContentValues (3);
        values.put ("mr_username",  un);
        values.put ("mr_password",  pw);
        values.put ("mr_emailaddr", em);
        sqldb.insert ("register", values);
        sqldb.execSQL ("COMMIT");
    }
}
