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

import android.support.annotation.NonNull;
import android.util.Log;

import com.apps4av.avarehelper.connections.Reporter;
import com.apps4av.avarehelper.connections.TopDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Receive information from external GPS/ADS-B receiver connected via bluetooth, WiFi etc.
 */
public class AdsbGpsRThread extends Thread implements Reporter {
    public final static String TAG = "WairToNow";

    private volatile boolean closed;
    private InputStream inputStream;
    public  long lengthReceived;
    public  volatile OutputStream rawLogStream;
    private TopDecoder decoder;
    private WairToNow wairToNow;

    /*****************************\
     *  Called by outside world  *
    \*****************************/

    /**
     * Run input stream through the ADS-B decoder.
     * Caller must call start() to start the thread.
     * @param stream = input stream containing raw ADS-B data
     * @param wtn = where to post UI thread level events
     */
    public AdsbGpsRThread (@NonNull InputStream stream, @NonNull WairToNow wtn)
    {
        decoder = new TopDecoder (this);
        inputStream = stream;
        wairToNow = wtn;
    }

    /**
     * Stop receiving.
     */
    public void close ()
    {
        closed = true;
        try { inputStream.close (); } catch (Exception e) { Lib.Ignored (); }
        try { join (); } catch (Exception e) { Lib.Ignored (); }
        try { rawLogStream.close (); } catch (Exception e) { Lib.Ignored (); }
        inputStream  = null;
        rawLogStream = null;
    }

    /**
     * Record the incoming raw ADS-B data to a log file.
     */
    @SuppressWarnings("unused")
    public void startRecording (OutputStream stream)
    {
        rawLogStream = stream;
    }

    /**
     * Internal thread to process receiving in the background.
     */
    @Override  // Thread
    public void run ()
    {
        try {
            try {
                while (!closed) {

                    /*
                     * Read bytes into top-level decoder input buffer.
                     * Stop if reached the end of the input stream.
                     */
                    int rc = inputStream.read (decoder.mBuf, decoder.mInsert,
                            decoder.mBuf.length - decoder.mInsert);
                    if (rc <= 0) break;

                    /*
                     * Accumulate total number of bytes received.
                     */
                    lengthReceived += rc;

                    /*
                     * Maybe write raw data to log file.
                     */
                    OutputStream os = rawLogStream;
                    if (os != null) {
                        try {
                            byte[] sizebytes = new byte[] { (byte) rc, (byte) (rc >> 8) };
                            os.write (sizebytes);
                            os.write (decoder.mBuf, decoder.mInsert, rc);
                            os.flush ();
                        } catch (IOException ioe) {
                            Log.e (TAG, "error logging raw ADS-B bytes", ioe);
                            try { os.close (); } catch (IOException ioe2) { Lib.Ignored (); }
                            rawLogStream = null;
                        }
                    }

                    /*
                     * Decode any messages therein into Reporter calls below.
                     * Save any unprocessed data for next receive and maybe it
                     * can be processed then.
                     */
                    decoder.topDecode (rc);
                }
            } finally {
                try { inputStream.close (); } catch (IOException ioe) { Lib.Ignored (); }
                SQLiteDBs.CloseAll ();
            }
        } catch (IOException ioe) {
            if (!closed) {
                Log.e (TAG, "error reading ADS-B stream", ioe);
            }
        }
    }

    /**
     * Format string for status display.
     */
    public String getConnectStatusText (String startWord)
    {
        StringBuilder sb = new StringBuilder ();
        sb.append (startWord);
        if (batteryLevel != null) {
            sb.append ("; battery ");
            sb.append (batteryLevel);
        }
        if (lengthReceived > 0) {
            sb.append (String.format (Locale.US, "; %,d bytes", lengthReceived));
        }
        synchronized (instanceCounts) {
            for (String type : instanceCounts.keySet ()) {
                int[] count = instanceCounts.nnget (type);
                sb.append ('\n');
                sb.append (type);
                sb.append (": ");
                sb.append (count[0]);
            }
        }
        return sb.toString ();
    }

    /***********************\
     *  Called by Decoder  *
    \***********************/

    private String batteryLevel;
    private final NNTreeMap<String,int[]> instanceCounts = new NNTreeMap<> ();

