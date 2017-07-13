/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.apps4av.avarehelper.connections;

import com.apps4av.avarehelper.gdl90.GDL90Decoder;
import com.apps4av.avarehelper.nmea.NMEADecoder;

/**
 * Take raw packets from communication link, accumulate bytes until we have a whole message,
 * figure out what format the messages are (dump978, GDL-90, NMEA), then process the message.
 * Repeat until no more messages in buffer.  Save remaining bytes to match with next incoming.
 *
 * AdsbGpsRThread.run()
 *  -> raw bytes -> TopDecoder.topDecode()
 *      -> dump978 messages -> MidDecode Dump978Decoder.midDecode()
 *          -> basic/long messages ->  commonBasicLongReport()
 *              -> Reporter.adsbGpsTraffic()
 *          -> uplink messages -> UplinkMessage.parse()
 *              -> Reporter.various()
 *      -> gdl90 messages -> MidDecoder GDL90Decoder.midDecode()
 *          -> similar to dump978
 *      -> nmea messages -> MidDecode NMEADecoder.midDecode()
 *          -> similar to dump978
 *
 * @author zkhan
 * mods mrieker
 *
 */
public class TopDecoder {
    public final static int BUFSIZE = 2048;
    private final static int MSIZ = BUFSIZE * 2;

    private final static char[] RAD40 = new char[] {
            '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F','G','H','I','J',
            'K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',' ',' ',' ',' '
    };

    public final Reporter mReporter;        // where to push final message decoding
    public final byte[] mBuf = new byte[MSIZ];     // incoming byte stream data
    public final byte[] tBuf = new byte[BUFSIZE];  // temp 'cooked' data
    public int mInsert;            // where to insert incoming data on end of mBuf

    // table containing list of message formats we handle
    private final MidDecoder[] midDecoders = new MidDecoder[] {
            new Dump1090Decoder (this),
            new Dump978Decoder (this),
            new GDL90Decoder (this),
            new NMEADecoder (this)
    };

    public TopDecoder (Reporter reporter)
    {
        mReporter = reporter;
    }

    /**
     * Accumulate the given bytes then decode as many complete messages as
     * there are in the input buffer.
     * @param len = length of bytes just received starting at mBuf[mInsert]
     */
    public void topDecode (int len)
    {
        /*
         * Invariant at this point:
         *    if mBuf empty, mInsert = midDecoders[].msgStartIndex = 0
         *    else, mInsert > 0
         *         and each of midDecoders[].msgStartIndex either points to its start char
         *             (ie, is < mInsert) or points to the end of mBuf (ie, is == mInsert)
         */

        // point to end of newly received bytes
        mInsert += len;

        /*
         * Process as many completed messages as we can.
         */
        //noinspection StatementWithEmptyBody
        while (processAMessage ()) { }

        // (entry invariant still holds) //

        /*
         * Make sure we give our caller some room to read more data into.
         */
        if (mInsert >= MSIZ) {

            /*
             * Find start of good data, ie, first of all the start characters.
             */
            int good = mInsert;
            for (MidDecoder md : midDecoders) {
                if (good > md.msgStartIndex) good = md.msgStartIndex;
            }

            /*
             * If there is no empty space at beg of buffer, not much we can do.
             */
            if (good <= 0) {
                mReporter.adsbGpsLog ("GPS/ADS-B receive buffer overflow, resetting buffer");
                mInsert = 0;
                for (MidDecoder md : midDecoders) {
                    md.msgStartIndex = 0;
                }
            } else {

                /*
                 * Move existing data up to make room at end.
                 */
                mInsert -= good;
                System.arraycopy (mBuf, good, mBuf, 0, mInsert);
                for (MidDecoder md : midDecoders) {
                    md.msgStartIndex -= good;
                }
            }
        }

        // (entry invariant holds at this point once again)     //
        // (this is required so it will hold when called again) //
    }

