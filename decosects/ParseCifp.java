//    Copyright (C) 2017, Mike Rieker, Beverly, MA USA
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
 * Decode FAA-provided Coded Instrument Flight Procedure data to xextract
 * IAP waypoint sequence info.
 *
 * cycles28=20170202
 * cycles56=20170302
 * javac ParseCifp.java
 * java ParseCifp $cycles56 datums/iapcifps_$cycles28 < FAACIFP18
 *
 * outputs one .csv file per state
 * one line per segment:
 *   icaoid,rwyid,appid,segid;leg;leg;leg...
 * each leg:
 *   pathterm,key=val,key=val,key=val...
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;

/*
    Spec references are for v20

    >> 3.2.4 airport (P)
    >> 3.2.4.13 terminal ndb (PN)
    >> 3.3 heliport (H)
    >> 4.1.2 vhf navaid (D)
    >> 4.1.3 ndb navaid (DB)
    >> 4.1.4 enroute waypoint (EA)
    >> 4.1.6 enroute airways (ER)
    >> 4.1.9 airport sid/star/approach (PD, PE, PF) << seems to be missing
    >> 4.1.25 controlled airspace (UC)
    >> 4.1.18 restrictive airspace (UR)


0 000 00 0000 00 0 000000 0 00000 0 000 00000 00 00 0 0000 0 000 00 0 0000 00 000000 0000 0000 0000 0000 00 0 0 0 0 00000 00000 00000 011 1111 11111 1 11 11 1 1 1 1 111 11111 1111
0 000 00 0000 11 1 111111 1 22222 2 222 23333 33 33 3 3444 4 444 44 4 5555 55 555566 6666 6666 7777 7777 77 8 8 8 8 88888 89999 99999 900 0000 00001 1 11 11 1 1 1 1 222 22222 2233
0 123 45 6789 01 2 345678 9 01234 5 678 90123 45 67 8 9012 3 456 78 9 0123 45 678901 2345 6789 0123 4567 89 0 1 2 3 45678 90123 45678 901 2345 67890 1 23 45 6 7 8 9 012 34567 8901

--------
S/CAN/P /PAKT/PA/F/L11-Z /A/ANN  / /030/PUTIY/PA/PC/0/EE  /R/010/RF/ /    /  /003010/3011/    /1146/0094/  / / /+/ /03000/     /     /   /    /CFCFB/ /PA/PC/0/ /P/S/   /G    /1612
                            ^segid      ^end point         ^turndir                                 ^arclen         ^altitude                  ^arc center
--------
IF: iaf colle:
S/USA/P /KBVY/K6/F/R16   /A/COLLE/ /010/COLLE/K6/PC/0/E  A/ /   /IF/ /    /  /      /    /    /    /    /  / / / / /     /     /18000/   /    /     / /  /  /A/ /J/S/   /00008/1310

TF: colle -> ladti at 2000':
S/USA/P /KBVY/K6/F/R16   /A/COLLE/ /020/LADTI/K6/PC/0/EE B/ /010/TF/ /    /  /      /    /    /    /    /  / / /+/ /02000/     /     /   /    /     / /  /  /A/ /J/S/   /00009/1310
--------

--------
HF: hold (single circuit) at ladti at 2000':
S/USA/P /KBVY/K6/F/R16   /A/LADTI/ /010/LADTI/K6/PC/0/EE A/R/   /HF/ /    /  /      /    /    /1565/0040/  / / /+/ /02000/     /18000/   /    /     / /  /  /A/ /J/S/   /00010/1310
--------

--------
IF: iaf mht:
S/USA/P /KBVY/K6/F/R16   /A/MHT  / /010/MHT  /K6/D /0/V  A/ /   /IF/ /    /  /      /    /    /    /    /  / / / / /     /     /18000/   /    /     / /  /  /A/ /J/S/   /00011/1109

TF: mht -> ladti at 2000':
S/USA/P /KBVY/K6/F/R16   /A/MHT  / /020/LADTI/K6/PC/0/EE B/ /010/TF/ /    /  /      /    /    /    /    /  / / /+/ /02000/     /     /   /    /     / /  /  /A/ /J/S/   /00012/1310
--------

--------
IF: iaf psm:
S/USA/P /KBVY/K6/F/R16   /A/PSM  / /010/PSM  /K6/D /0/V   / /   /IF/ /    /  /      /    /    /    /    /  / / / / /     /     /18000/   /    /     / /  /  /A/ /J/S/   /00013/1109

TF: -> nuvza at 3000':
S/USA/P /KBVY/K6/F/R16   /A/PSM  / /020/NUVZA/K6/PC/0/E  A/ /020/TF/ /    /  /      /    /    /    /    /  / / /+/ /03000/     /     /   /    /     / /  /  /A/ /J/S/   /00014/1310

TF: -> ladti at 2000':
S/USA/P /KBVY/K6/F/R16   /A/PSM  / /030/LADTI/K6/PC/0/EE B/ /010/TF/ /    /  /      /    /    /    /    /  / / /+/ /02000/     /     /   /    /     / /  /  /A/ /J/S/   /00015/1310
--------

--------
IF: iaf ladti at 2000':
S/USA/P /KBVY/K6/F/R16   /R/     / /010/LADTI/K6/PC/0/E  I/ /   /IF/ /    /  /      /    /    /    /    /  / / /+/ /02000/     /18000/   /    /     / /  /  /A/ /J/S/   /00016/1310

TF: -> zindu at 1800':
S/USA/P /KBVY/K6/F/R16   /R/     / /020/ZINDU/K6/PC/1/E  F/ /051/TF/ /    /  /      /    /    /    /    /  / / /+/ /01800/     /     /   /    /RW16 / /K6/PG/A/ /J/S/   /00017/1310 S/USA/P /KBVY/K6/F/R16   /R/     / /020/ZINDU/K6/PC/2/WALP/V/   /  / / ALN/AV//VNAV /ALNA/V   /    /    /  / / / / /     /     /     /   /    /     / /  /  / / /J/S/   /00018/1310

TF: -> rw16 at 147':
S/USA/P /KBVY/K6/F/R16   /R/     / /030/RW16 /K6/PG/0/GY M/ /031/TF/ /    /  /      /    /    /    /    /  / / / / /00147/     /     /   /-300/     / /  /  /A/ /J/S/   /00019/1201

CA: climb to 600' (missed approach):
S/USA/P /KBVY/K6/F/R16   /R/     / /040/     /  /  /0/  M / /   /CA/ /    /  /      /    /    /1566/    /  / / /+/ /00600/     /     /   /    /     / /  /  /A/ /J/S/   /00020/1109

DF: -> wakbi at 2000':
S/USA/P /KBVY/K6/F/R16   /R/     / /050/WAKBI/K6/PC/0/EY  /L/   /DF/ /    /  /      /    /    /    /    /  / / /+/ /02000/     /     /   /    /     / /  /  /A/ /J/S/   /00021/1310

HM: -> hold indefinitely at wakbi at 2000':
S/USA/P /KBVY/K6/F/R16   /R/     / /060/WAKBI/K6/PC/0/EE  /L/   /HM/ /    /  /      /    /    /0210/0040/  / / /+/ /02000/     /     /   /    /     / /  /  /A/ /J/S/   /00022/1310
--------
S/USA/P /KLWM/K6/F/I05-Z /A/BE   / /010/BE   /K6/PN/0/N  A/ /   /FC/ /LWM /K6/      /2349/0203/0576/0061/D / / /+/ /02000/     /18000/   /    /     / /  /  /0/ /N/S/   /00007/1213
--------
                            ^       ^   ^           ^ ^    ^ ^   ^                             ^    ^           ^   ^           ^         ^                      ^ ^
                            ^       ^   ^           ^ ^    ^ ^   ^                             ^    ^           ^   ^           ^         ^                      ^ ^
                            ^       ^   ^           ^ ^    ^ rnp ^                             ^    ^           ^   ^           ^         ^                      ^ ^
                            ^       ^   ^           ^ ^    turn  path and term (5.21 p354)     ^    ^           ^   ^           ^         ^                      ^ ^
                            ^       ^   ^           ^ waypt descrip code (5.17, p157)          ^    ^           ^   ^           ^         ^                      ^ ^
                            ^       ^   fix ident   cont rec no                                ^    ^           ^   altitude    ^         vertical angle         ^ has straight-in minimums (table 5-8)
                            ^       seq no                                                     ^    ^           ^               transition altitude              GPS required (table 5-8, p145)
                            transition identifier                                              ^    ^           altitude description (5.29, p165) +=at or above altitude 1; blank=at altitude 1
                          ^route type                                                          ^    distance (5.27, p164) naut miles 1/10th
                           5.7 p141                                                            mag course (5.26)
                           A=airway; R=RNAV

----------------------------

4.1.4 p42 type PC
4.1.4.1  Waypoint Primary Records
S/CAN/P/ /PAPB/PA/C/SL107/ /PA/0/    /I  /  / /N56360155/W169443044/                       /E0091/     /NAR/        /   /(IPBV 2872 / SRI 0035)   /0M008/1208
      ^           ^ ^
      P           C wptid
0 000 0 0 0001 11 1 11111 1 22 2 2222 222 33 3 3
1 234 5 6 7890 12 3 45678 9 01 2 3456 789 01 2 3
 
*/

