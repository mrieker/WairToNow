//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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

import android.database.Cursor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Thread runs in background forever fetching METARs and TAFs from internet for airports displayed on chart.
 * Feeds same list as METARs and TAFs received from ADSB.
 * Shows up a cyan text on FAAWP page for associated airport and as green/blue/red dot over airport.
 */
public class WebMetarThread extends Thread {
    public final static int INTERVALMS = 987654;    // refetch for same airport after this time
    public final static int MINSLEEPMS = 5432;      // always sleep at least this much
    public final static int RETRYMS    = 32716;     // retry for no internet connection
    public final static int WEBDELAYMS = 123;       // this much time between individual web requests
    public final static String BASEURL = "https://aviationweather.gov/taf/data?format=raw&metars=on&layout=off&ids=";
    public final static String TAG = "WairToNow";

    private double fetchednorthlat;
    private double fetchedsouthlat;
    private double fetchedeastlon;
    private double fetchedwestlon;
    private long fetchedtime;
    private long nowtime;
    private final NNHashMap<String,Long> lastfetcheds;
    private WairToNow wairToNow;
    public  WakeableSleep sleeper;

    public WebMetarThread (WairToNow wtn)
    {
        lastfetcheds = new NNHashMap<> ();
        sleeper = new WakeableSleep ();
        wairToNow = wtn;
    }

    @Override
    public void run ()
    {
        setName ("WebMetarThread");

        //noinspection InfiniteLoopStatement
        while (true) {
            long sleeptime;
            try {
                nowtime = System.currentTimeMillis ();

                // re-fetch old stuff
                synchronized (lastfetcheds) {
                    for (Iterator<String> it = lastfetcheds.keySet ().iterator (); it.hasNext ();) {
                        String ident = it.next ();
                        if (lastfetcheds.nnget (ident) <= nowtime - INTERVALMS) {
                            it.remove ();
                        }
                    }
                }

                // if it has been a while or we don't have the showing area,
                // fetch new metars and tafs
                boolean movedfar = movedFar ();
                while (movedfar) {
                    movedfar = false;

                    // get list of all airports in the area with METARs and/or TAFs
                    // but only those we haven't fetched METARs/TAFs for since INTERVALMS ago
                    String where = "(apt_lat BETWEEN " + fetchedsouthlat + " AND " + fetchednorthlat +
                            ") AND (apt_lon BETWEEN " + fetchedwestlon + " AND " + fetchedeastlon +
                            ") AND (apt_metaf<>'')";

                    double centerlat = (fetchednorthlat + fetchedsouthlat) / 2.0;
                    double centerlon = (fetchedeastlon + fetchedwestlon) / 2.0;
                    TreeMap<Double,Waypoint.Airport> apts = new TreeMap<> ();
                    SQLiteDBs sqldb = Waypoint.openWayptDB (wairToNow);
                    Cursor result = sqldb.query ("airports",
                            Waypoint.Airport.dbcols, where, null, null, null, null, null);
                    try {
                        if (result.moveToFirst ()) do {
                            synchronized (lastfetcheds) {
                                // assumes Waypoint.Airport.dbcols[0].equals("apt_icaoid")
                                if (! lastfetcheds.containsKey (result.getString (0))) {
                                    Waypoint.Airport apt = new Waypoint.Airport (result, wairToNow);
                                    double distnm = Lib.LatLonDist (apt.lat, apt.lon, centerlat, centerlon);
                                    while (apts.containsKey (distnm)) distnm += 0.000001;
                                    apts.put (distnm, apt);
                                }
                            }
                        } while (result.moveToNext ());
                    } finally {
                        result.close ();
                    }

                    // read metars from web
                    // but stop if screen moved far
                    for (Waypoint.Airport apt : apts.values ()) {
                        movedfar = movedFar ();
                        if (movedfar) break;
                        fetchMetars (apt);
                    }
                }

                // sleep until interval past last complete fetch
                sleeptime = fetchedtime + INTERVALMS - nowtime;
            } catch (Exception e) {
                Log.w (TAG, "exception processing metars from web", e);
                // probably no internet, try again in a minute
                fetchedtime = 0;
                sleeptime = RETRYMS;
            }

            // wait for sleep time or until chart moved
            // always sleep at least MINSLEEPMS
            SQLiteDBs.CloseAll ();
            try { Thread.sleep (MINSLEEPMS); } catch (InterruptedException ignored) { }
            sleeptime -= MINSLEEPMS;
            if (sleeptime > 0) {
                Log.d (TAG, "webmetarthread sleeping for " + sleeptime);
                sleeper.sleep (sleeptime);
            }
        }
    }

