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

/**
 * 
 * @author zkhan
 * mods mrieker
 *
 */
public class Id413Product {
    private static final char[] DLAC_CODE = {
        0x03, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B,
        0x4C, 0x4D, 0x4E, 0x4F, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,
        0x58, 0x59, 0x5A, 0x00, 0x09, 0x1e, 0x0a, 0x00, 0x20, 0x21, 0x22, 0x23,
        0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
        0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B,
        0x3C, 0x3D, 0x3E, 0x3F
    };

    public static void parse (long timems, byte[] msg, int beg, int end, Reporter reporter)
    {
        /*
         * Decode text: begins with @METAR, @TAF, @SPECI, @SUA, @PIREP, @WINDS
         */
        char[] text = new char[(end-beg)/3*4];
        int j = 0;
        for (int i = beg; i + 3 <= end; i += 3) {
            //noinspection PointlessArithmeticExpression
            int holder =
                    ((msg[i + 0] & 0xFF) << 24) +
                    ((msg[i + 1] & 0xFF) << 16) +
                    ((msg[i + 2] & 0xFF) <<  8);
            
            /*
             * 4 chars in 3 bytes
             */
            text[j++] = DLAC_CODE[((holder & 0xFC000000) >> 26) & 0x3F];
            text[j++] = DLAC_CODE[((holder & 0x03F00000) >> 20) & 0x3F];
            text[j++] = DLAC_CODE[((holder & 0x000FC000) >> 14) & 0x3F];
            text[j++] = DLAC_CODE[((holder & 0x00003F00) >>  8) & 0x3F];
        }

        if (j > 0) {
            String str = new String (text, 0, j);
            str = str.split ("\u001E")[0];
            str = str.replaceAll("\n\t[A-Z]{1}", "\n"); /* remove invalid chars after newline */

            // we get 'PIREP FINALRUNWAY ddhhmmZ aptid ...'
            // so convert it to 'PIREP aptid ddhhmmZ ...'
            // as the ... contains the phrase 'ON FINAL RUNWAY'
            // eg. 'PIREP FINALRUNWAY 271250Z KACK UA /OV ON FINAL RUNWAY 24/TM '
            //     '1250/FL005/TP B350/RM TOPS AT 500 FEET, LIGHTS AT 300 FEET, SMOOTH RIDE'
            if (str.startsWith ("PIREP FINALRUNWAY ")) {
                String[] split = str.split (" ", 5);
                if (split.length > 4) {
                    str = "PIREP " + split[3] + " " + split[2] + " " + split[4];
                }
            }

            // we get 'TAF COR' so make it 'TAF.COR'
            // this makes it like the 'TAF.AMD's we get
            if (str.startsWith ("TAF COR ")) {
                str = "TAF.COR" + str.substring (7);
            }

            // split off the type, location from the data
            // so the UI stuff can correlate it to an airport
            String[] parts = str.split (" ", 3);
            if (parts.length < 3) {
                reporter.adsbGpsLog ("runt Id413 <" + str + ">");
            } else {
                String type = parts[0];
                String loc  = parts[1];
                String data = parts[2];

                // sometimes we get K6B0 (which isn't a valid FAA-id or ICAO-id) instead of just 6B0
                if ((loc.length () > 0) && (loc.charAt (0) == 'K')) {
                    for (int i = loc.length (); -- i >= 0;) {
                        char c = loc.charAt (i);
                        if ((c >= '0') && (c <= '9')) {
                            loc = loc.substring (1);
                            break;
                        }
                    }
                }

                // send it on to the UI stuff
                reporter.adsbGpsMetar (timems, type, loc, data);
            }
        }
    }
}