public class ParseCifp {
    private static class State {
        public String stateid;  // eg 'MA'
        public TreeMap<String,Airport> airports = new TreeMap<> ();
    }

    private static class Airport {
        public State state;
        public String icaoid;  // eg 'KBVY'
        public TreeMap<String,Approach> approaches = new TreeMap<> ();

        private HashMap<String,Waypts.DBFix> localfixes = new HashMap<> ();

        public Airport (String icaoid)
        {
            this.icaoid = icaoid;

            // make list of waypoints near the airport
            // - the airport itself (by icaoid)
            // - its runways (RWnnl)
            // - nearby navaids, fixes etc
            Waypts.Airport wpapt = Waypts.allIcaoApts.get (icaoid);
            localfixes.put (wpapt.name, wpapt);
            for (Waypts.Runway rwy : wpapt.runways.values ()) {
                localfixes.put (rwy.name, rwy);
            }

            for (Waypts.DBFix dbfix : Waypts.allDBFixes) {

                // points must be within 200nm of the airport
                // and if duplicate, must be closer of the two
                double oldist = 200.0;
                Waypts.DBFix oldbfix = localfixes.get (dbfix.name);
                if (oldbfix != null) {
                    oldist = Lib.LatLonDist (wpapt.lat, wpapt.lon, oldbfix.lat, oldbfix.lon);
                }

                // add point if it meets those criteria
                double dist = Lib.LatLonDist (wpapt.lat, wpapt.lon, dbfix.lat, dbfix.lon);
                if (dist < oldist) {
                    localfixes.put (dbfix.name, dbfix);
                }
            }
        }

