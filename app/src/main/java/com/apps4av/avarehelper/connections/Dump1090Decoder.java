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

package com.apps4av.avarehelper.connections;

import com.outerworldapps.wairtonow.Lib;

import java.util.HashMap;

import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.Position;
import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.AirspeedHeadingMsg;
import org.opensky.libadsb.msgs.AltitudeReply;
import org.opensky.libadsb.msgs.CommBAltitudeReply;
import org.opensky.libadsb.msgs.IdentificationMsg;
import org.opensky.libadsb.msgs.LongACAS;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.ShortACAS;

/**
 * Process incoming dump1090 format messages.
 *   *hexdigits-6digitcrc;\n

 // https://adsb-decode-guide.readthedocs.io/en/latest/content/introduction.html
 *8daba324603df064f6ac2da07458;
 1000 1101 1010 1011 1010 0011 0010 0100 0110 0000 0011 1101 1111 0000 0110 0100 1111 0100 1010 1100 0010 1101 1010 0000 0111 0100 0101 1000
 \-df-/\-/ \---------icao addr---------/ \--------------------data-------------------------------------------/ \------------crc------------/
        ca

 CRC: a07458 (ok)
 DF 17: ADS-B message.
 Capability     : 5 (Level 2+3+4 (DF0,4,5,11,20,21,24,code7 - is on airborne))
 ICAO Address   : aba324
 Extended Squitter  Type: 12
 Extended Squitter  Sub : 0
 Extended Squitter  Name: Airborne Position (Baro Altitude)
 F flag   : even
 T flag   : non-UTC
 Altitude : 11375 feet
 Latitude : 12923 (not decoded)
 Longitude: 44077 (not decoded)

 */
public class Dump1090Decoder extends MidDecoder {
    private static class Aircraft {
        public int icao24;
        public String callsign = "";

        public double climb = Double.NaN;  // feet per minute
        public double speed = Double.NaN;  // knots
        public double geoMinusBaro = Double.NaN;  // feet
        public double heading = Double.NaN;  // degrees
        public double lat, lon;  // degrees
        public double presalt;   // feet
        public double truealt;   // feet

        AirbornePositionMsg posEven;
        AirbornePositionMsg posOdd;

        // set altitude given a pressure (barometric) altitude in metres
        public void setPresAlt (double baroalt, Reporter reporter)
        {
            presalt = baroalt * Lib.FtPerM;
            truealt = Double.isNaN (geoMinusBaro) ?
                    reporter.adsbGpsPalt2Talt (lat, lon, presalt) :
                    presalt - geoMinusBaro;
        }
    }

    private HashMap<Integer,Aircraft> aircrafts = new HashMap<> ();  // indexed by icao24
    private TopDecoder topDecoder;

    public Dump1090Decoder (TopDecoder td)
    {
        topDecoder = td;
    }

    /**
     * See if topDecoder.mBuf[] contains our start character anywhere.
     */
    @Override
    public boolean findStartIndex ()
    {
        byte[] buf = topDecoder.mBuf;
        int end = topDecoder.mInsert;
        int i;
        for (i = msgStartIndex; i < end; i ++) {
            byte c = buf[i];
            if (c == '*') break;
        }
        msgStartIndex = i;
        return i < end;
    }