    /**
     * Try to process a single message out of the input buffer.
     * @return true: some bytes were processed, loop back to try again
     *        false: nothing removed, we need more input before trying again
     */
    private boolean processAMessage ()
    {
        // (entry invariant may not hold at this point) //

        /*
         * Skip msgStartIndex pointers forward to their respective start chars.
         * Point to end of input if not found.
         */
        boolean anyFound = false;
        for (MidDecoder md : midDecoders) {
            anyFound |= md.findStartIndex ();
        }

        /*
         * If buffer completely empty, reset indices so we get lots of contiguous space.
         */
        if (!anyFound) {
            mInsert = 0;
            for (MidDecoder md : midDecoders) {
                md.msgStartIndex = 0;
            }
            // (entry invariant restored, case of empty buffer) //
            return false;
        }

        // (entry invariant restored, case of non-empty buffer) //

        /*
         * Sort factories by assending msgStartIndex, ie, whichever start
         * char comes first should be first in the list.  Use a bubble
         * sort cuz most likely same order every time as we are usually
         * dealing with just one message category and the others will
         ^ always point just past the end of the input.
         */
        boolean swapped;
        int endlimit = midDecoders.length;
        do {
            -- endlimit;
            swapped = false;
            MidDecoder md0 = midDecoders[0];
            for (int i = 0; i < endlimit; i ++) {
                MidDecoder md1 = midDecoders[i+1];
                if (md0.msgStartIndex > md1.msgStartIndex) {
                    midDecoders[i]   = md1;
                    midDecoders[i+1] = md0;
                    swapped = true;
                    md1 = md0;
                }
                md0 = md1;
            }
        } while (swapped && (endlimit > 1));

        /*
         * Try to process whichever is lower first followed by the rest.
         */
        for (MidDecoder md : midDecoders) {

            // if reached end of input buffer, there are no complete messages in input buffer
            // so we need to wait for more bytes received for hope of getting a complete message
            if (md.msgStartIndex >= mInsert) break;

            // some start char exists in input buffer, try to remove and process message
            // if it removed a message, loop back to find the next message
            if (md.midDecode ()) {
                return true;
            }

            // it didn't find a complete message, so ignore that handler for now
            // and try to find another handler that can find a complete message
        }

        /*
         * Tried all factories and none found a valid message.
         * Don't come back until we have received more input.
         */
        return false;
    }

    /**
     * One of the message factories found a valid message.
     * Move all the other pointers just past the found message.
     * @param end = just past end of the found valid message
     */
    public void validMessageEndsAt (int end)
    {
        for (MidDecoder md : midDecoders) {
            if (md.msgStartIndex < end) md.msgStartIndex = end;
        }
    }

    /*********************************************************\
     *  Utility functions for mid and bottom-level decoders  *
    \*********************************************************/

    /**
     * Convert hexadecimal ascii character in byte to binary equivalent.
     */
    public static int hexDigit (byte b)
    {
        if ((b >= '0') && (b <= '9')) return b - '0';
        b |= 'a' - 'A';
        if ((b >= 'a') && (b <= 'f')) return b - ('a' - 10);
        return -1;
    }