        // add local waypoints to list of waypoints valid for this airport
        // eg, FF04, SL107 (4.1.3.1)
        // since they aren't in the database that the client has, they have
        // to be given a self-defining name such as <navaid>[<dist>/<truerad>.
        public void addLocalWaypt (String line)
        {
            String lclid = line.substring (13, 18).trim ();
            if (!localfixes.containsKey (lclid)) {

                // get lat/lon of waypoint
                double lat = decodeLatString (line.substring (32, 41));
                double lon = decodeLonString (line.substring (41, 51));

                // build self-defining name for the waypoint that is visible to user

                Waypts.DBFix dbfix = new Waypts.DBFix ();
                dbfix.type = "lcl/" + icaoid;
                dbfix.lat  = lat;
                dbfix.lon  = lon;

                // it might be very close to a defined fix
                // KONT ILS 26L outer marker named FONTA on plate
                // ...but is in CIFP as one of these, so match it
                // up with FONTA
                double bestdist = 0.125;
                Waypts.DBFix bestfix = null;
                for (Waypts.DBFix fix : localfixes.values ()) {
                    if (!fix.type.startsWith ("lcl/")) {
                        double dist = Lib.LatLonDist (lat, lon, fix.lat, fix.lon);
                        if (bestdist > dist) {
                            bestdist = dist;
                            bestfix  = fix;
                        }
                    }
                }
                if (bestfix != null) {
                    dbfix.name = bestfix.name;
                } else {

                    // look through the reference name string to see if we can find a close navaid
                    // p300..304
                    String ref = line.substring (98, 123).trim ();
                    if (ref.startsWith ("(")) {
                        ref = ref.replace ("(", " ").replace (")", " ").replace ("/", " ");
                        String[] words = ref.split (" ");

                        // of all the words, find one that names the closest navaid
                        bestdist = 99.0;
                        for (String word : words) {
                            Waypts.DBFix thisfix = localfixes.get (word);
                            if ((thisfix != null) && !thisfix.type.startsWith ("lcl/")) {
                                double thisdist = Lib.LatLonDist (thisfix.lat, thisfix.lon, lat, lon);
                                if (bestdist > thisdist) {
                                    bestdist = thisdist;
                                    bestfix  = thisfix;
                                }
                            }
                        }

                        // if a nearby navaid found, define this one as a distance/radial offset
                        // orherwise, vfywp() will use runway waypoint when we know which approach this point is for
                        if (bestfix != null) {
                            dbfix.name = makeLocalName (dbfix, bestfix);
                        }
                    }
                }

                // add entry to the localfixes list
                localfixes.put (lclid, dbfix);
            }
        }

        // verify that the given waypoint actually exists so the app won't get an error.
        // amd in the case of local waypoints that aren't in the app's database,
        // return the self-defining string instead of the local name.
        // note that the app has runway waypoints ('RW16') that are local so we don't have to make those.
        public String vfywp (String name, Approach approach)
        {
            // maybe there is a [dme suffix
            // if so, verify the base fix id and then re-append the [dme stuff
            int i = name.indexOf ('[');
            if (i >= 0) {
                return vfywp (name.substring (0, i), approach) + name.substring (i);
            }

            Waypts.DBFix dbfix = localfixes.get (name);
            if ((dbfix == null) && (name.length () == 4) && (name.charAt (0) == 'I')) {
                dbfix = localfixes.get ("I-" + name.substring (1));
            }
            if (dbfix == null) throw new BadWayptException (name);

            // we might have a local fix that didn't have any reference waypoint
            // so try to use the approach's runway waypoint if defined, and use
            // airport as a last resort
            if (dbfix.name != null) return dbfix.name;
            Waypts.DBFix gblfix = null;
            String id = approach.appid.substring (1);  // eg 'I04RY' -> '04RY'
            char ch = id.charAt (0);
            if ((ch >= '0') && (ch <= '9')) {
                id = "RW" + id.replace ("-", "");
                ch = id.charAt (id.length () - 1);
                if ((ch > 'R') && (ch <= 'Z')) {
                    id = id.substring (id.length () - 1);
                }
                gblfix = localfixes.get (id);
            }
            if (gblfix == null) {
                gblfix = localfixes.get (icaoid);
            }
            return makeLocalName (dbfix, gblfix);
        }

