// find differences between published localizer headings
// ... and corresponding runway alignment

// javac OffsetLocs.java
// java OffsetLocs datums/localizers_20200716.csv datums/runways_20200716.csv 5

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;

public class OffsetLocs {
    public static class Loc {
        public String ident;
        public String aptfid;
        public String runway;
        public double truhdg;
    }

    public static void main (String[] args)
            throws Exception
    {
        HashMap<String,Loc> localizers = new HashMap<> ();
        BufferedReader locrdr = new BufferedReader (new FileReader (args[0]));
        for (String locline; (locline = locrdr.readLine ()) != null;) {
            String[] locparts = Lib.QuotedCSVSplit (locline);
            if (! locparts[6].equals ("")) {
                Loc loc = new Loc ();
                loc.ident  = locparts[1];
                loc.aptfid = locparts[14];
                loc.runway = locparts[15];
                loc.truhdg = normhdg (Double.parseDouble (locparts[6]));
                localizers.put (loc.aptfid + "." + loc.runway, loc);
            }
        }
        locrdr.close ();

        double mindiff = Double.parseDouble (args[2]);
        BufferedReader rwyrdr = new BufferedReader (new FileReader (args[1]));
        for (String rwyline; (rwyline = rwyrdr.readLine ()) != null;) {
            String[] rwyparts = Lib.QuotedCSVSplit (rwyline);
            String aptfid = rwyparts[0];
            String runway = rwyparts[1];
            Loc loc = localizers.get (aptfid + "." + runway);
            if ((loc != null) && ! rwyparts[4].equals ("") && ! rwyparts[5].equals ("") &&
                    ! rwyparts[6].equals ("") && ! rwyparts[7].equals ("")) {
                double beglat = Double.parseDouble (rwyparts[4]);
                double beglon = Double.parseDouble (rwyparts[5]);
                double endlat = Double.parseDouble (rwyparts[6]);
                double endlon = Double.parseDouble (rwyparts[7]);
                double truhdg = normhdg (Lib.LatLonTC (beglat, beglon, endlat, endlon));
                double diff = Math.abs (loc.truhdg - truhdg);
                if (diff > 180) diff = 360 - diff;
                if (diff >= mindiff) {
                    System.out.format ("%5s %5s %06.2f %6s %06.2f  %6.2f%n", aptfid, runway, truhdg, loc.ident, loc.truhdg, diff);
                }
            }
        }
        rwyrdr.close ();
    }

    private static double normhdg (double hdg)
    {
        if (hdg < 0) hdg += 360;
        if (hdg >= 360) hdg -= 360;
        return hdg;
    }
}
