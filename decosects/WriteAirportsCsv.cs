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
 * @brief Take an .html file retrieved by get_all_airportids.sh
 *        and convert it to be suitable for stand-alone display
 *        and also write a record to airports.csv.
 *
 *  gmcs -debug -out:WriteAirportsCsv.exe WriteAirportsCsv.cs
 *  mono --debug WriteAirportsCsv.exe aptinfo_<expdate>
 *  scans aptinfo/ for <aptid>.html.gz files
 *  writes airports.csv file
 *  prints expiration date yyyymmdd
 */

using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

public class WriteAirportsCsv {

    public static Dictionary<string,string> knownstates = new Dictionary<string,string> ();

    public static void Main (string[] args)
    {
        string aptinfo = args[0];

        /*
         * The info pages for these airports do not indicate the state code for the airport.
         */
        knownstates["48Y"] = "MN";
        knownstates["AND"] = "SC";
        knownstates["CMH"] = "OH";
        knownstates["CUL"] = "IL";
        knownstates["IJX"] = "IL";
        knownstates["ILE"] = "TX";
        knownstates["IMM"] = "FL";
        knownstates["IOW"] = "IA";
        knownstates["ISO"] = "NC";
        knownstates["LAL"] = "FL";
        knownstates["MLY"] = "AK";
        knownstates["OBE"] = "FL";
        knownstates["PNA"] = "WY";
        knownstates["TAL"] = "AK";
        knownstates["UGN"] = "IL";
        knownstates["UNV"] = "PA";
        knownstates["UVA"] = "TX";
        knownstates["WCR"] = "AK";
        knownstates["WDG"] = "OK";
        knownstates["WEA"] = "TX";
        knownstates["WHP"] = "CA";
        knownstates["WMC"] = "NV";
        knownstates["WST"] = "RI";
        knownstates["WVI"] = "CA";

        /*
         * Scan directory to read all airport files and write summary in airports.csv.
         */
        string expdate = null;
        string expdateaptid = null;
        StreamWriter airportsFile = new StreamWriter ("airports.csv");
        StreamWriter runwaysFile  = new StreamWriter ("runways.csv");

        string[] subdirNames = Directory.GetDirectories (aptinfo);
        foreach (string subdir in subdirNames) {
            string[] htmlFileNames = Directory.GetFiles (subdir, "*.html.gz");
            foreach (string name in htmlFileNames) {
                string htmlexpdate;
                int i = name.LastIndexOf ('/') + 1;
                int j = name.LastIndexOf (".html");
                string aptid = name[i-2] + name.Substring (i, j - i);
                string airportsLine = ProcessHtmlFile (name, aptid, out htmlexpdate, runwaysFile);
                if (htmlexpdate != "") {
                    airportsFile.WriteLine (airportsLine);
                    if ((expdate != null) && (expdate != htmlexpdate)) {
                        throw new Exception ("mismatched expdates " + expdate + " (" + expdateaptid
                                             + ") vs " + htmlexpdate + " (" + aptid + ")");
                    }
                    expdate = htmlexpdate;
                    expdateaptid = aptid;
                }
            }
        }
        airportsFile.Close ();
        runwaysFile.Close  ();
        if (expdate != null) {
            Console.WriteLine (expdate);
        }
    }

