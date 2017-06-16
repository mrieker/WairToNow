/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.apps4av.avarehelper.nmea;

import com.apps4av.avarehelper.connections.Reporter;
import com.apps4av.avarehelper.connections.TopDecoder;
import com.apps4av.avarehelper.connections.MidDecoder;

/**
 * 
 * @author zkhan
 * mods mrieker
 *
 * http://www.gpsinformation.org/dale/nmea.htm#RMC
 *
 */
public class NMEADecoder extends MidDecoder {
    public float ownHeading   = Float.NaN;
    public float ownLatitude  = Float.NaN;
    public float ownLongitude = Float.NaN;
    public float ownSpeed     = Float.NaN;
    public float ownTrueAlt   = Float.NaN;
    public long  reportTime   = -1;

    private GSVMessage gsvMessage = new GSVMessage (this);
    public TopDecoder topDecoder;

    public NMEADecoder (TopDecoder td)
    {
        topDecoder = td;
    }

    /**
     * See if current incoming message buffer contains our start char anywhere.
     */
    @Override  // MidDecoder
    public boolean findStartIndex ()
    {
        byte[] buf = topDecoder.mBuf;
        int end = topDecoder.mInsert;
        int i;
        for (i = msgStartIndex; i < end; i ++) {
            if (buf[i] == '$') break;
        }
        msgStartIndex = i;
        return i < end;
    }

    /**
     * See if current incoming message buffer contains a whole message.
     * If so, remove and process it.
     */
    @Override  // MidDecoder
    public boolean midDecode ()
    {
        int saveRem = msgStartIndex;

        /*
         * Try to get NMEA message starting at the '$'.
         */
        String str = getMessage ();

        /*
         * If NMEA message had a bad checksum or runt length,
         * don't increment other pointers cuz there could be a good
         * other message before end of bad NMEA message.
         */
        if ((str != null) && (str.length () > 0)) {

            /*
             * Complete valid NMEA message found.
             * Make sure other pointers point past end of this message.
             */
            topDecoder.validMessageEndsAt (msgStartIndex);

            /*
             * Process NMEA message.
             */
            try {
                processMessage (str);
            } catch (Exception e) {
                topDecoder.mReporter.adsbGpsLog ("error processing NMEA message", e);
            }
        }

        return msgStartIndex > saveRem;
    }

    /**
     * Get message from buffer and remove it.
     * We can assume msgStartIndex points to '$' cuz findStartIndex() was just called.
     * @return null: buffer contains partial message, need more input
     *           "": bad checksum etc, call again to get next message
     *         else: valid message including '$' but not trailing checksum
     */
    private String getMessage ()
    {
        byte[] buf = topDecoder.mBuf;
        int end = topDecoder.mInsert;

        // find terminating newline
        // and if we see another '$', toss the old one away
        int j = msgStartIndex;
        int i;
        for (i = j; ++ i < end;) {
            byte c = buf[i];
            if ((c == '\n') || (c == '\r')) break;
            if (c == '$') {
                msgStartIndex = i;
                return "";
            }
        }
        if (i >= end) return null;

        // strip message from buffer
        msgStartIndex = ++ i;

        // strip trailing whitespace
        while ((i > j) && (buf[i-1] <= ' ')) -- i;

        // j = points to '$'
        // i = points just past checksum digits
        if (i - j < 6) return "";

        // checksum is last two hex digits preceded by a '*'
        int lodig = TopDecoder.hexDigit (buf[--i]);
        int hidig = TopDecoder.hexDigit (buf[--i]);
        if ((lodig | hidig) < 0) return "";
        if (buf[--i] != '*') return "";

        // xor everything between '$' and '*' exclusive
        byte xor = 0;
        for (int k = j; ++ k < i;) {
            xor ^= buf[k];
        }
        if ((xor & 0xFF) != (hidig * 16 + lodig)) return "";

        // valid sentence, return string
        return new String (buf, j, i - j);
    }

    /**
     * Process message depending on the message type.
     */
    private void processMessage (String data)
    {
        Reporter reporter = topDecoder.mReporter;
        String[] tokens = data.split (",");
        String type = tokens[0];
        reporter.adsbGpsInstance ("nmea-" + type);
        switch (type.substring (3)) {
            case "GGA": {
                GGAMessage.parse (tokens, this);
                break;
            }
            case "GSV": {
                gsvMessage.parse (tokens);
                break;
            }
            case "PWR": {
                PWRMessage.parse (tokens, reporter);
                break;
            }
            case "RMC": {
                RMCMessage.parse (tokens, this);
                break;
            }
            case "RTM": {
                RTMMessage.parse (tokens, reporter);
                break;
            }
            case "VTG": {
                VTGMessage.parse (tokens, this);
                break;
            }
        }
    }

    /**
     * Heading, speed, altitude come split in various messages (GGA, RMC, VTG),
     * so we have to receive all data before reporting ownship info.
     * @param time = ms since 1970-01-01 0000Z
     * @param hdg  = heading degrees
     * @param spd  = speed knots
     * @param alt  = true altitude feet MSL
     */
    public void maybeReportOwnshipInfo (long time, float hdg, float spd, float alt)
    {
        // message pairs have same time
        // so if this is first of a different time, reset everything
        if (reportTime != time) {
            reportTime = time;
            ownHeading = Float.NaN;
            ownSpeed   = Float.NaN;
            ownTrueAlt = Float.NaN;
        }

        // now fill in what this one is supplying
        if (!Float.isNaN (hdg)) ownHeading = hdg;
        if (!Float.isNaN (spd)) ownSpeed   = spd;
        if (!Float.isNaN (alt)) ownTrueAlt = alt;

        // if we now know everything, report current location
        if (!Float.isNaN (ownSpeed) && !Float.isNaN (ownTrueAlt)) {
            topDecoder.mReporter.adsbGpsOwnship (
                    reportTime,
                    ownTrueAlt,
                    ownHeading,
                    ownLatitude,
                    ownLongitude,
                    ownSpeed);
        }
    }

    /**
     * Convert the hhmmss.ttt string to number of milliseconds.
     */
    public static int milliseconds (String str)
    {
        int hhmmssttt = Math.round (Float.parseFloat (str) * 1000.0F);
        int hours     = hhmmssttt / 10000000;
        int minutes   = (hhmmssttt / 100000) % 100;
        int msecs     = hhmmssttt % 100000;
        return ((hours * 60) + minutes) * 60000 + msecs;
    }

    /**
     * Convert the bastardized lat/lon field to degrees.
     * @param num = dddmm.mmm string
     * @param dir = which direction from record
     * @param pos = what dir is positive ("N" or "E")
     * @param neg = what dir is negative ("S" or "W")
     * @return degrees
     */
    public static float degrees (String num, String dir, String pos, String neg)
    {
        float degflt = Float.parseFloat (num) / 100.0F;
        float degint = (int) degflt;
        degflt -= degint;
        degint += degflt * (100.0F / 60.0F);
        if (dir.equals (neg)) degint = -degint;
        else if (!dir.equals (pos)) {
            throw new NumberFormatException ("bad direction " + dir);
        }
        return degint;
    }

    /**
     * Parse floating-point number allowing a default value if string is empty.
     */
    public static float parseFloat (String alt, float empty, float mult)
    {
        if (alt.length () == 0) return empty;
        return Float.parseFloat (alt) * mult;
    }
}