    /**
     * msgStartIndex points to our start character.
     * If we have a whole message, remove from buffer and process it.
     * @return true: we removed a message; false: nothing removed
     */
    @Override
    public boolean midDecode ()
    {
        byte[] mbuf = topDecoder.mBuf;
        int end = topDecoder.mInsert;
        int saveRem = msgStartIndex + 1;

        /*
         * Try to get dump1090 message starting at the '*' ending with ';\n'.
         */
        int semiNl = saveRem;
        while (true) {
            if (++ semiNl + 2 > end) return false;
            byte c = mbuf[semiNl];
            if ((c == ';') && (mbuf[semiNl+1] == '\n')) break;
            if (c == '*') {
                msgStartIndex = semiNl;
                return true;
            }
        }

        /*
         * Found a whole message, good or bad, remove from buffer so we don't try to parse it again.
         */
        msgStartIndex = semiNl + 2;

        /*
         * Complete dump1090 message found.
         * Make sure other pointers point past end of this message.
         */
        topDecoder.validMessageEndsAt (msgStartIndex);

        Reporter reporter = topDecoder.mReporter;
        try {
            // decode message and get ICAO identifier for the aircraft
            ModeSReply msg = Decoder.genericDecoder (new String (mbuf, saveRem, semiNl - saveRem));
            reporter.adsbGpsInstance ("dump1090-" + msg.getClass ().getSimpleName ());
            int icao24 = icao24int (msg.getIcao24 ());
            Aircraft aircraft = aircrafts.get (icao24);
            if (aircraft == null) {
                aircraft = new Aircraft ();
                aircraft.icao24 = icao24;
                aircrafts.put (icao24, aircraft);
            }

            // process message
            switch (msg.getType ()) {

                // aircraft is reporting its position
                //  we supposedly get alternating even/odd messages
                //  so if we actually get that, report position
                //  otherwise do nothing until we get that sequence
                case ADSB_AIRBORN_POSITION: {
                    AirbornePositionMsg msgpos = (AirbornePositionMsg) msg;
                    AirbornePositionMsg other;
                    if (msgpos.isOddFormat ()) {
                        aircraft.posOdd = msgpos;
                        other = aircraft.posEven;
                        aircraft.posEven = null;
                    } else {
                        aircraft.posEven = msgpos;
                        other = aircraft.posOdd;
                        aircraft.posOdd = null;
                    }

                    // see if we have even/odd or odd/even
                    if (other != null) {

                        // we should be able to compute and report position now
                        Position position = msgpos.getGlobalPosition (other);
                        aircraft.lat = position.getLatitude  ();
                        aircraft.lon = position.getLongitude ();
                        Double boxedalt = position.getAltitude  ();  // metres
                        if (boxedalt != null) {
                            if (msgpos.isBarometricAltitude ()) {
                                aircraft.setPresAlt (boxedalt, reporter);
                            } else {
                                aircraft.presalt = Double.NaN;
                                aircraft.truealt = boxedalt * Lib.FtPerM;
                            }
                        }
                        reporter.adsbGpsTraffic (
                                System.currentTimeMillis (),
                                aircraft.truealt,
                                aircraft.heading,
                                aircraft.lat,
                                aircraft.lon,
                                aircraft.speed,
                                aircraft.climb,
                                aircraft.icao24,
                                aircraft.callsign
                        );
                    }
                    break;
                }

                case ADSB_AIRSPEED: {
                    AirspeedHeadingMsg msgspd = (AirspeedHeadingMsg) msg;
                    if (msgspd.hasAirspeedInfo ()) {
                        aircraft.speed = msgspd.getAirspeed () * Lib.KtPerMPS;
                    }
                    if (msgspd.hasVerticalRateInfo ()) {
                        aircraft.climb = msgspd.getVerticalRate () * Lib.FtPerM * 60.0;
                    }
                    if (msgspd.hasGeoMinusBaroInfo ()) {
                        aircraft.geoMinusBaro = msgspd.getGeoMinusBaro () * Lib.FtPerM;
                    }
                    if (msgspd.hasHeadingInfo ()) {
                        aircraft.heading = msgspd.getHeading ();
                    }
                    break;
                }

                // aircraft is sending its ident (N-number etc)
                case ADSB_IDENTIFICATION: {
                    IdentificationMsg msgidn = (IdentificationMsg) msg;
                    char[] ident = msgidn.getIdentity ();
                    int i = ident.length;
                    while (i > 0) {
                        if (ident[i-1] > ' ') break;
                        -- i;
                    }
                    aircraft.callsign = new String (msgidn.getIdentity (), 0, i);
                    break;
                }

                case ALTITUDE_REPLY: {
                    AltitudeReply msgalt = (AltitudeReply) msg;
                    Double boxedalt = msgalt.getAltitude ();
                    if (boxedalt != null) {
                        //TODO verify pressure alt
                        aircraft.setPresAlt (boxedalt, reporter);
                    }
                    break;
                }

                case COMM_B_ALTITUDE_REPLY: {
                    CommBAltitudeReply msgcba = (CommBAltitudeReply) msg;
                    Double boxedalt = msgcba.getAltitude ();
                    if (boxedalt != null) {
                        //TODO verify pressure alt
                        aircraft.setPresAlt (boxedalt, reporter);
                    }
                    break;
                }

                case LONG_ACAS: {
                    LongACAS msglac = (LongACAS) msg;
                    Double boxedalt = msglac.getAltitude ();
                    if (boxedalt != null) {
                        //TODO verify pressure alt
                        aircraft.setPresAlt (boxedalt, reporter);
                    }
                    break;
                }

                case SHORT_ACAS: {
                    ShortACAS msgsac = (ShortACAS) msg;
                    Double boxedalt = msgsac.getAltitude ();
                    if (boxedalt != null) {
                        //TODO verify pressure alt
                        aircraft.setPresAlt (boxedalt, reporter);
                    }
                    break;
                }

                // don't really use anything from these messages
                case ADSB_EMERGENCY:
                case ADSB_STATUS:
                case ADSB_VELOCITY:
                case ALL_CALL_REPLY:
                case COMM_B_IDENTIFY_REPLY:
                case EXTENDED_SQUITTER:
                case IDENTIFY_REPLY: {
                    break;
                }

                default: throw new Exception ("unknown message type " + msg.getType ());
            }
        } catch (Exception e) {
            reporter.adsbGpsLog ("error decoding 1090 message: " + e.getMessage ());
        }
        return true;
    }

    private static int icao24int (byte[] icao24bytes)
    {
        if (icao24bytes.length != 3) throw new IllegalArgumentException ("icao24bytes length " + icao24bytes.length + " not 3");
        return ((icao24bytes[0] & 0xFF) << 16) | ((icao24bytes[1] & 0xFF) << 8) | (icao24bytes[0] & 0xFF);
    }
}
