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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * 
 * @author zkhan
 * mods mrieker
 *
 */
public class FisBuffer {

    private final static int PRODUCT_TYPE_NOTAMS = 8;
    private final static int PRODUCT_TYPE_D_ATIS = 9;
    private final static int PRODUCT_TYPE_TWIP = 10;
    private final static int PRODUCT_TYPE_AIRMET = 11;
    private final static int PRODUCT_TYPE_SIGMET = 12;
    private final static int PRODUCT_TYPE_SUA = 13;
    private final static int PRODUCT_TYPE_NEXRAD_REGION = 63;
    private final static int PRODUCT_TYPE_NEXRAD_CONUS = 64;
    private final static int PRODUCT_TYPE_TEXT = 413;

    private final static TimeZone tzUtc = TimeZone.getTimeZone ("UTC");
    
    /**
     * Parse products out of the Fis
     */
    public static void makeProducts (byte buffer[], int offset, Reporter reporter)
    {
        int bufend = offset + 424;

        int frameLength;
        for (int i = offset; i + 2 <= bufend; i += frameLength) {

            /*
             * Get and check number of bytes in frame.
             */
            frameLength  = (buffer[i++] & 0xFF) << 1;
            frameLength += (buffer[i++] & 0x80) >> 7;
            
            if (frameLength == 0) break;
            if (frameLength + i > bufend) break;

            /*
             * Reserved frame ! = 0.
             */
            int frameType = buffer[i-1] & 0x0F;
            if (frameType != 0) break;

            /*
             * Create and output bitmap.
             */
            try {
	            buildProduct (buffer, i, frameLength, reporter);
            } catch (ArrayIndexOutOfBoundsException e) {
            	//XXX: Skip for now buffer parsing overflow errors
                reporter.adsbGpsLog ("Error parsing FIS product, buffer overflow! Please report this issue (specify ADSB unit type)", e);
            }
        }
    }

    private static void buildProduct (byte[] fisBuffer, int fisOffset, int fisLength,
                                      Reporter reporter)
    {
        // get current time (what we think time is)
        // chop off milliseconds cuz we only get seconds from message
        // so we shouldn't put in arbitrary milliseconds
        long nowBin = System.currentTimeMillis () / 1000 * 1000;

        Calendar time = new GregorianCalendar ();
        time.setTimeZone (tzUtc);
        time.setTimeInMillis (nowBin);

        BitInputStream s = new BitInputStream (fisBuffer, fisOffset, fisLength);

        /*
         * XXX:
         * Skip this. This over the air does not contain ADPU header.
         *   s.getBits(16);
         */

        boolean flagAppMethod = s.getBoolean ();
        boolean flagGeoLocator = s.getBoolean ();
        s.getBoolean (); /* Provider spec flag, discard */

        int productID = s.getBits(11);

        if(flagAppMethod) {
            s.getBits(8);
        }

        if(flagGeoLocator) {
            s.getBits(20);
        }

        boolean segFlag = s.getBoolean ();
        if (segFlag) {
            /*
             * XXX:
             * Implement this. Uncommon?
             */
            return;
        }

        int timeOpts = s.getBits(2);

        // 00 - No day, No sec
        // 01 - sec
        // 10 - day
        // 11 - day, sec
        int year  = time.get (Calendar.YEAR);
        int month = 0;
        int day   = 0;
        boolean explicitMonthDay = (timeOpts & 0x02) != 0;
        if (explicitMonthDay) {
            month = s.getBits(4);  // 1=january
            day   = s.getBits(5);  // 1=first day of month

            month = month - 1 + Calendar.JANUARY;

            // we could think it is Dec 31 but we are given Jan 1
            // so we need to advance current time by one year
            int nowMonth = time.get (Calendar.MONTH);
            if (nowMonth - month >= 9) {
                year ++;
            }

            // conversely, we could think it is Jan 1 but we are
            // given Dec 31, so back up current time by one year
            if (month - nowMonth >= 9) {
                -- year;
            }
        }

        // if seconds not given, use 0 so we don't have random second
        int hours = s.getBits (5);
        int mins  = s.getBits(6);
        int secs  = 0;
        if ((timeOpts & 0x01) != 0) {
            secs = s.getBits(6);
        }

        // if explicit month,day given, we don't want to back up or advance
        // the given month,day by a day
        if (!explicitMonthDay) {
            int nowHour = time.get (Calendar.HOUR_OF_DAY);

            // we could think it is 2359Z but the data was received for 0001Z
            // so we can't just use the year/month/day in time, we have to
            // advance it one day first
            if (nowHour - hours >= 18) {
                time.add (Calendar.HOUR_OF_DAY, 24);
            }

            // conversely we could think it is 0001Z but the data was received
            // for 2359Z so we have to back time up one day first
            if (hours - nowHour >= 18) {
                time.add (Calendar.HOUR_OF_DAY, -24);
            }

            // get the possibly modified year/month/day
            year  = time.get (Calendar.YEAR);
            month = time.get (Calendar.MONTH);
            day   = time.get (Calendar.DAY_OF_MONTH);
        }

        // modify the 'now' time with the date/time values supplied in the message
        // or defaults from the current date/time
        time.set (year, month, day, hours, mins, secs);
        long timems = time.getTimeInMillis ();

        int totalRead = s.totalRead ();

        switch (productID) {
            case PRODUCT_TYPE_NOTAMS:
            case PRODUCT_TYPE_D_ATIS:
            case PRODUCT_TYPE_TWIP:
            case PRODUCT_TYPE_AIRMET:
            case PRODUCT_TYPE_SIGMET:
            case PRODUCT_TYPE_SUA:
            default: {
                reporter.adsbGpsLog ("product " + productID + " not implemented");
                break;
            }
            case PRODUCT_TYPE_NEXRAD_REGION: {
                Id6364Product.parse (timems, fisBuffer, fisOffset + totalRead,
                        fisOffset + fisLength, reporter, false);
                break;
            }
            case PRODUCT_TYPE_NEXRAD_CONUS: {
                Id6364Product.parse (timems, fisBuffer, fisOffset + totalRead,
                        fisOffset + fisLength, reporter, true);
                break;
            }
            case PRODUCT_TYPE_TEXT: {
                Id413Product.parse (timems, fisBuffer, fisOffset + totalRead,
                        fisOffset + fisLength, reporter);
                break;
            }
        }
    }
}