    private static string ProcessHtmlFile (string htmlname, string aptid, out string expdate, StreamWriter runwaysFile)
    {
        int i, j;

        FileStream fs = new FileStream (htmlname, FileMode.Open, FileAccess.Read, FileShare.Read);
        GZipStream gs = new GZipStream (fs, CompressionMode.Decompress, false);
        StreamReader sr = new StreamReader (gs);
        string input = sr.ReadToEnd ();
        sr.Close ();
        input = input.Replace ("&nbsp;", " ");

        /*
         * Get summary values.
         */
        string aptname = aptid;
        i = input.IndexOf ("<title>");
        if (i >= 0) {
            j = input.IndexOf ("</title>", i);
            if (j >= 0) {
                i += 7;
                aptname = input.Substring (i, j - i);
            }
        }

        double latitude  = FindLatLon (input, "Latitude");
        double longitude = FindLatLon (input, "Longitude");
        double magvar    = FindMagVar (input);
        double elevation = 0;

        string descrip = "";
        string st = FindTableVal (input, "From city", 0);
        if (st != null) {
            descrip += "\\n" + st;
        }
        st = FindTableVal (input, "Elevation", 0);
        if (st != null) {
            st = NoRemark (st);
            elevation = double.Parse (st.Replace ("ft.", "").Trim ());
            descrip  += "\\nElev: " + st;
        }

        string altaptid = aptid;
        i = input.IndexOf ("> " + aptid + " (");
        if (i >= 0) {
            i += aptid.Length + 4;
            j  = input.IndexOf (')', i);
            if (j >= 0) {
                altaptid = input.Substring (i, j - i);
            }
        }

        expdate = "";
        i = input.IndexOf ("Data is effective:");
        if (i >= 0) {
            j = input.IndexOf ('-', i);
            if (j >= 0) {
                i = input.IndexOf ("</div>", ++ j);
                if (i >= 0) {
                    expdate = input.Substring (j, i - j).Trim ();
                    if ((expdate.Length == 10) && (expdate[2] == '/') && (expdate[5] == '/')) {
                        expdate = expdate.Substring (6) + expdate.Substring (0, 2) + expdate.Substring (3, 2);
                    }
                }
            }
        }

        string[] runwayList = input.Split (new string[] { "<div class=\"runwayTitle\">" }, StringSplitOptions.None);
        for (int k = 0; ++ k < runwayList.Length;) {
            string rwstr = runwayList[k];
            j = rwstr.IndexOf ("</div>");
            if (j < 0) continue;
            string rwid = rwstr.Substring (0, j);
            string dims = FindTableVal (rwstr, "Dimensions", j);
            string type = FindTableVal (rwstr, "Surface Type", j);
            string cond = FindTableVal (rwstr, "Surface Condition", j);
            rwid = NoRemark (rwid);
            descrip += "\\nRwy " + rwid + ":";
            if (dims != null) descrip += " " + NoRemark (dims);
            if (type != null) descrip += " " + NoRemark (type);
            if (cond != null) descrip += " " + NoRemark (cond);

            try {
                string elevationL, elevationR;
                string latitudeL,  latitudeR;
                string longitudeL, longitudeR;
                string trueAlignL, trueAlignR;
                FindTableValPair (rwstr, "Elevation",      j, out elevationL, out elevationR);
                FindTableValPair (rwstr, "Latitude",       j, out latitudeL,  out latitudeR);
                FindTableValPair (rwstr, "Longitude",      j, out longitudeL, out longitudeR);
                FindTableValPair (rwstr, "True Alignment", j, out trueAlignL, out trueAlignR);

                if ((latitudeL == "") && (latitudeR == "") && (longitudeL == "") && (longitudeR == "")) continue;

                if (elevationL == "") elevationL = elevation.ToString ();
                if (elevationR == "") elevationR = elevation.ToString ();

                double latL = DecodeLatLon (latitudeL);
                double lonL = DecodeLatLon (longitudeL);
                double latR = DecodeLatLon (latitudeR);
                double lonR = DecodeLatLon (longitudeR);

                StringBuilder runwayLine = new StringBuilder ();

                runwayLine.Append (aptid);
                runwayLine.Append (',');
                runwayLine.Append (rwid.Substring (0, rwid.IndexOf ('/')));
                runwayLine.Append (',');
                runwayLine.Append (trueAlignL.Replace ("&deg;", ""));
                runwayLine.Append (',');
                runwayLine.Append (elevationL.Replace (" ft.", ""));
                runwayLine.Append (',');
                runwayLine.Append (latL);
                runwayLine.Append (',');
                runwayLine.Append (lonL);
                runwayLine.Append (',');
                runwayLine.Append (latR);
                runwayLine.Append (',');
                runwayLine.Append (lonR);
                runwayLine.Append ('\n');

                runwayLine.Append (aptid);
                runwayLine.Append (',');
                runwayLine.Append (rwid.Substring (rwid.IndexOf ('/') + 1));
                runwayLine.Append (',');
                runwayLine.Append (trueAlignR.Replace ("&deg;", ""));
                runwayLine.Append (',');
                runwayLine.Append (elevationR.Replace (" ft.", ""));
                runwayLine.Append (',');
                runwayLine.Append (latR);
                runwayLine.Append (',');
                runwayLine.Append (lonR);
                runwayLine.Append (',');
                runwayLine.Append (latL);
                runwayLine.Append (',');
                runwayLine.Append (lonL);
                runwayLine.Append ('\n');

                runwaysFile.Write (runwayLine.ToString ());
            } catch {
            }
        }

        i = input.IndexOf ("<div class=\"formHeaderCell\">Airport Communications");
        if (i >= 0) {
            i ++;
            while ((i = input.IndexOf ('<', i)) >= 0) {
                string rest = input.Substring (i);
                if (rest.StartsWith ("</table>")) break;
                if (rest.StartsWith ("<tr>")) {
                    descrip += "\\n";
                    i += 4;
                    continue;
                }
                if (rest.StartsWith ("<td")) {
                    j = input.IndexOf ('>', i);
                    if (j < 0) break;
                    i = input.IndexOf ("</td>", ++ j);
                    if (i < 0) break;
                    string str = input.Substring (j, i - j).Trim ();
                    if (str != "") {
                        if (!descrip.EndsWith ("\\n")) descrip += " ";
                        descrip += NoRemark (str);
                    }
                }
                i ++;
            }
        }

        if (descrip.StartsWith ("\\n")) descrip = descrip.Substring (2);

        /*
         * Get state code.
         */
        string state;
        if (!knownstates.TryGetValue (aptid, out state)) {
            state = "XX";
            input = input.Replace (" ", "").ToUpperInvariant ();
            i = input.IndexOf ("<BR>UNITEDSTATES");
            if ((i >= 3) && (input[i-3] == ',')) {
                state = input.Substring (i - 2, 2);
            }
        }

        /*
         * Build and return airports.csv line.
         */
        StringBuilder sb = new StringBuilder ();
        sb.Append (altaptid);  // eg, KBVY, PAAK
        sb.Append (',');
        sb.Append (aptid);     // eg, BVY, AKA
        sb.Append (',');
        sb.Append (elevation); // feet
        sb.Append (",\"");
        sb.Append (aptname);
        sb.Append ("\",");
        sb.Append (latitude);
        sb.Append (',');
        sb.Append (longitude);
        sb.Append (',');
        sb.Append (magvar);
        sb.Append (",\"");
        sb.Append (descrip);
        sb.Append ("\",");
        sb.Append (state);
        return sb.ToString ();
    }

