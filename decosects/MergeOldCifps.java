/**
 * Merge CIFP records from old CIFP file that are missing in new file.
 * Seems they cut out a lot of VOR CIFP stuff in March 2017.
 * So the old CIFP data comes from cycle ending 2017-03-02.
 */

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

public class MergeOldCifps {

    // args[0] = new CIFP state file
    // args[1] = old CIFP state file
    // args[2] = datums/aptplates_20170817/state/MA.csv (list of new plate .gif files)
    // args[3] = airports.csv eg datums/airports_20170817.csv
    public static void main (String[] args) throws Exception
    {
        HashMap<String,String> faa2icao = new HashMap<> ();
        BufferedReader br = new BufferedReader (new FileReader (args[3]));
        for (String line; (line = br.readLine ()) != null;) {
            String[] parts = line.split (",");
            faa2icao.put (parts[1], parts[0]);
        }
        br.close ();

        HashMap<String,LinkedList<GetCIFPSegments>> iapss = new HashMap<> ();
        try {
            br = new BufferedReader (new FileReader (args[2]));
            for (String line; (line = br.readLine ()) != null;) {
                int i = line.indexOf (",\"");
                int j = line.indexOf ("\",");
                String faaid   = line.substring (0, i);
                String plateid = line.substring (i + 2, j);
                if (plateid.startsWith ("IAP-")) {
                    String icaoid = faa2icao.get (faaid);
                    if (icaoid != null) {
                        LinkedList<GetCIFPSegments> iaps = iapss.get (icaoid);
                        if (iaps == null) {
                            iaps = new LinkedList<> ();
                            iapss.put (icaoid, iaps);
                        }
                        iaps.addLast (new GetCIFPSegments (plateid));
                    }
                }
            }
            br.close ();
        } catch (FileNotFoundException fnfe) {
        }

        // read new FAA file
        TreeMap<String,LinkedList<String>> newfile = readCifpFile (args[0]);

        // read old FAA file
        TreeMap<String,LinkedList<String>> oldfile = readCifpFile (args[1]);

        // go through old file
        // for each IAP in old file what is not found in new file,
        //  copy record from old file to new file
        for (String key : oldfile.keySet ()) {
            if (newfile.get (key) == null) {
                // have record in old file that is not in new file
                // make sure we have an approach plate .gif file
                // ... ie, approach hasn't been deleted
                String[] parts = key.split (",");
                String icaoid = parts[0];
                String cifpid = parts[1];
                LinkedList<GetCIFPSegments> iaps = iapss.get (icaoid);
                if (iaps != null) {
                    for (GetCIFPSegments iap : iaps) {
                        if (iap.appMatches (cifpid) != null) {
                            // found a plate .gif file, use the old CIFP record
                            System.err.println ("restoring " + key);
                            newfile.put (key, oldfile.get (key));
                            break;
                        }
                    }
                }
            }
        }

        // write out updated new file
        for (LinkedList<String> ll : newfile.values ()) {
            for (String line : ll) {
                System.out.println (line);
            }
        }
    }

    // read CIFP file into a tree
    // each CIFP record begins with <ICAOID>,<APPID>,...
    // the tree key is <ICAOID>,<APPID> eg KLWM,S23 for KLWM VOR 23
    // the linked list is all the records beginning with that <ICAOID>,<APPID>
    public static TreeMap<String,LinkedList<String>> readCifpFile (String name) throws Exception
    {
        BufferedReader br = new BufferedReader (new FileReader (name));
        TreeMap<String,LinkedList<String>> tm = new TreeMap<> ();
        for (String line; (line = br.readLine ()) != null;) {
            int i = line.indexOf (',');
            if (i < 0) continue;
            int j = line.indexOf (',', i + 1);
            if (j < 0) continue;
            String key = line.substring (0, j);
            LinkedList<String> ll = tm.get (key);
            if (ll == null) {
                ll = new LinkedList<> ();
                tm.put (key, ll);
            }
            ll.addLast (line);
        }
        return tm;
    }

    // based on GetCIFPSegments from PlateCIFP.java
    private static class GetCIFPSegments {
        private final static HashMap<String,Character> circlingcodes = makeCirclingCodes ();

        // convert 3-letter circling approach type to 1-letter approach type
        // p149 5.10 approach route identifier
        // p150 Table 5-10 Circle-to-Land Procedures Identifier
        private static HashMap<String,Character> makeCirclingCodes ()
        {
            HashMap<String,Character> cc = new HashMap<> ();
            cc.put ("LBC", 'B');  // localizer back-course
            cc.put ("VDM", 'D');  // vor with required dme
            cc.put ("GLS", 'J');  // gnss landing system
            cc.put ("LOC", 'L');  // localizer only
            cc.put ("NDB", 'N');  // ndb only
            cc.put ("GPS", 'P');  // gps
            cc.put ("NDM", 'Q');  // ndb with required dme
            cc.put ("RNV", 'R');  // rnav/gps
            cc.put ("VOR", 'V');  // vor only
            cc.put ("SDF", 'U');  // sdf
            cc.put ("LDA", 'X');  // lda
            return cc;
        }

