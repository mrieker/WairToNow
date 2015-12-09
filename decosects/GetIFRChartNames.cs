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
 * Parse out the chart downloading URL and chart name.
 *
 *    gmcs -debug -out:GetIFRChartNames.exe GetIFRChartNames.cs
 *    mono --debug GetIFRChartNames.exe 10-15-2015 < ifrcharts.htm
 *
    <tr>
      <td>
        <cfoutput>
          <a href="http://aeronav.faa.gov/enroute/10-15-2015/enr_l01.zip">
            ELUS1 Oct 15 2015     << effective date
          </a>
          <small>
            (ZIP)
          </small>
        </cfoutput>
      </td>
      <td>
        ELUS1 Dec 10 2015 
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
        string urlbase = "http://aeronav.faa.gov/enroute/";

        for (int i = 0; (i = htm.IndexOf ("<a href=\"" + urlbase, i)) > 0;) {
            i = htm.IndexOf (urlbase, i);
            int j = htm.IndexOf ("\">", i);
            string url = htm.Substring (i, j - i);
            int k = url.IndexOf ("/enr_");
            if (k >= 0) {
                Console.WriteLine (urlbase + effdate_mm + url.Substring (k));
                for (j += 2; htm[j] <= ' '; j ++) { }
                for (i = j; htm[i] > ' '; i ++) { }
                Console.WriteLine (htm.Substring (j, i - j));
            }
        }
    }
}
