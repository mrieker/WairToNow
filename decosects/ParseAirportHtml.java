
// airports:
//  KBVY,BVY,107.3,"BEVERLY MUNI",42.5841410277778,-70.9161444166667,16,"3 miles NW of BEVERLY, MA\nElev: 107.3 ft.\nRwy 09/27: 4755 ft. x 100 ft. ASPH EXCELLENT\nRwy 16/34: 5000 ft. x 100 ft. ASPH FAIR\nUNICOM: 122.95 MHz \nATIS: 119.2 MHz \nRADAR SERVICE: Approach / Departure\nBEVERLY GROUND: 121.6 MHz GND/P\nBEVERLY TOWER: 125.2 MHz LCL/P\nBOSTON APPROACH/DEPARTURE: 124.4 MHz APCH/P DEP/P\n279.6 MHz APCH/P DEP/P",MA

// runways:
//  BVY,09,74,71.6,42.579744,-70.9247553055556,42.5832796944445,-70.9077619722222

// CLASSPATH=.:jsoup-1.8.3.jar javac ParseAirportHtml.java Lib.java
// CLASSPATH=.:jsoup-1.8.3.jar java ParseAirportHtml <rawhtmlinput> <cookedhtmlout>

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

// http://jsoup.org/apidocs/allclasses-noframe.html

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ParseAirportHtml {
    public static void main (String[] args)
    {
        try {
            String rawhtmlinput  = args[0];
            String cookedhtmlout = args[1];

            File input = new File (rawhtmlinput);
            Document doc = Jsoup.parse (input, "UTF-8", "http://no.such.host/");
            String str = doc.outerHtml ();

            /*
             * Strip crap from HTML.
             */
            str = StripBetween (str, "<nav", "</nav>");
            str = StripBetween (str, "<ul class=\"breadcrumb\">", "</ul>");
            str = StripBetween (str, "<div class=\"tabbable\">", "</div>");
            str = StripBetween (str, "<div id=\"map\">", "</div>");

            BufferedWriter writer = new BufferedWriter (new FileWriter (cookedhtmlout));
            writer.write (str);
            writer.close ();
        } catch (Exception e) {
            System.err.println (e.toString ());
        }
    }

    /**
     * Strip everything between first occurences of the two markers, including the markers.
     */
    public static String StripBetween (String str, String beg, String end)
    {
        int i = str.indexOf (beg);
        int j = str.indexOf (end, i);
        return str.substring (0, i) + str.substring (j + end.length ());
    }
}