    // see if the canvas has been moved far away from what we have metars/tafs for
    // ie, see if we have possibly missed an airport
    private boolean movedFar ()
    {
        // get lat/lon limits of displayed area
        PixelMapper pmap = wairToNow.chartView.pmap;
        double showingnorthlat = pmap.canvasNorthLat;
        double showingsouthlat = pmap.canvasSouthLat;
        double showingeastlon  = pmap.canvasEastLon;
        double showingwestlon  = pmap.canvasWestLon;

        // if it has been a while or we don't have the showing area,
        // fetch new metars and tafs
        if ((nowtime - fetchedtime >= INTERVALMS) ||
                (showingnorthlat > fetchednorthlat) ||
                (showingsouthlat < fetchedsouthlat) ||
                (showingeastlon > fetchedeastlon) ||
                (showingwestlon < fetchedwestlon)) {
            double showingheight = showingnorthlat - showingsouthlat;
            double showingwidth = showingeastlon - showingwestlon;
            fetchednorthlat = showingnorthlat + showingheight / 2.0;
            fetchedsouthlat = showingsouthlat - showingheight / 2.0;
            fetchedeastlon = showingeastlon + showingwidth / 2.0;
            fetchedwestlon = showingwestlon - showingwidth / 2.0;
            fetchedtime = nowtime;
            return true;
        }
        return false;
    }

    // try to fetch METARs and TAFs for the given airport
    // can be called by any thread
    //  input:
    //   apt = Waypoint.Airport being fetched
    //  output:
    //   wairToNow.metarRepos = updated with METARs and TAFs
    private void fetchMetars (Waypoint.Airport apt)
            throws Exception
    {
        // read METARs and TAFs from FAA
        String uri = BASEURL + apt.ident;
        Log.d (TAG, "fetching " + uri);
        try { Thread.sleep (WEBDELAYMS); } catch (InterruptedException ignored) { }
        URL url = new URL (uri);
        HttpURLConnection httpcon = (HttpURLConnection) url.openConnection ();
        try {
            httpcon.connect ();
            int rc = httpcon.getResponseCode ();
            if (rc != HttpURLConnection.HTTP_OK) {
                throw new IOException ("http response code " + rc);
            }
            BufferedReader br = new BufferedReader (new InputStreamReader (httpcon.getInputStream ()));
            StringBuilder sb = new StringBuilder ();
            for (String line; (line = br.readLine ()) != null; ) {
                line = line.trim ();
                if (line.length () > 0) {
                    if (sb.length () > 0) sb.append (' ');
                    sb.append (line);
                }
            }
            processMetarReply (apt, sb);
        } finally {
            httpcon.disconnect ();
        }
    }

    // process internet reply to metar/taf request
    // can be called from any thread
    private void processMetarReply (Waypoint.Airport apt, StringBuilder sb)
    {
        for (int i = 0; (i = sb.indexOf ("<", i)) >= 0;) {
            int j = sb.indexOf (">", i);
            sb.replace (i, ++ j, " ");
        }
        String st = sb.toString ().replace ("&nbsp;", " ").trim ().replaceAll ("\\s+", " ");
        Log.d (TAG, "webmetar " + st);
        String[] words = st.split (" ");
        int nwords = words.length;
        int lasti = -1;
        for (int i = 0; i < nwords; i ++) {
            String word = words[i];

            // both METARs and TAFs begin with icaoid ddhhmmZ ...
            // TAFs have words FMddhhmm
            if (word.equalsIgnoreCase (apt.ident)) {
                if (lasti >= 0) {
                    insertMetarOrTaf (apt, words, lasti, i);
                }
                lasti = i;
            }
        }
        if (lasti >= 0) {
            insertMetarOrTaf (apt, words, lasti, nwords);
        }
    }

    // insert METAR or TAF for an airport
    //  input:
    //   apt = airport waypoint
    //   words[i] = airport icaoid
    //   words[i+1] = ddhhmmZ
    //   words[i+2..j-1] = METAR or TAF text
    //  output:
    //   entry added to wairToNow.metarRepos
    private void insertMetarOrTaf (Waypoint.Airport apt, String[] words, int i, int j)
    {
        synchronized (lastfetcheds) {
            lastfetcheds.put (apt.ident, nowtime);
        }
        long time;
        String type;
        try {
            time = parseMetarTime (words[i+1]);
        } catch (NumberFormatException nfe) {
            // this happens when we get an error message saying no metar/taf report for this airport
            return;
        }
        type = "METAR";
        StringBuilder sb = new StringBuilder ();
        while (++ i < j) {
            String word = words[i];
            if ((word.length () == 8) && word.startsWith ("FM")) {
                type = "TAF";
                sb.append ('\n');
            }
            if (sb.length () > 0) sb.append (' ');
            sb.append (word);
        }
        Metar metar = new Metar (time, type, sb.toString ());
        synchronized (wairToNow.metarRepos) {
            MetarRepo repo = wairToNow.metarRepos.get (apt.ident);
            if (repo == null) {
                repo = new MetarRepo (apt.lat, apt.lon);
                wairToNow.metarRepos.put (apt.ident, repo);
            }
            repo.insertNewMetar (metar);
            Log.d (TAG, "webmetar " + apt.ident + " cig=" + repo.ceilingft + " vis=" + repo.visibsm);
        }
        wairToNow.chartView.backing.getView ().postInvalidate ();
    }

