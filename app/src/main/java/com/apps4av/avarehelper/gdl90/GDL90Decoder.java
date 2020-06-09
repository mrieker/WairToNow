/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.apps4av.avarehelper.gdl90;

import com.apps4av.avarehelper.connections.MidDecoder;
import com.apps4av.avarehelper.connections.Reporter;
import com.apps4av.avarehelper.connections.TopDecoder;
import com.apps4av.avarehelper.connections.UplinkMessage;

/**
 * 
 * @author zkhan
 * mods mrieker
 *
 */
public class GDL90Decoder extends MidDecoder {
    private static final int HEARTBEAT = 0x00;
    private static final int UPLINK = 0x07;
    private static final int OWNSHIP = 0x0A;
    private static final int OWNSHIP_GEOMETRIC_ALTITUDE = 0x0B;
    private static final int TRAFFIC_REPORT = 0x14;
    private static final int BASIC_REPORT = 0x1E;
    private static final int LONG_REPORT = 0x1F;
    private static final int STRATUX_AHRS_REPORT = 0x4C; // makeAHRSGDL90Report()
    private static final int STRATUX_STATUS = 0x53; // 'S'
    private static final int DEVICE_REPORT = 0x7A;
    private static final int STRATUX_HEARTBEAT = 0xCC;

    private boolean gotOwnGeoAltLastCycle;
    private boolean gotOwnGeoAltThisCycle;
    public  boolean heartbeatPosVal;  // heartbeat says whether GPS position is valid or not
    private boolean stratuxAhrsValid;
    public  double  ownHeading;
    public  double  ownLatitude;
    public  double  ownLongitude;
    public  double  ownSpeed;
    public  double  ownTrueAlt;
    public  long    heartbeatTime;    // ms since 1970-01-01 0000Z of last heartbeat
    public  TopDecoder topDecoder;

    public GDL90Decoder (TopDecoder td)
    {
        topDecoder = td;
    }

    /**
     * See if current input buffer contains our start character.
     */
    @Override  // MidDecoder
    public boolean findStartIndex ()
    {
        byte[] buf = topDecoder.mBuf;
        int end = topDecoder.mInsert;
        int i;
        for (i = msgStartIndex; i < end; i ++) {
            if (buf[i] == 0x7E) break;
        }
        msgStartIndex = i;
        return i < end;
    }

    @Override  // MidDecoder
    public boolean midDecode ()
    {
        int saveRem = msgStartIndex;

        /*
         * Try to get GDL-90 message starting at the 0x7E.
         * Put cooked message in tBuf and return its length in len.
         */
        int len = getMessage ();

        /*
         * If GDL-90 message had a bad checksum or runt length,
         * don't increment other pointers cuz there could be a good
         * other message before end of bad GDL-90 message.
         */
        if (len > 0) {

            /*
             * Complete valid GDL-90 message found.
             * Make sure other pointers point past end of this message.
             */
            topDecoder.validMessageEndsAt (msgStartIndex);

            /*
             * Process the GDL-90 message.
             */
            try {
                processMessage (topDecoder.tBuf, len);
            } catch (Exception e) {
                topDecoder.mReporter.adsbGpsLog ("error processing GDL-90 message", e);
            }
        }

        return msgStartIndex > saveRem;
    }

