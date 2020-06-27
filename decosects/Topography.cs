//    Copyright (C) 2015, Mike Rieker, Beverly, MA USA
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

/**
 * Get elevation of a given lat,lon in feet MSL.
 */

using System;
using System.IO;

public class Topography {

    // Data is 'grid registered', ie, value covers area centered on lat/lon minute,
    // so is valid when rounding the given lat/lon to nearest minute.
    public static double GetElevFt (double lat, double lon)
    {
        while (lon < -180.0) lon += 360.0;
        while (lon >= 180.0) lon -= 360.0;

        int latmin = (int) Math.Round (lat * 60.0);
        int lonmin = (int) Math.Round (lon * 60.0);

        int latdeg = latmin / 60;
        int londeg = lonmin / 60;

        latmin -= latdeg * 60;
        lonmin -= londeg * 60;

        if (latmin < 0) { latmin += 60; -- latdeg; }
        if (lonmin < 0) { lonmin += 60; -- londeg; }

        string toponame = "datums/topo/" + latdeg + "/" + londeg;
        Stream topostream = File.Open (toponame, FileMode.Open);

        byte[] topobytes = new byte[7200];
        int rc = topostream.Read (topobytes, 0, 7200);
        if (rc != 7200) throw new Exception ("only read " + rc + " bytes of 7200");

        short[] toposhorts = new short[3600];
        int j = 0;
        for (int i = 0; i < 3600; i ++) {
            int lo = topobytes[j++] & 0xFF;
            int hi = topobytes[j++] & 0xFF;
            toposhorts[i] = (short) ((hi << 8) + lo);
        }

        topostream.Close ();

        return toposhorts[latmin*60+lonmin] * 3.28084;
    }
}
