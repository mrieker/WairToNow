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
public class UplinkMessage {

    /**
     * Parse incoming message
     */
    public static void parse (byte[] msg, int len, Reporter reporter)
    {
        /*
         * First byte is GDL-90 message type UPLINK.
         * Next 3 bytes are GDL-90 80nS since heartbeat time,
         * Next 8 is UAT header
         * Rest of 424 is payload
         */
        if (len < 436) throw new IllegalArgumentException ();

        boolean applicationDataValid = (msg[10] & 0x20) != 0;
        if (!applicationDataValid) {
            return;
        }

        // ground station position
        //  boolean positionValid = (msg[9] & 0x01) != 0;
        //  double degLat = TopDecoder.calculateLatDegrees (msg, 4);
        //  double degLon = TopDecoder.calculateLonDegrees (msg, 4);

        /*
        // byte 6, bits 4-8: slot ID
        int slotID = msg[skip + 6] & 0x1f;

        // byte 7, bit 1-4: TIS-B site ID. If zero, the broadcasting station is not broadcasting TIS-B data
        int tisbSiteID = (msg[skip + 7] & 0xf0) >> 4;
        */

        /*
         * Now decode all.
         */
        // byte 12-432: application data (multiple iFrames).
        FisBuffer.makeProducts (msg, 12, reporter);
    }
}
