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

import com.apps4av.avarehelper.connections.Reporter;

/**
 * 
 * @author zkhan
 * mods mrieker
 *
 */
public class DeviceReportMessage {
    
    /**
     *
     */
    public static void parse (byte[] msg, int len, Reporter reporter)
    {
        if (len < 6) throw new IllegalArgumentException ();

        /*
         * Battery status
         */
        int vbat;
        vbat = ((int)msg[1] & 0xFF) << 8;
        vbat += ((int)msg[2]) & 0xFF;
        double batLevel = (double)(vbat - 3500) / 600.0;
        if (batLevel > 1.0) {
            batLevel = 1.0;
        }
        if (batLevel < 0.0) {
            batLevel = 0.0;
        }
        
        /*
         * Charge
         */
        boolean isCharging = ( (msg[5] & 0x04) != 0);
        String batlevel = Long.toString (Math.round (batLevel * 100.0)) + '%';
        if (isCharging) batlevel += " charging";
        reporter.adsbGpsBattery (batlevel);
    }
}
