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

import com.outerworldapps.wairtonow.MyGpsSatellite;

import java.util.Collection;

/**
 * Used by AdsbGps message decoders to pass message decoding results to their caller.
 */
public interface Reporter {
    void adsbGpsLog (String message);
    void adsbGpsLog (String message, Exception e);

    void adsbGpsAHRS (
            float bank,
            float heading,
            float pitch,
            long time
    );

    void adsbGpsBattery (
            String batlevel
    );

    void adsbGpsInstance (
            String type
    );

    void adsbGpsMetar (
            long time,          // ms since 1970-01-01 00:00:00 UTC
            String type,
            String location,
            String data
    );

    void adsbGpsNexradClear (
            long time,          // ms since 1970-01-01 00:00:00 UTC
            boolean conus,
            int nblknums,
            int[] blknums
    );

    void adsbGpsNexradImage (
            long time,          // ms since 1970-01-01 00:00:00 UTC
            boolean conus,
            int blockno,
            int[] pixels
    );

    void adsbGpsOwnship (
            long time,          // ms since 1970-01-01 00:00:00 UTC
            float taltitude,    // feet MSL (TRUE altitude)
            float heading,      // degrees
            float latitude,     // degrees
            float longitude,    // degrees
            float speed         // knots
    );

    void adsbGpsSatellites (
            Collection<MyGpsSatellite> satellites
    );

    void adsbGpsTraffic (
            long time,          // ms since 1970-01-01 00:00:00 UTC
            float taltitude,    // feet MSL (TRUE altitude)
            float heading,      // degrees
            float latitude,     // degrees
            float longitude,    // degrees
            float speed,        // knots
            float climb,        // feet per minute
            int address,        // <27:24>:addr type; <23:00>:address
            String callsign
    );

    float adsbGpsPalt2Talt (
            float latitude,
            float longitude,
            float paltitude
    );
}
