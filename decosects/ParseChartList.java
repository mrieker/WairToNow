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

// Extract download links for VFR charts from FAA webpage listing those charts

// javac ParseChartList.java

// java effyyyymmdd sectional-files|tac-files|heli_files|grand_canyon_files|Caribbean < ParseChartList.dat
// output is lines of 'htmllink'

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ParseChartList {

    public static void main (String[] args)
            throws Exception
    {
        String effyyyymmdd = args[0];
        String categ = "/" + args[1] + "/";

        String eff_mm_dd_yyyy = effyyyymmdd.substring (4, 6) + "-" + effyyyymmdd.substring (6, 8) + "-" + effyyyymmdd.substring (0, 4);

        BufferedReader br = new BufferedReader (new InputStreamReader (System.in));
        for (String line; (line = br.readLine ()) != null;) {
            if (line.startsWith (categ)) {
                System.out.println ("https://aeronav.faa.gov/visual/" + eff_mm_dd_yyyy + line);
            }
        }
        br.close ();
    }
}
