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
 * @brief Extracts airport identifiers (FAA format, not ICAO)
 *
 * gmcs -debug -out:GetAirportIDs.exe GetAirportIDs.cs
 * mono --debug GetAirportIDs.exe < APT.txt > ...
 */

// https://nfdc.faa.gov/xwiki -> automatic redirect
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/WebHome -> 56 Day NASR Subscription
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/56+Day+NASR+Subscription -> Current
// https://nfdc.faa.gov/xwiki/bin/view/NFDC/56DaySub-2015-06-25   (2015-06-25 = cycle start date)
// https://nfdc.faa.gov/webContent/56DaySub/2015-06-25/APT.zip

using System;
using System.IO;

public class GetAirportIDs {
    public static void Main (string[] args)
    {
        string line;
        while ((line = Console.ReadLine ()) != null) {
            if (line.Substring (14, 13) == "AIRPORT      ") {
                string faaid = line.Substring (27, 4).Trim ();
                Console.WriteLine (faaid);
            }
        }
    }
}