        // get DBFix for a given waypoint
        // name is as returned by vfywp()
        public Waypts.DBFix getwp (String name)
        {
            return localfixes.get (name);
        }

        // set the name of a local waypoint to be relative to the global waypoint
        private static String makeLocalName (Waypts.DBFix lcl, Waypts.DBFix gbl)
        {
            double dist = Lib.LatLonDist (gbl.lat, gbl.lon, lcl.lat, lcl.lon);
            double tc   = Lib.LatLonTC   (gbl.lat, gbl.lon, lcl.lat, lcl.lon);
            String name;
            while (true) {
                name = String.format ("%s[%.1f/%.1f", gbl.name, dist, tc).replace (".0", "");
                if (name.indexOf ('-') < 0) return name;
                tc += 360.0;
            }
        }
    }

    private static class Approach {
        public Airport airport;
        public String  appid;  // p149 5.10 eg, 'R16'
        public TreeMap<String,Segment> segments = new TreeMap<> ();
        public TreeMap<String,Leg> iaflegs = new TreeMap<> ();

        public void print (PrintWriter pw)
        {
            // output transition and final and missed segments
            for (Segment seg : segments.values ()) {
                seg.print (pw);
            }

            // make a radar vector transition segment
            try {
                // get final segment
                Segment finseg = segments.get ("~f~");

                // get each leg string that we wrote to database
                String[] finlegs = finseg.printed.split (";");

                // get first leg of final segment, it should always be a CF with a fix id
                ParsedLeg finleg1 = new ParsedLeg (finlegs[1]);
                if (!finleg1.pathterm.equals ("CF")) throw new Exception ("final seg doesn't start with CF leg");
                String finid = finleg1.parms.get ("wp");
                finid = airport.vfywp (finid, this);

                // see if final segment starts right at the faf
                // if not, we can use whatever it starts at as the vectoring fix
                String vecid = finid;
                if (finleg1.parms.containsKey ("faf")) {

                    // if so, we have to make up a fix to put before the faf for vectoring to
                    // this will give us a line in line with the runway
                    // that the radar controller will vector the plane to
                    // eg, KLWM VOR-23

                    // get fix at end of final segment
                    // not necessarily a runway, might be arbitrary fix for circling-only approach
                    ParsedLeg finlege = new ParsedLeg (finlegs[finlegs.length-1]);
                    if (!finlege.pathterm.equals ("CF")) throw new Exception ("final seg doesn't end with CF leg");
                    String mapid = finlege.parms.get ("wp");
                    mapid = airport.vfywp (mapid, this);
                    Waypts.DBFix mapwp = airport.getwp (mapid);

                    // get distance and heading from map to faf
                    Waypts.DBFix fafwp = airport.getwp (finid);
                    double m2fdist = Lib.LatLonDist (mapwp.lat, mapwp.lon, fafwp.lat, fafwp.lon);
                    double m2ftc   = Lib.LatLonTC   (mapwp.lat, mapwp.lon, fafwp.lat, fafwp.lon);
                    if (m2ftc < 0.0) m2ftc += 360.0;

                    // make vectoring fix that same distance and heading behind the faf
                    vecid = String.format ("%s[%.1f/%.1f", finid, m2fdist, m2ftc);
                    vecid = vecid.replace (".0", "");
                }

                StringBuilder sb = new StringBuilder ();
                sb.append (airport.icaoid);
                sb.append (',');
                sb.append (appid);
                sb.append (',');
                sb.append ("(rv);CF,wp=");
                sb.append (vecid);
                pw.println (sb.toString ());
            } catch (Exception e) {
                System.out.println (airport.icaoid + "." + appid + ".(rv): exception");
                e.printStackTrace ();
            }

            // some IAFs are buried inside transition segments
            // but we want the pilot to be able to select them
            // so output them as their own transition
            for (String iafname : iaflegs.keySet ()) {

                // maybe it was already output as a transition segment
                if (!segments.containsKey (iafname)) {

                    // it wasn't, make a segment that starts at the IAF
                    Leg iafleg = iaflegs.get (iafname);
                    int iaflegnum = iafleg.legnum;
                    Segment oldiafseg = iafleg.segment;
                    Segment newiafseg = new Segment ();
                    newiafseg.approach = this;
                    newiafseg.segid = iafname;
                    for (Integer legnum : oldiafseg.legs.keySet ()) {
                        if (legnum >= iaflegnum) {
                            Leg leg = oldiafseg.legs.get (legnum);
                            leg.segment = newiafseg;
                            newiafseg.legs.put (legnum, leg);
                        }
                    }

                    // output that segment to data file
                    newiafseg.print (pw);
                }
            }

            // output referenced navaids in final segment
            // - this ends up printing out the main navaid for ILS/LOC/NDB/VOR etc approaches
            segments.get ("~f~").printrefs (pw);
        }
    }