    /**
     * Get message from buffer and remove it.
     * We can assume msgStartIndex points to first 0x7E cuz findStartIndex() was just called.
     * 0x7Es, escapes and CRC have been processed, checked and stripped.
     * @return < 0: no complete message in buffer
     *           0: runt, try again
     *        else: number of cooked bytes starting at dec.tBuf[0]
     */
    private int getMessage ()
    {
        byte[] mbuf = topDecoder.mBuf;
        int end = topDecoder.mInsert;

        // find ending 0x7E
        int i;
        for (i = msgStartIndex; ++ i < end;) {
            if (mbuf[i] == 0x7E) break;
        }
        if (i >= end) return -1;

        // check for 0x7E runt 0x7E
        // if so discard first 0x7E and runt then try again
        // be sure not to discard the second 0x7E cuz it might
        // be the start of the next packet
        if (i - msgStartIndex < 5) {
            msgStartIndex = i;
            return 0;
        }

        // have a message, strip escapes
        // i = points to terminating 0x7E
        byte[] tbuf = topDecoder.tBuf;
        int j = msgStartIndex;  // points to initial 0x7E
        int k = 0;             // where we put result
        while (++ j < i) {
            byte b = mbuf[j];
            if (b == 0x7D) {
                b = (byte) (mbuf[++j] ^ 0x20);
            }
            tbuf[k++] = b;
        }

        // mark message as removed from buffer
        msgStartIndex = ++ i;  // point just past terminating 0x7E

        // check resultant crc
        // we checked for runts above so we should always have at least 2 bytes
        int msb = tbuf[--k] & 0xFF;
        int lsb = tbuf[--k] & 0xFF;
        int inCrc = (msb << 8) + lsb;
        if (Crc.calcCrc (tbuf, 0, k) != inCrc) {
            topDecoder.mReporter.adsbGpsLog ("bad GDL-90 crc");
            return 0;
        }

        // return number of cooked message bytes at beginning of dec.tBuf
        return k;
    }

    /**
     * Process GDL-90 style message
     * @param bufin = array containing cooked bytes starting with message type byte
     * @param bufln = length of message in bufin array excluding CRC, escapes, 0x7Es
     */
    private void processMessage (byte[] bufin, int bufln)
    {
        Reporter reporter = topDecoder.mReporter;
        int type = bufin[0] & 0xFF;
        switch (type) {
            case HEARTBEAT:
                reporter.adsbGpsInstance ("gdl90-heartbeat");
                HeartbeatMessage.parse (this, bufin, bufln);
                gotOwnGeoAltLastCycle = gotOwnGeoAltThisCycle;
                gotOwnGeoAltThisCycle = false;
                break;
                
            case UPLINK:
                reporter.adsbGpsInstance ("gdl90-uplink");
                UplinkMessage.parse (bufin, bufln, reporter);
                break;
                
            case OWNSHIP: {
                reporter.adsbGpsInstance ("gdl90-ownship");
                decodeOwnshipTraffic (bufin, bufln);

                // it always gives us latitude and longitude
                ownLatitude  = dot_lat;
                ownLongitude = dot_lon;

                // it might give us our speed and track
                if (!Double.isNaN (dot_speed)) ownSpeed   = dot_speed;
                if (!Double.isNaN (dot_track)) ownHeading = dot_track;

                // if it gives us altitude, it was derived from pressure altitude
                // and a nearby altimeter setting.  but if we are getting geometric
                // altitude messages, ignore this altitude and use geometric altitude.
                if (!Double.isNaN (dot_talt) && !gotOwnGeoAltLastCycle && !gotOwnGeoAltThisCycle) {
                    // 1) we were able to calculate true altitude from pressure altitude
                    // 2) we didn't get geom alt message last cycle so probably won't get one this cycle
                    // 3) we haven't received a geom alt message this cycle either
                    // ... so this altitude is the best we are going to get
                    ownTrueAlt = dot_talt;
                }

                // report ownship info if we have everything
                maybeReportOwnshipInfo ();
                break;
            }

            case OWNSHIP_GEOMETRIC_ALTITUDE: {
                reporter.adsbGpsInstance ("gdl90-geoalt");

                /*
                 *  bytes 1-2 are the altitude
                 *  16-bit signed integer in units of 5ft MSL
                 */
                int talt = (bufin[1] << 8) + (bufin[2] & 0xFF);

                /*
                 * See if it is valid.
                 * Must be within 328 feet (100 metres).
                 */
                int vfom = ((bufin[3] & 0xFF) << 8) + (bufin[4] & 0xFF);
                if (vfom <= 100) {
                    ownTrueAlt = talt * 5;
                    gotOwnGeoAltThisCycle = true;
                    maybeReportOwnshipInfo ();
                }
                break;
            }

            case TRAFFIC_REPORT:
                reporter.adsbGpsInstance ("gdl90-traffic");
                decodeOwnshipTraffic (bufin, bufln);
                reporter.adsbGpsTraffic (heartbeatTime,
                        dot_talt, dot_track, dot_lat, dot_lon,
                        dot_speed, dot_climb, dot_addr, dot_call);
                break;
                
            case BASIC_REPORT:
                reporter.adsbGpsInstance ("gdl90-basic");
                TopDecoder.commonBasicLongReport (heartbeatTime, bufin, bufln, reporter);
                break;
                
            case LONG_REPORT:
                reporter.adsbGpsInstance ("gdl90-long");
                TopDecoder.commonBasicLongReport (heartbeatTime, bufin, bufln, reporter);
                break;

            case STRATUX_AHRS_REPORT:
                reporter.adsbGpsInstance ("gdl90-stratux-ahrs");
                stratuxAHRSReport (bufin, bufln, reporter);
                break;

            case STRATUX_STATUS:
                reporter.adsbGpsInstance ("gdl90-stratux-status");
                stratuxAhrsValid = (bufin[13] & 0x04) != 0;
                break;

            case DEVICE_REPORT:
                reporter.adsbGpsInstance ("gdl90-device");
                DeviceReportMessage.parse (bufin, bufln, reporter);
                break;

            case STRATUX_HEARTBEAT:
                reporter.adsbGpsInstance ("gdl90-stratux-heartbeat");
                stratuxAhrsValid = (bufin[1] & 0x01) != 0;
                break;

            default:
                reporter.adsbGpsInstance ("gdl90-" + type);
                break;
        }
    }