    /**
     * @brief Extract a magnetic variation value.
     *        <td class="formLabelCell">name:</td>
     *        <td class="formCell">dd-mm-ss.s N/S/E/W</td>
     * @param input = whole html file contents
     * @param label = "Latitude" or "Longitude"
     */
    private static double FindLatLon (string input, string label)
    {
        string st = FindTableVal (input, label, 0);
        if (st == null) return 0;
        int i = 0;
        int j = st.IndexOf ('-', i);
        int deg = int.Parse (st.Substring (i, j - i));
        i = st.IndexOf ('-', ++ j);
        int min = int.Parse (st.Substring (j, i - j));
        j = st.IndexOf (' ', ++ i);
        double sec = double.Parse (st.Substring (i, j - i));
        char dir = st[++j];
        double ll = deg + (double)min / 60 + sec / 3600;
        if ((dir == 'W') || (dir == 'S')) ll = -ll;
        return ll;
    }

    /**
     * @brief Extract a magnetic variation value.
     *        <td class="formLabelCell">Variation:</td>
     *        <td class="formCell">integer E/W year</td>
     * @param input = whole html file contents
     */
    private static double FindMagVar (string input)
    {
        string st = FindTableVal (input, "Variation", 0);
        if (st == null) return 0;
        int i = st.IndexOf (' ');
        if (i < 0) return 0;
        int deg = int.Parse (st.Substring (0, i));
        char dir = st[++i];
        if (dir == 'E') return -deg;
        if (dir == 'W') return  deg;
        return 0;
    }

    /**
     * @brief Extract a labeled table value.
     *        <td class="formLabelCell">label:</td>
     *        <td class="formCell">value</td>
     * @param input  = whole html file contents
     * @param label  = label string to look for
     * @param offset = where to start looking in input string
     */
    private static string FindTableVal (string input, string label, int offset)
    {
        string search = "<td class=\"formLabelCell\">" + label + ":</td>";
        int i = input.IndexOf (search, offset);
        if (i < 0) return null;
        int j = input.IndexOf ("<td class=\"formCell\">", i);
        if (j < 0) return null;
        j += 21;
        i = input.IndexOf ("</t", j);
        if (i < 0) return null;
        return input.Substring (j, i - j).Trim ();
    }

    /**
     * @brief Find pair of values for runway attributes.
     *
     *   <tr>
     *     <td class="formLabelCell">
     *       Elevation:
     *     </td>
     *     <td class="formCell">
     *       71.6 ft. 
     *     </td>
     *     <td class="formCell">
     *       91.2 ft. 
     *     </td>
     *   </tr>
     */
    private static void FindTableValPair (string input, string label, int offset,
                                          out string leftVal, out string riteVal)
    {
        int j, k;

        try {
            j = input.IndexOf (label, offset);
            j = input.IndexOf ("<td", j);
            j = input.IndexOf ('>', j);
            k = input.IndexOf ("</td>", ++ j);
            leftVal = NoRemark (input.Substring (j, k - j)).Trim ();

            j = input.IndexOf ("<td", k);
            j = input.IndexOf ('>', j);
            k = input.IndexOf ("</td>", ++ j);
            riteVal = NoRemark (input.Substring (j, k - j)).Trim ();
        } catch {
            leftVal = "";
            riteVal = "";
        }
    }

    /**
     * @brief Remove any <br> or <span...</span> strings.
     *        They provide additional information that is not needed for
     *        the summary strings that go to the airports.csv file.
     */
    private static string NoRemark (string str)
    {
        int i, j, k;
        str = str.Replace ("<br>", "");
        while ((i = str.IndexOf ("<span")) >= 0) {
            j = str.IndexOf ('>', i);
            if (j < 0) break;
            k = str.IndexOf ("</span>", ++ j);
            if (k < 0) break;
            str = str.Substring (0, i) + str.Substring (k + 7);
        }
        return str;
    }

    private static double DecodeLatLon (string st)
    {
        if (st == "") return 0.0;
        int i = 0;
        int j = st.IndexOf ('-', i);
        int deg = int.Parse (st.Substring (i, j - i));
        i = st.IndexOf ('-', ++ j);
        int min = int.Parse (st.Substring (j, i - j));
        j = st.IndexOf (' ', ++ i);
        double sec = double.Parse (st.Substring (i, j - i));
        char dir = st[++j];
        double ll = deg + (double)min / 60 + sec / 3600;
        if ((dir == 'W') || (dir == 'S')) ll = -ll;
        return ll;
    }
}