    @Override  // Reporter
    public void adsbGpsLog (String message)
    {
        Log.w (TAG, "adsb decoder: " + message);
    }

    @Override  // Reporter
    public void adsbGpsLog (String message, Exception e)
    {
        Log.w (TAG, "adsb decoder: " + message, e);
    }

    @Override  // Reporter
    public void adsbGpsAHRS (
            double bank,
            double heading,
            double pitch,
            long time)
    {
        //TODO pass to glass display
    }

    /**
     * Device is telling us its battery status.
     */
    @Override  // Reporter
    public void adsbGpsBattery (
            String batlevel)
    {
        batteryLevel = batlevel;
    }

    /**
     * Device just sent one of these type messages to us.
     * @param type = string identifying message type
     */
    @Override  // Reporter
    public void adsbGpsInstance (
            String type)
    {
        synchronized (instanceCounts) {
            int[] count = instanceCounts.get (type);
            if (count == null) {
                count = new int[1];
                instanceCounts.put (type, count);
            }
            count[0] ++;
        }
    }

    /**
     * Device is passing along some textual weather report.
     * @param time = time of report
     * @param type = "METAR", "PIREP", "SPECI", "TAF", "WINDS", etc.
     * @param location = FAA-id or IACO-id
     * @param data = report text
     */
    @Override  // Reporter
    public void adsbGpsMetar (
            long time,
            String type,
            String location,
            String data)
    {
        Log.d (TAG, "adsbGpsMetar: type=" + type + " loc=" + location + " data=" + data);
        Waypoint apt = Waypoint.GetAirportByIdent (location, wairToNow);
        if (apt != null) {
            String icaoid = apt.ident;
            Metar metar = new Metar ();
            metar.time = time;
            metar.type = type;
            metar.data = data;
            synchronized (wairToNow.metarRepos) {
                MetarRepo repo = wairToNow.metarRepos.get (icaoid);
                if (repo == null) {
                    repo = new MetarRepo (apt.lat, apt.lon);
                    wairToNow.metarRepos.put (icaoid, repo);
                }
                repo.insertNewMetar (metar);
            }
        } else {
            Log.w (TAG, "unknown metar location " + location + ", type=" + type + ", data=" + data);
        }
    }

    /**
     * Device is telling us to clear some Nexrad images from the screen.
     * @param time     = not used
     * @param conus    = true: continental; false: regional
     * @param nblknums = number of block numbers in blknums[]
     * @param blknums  = array of block numbers to clear
     */
    @Override  // Reporter
    public void adsbGpsNexradClear (
            long time,
            boolean conus,
            int nblknums,
            int[] blknums)
    {
        /*
         * Discard the listed bitmaps.
         * The blknums[] array contains block numbers to toss.
         */
        synchronized (wairToNow.nexradRepo) {
            wairToNow.nexradRepo.deleteBlocks (conus, nblknums, blknums);
        }

        redrawChart ();
    }

    /**
     * Device has a new Nexrad image for us.
     * @param time    = when image was generated
     * @param conus   = true: continental; false: regional
     * @param blockno = block number (lat/lon)
     * @param pixels  = pixel color array
     */
    @Override  // Reporter
    public void adsbGpsNexradImage (
            long time,
            boolean conus,
            int blockno,
            int[] pixels)
    {
        /*
         * Add a new image to the repo, possibly replacing an existing one.
         */
        NexradImage ni = new NexradImage (time, pixels, blockno, conus);
        Log.d (TAG, "adsbGpsNexradImage: lat=" + ni.northLat + " lon=" + ni.westLon + " conus=" + conus);
        synchronized (wairToNow.nexradRepo) {
            wairToNow.nexradRepo.insertNewNexrad (ni);
        }

        // tell chart to redraw itself
        redrawChart ();
    }