    // convert given ddhhmmZ string to millisecond time
    //  input:
    //   ddhhmmz = time in form ddhhmmZ
    //  output:
    //   returns millisecond time
    private static long parseMetarTime (String ddhhmmz)
    {
        GregorianCalendar gcal = new GregorianCalendar (Lib.tzUtc, Locale.US);
        int dd = Integer.parseInt (ddhhmmz.substring (0, 2));
        int hh = Integer.parseInt (ddhhmmz.substring (2, 4));
        int mm = Integer.parseInt (ddhhmmz.substring (4, 6));
        if (ddhhmmz.charAt (6) != 'Z') throw new NumberFormatException ("bad metar time " + ddhhmmz);
        if ((gcal.get (GregorianCalendar.DAY_OF_MONTH) > 20) && (dd < 10)) {
            gcal.add (GregorianCalendar.MONTH, 1);
        }
        gcal.set (GregorianCalendar.DAY_OF_MONTH, dd);
        gcal.set (GregorianCalendar.HOUR_OF_DAY, hh);
        gcal.set (GregorianCalendar.MINUTE, mm);
        gcal.set (GregorianCalendar.SECOND, 0);
        gcal.set (GregorianCalendar.MILLISECOND, 0);
        return gcal.getTimeInMillis ();
    }

    // get a url that, when fetched from, will get the latest METAR/TAF info for the given airport
    // and put it into the wairToNow.metarRepo.  it just replies "OK" when complete.
    public String getWebMetarProxyURL (String icaoid)
    {
        if (webMetarProxyServer == null) {
            webMetarProxyServer = new WebMetarProxyServer ();
        }
        if (webMetarProxyServer.serverSocket == null) return "";
        return "http://localhost:" + webMetarProxyServer.serverSocket.getLocalPort () + "/" + icaoid;
    }

    public String getInetStatusURL ()
    {
        if (webMetarProxyServer == null) {
            webMetarProxyServer = new WebMetarProxyServer ();
        }
        if (webMetarProxyServer.serverSocket == null) return "";
        return "http://localhost:" + webMetarProxyServer.serverSocket.getLocalPort () + "/inetstatus.txt";
    }

    private WebMetarProxyServer webMetarProxyServer;

    private class WebMetarProxyServer extends Thread {
        public ServerSocket serverSocket;

        public WebMetarProxyServer ()
        {
            try {
                serverSocket = new ServerSocket (0);
            } catch (IOException ioe) {
                Log.e (TAG, "exception creating WebMetarProxyServer socket", ioe);
                return;
            }
            start ();
        }

        @Override
        public void run ()
        {
            setName ("WebMetarProxyServer");
            //noinspection InfiniteLoopStatement
            while (true) {
                try {
                    Socket connectSocket = serverSocket.accept ();
                    try {
                        BufferedReader br = new BufferedReader (new InputStreamReader (connectSocket.getInputStream ()));
                        String req = br.readLine ();
                        if (!req.startsWith ("GET /") || !req.endsWith (" HTTP/1.1")) throw new Exception ("bad request " + req);
                        String icaoid = req.substring (5, req.length () - 9);
                        while (! (br.readLine ()).trim ().equals ("")) Lib.Ignored ();
                        String reply;
                        if (icaoid.equals ("inetstatus.txt")) {
                            reply = PlanView.getInetStatus () + "\n";
                        } else {
                            Waypoint.Airport apt = Waypoint.GetAirportByIdent (icaoid, wairToNow);
                            if (apt == null) throw new Exception ("bad airport icaoid " + icaoid);
                            fetchMetars (apt);
                            reply = "OK\n";
                        }
                        BufferedWriter bw = new BufferedWriter (new OutputStreamWriter (connectSocket.getOutputStream ()));
                        bw.write ("HTTP/1.1 200 OK\r\n");
                        bw.write ("Access-Control-Allow-Origin: *\r\n");
                        bw.write ("Content-Length: " + reply.length () + "\r\n\r\n");
                        bw.write (reply);
                        bw.flush ();
                    } finally {
                        connectSocket.close ();
                    }
                } catch (Exception e) {
                    Log.w (TAG, "exception processing WebMetarProxy request", e);
                }
            }
        }
    }
}