    private static class ParsedLeg {
        HashMap<String,String> parms = new HashMap<> ();
        String pathterm;

        public ParsedLeg (String legstr)
        {
            String[] vals = legstr.split (",");
            pathterm = vals[0];
            int nparms = vals.length;
            for (int i = 0; ++ i < nparms;) {
                String val = vals[i];
                int j = val.indexOf ('=');
                if (j < 0) {
                    parms.put (val, null);
                } else {
                    parms.put (val.substring (0, j), val.substring (j + 1));
                }
            }
        }
    }

    private static class Segment {
        public Approach approach;
        public String printed;
        public String segid;  // eg 'COLLE' or '~f~' or '~m~'
        public TreeMap<Integer,Leg> legs = new TreeMap<> ();

        public void print (PrintWriter pw)
        {
            if (printed == null) {
                StringBuilder sb = new StringBuilder ();
                sb.append (approach.airport.icaoid);
                sb.append (',');
                sb.append (approach.appid);
                sb.append (',');
                sb.append (segid);
                boolean first = true;
                for (Leg leg : legs.values ()) {
                    int startlen = sb.length ();
                    sb.append (';');
                    try {
                        leg.print (sb, first);
                    } catch (BadWayptException bwe) {
                        System.out.println (approach.airport.icaoid + "." + approach.appid + "." + segid +
                            ": bad waypoint <" + bwe.wp + ">");
                        return;
                    }
                    first = false;
                }
                printed = sb.toString ();
            }
            pw.println (printed);
        }

        // print referenced navaids
        // this is called in the final segment
        // ...to print out which navaid to use for the approach
        public void printrefs (PrintWriter pw)
        {
            try {
                TreeMap<String,Leg> rmdnavs = new TreeMap<> ();
                for (Leg leg : legs.values ()) {
                    String rmdnav = leg.line.substring (50, 54).trim ();
                    if (!rmdnav.equals ("")) {
                        rmdnavs.put (approach.airport.vfywp (rmdnav, approach), leg);
                    }
                }
                if (!rmdnavs.isEmpty ()) {
                    StringBuilder sb = new StringBuilder ();
                    sb.append (approach.airport.icaoid);
                    sb.append (',');
                    sb.append (approach.appid);
                    sb.append (",~r~;");
                    boolean first = true;
                    for (String rmdnav : rmdnavs.keySet ()) {
                        if (!first) sb.append (',');
                        sb.append (rmdnav);
                        first = false;
                    }
                    pw.println (sb.toString ());
                }
            } catch (BadWayptException bwe) {
                System.out.println (approach.airport.icaoid + "." + approach.appid + "." + segid +
                    ": bad waypoint <" + bwe.wp + ">");
            }
        }
    }

    private static class Leg {
        public Segment segment;
        public int legnum;      // eg 10
        public String line;
        public String pathterm; // eg, HM

        public Leg (String line)
        {
            this.line = line;
            legnum    = Integer.parseInt (line.substring (26, 29)); // eg, 060
            pathterm  = line.substring (47, 49).trim (); // eg, HM
        }

