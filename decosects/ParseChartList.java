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

// wget https://jsoup.org/packages/jsoup-1.9.2.jar
// export CLASSPATH=.:jsoup-1.9.2.jar
// javac ParseChartList.java

// wget -q http://www.faa.gov/air_traffic/flight_info/aeronav/digital_products/vfr/ -O chartlist_all.htm
// java chartlist_all.htm sectional-files|tac-files|heli_files|grand_canyon_files|Caribbean $next28
// output is lines of 'effdate htmllink'

import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ParseChartList {

    public static void main (String[] args)
            throws Exception
    {
        String category = "/" + args[1] + "/";
        boolean next28 = Integer.parseInt (args[2]) != 0;

        Date todate = Calendar.getInstance ().getTime ();
        String today = new SimpleDateFormat ("yyyy").format (todate) +
                        new SimpleDateFormat ("MM").format (todate) +
                        new SimpleDateFormat ("dd").format (todate);

        // read chartlist_all.htm into memory
        StringBuilder sb = new StringBuilder ();
        BufferedReader br = new BufferedReader (new FileReader (args[0]), 4096);
        for (String st; (st = br.readLine ()) != null;) {
            sb.append (st);
        }
        br.close ();
        String doc = sb.toString ();

        String baseurl = "https://aeronav.faa.gov/visual/";

        for (int i = 0; (i = doc.indexOf (baseurl, ++ i)) >= 0;) {
            String url = doc.substring (i, doc.indexOf (">", i));
            if (url.contains (category)) {
                url = url.replace ("\"", "");
                String eff = url.substring (baseurl.length (), baseurl.length () + 10);   // mm-dd-yyyy
                eff = eff.substring (6, 10) + eff.substring (0, 2) + eff.substring (3, 5);
                if (next28 ^ (eff.compareTo (today) <= 0)) {
                    System.out.println (eff + " " + url);
                }
            }
        }
    }
}
