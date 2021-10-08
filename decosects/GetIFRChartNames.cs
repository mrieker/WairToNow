//    Copyright (C) 2015,2016,2018 Mike Rieker, Beverly, MA USA
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
 * Parse out the chart downloading URL and chart name.
 *
 *    mcs -debug -out:GetIFRChartNames.exe GetIFRChartNames.cs
 *    mono --debug GetIFRChartNames.exe 10-15-2015
*/

using System;

public class GetIFRChartNames {
    public static void Main (string[] args)
    {
        string effdate_mm = args[0];
        string urlbase = "https://aeronav.faa.gov/enroute/" + effdate_mm + "/";

        for (int i = 1; i <= 36; i ++) {
            string url = urlbase + "enr_l" + ((i < 10) ? ("0" + i.ToString ()) : i.ToString ()) + ".zip";
            string nam = "ELUS" + i;
            Console.WriteLine (url);  // eg, https://aeronav.faa.gov/enroute/01-05-2017/enr_l01.zip
            Console.WriteLine (nam);  // eg, ELUS1
        }

        for (int i = 1; i <= 4; i ++) {
            string url = urlbase + "enr_akl0" + i.ToString () + ".zip";
            string nam = "ELAK" + i;
            Console.WriteLine (url);  // eg, https://aeronav.faa.gov/enroute/01-05-2017/enr_akl01.zip
            Console.WriteLine (nam);  // eg, ELAK1
        }

        Console.WriteLine (urlbase + "enr_a01.zip");
        Console.WriteLine ("Area1");
        Console.WriteLine (urlbase + "enr_a02.zip");
        Console.WriteLine ("Area2");

        Console.WriteLine (urlbase + "enr_p02.zip");
        Console.WriteLine ("EPHI2");
    }
}