    /**
     * Decode common parts of Basic and Long reports then report traffic.
     * @param time     = latest GDL-90 heartbeat time
     * @param msg      = incoming message including GDL-90 message type and time-of-reception
     * @param len      = length of incoming message
     * @param reporter = where to report traffic to
     */
    public static void commonBasicLongReport (long time, byte[] msg, int len, Reporter reporter)
    {
        if (len < 22) throw new IllegalArgumentException ();

        int type = (msg[4] & 0xFF) >> 3;

        // message may include a time-of-reception field
        // it is 80nS offset from heartbeat time
        int timeOfReception;
        timeOfReception  = (msg[3] & 0xFF) << 16;
        timeOfReception += (msg[2] & 0xFF) << 8;
        timeOfReception += (msg[1] & 0xFF);
        if (timeOfReception != 0xFFFFFF) time += timeOfReception / 12500;

        // 24-bit ICAO address with address qualifier on top
        int mIcaoAddress;
        mIcaoAddress  = (msg[4] & 0x07) << 24;  // <26:24> = address qualifier bits
        mIcaoAddress += (msg[5] & 0xFF) << 16;  // <23:00> = 24 ICAO address bits
        mIcaoAddress += (msg[6] & 0xFF) <<  8;
        mIcaoAddress += (msg[7] & 0xFF);

        // latitude and longitude
        float mLat = calculateLatDegrees (msg, 8);
        float mLon = calculateLonDegrees (msg, 8);

        // altitude
        float altitude = Float.NaN;
        int codedAltitude;
        codedAltitude  = (msg[14] & 0xFF) << 4;
        codedAltitude += (msg[15] & 0xF0) >> 4;
        if (codedAltitude != 0) {
            // assume coded altitude is true (geometric)
            altitude = codedAltitude * 25 - 1025;
            // but maybe it is pressure altitude
            if ((msg[13] & 0x01) == 0) {
                altitude = reporter.adsbGpsPalt2Talt (mLat, mLon, altitude);
            }
        }

        // heading, speed, climb
        float mHeading, mSpeed, mVertVelocity;
        boolean airborne = (msg[16] & 0x80) == 0;
        if (airborne) {
            int vel;

            boolean supersonic = (msg[16] & 0x40) != 0;

            float mNortherlyVelocity = Float.NaN;
            vel = ((msg[16] & 0x1F) << 6) | ((msg[17] & 0xFC) >> 2);
            boolean northVelocityValid = (vel & 0x3FF) != 0;
            if (northVelocityValid) {
                mNortherlyVelocity = (vel & 0x3FF) - 1;
                if ((vel & 0x400) != 0) mNortherlyVelocity = -mNortherlyVelocity;
                if (supersonic) mNortherlyVelocity *= 4;
            }

            float mEasterlyVelocity = Float.NaN;
            vel = ((msg[17] & 0x03) << 9) | ((msg[18] & 0xFF) << 1) | ((msg[19] & 0x80) >> 7);
            boolean eastVelocityValid = (vel & 0x3FF) != 0;
            if (eastVelocityValid) {
                mEasterlyVelocity = (vel & 0x3FF) - 1;
                if ((vel & 0x400) != 0) mEasterlyVelocity = -mEasterlyVelocity;
                if (supersonic) mEasterlyVelocity *= 4;
            }

            if (northVelocityValid && eastVelocityValid) {
                mHeading = (float) Math.toDegrees (Math.atan2 (mEasterlyVelocity, mNortherlyVelocity));
                mSpeed   = (float) Math.hypot (mEasterlyVelocity, mNortherlyVelocity);
            } else {
                mHeading = Float.NaN;
                mSpeed   = Float.NaN;
            }

            // vertical velocity (ft/min)
            //  boolean vertVelocitySourceisBarometric = ((int)msg[19] & 0x40) != 0
            int verticalRate;
            verticalRate  = (msg[19] & 0x1F) << 4;
            verticalRate += (msg[20] & 0xF0) >> 4;
            if (verticalRate != 0) {
                mVertVelocity = verticalRate * 64 - 64;
                if ((msg[19] & 0x20) != 0) {
                    mVertVelocity = - mVertVelocity;
                }
            } else {
                mVertVelocity = Float.NaN;
            }
        } else {
            int vel;
            vel  = (msg[16] & 0x0F) << 6;
            vel += (msg[17] & 0xFC) >> 2;
            if (vel != 0) {
                mSpeed = vel - 1;
            } else {
                mSpeed = Float.NaN;
            }

            // msg[17] & 0x03 == 3 : TRUE heading  << assume this
            //                   2 : MAGNETIC heading
            //                   1 : ?
            //                   0 : ?
            int tracking = ((msg[18] & 0xFF) << 1) | ((msg[19] & 0x80) >> 7);
            mHeading = tracking * (360.0F / 512);

            // assume things on ground neither climb nor descend
            mVertVelocity = 0.0F;
        }

        // aircraft callsign
        String mCallSign = "";
        if ((type == 1) || (type == 3)) {
            char[] callsign = new char[8];
            int v0 = ((msg[21] & 0xFF) << 8) | (msg[22] & 0xFF);
            callsign[0] = RAD40[(v0 / 40) % 40];
            callsign[1] = RAD40[v0 % 40];
            int v1 = ((msg[23] & 0xFF) << 8) | (msg[24] & 0xFF);
            callsign[2] = RAD40[(v1 / 1600) % 40];
            callsign[3] = RAD40[(v1 / 40) % 40];
            callsign[4] = RAD40[v1 % 40];
            int v2 = ((msg[25] & 0xFF) << 8) | (msg[26] & 0xFF);
            callsign[5] = RAD40[(v2 / 1600) % 40];
            callsign[6] = RAD40[(v2 / 40) % 40];
            callsign[7] = RAD40[v2 % 40];
            mCallSign = new String (callsign).trim ();
            if ((msg[30] & 0x02) == 0) mCallSign += ":SQUAWK";
        }

        // emit traffic report
        reporter.adsbGpsTraffic (time, altitude, mHeading, mLat,
                mLon, mSpeed, mVertVelocity, mIcaoAddress, mCallSign);
    }