        // results of parsing full approach name string
        // eg, "IAP-LWM VOR 23"
        private boolean sawdme = false;  // these get set if corresponding keyword seen
        private boolean sawgls = false;
        private boolean sawgps = false;
        private boolean sawils = false;
        private boolean sawlbc = false;
        private boolean sawlda = false;
        private boolean sawloc = false;
        private boolean sawndb = false;
        private boolean sawrnv = false;
        private boolean sawsdf = false;
        private boolean sawvor = false;

        private String runway = null;  // either twodigitsletter string or -letter string
        private String variant = "";   // either null or 'Y', 'Z' etc

        public GetCIFPSegments (String plateid)
        {
            // Parse full approach plate name
            String fullname = plateid.replace ("IAP-", "").replace ("-", " -").replace ("DME", " DME");
            fullname = fullname.replace ("ILSLOC", "ILS LOC").replace ("VORTACAN", "VOR TACAN");

            String[] fullwords = fullname.split (" ");
            for (String word : fullwords) {
                if (word.equals ("")) continue;

                // an actual runway number 01..36 maybe followed by C, L, R
                if ((word.charAt (0) >= '0') && (word.charAt (0) <= '3')) {
                    runway = word;
                    continue;
                }

                // an hyphen followed by a letter such as -A, -B, -C is circling-only approach
                if (word.charAt (0) == '-') {
                    runway = word;
                    continue;
                }

                // a single letter such as W, X, Y, Z is an approach variant
                if ((word.length () == 1) && (word.charAt (0) <= 'Z') && (word.charAt (0) >= 'S')) {
                    variant = word;
                    continue;
                }

                // 'BC' is for localizer back-course
                if (word.equals ("BC")) {
                    sawlbc = sawloc;
                    sawloc = false;
                    continue;
                }

                // all others simple keyword match
                if (word.equals ("DME"))   sawdme = true;
                if (word.equals ("GLS"))   sawgls = true;
                if (word.equals ("GPS"))   sawgps = true;
                if (word.equals ("ILS"))   sawils = true;
                if (word.equals ("LDA"))   sawlda = true;
                if (word.equals ("LOC"))   sawloc = true;
                if (word.equals ("NDB"))   sawndb = true;
                if (word.equals ("SDF"))   sawsdf = true;
                if (word.equals ("VOR"))   sawvor = true;
                if (word.equals ("(GPS)")) sawrnv = true;
            }

            if (runway == null) {
                System.err.println ("no runway in " + plateid);
            }
        }

        /**
         * See if an approach matches the plate title string
         * @param appid = CIFP approach name, eg, "I05Z"
         * @return null: it doesn't match
         *         else: approach type, eg, "ILS"
         */
        public String appMatches (String appid)
        {
            String apptype = null;
            char appcode;
            boolean rwmatch;

            if (runway == null) return null;

            // if circling-only approach, convert 3-letter code to 1-letter code
            // also see if -A in displayed plate id matches -A in database record approach id
            if (runway.startsWith ("-")) {
                String appid3 = appid.substring (0, 3);
                appcode = 0;
                rwmatch = circlingcodes.containsKey (appid3);
                if (rwmatch) {
                    appcode = circlingcodes.get (appid3);
                    rwmatch = appid.substring (3).equals (runway);
                }
            } else {
                // appid : <1-letter-code><runway><variant>
                //   1-letter-code = eg, 'D' for vor/dme
                //   runway = two digits
                //   variant = 'Z', 'Y', etc or missing
                //     (variant might be preceded by an hyphen)

                // straight-in capable, get 1-letter code
                appcode = appid.charAt (0);

                // also see if runway and variant match
                String appidnd = appid.replace ("-", "");
                rwmatch = appidnd.substring (1).equals (runway + variant);
            }

            if (rwmatch) {
                // see if the approach type matches
                // p150 Table 5-9 Runway Dependent Procedure Ident
                switch (appcode) {
                    case 'B': {  // localizer back course
                        if (sawlbc) apptype = "LOC-BC";
                        break;
                    }
                    case 'D': {  // vor with dme required
                        if (sawvor && sawdme) apptype = "VOR/DME";
                        break;
                    }
                    case 'I': {  // ils
                        if (sawils) apptype = "ILS";
                        break;
                    }
                    case 'J': {  // gnss landing system
                        if (sawgls) apptype = "GLS";
                        break;
                    }
                    case 'L': {  // localizer
                        if (sawloc) apptype = "LOC";
                        break;
                    }
                    case 'N': {  // ndb
                        if (sawndb) apptype = "NDB";
                        break;
                    }
                    case 'P': {  // gps
                        if (sawgps) apptype = "GPS";
                        break;
                    }
                    case 'Q': {  // ndb with dme required
                        if (sawndb && sawdme) apptype = "NDB/DME";
                        break;
                    }
                    case 'R': {  // rnav (gps)
                        if (sawrnv) apptype = "RNAV/GPS";
                        break;
                    }
                    case 'S':    // vor has dme but not required
                    case 'V': {  // vor
                        if (sawvor) apptype = "VOR";
                        break;
                    }
                    case 'U': {  // sdf
                        if (sawsdf) apptype = "SDF";
                        break;
                    }
                    case 'X': {  // lda
                        if (sawlda) apptype = "LDA";
                        break;
                    }
                }
            }
            return apptype;
        }
    }
}