        public void print (StringBuilder sb, boolean first)
        {
            // p51 4.1.9.1 get some needed fields
            String wpid   = line.substring (29, 34).trim ();
            char   iaffaf = line.charAt (42);
            String rmdnav = line.substring (50, 54).trim ();
            String theta  = line.substring (62, 66).trim ();
            String rho    = line.substring (66, 70).trim ();
            String magcrs = line.substring (70, 74).trim ();
            String tmdst  = line.substring (74, 78).trim ();

            // p356 Attachment 5
            switch (pathterm) {

                // dme arc
                case "AF": {
                    sb.append ("AF");
                    sb.append (",nav=" + vfywp (rmdnav));   // navaid at center of arc
                    sb.append (",nm=" + vfynm (rho));       // distance from navaid
                    sb.append (",beg=" + vfymc (magcrs));   // starting radial from navaid
                    sb.append (",end=" + vfymc (theta));    // ending radial from navaid
                    break;
                }

                // fly given course
                case "CA":
                case "FA":
                case "FM":
                case "VA":
                case "VM": {
                    sb.append ("CA,mc=" + vfymc (magcrs));
                    break;
                }

                // fly given course until specified distance from a navaid
                case "CD":
                case "FD":
                case "VD": {
                    sb.append ("CD,mc=" + vfymc (magcrs) + ",nav=" + vfywp (rmdnav) + ",nm=" + vfynm (tmdst));
                    break;
                }

                // fly to given waypoint
                case "CF":
                case "DF":
                case "IF":
                case "TF": {
                    sb.append ("CF,wp=" + vfywp (wpid));
                    // used by client when preceded by 'CI' leg
                    if (!magcrs.equals ("")) sb.append (",mc=" + vfymc (magcrs));
                    break;
                }

                // fly given course to intercept next leg
                case "CI":
                case "VI": {
                    sb.append ("CI,mc=" + vfymc (magcrs));
                    break;
                }

                // fly given course to intercept navaid radial
                case "CR":
                case "VR": {
                    sb.append ("CR,mc=" + vfymc (magcrs) + ",nav=" + vfywp (rmdnav) + ",rad=" + vfymc (theta));
                    break;
                }

                // fly from fix on given course for given distance
                // splice in a CF leg in front to make it easy on client
                case "FC": {
                    sb.append ("CF,wp=" + vfywp (wpid));
                    appendAlt (sb);
                    iaffaf = appendIafFaf (sb, iaffaf);
                    sb.append (";FC,mc=" + vfymc (magcrs) + ",nm=" + vfynm (tmdst));
                    break;
                }

                // fly procedure turn
                case "PI": {
                    if (first) iaffaf = spliceInCF (sb, wpid, iaffaf);
                    sb.append ("PI");
                    sb.append (",wp=" + vfywp (wpid));                  // navaid procedure turn measured from
                    sb.append (",toc=" + vfymc (magcrs));               // course to turn away from final course to loop outbound
                    sb.append (",td=" + vfytd (line.charAt (43)));      // turn direction from outbound to inbound turn
                    break;
                }

                case "HA":      // hold until reached given altitude
                case "HF":      // hold once around to get to fix
                case "HM": {    // hold indefinitely
                    if (first) iaffaf = spliceInCF (sb, wpid, iaffaf);
                    sb.append (pathterm);
                    sb.append (",wp=" + vfywp (wpid));                  // hold waypoint
                    sb.append (",rad=" + vfymc (magcrs));               // radial to the waypoint
                    sb.append (",td=" + vfytd (line.charAt (43)));      // turn direction
                    break;
                }

                case "RF": {    // arc with named endpoint
                    sb.append ("RF,ep=");      // end point
                    sb.append (vfywp (wpid));
                    sb.append (",td=");        // turn direction
                    sb.append (vfytd (line.charAt (43)));
                    sb.append (",cp=");        // center point (5.144)
                    sb.append (vfywp (line.substring (106, 111)));
                    break;
                }

                default: {
                    throw new RuntimeException ("unknown pathterm " + pathterm);
                }
            }

            appendAlt (sb);
            appendIafFaf (sb, iaffaf);
        }

        // if seg begins with PI or an hold, splice in a CF so we get dots drawn
        // from initial airplane location to beginning of procedure turn
        private char spliceInCF (StringBuilder sb, String wpid, char iaffaf)
        {
            sb.append ("CF,wp=" + vfywp (wpid));
            appendAlt (sb);
            iaffaf = appendIafFaf (sb, iaffaf);
            sb.append (';');
            return iaffaf;
        }

        private void appendAlt (StringBuilder sb)
        {
            char    altdesc = line.charAt (82);                 // 5.29 (p165) altitude description
            String  altone  = line.substring (84, 89).trim ();  // 5.30 altitude ft msl
            String  alttwo  = line.substring (89, 94).trim ();  // 5.30 altitude ft msl
            switch (altdesc) {
                case '+': {
                    if (!altone.equals ("")) sb.append (",a=+" + Integer.parseInt (altone));
                    break;
                }
                case '-': {
                    if (!altone.equals ("")) sb.append (",a=-" + Integer.parseInt (altone));
                    break;
                }
                case ' ':
                case '@': {
                    if (!altone.equals ("")) sb.append (",a=@" + Integer.parseInt (altone));
                    break;
                }
                case 'B':     // between altone and alttwo (altone <= alttwo)
                case 'G':     // at alttwo before wp; intercept glideslope; should be at altone at wp on glideslope
                case 'H':     // not in spec - KABY I04 <final> AB altone=02200 alttwo=02162
                case 'I':     // not in spec - KAPA I35R <final> FIRPI altone=09000 alttwo=08000
                case 'J':     // not in spec - KLWM I05-Y <final> TEWKS altone=02000 alttwo=01900
                case 'V': {   // alttwo for glideslope; altone for localizer
                              // alttwo is slightly above altone while on vertical descent on final
                    sb.append (",a=" + altdesc + Integer.parseInt (altone) + ":" + Integer.parseInt (alttwo));
                    break;
                }
                default: {
                    throw new RuntimeException ("unknown altdesc " + altdesc);
                }
            }
        }

        private char appendIafFaf (StringBuilder sb, char iaffaf)
        {
            switch (iaffaf) {
                case 'A': {
                    sb.append (",iaf");
                    String wpid = line.substring (29, 34).trim ();
                    segment.approach.iaflegs.put (vfywp (wpid), this);
                    break;
                }
                case 'F': {
                    sb.append (",faf");
                    break;
                }
            }
            return ' ';
        }

