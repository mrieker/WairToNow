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
// java chartlist_all.htm grandCanyon|helicopter|sectional|terminalArea

import java.io.BufferedReader;
import java.io.FileReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

public class ParseChartList {
    public static void main (String[] args)
    {
        try {

            // read chartlist_all.htm into memory
            // convert the &ndash; to -
            StringBuilder sb = new StringBuilder ();
            BufferedReader br = new BufferedReader (new FileReader (args[0]), 4096);
            for (String st; (st = br.readLine ()) != null;) {
                sb.append (st.replace ("&ndash;", "-"));
            }
            br.close ();

            // parse it
            Document doc = Jsoup.parse (sb.toString ());

            // maybe dump it out for devel
            //printElement (doc, "");

            // #sectional > table.striped > tbody > tr:nth-child(1) > td:nth-child(1)
            // #terminalArea > table.striped > tbody > tr:nth-child(4) > td:nth-child(1)

            Element div   = doc.getElementById (args[1]);               // <DIV CLASS="sectional">
            Element table = getFirstChildWithTagName (div,   "table");  //   <TABLE>
            Element tbody = getFirstChildWithTagName (table, "tbody");  //     <TBODY>

            // step through the rows, one per chart
            for (int rowno = 0;; rowno ++) {
                Element tableRow;
                try {
                    tableRow = tbody.child (rowno);
                } catch (IndexOutOfBoundsException ioobe) {
                    break;
                }
                if (!tableRow.tagName ().equals ("tr")) continue;

                // the current <a href=...> is in column 1
                // if next28 is in effect, vfr_charts_download.sh will handle it
                Element tableCol = tableRow.child (1);
                String ahrefzip = getFirstAHrefZip (tableCol);
                System.out.println (ahrefzip);
            }
        } catch (Exception e) {
            System.err.println (e.toString ());
            e.printStackTrace (System.err);
        }
    }

    public static void printElement (Element elem, String indent)
    {
        System.out.println (indent + "<" + elem.tagName ());
        System.out.println (indent + "   " + elem.cssSelector () + ">");
        if (elem.hasText ()) {
            for (TextNode text : elem.textNodes ()) {
                System.out.println (indent + "     " + text.text ());
            }
        }
        String indentchild = indent + "    ";
        for (Element child : elem.children ()) {
            printElement (child, indentchild);
        }
        System.out.println (indent + "</" + elem.tagName () + ">");
    }

    public static Element getFirstChildWithTagName (Element elem, String tagName)
    {
        for (Element e : elem.children ()) {
            if (e.tagName ().equals (tagName)) return e;
        }
        return null;
    }

    public static String getFirstAHrefZip (Element elem)
    {
        for (Element aelem : elem.getElementsByTag ("a")) {
            String href = aelem.attr ("href");
            if (href.endsWith (".zip")) return href;
        }
        return null;
    }
}