    /**
     * Decode message from stratux makeGDL90AHRSReport()
     */
    private void stratuxAHRSReport (byte[] bufin, int bufln, Reporter reporter)
    {
        if (bufln < 10) return;
        if ((bufin[1] != 0x45) || (bufin[2] != 0x01) || (bufin[3] != 0x00)) return;

        if (stratuxAhrsValid) {
            double bank  = ((bufin[4] << 8) + (bufin[5] & 0xFF)) / 10.0;
            double pitch = ((bufin[6] << 8) + (bufin[7] & 0xFF)) / 10.0;
            double headg = ((bufin[8] << 8) + (bufin[9] & 0xFF)) / 10.0;
            reporter.adsbGpsAHRS (bank, headg, pitch, System.currentTimeMillis ());
        }
    }

    /**
     * If we have gathered true altitude and lat/lon since last heartbeat,
     * we are ready to report ownship position
     * The HEARTBEAT message must indicate positioning is valid
     * The OWNSHIP_GEOMETRIC_ALTITUDE message gives us true altitude
     * The OWNSHIP message gives us lat/lon, optionally speed & heading
     */
    private void maybeReportOwnshipInfo ()
    {
        if (heartbeatPosVal && !Double.isNaN (ownTrueAlt) && !Double.isNaN (ownLatitude)) {
            topDecoder.mReporter.adsbGpsOwnship (heartbeatTime, ownTrueAlt,
                    ownHeading, ownLatitude, ownLongitude, ownSpeed);
            ownTrueAlt  = Double.NaN;
            ownLatitude = Double.NaN;
        }
    }

    /**
     * Decode common Ownship/Traffic message info.
     */
    private double  dot_climb;  // feet per minute
    private double  dot_lat;    // degrees
    private double  dot_lon;    // degrees
    private double  dot_speed;  // knots
    private double  dot_talt;   // feet MSL
    private double  dot_track;  // degrees
    private int    dot_addr;   // <26:34>: address type; <23:00>: icao address
    private String dot_call;