        private String vfywp (String q)
        {
            return segment.approach.airport.vfywp (q, segment.approach);
        }
    }

    public static TreeMap<String,State> states = new TreeMap<> ();

    public static void main (String[] args)
    {
        try {
            Waypts.locknload (".", args[0]);

            // read through CIFP 132-char fixed length records
            BufferedReader infile = new BufferedReader (new InputStreamReader (System.in), 4096);
            for (String line; (line = infile.readLine ()) != null;) {
                line = line.replace ("\r", "");
                if (line.length () != 132) continue;

                // local-to-airport waypoints
                // these waypoints might not be defined in the general waypoint database
                // so if the IAP stuff references them, we must define them first
                if ((line.charAt ( 0) == 'S') &&
                    (line.charAt ( 4) == 'P') &&
                    (line.charAt (12) == 'C')) {
                    String icaoid = line.substring (6, 10).trim ();
                    Airport airport = makeAirport (icaoid);
                    if (airport != null) airport.addLocalWaypt (line);
                }

                // check for IAP primary leg record
                if ((line.charAt ( 0) == 'S') &&
                    (line.charAt ( 4) == 'P') &&
                    (line.charAt (12) == 'F') &&
                    (line.charAt (13) != 'H') &&  // skip RNAV (RNP) approaches for now
                    ((line.charAt (38) == '0') || (line.charAt (38) == '1'))) {

                    // p51 4.1.9.1 fields
                    String icaoid = line.substring (6, 10).trim ();   // p138 5.6  eg, KBVY  - which airport
                    String appid  = line.substring (13, 19).trim ();  // p149 5.10 eg, R16   - which approach
                    String segid  = line.substring (20, 25).trim ();  // p152 5.11 eg, COLLE - starting fix, blank for final segment

                    // broken strings
                    if (icaoid.equals ("KABY") && appid.equals ("I04") && segid.equals ("SHANY") && line.substring (29, 34).equals ("AB   ") && (line.charAt (43) == 'A')) {
                        line = line.substring (0, 43) + 'L' + line.substring (44);
                    }

                    if (icaoid.equals ("S59") && appid.equals ("GPSA")) appid = "GPS-A";

                    // catalog the leg
                    Airport airport = makeAirport (icaoid);
                    if (airport != null) {
                        Approach approach = airport.approaches.get (appid);
                        if (approach == null) {
                            approach = new Approach ();
                            approach.airport = airport;
                            approach.appid = appid;
                            airport.approaches.put (appid, approach);
                        }

                        Segment segment = approach.segments.get (segid);
                        if (segment == null) {
                            segment = new Segment ();
                            segment.approach = approach;
                            segment.segid = segid;
                            approach.segments.put (segid, segment);
                        }

                        Leg leg = new Leg (line);
                        leg.segment = segment;
                        segment.legs.put (leg.legnum, leg);
                    }
                }
            }

            // go through all the approaches we found
            for (State state : states.values ()) {
                for (Airport airport : state.airports.values ()) {
                    for (Iterator<Approach> itapp = airport.approaches.values ().iterator (); itapp.hasNext ();) {
                        Approach approach = itapp.next ();
                        boolean appbad = false;

                        // split the unnamed segment into final and missed segments
                        // mark final seg with name ~f~ and missed with ~m~
                        Segment tempseg = approach.segments.get ("");
                        approach.segments.remove ("");

                        Segment finalseg   = new Segment ();
                        Segment missedseg  = new Segment ();
                        finalseg.approach  = approach;
                        missedseg.approach = approach;
                        finalseg.segid     = "~f~";
                        missedseg.segid    = "~m~";

                        boolean infinal = true;
                        for (Leg leg : tempseg.legs.values ()) {
                            boolean missbeg = (leg.line.charAt (41) == 'M');    // 5.17 First Leg of Missed Approach Procedure
                            infinal &= !missbeg;
                            leg.segment = infinal ? finalseg : missedseg;
                            leg.segment.legs.put (leg.legnum, leg);
                        }

                        // transition segments must contain IAF but some errantly do not (so don't check for it)
                        // but if there is an IAF, is must be CF, FC, HF, HM, PI
                        for (Iterator<Segment> itseg = approach.segments.values ().iterator (); itseg.hasNext ();) {
                            Segment segment = itseg.next ();
                            for (Leg leg : segment.legs.values ()) {
                                if (leg.line.charAt (42) == 'A') {
                                    StringBuilder sb = new StringBuilder ();
                                    leg.print (sb, false);
                                    sb.append ("   ");
                                    String st = sb.toString ().substring (0, 3);
                                    if (!"CF,FC,HF,HM,PI,".contains (st)) {
                                        System.out.println (airport.icaoid + "." + approach.appid + "." + segment.segid + ": IAF not CF,FC,HF,HM,PI");
                                        itseg.remove ();
                                        break;
                                    }
                                }
                            }
                        }

                        // finalseg must begin with CF leg and contain FAF leg
                        // it must also end with CF leg that has an altitude
                        Leg firstleg = null;
                        Leg fafleg   = null;
                        Leg lastleg  = null;
                        for (Leg leg : finalseg.legs.values ()) {
                            if (firstleg == null) firstleg = leg;
                            if (leg.line.charAt (42) == 'F') fafleg = leg;
                            lastleg = leg;
                        }
                        if (firstleg == null) {
                            System.out.println (airport.icaoid + "." + approach.appid + ": empty final segment");
                            appbad = true;
                        } else {
                            StringBuilder sb = new StringBuilder ();
                            firstleg.print (sb, true);
                            String st = sb.toString ();
                            if (!st.startsWith ("CF,")) {
                                System.out.println (airport.icaoid + "." + approach.appid + ": final seg starts with " + st.substring (0, 3));
                                appbad = true;
                            }
                            if (fafleg == null) {
                                System.out.println (airport.icaoid + "." + approach.appid + ": final segment missing FAF <" + st + ">");
                                appbad = true;
                            }
                            sb.delete (0, sb.length ());
                            lastleg.print (sb, false);
                            st = sb.toString ();
                            if (!st.startsWith ("CF")) {
                                System.out.println (airport.icaoid + "." + approach.appid + ": final segment doesn't end with CF");
                                appbad = true;
                            }
                            if (!st.contains (",a=")) {
                                System.out.println (airport.icaoid + "." + approach.appid + ": final segment doesn't end with altitude");
                                appbad = true;
                            }
                        }

                        // if something makes approach unusable, don't write it to datafile
                        if (appbad) itapp.remove ();
                        else {

                            // add final and missed segments to the approach so they will be written to datafile
                            approach.segments.put (finalseg.segid, finalseg);
                            approach.segments.put (missedseg.segid, missedseg);
                        }
                    }
                }
            }

            // output one .csv file per state
            for (State state : states.values ()) {
                PrintWriter pw = new PrintWriter (args[1] + "/" + state.stateid + ".csv");
                for (Airport airport : state.airports.values ()) {
                    for (Approach approach : airport.approaches.values ()) {

                        // output one line per approach segment (transitions, final, missed)
                        approach.print (pw);
                    }
                }
                pw.close ();
            }
        } catch (Exception e) {
            System.err.println ("exception:");
            e.printStackTrace ();
            System.exit (1);
        }
    }