    /**
     * Device knows our own position.
     * @param time      = time of report (ms since 1970-01-01 0000Z)
     * @param taltitude = true altitude feet MSL (or INV_ALT)
     * @param heading   = degrees
     * @param latitude  = degrees
     * @param longitude = degrees
     * @param speed     = knots
     */
    @Override  // Reporter
    public void adsbGpsOwnship (
            final long time,
            final double taltitude,
            final double heading,
            final double latitude,
            final double longitude,
            final double speed)
    {
        ReportAdsbGpsOwnship r = reportAdsbGpsOwnship;
        if (r.bussy) return;

        // pass GPS sample on to GUI thread
        r.bussy = true;
        r.time = time;
        r.taltitude = taltitude;
        r.heading = heading;
        r.latitude = latitude;
        r.longitude = longitude;
        r.speed = speed;
        WairToNow.wtnHandler.runDelayed (0, r);
    }

    private final ReportAdsbGpsOwnship reportAdsbGpsOwnship = new ReportAdsbGpsOwnship ();

    private class ReportAdsbGpsOwnship implements Runnable {
        public volatile boolean bussy;

        public long time;
        public double taltitude;
        public double heading;
        public double latitude;
        public double longitude;
        public double speed;

        @Override
        public void run () {
            wairToNow.LocationReceived (
                    speed / Lib.KtPerMPS,
                    taltitude / Lib.FtPerM,
                    heading,
                    latitude,
                    longitude,
                    time);
            bussy = false;
        }
    }

    /**
     * Update on GPS satellite status.
     */
    @Override
    public void adsbGpsSatellites (Collection<MyGpsSatellite> satellites)
    {
        final LinkedList<MyGpsSatellite> sats = new LinkedList<> (satellites);
        WairToNow.wtnHandler.runDelayed (0, new Runnable () {
            @Override
            public void run ()
            {
                wairToNow.sensorsView.gpsStatusView.SetGPSStatus (sats);
            }
        });
    }

    /**
     * Device is reporting some traffic to us.
     * @param time      = time of report
     * @param taltitude = true altitude of other aircraft feet MSL (or NaN)
     * @param heading   = other aircraft heading (or INV_HDG)
     * @param latitude  = other aircraft latitude
     * @param longitude = other aircraft longitude
     * @param speed     = other aircraft speed (or INV_SPD)
     * @param address   = other aircraft ICAO address
     * @param callsign  = other aircraft callsign
     */
    @Override  // Reporter
    public void adsbGpsTraffic (
            long time,
            double taltitude,
            double heading,
            double latitude,
            double longitude,
            double speed,
            double climb,
            int address,
            String callsign)
    {
        Log.d (TAG, "adsbGpsTraffic: " + Lib.TimeStringUTC (time) + " lat=" + latitude +
                " lon=" + longitude + " alt=" + taltitude + " adr=" + Integer.toHexString (address) +
                " ident=" + callsign);
        Traffic traffic   = new Traffic ();
        traffic.time      = time;
        traffic.taltitude = taltitude / Lib.FtPerM;
        traffic.heading   = heading;
        traffic.latitude  = latitude;
        traffic.longitude = longitude;
        traffic.speed     = speed / Lib.KtPerMPS;
        traffic.climb     = climb;
        traffic.address   = address;

        if (callsign.endsWith (":SQUAWK")) {
            traffic.squawk   = callsign.substring (0, callsign.length () - 7);
        } else {
            traffic.callsign = callsign;
        }

        synchronized (wairToNow.trafficRepo) {
            wairToNow.trafficRepo.insertNewTraffic (traffic);
        }

        redrawChart ();
    }

    /**
     * Convert a pressure altitude to a true altitude.
     * @param latitude  = latitude where pressure altitude is measured
     * @param longitude = longitude where pressure altitude is measured
     * @param paltitude = pressure altitude, feet MSL
     * @return true altitude, feet MSL, or NaN if unknown
     */
    @Override  // Reporter
    public double adsbGpsPalt2Talt (
            double latitude,
            double longitude,
            double paltitude)
    {
        double altsetting = MetarRepo.getAltSetting (wairToNow.metarRepos, latitude, longitude);
        if (altsetting <= 0.0) return Double.NaN;
        // really indicated altitude but close enuf for traffic reports
        return paltitude + 1000.0 * (altsetting - 29.92);
    }

    /**
     * Tell chart to redraw itself.
     */
    private void redrawChart ()
    {
        try {
            wairToNow.currentTabButton.postInvalidate ();
        } catch (NullPointerException npe) {
            Lib.Ignored ();
        }
    }
}
