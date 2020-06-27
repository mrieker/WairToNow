//    Copyright (C) 2015, Mike Rieker, Beverly, MA USA
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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;

/**
 * Display plan page in a web view.
 * Allows internal links to other resource pages
 * and allows external links to web pages.
 */
@SuppressLint("ViewConstructor")
public class PlanView extends WebView
        implements WairToNow.CanBeMainView {

    private final static String TAG = "WairToNow";

    private boolean initted;
    private ListenThread listenThread;
    private NNTreeMap<String,String> linkbuttons;
    private WairToNow wairToNow;

    public PlanView (WairToNow wtn)
    {
        super (wtn);
        wairToNow = wtn;
    }

    // implement WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Plan";
    }
    @SuppressLint({ "AddJavascriptInterface", "SetJavaScriptEnabled" })
    public void OpenDisplay ()
    {
        if (! initted) {
            initted = true;

            try {
                listenThread = new ListenThread ();
                listenThread.serverSocket = new ServerSocket (0);
                listenThread.start ();
            } catch (IOException ioe) {
                Log.e (TAG, "error starting planview ajax server", ioe);
            }

            WebSettings settings = getSettings ();
            settings.setBuiltInZoomControls (true);
            settings.setDomStorageEnabled (true);
            settings.setJavaScriptEnabled (true);
            settings.setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
            settings.setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
            settings.setSupportZoom (true);
            addJavascriptInterface (new JavaScriptObject (), "pvjso");

            ReClicked ();
        }
    }
    public void CloseDisplay ()
    { }
    public void ReClicked ()
    {
        loadUrl ("file:///android_asset/plan.html");
    }
    public View GetBackPage ()
    {
        goBack ();
        return this;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    /**
     * Accessed via javascript in the internal .HTML files.
     */
    private class JavaScriptObject {
        private OfflineFPFormView offfpfv;

        // get preference
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getPref (String name, String defval)
        {
            SharedPreferences prefs = wairToNow.getPreferences (Activity.MODE_PRIVATE);
            return prefs.getString (name, defval);
        }

        // set preference
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void setPref (String name, String value)
        {
            SharedPreferences prefs = wairToNow.getPreferences (Activity.MODE_PRIVATE);
            SharedPreferences.Editor editr = prefs.edit ();
            editr.putString (name, value);
            editr.apply ();
        }

        // open offline flight plan page
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void openOfflineFPForm ()
        {
            WairToNow.wtnHandler.runDelayed (0, new Runnable () {
                @Override
                public void run ()
                {
                    if (offfpfv == null) offfpfv = new OfflineFPFormView (wairToNow);
                    wairToNow.SetCurrentTab (offfpfv);
                }
            });
        }

        // open FAAWP2 page for the given airport
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void openFAAWP (final String icaoid)
        {
            WairToNow.wtnHandler.runDelayed (0, new Runnable () {
                @Override
                public void run ()
                {
                    Waypoint wp = Waypoint.GetAirportByIdent (icaoid, wairToNow);
                    wairToNow.waypointView2.WaypointSelected (wp);
                    wairToNow.SetCurrentTab (wairToNow.waypointView2);
                }
            });
        }

        // get link buttons
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public int getNumLinkButtons ()
        {
            linkbuttons = readLinkButtonFile ();
            return linkbuttons.size ();
        }
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getLinkButtonsLabel (int i)
        {
            for (String key : linkbuttons.keySet ()) {
                if (-- i < 0) return key;
            }
            return null;
        }
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getLinkButtonsLink (int i)
        {
            for (String key : linkbuttons.keySet ()) {
                if (-- i < 0) return linkbuttons.nnget (key);
            }
            return null;
        }
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public void writeLinkButton (String oldkey, String newkey, String newval)
        {
            linkbuttons.remove (oldkey);
            if (! newkey.equals ("")) linkbuttons.put (newkey, newval);
            writeLinkButtonFile (linkbuttons);
        }

        // open external link in web browser
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String openExternalLink (String link)
        {
            try {
                Intent intent = new Intent (Intent.ACTION_VIEW);
                intent.setData (Uri.parse (link));
                wairToNow.startActivity (intent);
                return null;
            } catch (Throwable e) {
                return e.getMessage ();
            }
        }

        // get URL for accessing Ajax server thread
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String getAjaxUrl ()
        {
            int port = listenThread.serverSocket.getLocalPort ();
            return "http://localhost:" + port;
        }

        // search for airports
        @SuppressWarnings ("unused")
        @JavascriptInterface
        public String searchAirports (int mindist, int maxdist, int minhdg, int maxhdg,
                                      String hdgtype, String apticao, int minrwylen)
        {
            try {
                if ((mindist < 0) || (maxdist < mindist)) {
                    throw new Exception ("bad distance range");
                }
                while (minhdg < 0) minhdg += 360;
                while (maxhdg < 0) maxhdg += 360;
                minhdg %= 360;
                maxhdg %= 360;
                if (maxhdg <= minhdg) maxhdg += 360;

                /*
                 * Open database.
                 */
                SQLiteDBs sqldb = Waypoint.openWayptDB ();

                /*
                 * Find starting point airport's lat/lon.
                 */
                Cursor result1 = sqldb.query (
                        Waypoint.Airport.dbtable, Waypoint.Airport.dbcols,
                        Waypoint.Airport.dbkeyid + "=?", new String[] { apticao },
                        null, null, null, null);
                double fromLat, fromLon;
                Waypoint.Airport fromAptWp;
                try {
                    if (!result1.moveToFirst ()) {
                        throw new Exception ("starting airport not defined");
                    }
                    fromAptWp = new Waypoint.Airport (result1, wairToNow);
                    fromLat = fromAptWp.lat;
                    fromLon = fromAptWp.lon;
                } finally {
                    result1.close ();
                }

                /*
                 * Read list of airports into waypoint list that are within the given distance range
                 * and that are within the given heading range.
                 */
                boolean magnetic = hdgtype.equals ("M");
                double magvar = magnetic ? Lib.MagVariation (fromLat, fromLon, fromAptWp.elev / Lib.FtPerM) : 0.0;
                StringBuilder sb = new StringBuilder ();
                Cursor result2 = sqldb.query (
                        "airports", Waypoint.Airport.dbcols,
                        null, null, null, null, null, null);
                try {
                    if (result2.moveToFirst ()) do {
                        Waypoint.Airport wp = new Waypoint.Airport (result2, wairToNow);
                        double distNM = Lib.LatLonDist (fromLat, fromLon, wp.lat, wp.lon);
                        double hdg = Lib.LatLonTC (fromLat, fromLon, wp.lat, wp.lon) + magvar;
                        while (hdg < minhdg) hdg += 360.0;
                        if ((distNM >= mindist) && (distNM <= maxdist) && (hdg <= maxhdg)) {
                            for (Waypoint.Runway rwy : wp.GetRunways ().values ()) {
                                if (rwy.getLengthFt () >= minrwylen) {
                                    if (sb.length () == 0) {
                                        sb.append ("<TR><TH>ICAO</TH><TH>Name</TH><TH>Hdg</TH><TH>Dist</TH></TR>");
                                    }
                                    sb.append ("<TR><TD>");
                                    sb.append (wp.ident);
                                    sb.append ("</TD><TD>");
                                    sb.append (wp.GetName ());
                                    sb.append ("</TD><TD ALIGN=RIGHT>");
                                    sb.append (Long.toString ((Math.round (hdg) + 359) % 360 + 1001), 1, 4);
                                    sb.append ("&#176;</TD><TD ALIGN=RIGHT>");
                                    sb.append (Math.round (distNM));
                                    sb.append ("</TD><TD><INPUT TYPE=BUTTON ONCLICK=\"infoClicked('");
                                    sb.append (wp.ident);
                                    sb.append ("')\" VALUE=\"info\"></TD><TD><INPUT TYPE=BUTTON ONCLICK=\"fuelClicked('");
                                    sb.append (wp.ident);
                                    sb.append ("')\" VALUE=\"fuel\"></TD></TR>");
                                    break;
                                }
                            }
                        }
                    } while (result2.moveToNext ());
                } finally {
                    result2.close ();
                }
                return sb.toString ();
            } catch (Exception e) {
                return "<TR><TH>error: " + e.getMessage () + "</TH></TD>";
            }
        }
    }

    /**************************\
     *  Internal Ajax Server  *
    \**************************/

    private class ListenThread extends Thread {
        public ServerSocket serverSocket;

        @Override
        public void run ()
        {
            try {
                //noinspection InfiniteLoopStatement
                while (true) {
                    ConnectThread connectThread = new ConnectThread ();
                    connectThread.socket = serverSocket.accept ();
                    connectThread.start ();
                }
            } catch (IOException ioe) {
                Log.e (TAG, "error starting planview ajax server", ioe);
            }
        }
    }

    private class ConnectThread extends Thread {
        public Socket socket;

        @Override
        public void run ()
        {
            try {
                // read GET /url HTTP/1.1 line and skip following header lines
                BufferedReader br = new BufferedReader (new InputStreamReader (socket.getInputStream ()));
                String req = br.readLine ();
                if (req == null) throw new EOFException ("empty request body");
                if (! req.startsWith ("GET /") || ! req.endsWith (" HTTP/1.1")) {
                    throw new IOException ("http header " + req);
                }
                String url = req.substring (5, req.length () - 9);
                int i = url.indexOf ('?');
                String urlfile  = (i < 0) ? url : url.substring (0, i);
                String urlquery = (i < 0) ? ""  : url.substring (++ i);
                while (! (br.readLine ()).equals ("")) Lib.Ignored ();

                // set up to write reply into a byte array
                byte[] bytes = null;
                OutputStream sos = socket.getOutputStream ();
                String mime = null;

                // process request
                switch (urlfile) {
                    case "alert.txt": {
                        AlertTxt at = new AlertTxt ();
                        at.urlquery = urlquery;
                        WairToNow.wtnHandler.runDelayed (0, at);
                        //noinspection SynchronizationOnLocalVariableOrMethodParameter
                        synchronized (at) {
                            while (at.answer == null) {
                                at.wait ();
                            }
                        }
                        bytes = (at.answer + "\r\n").getBytes ();
                        mime  = "text/plain";
                        break;
                    }
                    case "inetstatus.txt": {
                        bytes = (getInetStatus () + "\r\n").getBytes ();
                        mime  = "text/plain";
                        break;
                    }
                    default: {
                        sos.write ("HTTP/1.1 500 Bad Request\r\n\r\n".getBytes ());
                        break;
                    }
                }

                // send reply with OK header line
                if (mime != null) {
                    String rep = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Content-length: " + bytes.length + "\r\n" +
                            "Content-type: " + mime + "\r\n" +
                            "\r\n";
                    sos.write (rep.getBytes ());
                    sos.write (bytes);
                }
            } catch (Exception e) {
                Log.e (TAG, "error processing planview ajax connection", e);
            } finally {
                try { socket.close (); } catch (IOException ignored) { }
            }
        }
    }

    // display alert dialog as passed to ajax by javascript
    private class AlertTxt implements Runnable {
        public String answer;
        public String urlquery;

        @Override
        public void run ()
        {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            for (String query : urlquery.split ("&")) {
                int i = query.indexOf ('=');
                String k = query.substring (0, i);
                String v = URLDecoder.decode (query.substring (++ i));
                switch (k) {
                    case "title": adb.setTitle (v); break;
                    case "msg": adb.setMessage (v); break;
                    case "pos": {
                        final String posbut = v;
                        adb.setPositiveButton (posbut, new DialogInterface.OnClickListener () {
                            @Override
                            public void onClick (DialogInterface dialog, int which)
                            {
                                synchronized (AlertTxt.this) {
                                    answer = posbut;
                                    AlertTxt.this.notifyAll ();
                                }
                            }
                        });
                        break;
                    }
                    case "neg": {
                        final String negbut = v;
                        adb.setNegativeButton (negbut, new DialogInterface.OnClickListener () {
                            @Override
                            public void onClick (DialogInterface dialog, int which)
                            {
                                synchronized (AlertTxt.this) {
                                    answer = negbut;
                                    AlertTxt.this.notifyAll ();
                                }
                            }
                        });
                        break;
                    }
                }
            }
            adb.show ();
        }
    }

    /******************\
     *  Link Buttons  *
    \******************/

    private static NNTreeMap<String,String> readLinkButtonFile ()
    {
        NNTreeMap<String,String> lbt = new NNTreeMap<> ();
        try {
            BufferedReader br = new BufferedReader (new FileReader (WairToNow.dbdir + "/linkbuttons.txt"), 4096);
            try {
                for (String line; (line = br.readLine ()) != null;) {
                    String[] parts = line.split (",,,,,");
                    lbt.put (parts[0], parts[1]);
                }
            } finally {
                br.close ();
            }
        } catch (FileNotFoundException fnfe) {
            lbt.put ("ADDS - Aviation Weather", "https://www.aviationweather.gov/adds/");
            lbt.put ("AirNav - Fuel Prices", "https://www.airnav.com/fuel/local.html");
            lbt.put ("FlightService - File Flight Plan", "https://www.1800wxbrief.com");
            lbt.put ("SkyVector - Planning", "https://skyvector.com/");
        } catch (Exception e) {
            Log.w (TAG, "error reading linkbuttons.txt", e);
        }
        return lbt;
    }

    private static void writeLinkButtonFile (NNTreeMap<String,String> lbt)
    {
        try {
            File permfile = new File (WairToNow.dbdir, "linkbuttons.txt");
            File tempfile = new File (WairToNow.dbdir, "linkbuttons.txt.tmp");
            BufferedWriter bw = new BufferedWriter (new FileWriter (tempfile));
            try {
                for (String label : lbt.keySet ()) {
                    String link = lbt.nnget (label);
                    bw.write (label + ",,,,," + link + "\n");
                }
            } finally {
                bw.close ();
            }
            if (! tempfile.renameTo (permfile)) throw new IOException ("error renaming temp to perm");
        } catch (Exception e) {
            Log.w (TAG, "error writing linkbuttons.txt", e);
        }
    }

    /**************************\
     *  Poll Internet Status  *
    \**************************/

    private boolean getInetStatus ()
    {
        try {
            URL url = new URL (MaintView.dldir);
            String host = url.getHost ();
            InetAddress[] addrs = InetAddress.getAllByName (host);
            int port = url.getPort ();
            if (port < 0) port = url.getDefaultPort ();

            // try to connect to each ip address until one succeeds
            for (InetAddress addr : addrs) {
                try {
                    Socket sock = new Socket (addr, port);
                    sock.close ();
                    return true;
                } catch (IOException ioe) {
                    Log.w (TAG, "error connecting to " + addr + ":" + port, ioe);
                }
            }
        } catch (IOException ioe) {
            Log.w (TAG, "error accessing url " + MaintView.dldir, ioe);
        }
        return false;
    }
}