    public static Airport makeAirport (String icaoid)
    {
        Waypts.Airport apt = (Waypts.Airport) Waypts.allIcaoApts.get (icaoid);
        if (apt == null) return null;  // KAPS

        State state = states.get (apt.stateid);
        if (state == null) {
            state = new State ();
            state.stateid = apt.stateid;
            states.put (apt.stateid, state);
        }

        Airport airport = state.airports.get (icaoid);
        if (airport == null) {
            airport = new Airport (icaoid);
            state.airports.put (icaoid, airport);
        }
        return airport;
    }

    // p173 5.36 Latitude
    public static double decodeLatString (String latstr)
    {
        int deg = Integer.parseInt (latstr.substring (1, 3));
        int min = Integer.parseInt (latstr.substring (3, 5));
        int hun = Integer.parseInt (latstr.substring (5));
        double lat = deg + min / 60.0 + hun / 360000.0;
        if (latstr.charAt (0) == 'N') return  lat;
        if (latstr.charAt (0) == 'S') return -lat;
        throw new RuntimeException ("bad lat " + latstr);
    }

    // p173 5.37 Longitude
    public static double decodeLonString (String lonstr)
    {
        int deg = Integer.parseInt (lonstr.substring (1, 4));
        int min = Integer.parseInt (lonstr.substring (4, 6));
        int hun = Integer.parseInt (lonstr.substring (6));
        double lon = deg + min / 60.0 + hun / 360000.0;
        if (lonstr.charAt (0) == 'E') return  lon;
        if (lonstr.charAt (0) == 'W') return -lon;
        throw new RuntimeException ("bad lon " + lonstr);
    }

    public static String vfymc (String q)
    {
        int mc = Integer.parseInt (q);
        if ((mc < 0) || (mc > 3600)) throw new RuntimeException ("bad mc " + mc);
        return Integer.toString (mc);
    }

    public static String vfynm (String q)
    {
        int nm = Integer.parseInt (q);
        if ((nm < 0) || (nm > 9999)) throw new RuntimeException ("bad nm " + nm);
        return Integer.toString (nm);
    }

    public static char vfytd (char q)
    {
        if ((q != 'L') && (q != 'R')) throw new RuntimeException ("bad td " + q);
        return q;
    }

    public static class BadWayptException extends RuntimeException {
        public String wp;

        public BadWayptException (String wp)
        {
            super ("bad wp " + wp);
            this.wp = wp;
        }
    }
}
