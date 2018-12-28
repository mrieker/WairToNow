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
 *    mono --debug GetIFRChartNames.exe 10-15-2015 < ifrcharts.htm
 *
                    <tr>
                      <td>
                        ELUS1
                      </td>
                      <td>
                        Nov 10 2016
                        <br>
                        <cfoutput>
                          <a href="https://aeronav.faa.gov/enroute/11-10-2016/enr_l01.zip">
                            GEO-TIFF
                          </a>
                          <small>
                            (ZIP)
                          </small>
                        </cfoutput>
                        <br>
                        <cfoutput>
                          <a href="https://aeronav.faa.gov/enroute/11-10-2016/delus1.zip">
                            PDF
                          </a>
                          <small>
                            (ZIP)
                          </small>
                        </cfoutput>
                      </td>
                      <td>
                        Jan 05 2017
                        <br>
                        <cfoutput>
                          <a href="https://aeronav.faa.gov/enroute/01-05-2017/enr_l01.zip">
                            GEO-TIFF
                          </a>
                          <small>
                            (ZIP)
                          </small>
                        </cfoutput>
                        </cfoutput>
                        <br>
                        <cfoutput>
                          <a href="https://aeronav.faa.gov/enroute/01-05-2017/delus1.zip">
                            PDF
                          </a>
                          <small>
                            (ZIP)
                          </small>
                        </cfoutput>
                      </td>
                    </tr>
*/

using System;

public class GetIFRChartNames {
    public static void Main (string[] args)
    {
        string htm = "";
        string line;
        while ((line = Console.ReadLine ()) != null) htm += line;

        string effdate_mm = args[0];
        string urlbase = "https://aeronav.faa.gov/enroute/" + effdate_mm + "/";

        for (int i = 0; (i = htm.IndexOf ("<a href=\"" + urlbase, i)) > 0;) {
            i = htm.IndexOf (urlbase, i);
            int j = htm.IndexOf ("\">", i);
            string url = htm.Substring (i, j - i);
            string fnm = url.Substring (urlbase.Length);

            int k = htm.LastIndexOf ("<tr><td>", i) + 8;
            int m = htm.IndexOf ("</td>", k);
            string nam = htm.Substring (k, m - k).Replace (" ", "");

            if (fnm.StartsWith ("enr") && (nam.StartsWith ("EL") || nam.StartsWith ("Area") || (nam == "EPHI2"))) {
                Console.WriteLine (url);  // eg, https://aeronav.faa.gov/enroute/01-05-2017/enr_l01.zip
                Console.WriteLine (nam);  // eg, ELUS1
            }
        }
    }
}