    /**
     * Convert a three-byte latitude degree value to floatingpoint.
     * Format used by Basic, Long, Uplink messages.
     * @param msg  = message containing bytes
     * @param skip = first byte of 6-byte lat/lon field
     * @return floatingpoint degrees latitude
     */
    public static float calculateLatDegrees (byte[] msg, int skip)
    {
        int lat;

        //noinspection PointlessArithmeticExpression
        lat   = msg[skip+0];  // leave sign-extension alone
        lat <<= 8;
        lat  += msg[skip+1] & 0xFF;
        lat <<= 8;
        lat  += msg[skip+2] & 0xFE;

        return lat * (180.0F / (1 << 24));
    }

    /**
     * Convert a three-byte longitude degree value to floatingpoint.
     * Format used by Basic, Long, Uplink messages.
     * @param msg  = message containing bytes
     * @param skip = first byte of 6-byte lat/lon field
     * @return floatingpoint degrees longitude
     */
    public static float calculateLonDegrees (byte[] msg, int skip)
    {
        int lon = 0 - (msg[skip+2] & 0x01);  // get top bit, sign extended

        lon <<= 8;
        lon  += msg[skip+3] & 0xFF;
        lon <<= 8;
        lon  += msg[skip+4] & 0xFF;
        lon <<= 8;
        lon  += msg[skip+5] & 0xFE;

        return lon * (180.0F / (1 << 24));
    }

    /**
     * Given a time since 000000Z, calculate the full time.
     * @param gpsMSecs = milliseconds since today 0000Z
     * @return milliseconds since 1970-01-01 0000Z
     */
    public static long GetFullTime (int gpsMSecs)
    {
        // what our clock says ms since 1970-01-01 000000Z
        long ourClock = System.currentTimeMillis ();

        // splice gpsMSecs onto ourClock to get full GPS time
        long gpsClock = ourClock / 86400000 * 86400000 + gpsMSecs;

        // ourClock could be May 15 23:59:59 and gpsMSecs could be 00:00:02
        // so the above splice ends up with May 15 00:00:02 which is one day behind
        int gpsHours = gpsMSecs / 3600000;
        int ourHours = (int) (ourClock / 3600000 % 24);
        if (ourHours - gpsHours >= 20) {
            gpsClock += 86400000;
        }

        // likewise, ourClock could be May 16 00:00:02 and gpsMSecs could be 23:59:59
        // so the above splice ends up with May 16 23:59:59 which is one day ahead
        if (gpsHours - ourHours >= 20) {
            gpsClock -= 86400000;
        }

        return gpsClock;
    }
}
