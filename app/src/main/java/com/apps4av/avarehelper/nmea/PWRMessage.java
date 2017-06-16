//    Copyright (C) 2017, Mike Rieker, Beverly, MA USA
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

package com.apps4av.avarehelper.nmea;

import com.apps4av.avarehelper.connections.Reporter;

/* https://github.com/Knio/pynmea2/issues/56
The PPWR sentence contains device specific information and looks like this:
$GPPWR,0876,0,0,0,0,00,F,0,97,1,3,000,00190108EEEE,0017E9B92122*74
• Element #1, 0876, is the battery voltage. Battery voltage is not valid while the device is charging.
• Element #5 is the charging status: 1 = charging, 0 = not charging.
 */

public class PWRMessage {
    public static void parse (String[] tokens, Reporter reporter)
    {
        String batlevel = tokens[1];
        if (tokens[5].charAt (0) != '0') batlevel += " charging";
        reporter.adsbGpsBattery (batlevel);
    }
}