    private void decodeOwnshipTraffic (byte[] msg, int len)
    {
        if (len < 28) throw new IllegalArgumentException ();

        /*
         * upper nibble of first byte is the traffic alert mStatus
         */
        //int mStatus = (msg[1] & 0xF0) >> 4;

        /*
         * lower nibble of first byte is the address type:
         * 0 = ADS-B with ICAO address
         * 1 = ADS-B with self-assigned address
         * 2 = TIS-B with ICAO address
         * 3 = TIS-B with track file ID
         * 4 = surface vehicle
         * 5 = ground station beacon
         * 6-15 = reserved
         */
        int addressType = msg[1] & 0x0F;

        /*
         * next three bytes are the traffic's ICAO address
         */
        int icaoAddress = ((msg[2] << 16) & 0xFF) + ((msg[3] << 8) & 0xFF) + (msg[4] & 0xFF);

        /*
         * Merge them together for a single output value.
         */
        dot_addr = (addressType << 24) + icaoAddress;

        /*
         * Lon/lat
         */
        dot_lat = calculateDegrees (msg[5], msg[6], msg[7]);
        dot_lon = calculateDegrees (msg[8], msg[9], msg[10]);

        /*
         * Altitude
         */
        dot_talt = Double.NaN;
        int presalt = ((msg[11] & 0xFF) << 4) + ((msg[12] & 0xF0) >> 4);
        if (presalt != 0xFFF) {
            dot_talt = topDecoder.mReporter.adsbGpsPalt2Talt (
                    dot_lat, dot_lon, presalt * 25 - 1000);
        }

        /*
         * Misc.
         */
        //boolean mIsAirborne = (msg[12] & 0x08) != 0;
        //boolean mIsExtrapolated = (msg[12] & 0x04) != 0;
        //int mTrackType = msg[12] & 0x03;

        /*
         * Quality
         */
        //int mNIC = ((msg[13] & 0xF0) >> 4) & 0x0F;
        //int mNACP = msg[13] & 0x0F;

        /*
         * Horizontal Velocity (really just speed, knots).
         */
        int speed = ((msg[14] & 0xFF) << 4) + ((msg[15] & 0xF0) >> 4);
        dot_speed = (speed == 0xFFF) ? Double.NaN : speed;

        /*
         * Next 12 bits are vertical velocity in units of 64 fpm. (0x800 = unknown)
         */
        int climb = ((msg[15] & 0x0F) << 8) + (msg[16] & 0xFF);
        dot_climb = (climb == 0x800) ? Double.NaN : climb * 64;

        /*
         * Track/heading
         */
        double heading = (msg[17] & 0xFF) * (360.0 / 256);
        switch (msg[12] & 0x03) {
            case 1: {  // true track
                dot_track = heading;
                break;
            }
            case 0:    // invalid
            case 2:    // magnetic heading
            case 3: {  // true heading
                // we only handle track, not heading
                dot_track = Double.NaN;
                break;
            }
        }

        /*
         * next 8 bytes are callsign
         */
        byte[] callsign = new byte[8];
        callsign[0] = msg[19];
        callsign[1] = msg[20];
        callsign[2] = msg[21];
        callsign[3] = msg[22];
        callsign[4] = msg[23];
        callsign[5] = msg[24];
        callsign[6] = msg[25];
        callsign[7] = msg[26];
        dot_call = new String (callsign).trim ();

        /*
         * next 4 bits are emergency/priority code
         */
        //emergencyPriorityCode = ((int)msg[27] & 0xF0) >> 4;
    }

    /**
     * Convert a three-byte degree value to floatingpoint.
     * Format used by Ownship and Traffic Report messages.
     */
    private static double calculateDegrees (byte highByte, byte midByte, byte lowByte)
    {
        int position;

        position   = highByte;  // leave sign-extension alone
        position <<= 8;
        position  |= (midByte & 0xFF);
        position <<= 8;
        position  |= (lowByte & 0xFF);

        return position * (360.0 / (1 << 24));
    }
}
