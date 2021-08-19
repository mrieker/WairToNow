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
 * @brief Generate <chartname>.csv file that contains all the parameters needed to convert lat/lon <-> chart/pixel
 */
// mcs -debug -out:GenChartsCSV.exe GenChartsCSV.cs

// cd charts
// mono --debug ../GenChartsCSV.exe 'New York 86 North' chartlist_all.htm

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

public class GenChartsCSV {
    public static void Main (string[] args)
    {
        string spacename = args[0]; // chart name with spaces, eg, "New York 86 North"
        string clistname = args[1]; // input .htm file with all charts and their expiration dates

        Chart chart = new Chart ();
        chart.spacename = spacename;
        chart.ReadParams (clistname);
        chart.GenEllipseCsv ();
    }
}

public class Chart {
    public string spacename;

    public double centerLat;
    public double centerLon;
    public double stanPar1;
    public double stanPar2;
    public int begdate, enddate;

    public double e_rada; // earth radius at equator (metres)
    public double e_radb; // earth radius at poles (metres)

    public double e_tfw_a;
    public double e_tfw_b;
    public double e_tfw_c;
    public double e_tfw_d;
    public double e_tfw_e;
    public double e_tfw_f;
    public int e_height, e_width;

    /**
     * @brief Read elliptical chart parameter files.
     */
    public void ReadParams (string clistname)
    {
        string htmname = spacename + ".htm";
        string tfwname = spacename + ".tfw";

        /*
         * Read image parameters from HTM file.
         */
        BinaryReader htmreader = new BinaryReader (File.Open (htmname, FileMode.Open));
        StringBuilder sb = new StringBuilder ();
        try {
            bool intag = false;
            bool needspace = false;
            while (true) {
                byte b = htmreader.ReadByte ();
                if (b > 32) {
                    if ((b >= 'A') && (b <= 'Z')) b += 'a' - 'A';
                    if (b == '<') {
                        intag = true;
                        needspace = true;
                        continue;
                    }
                    if (b == '>') {
                        intag = false;
                        continue;
                    }
                    if (! intag) {
                        if (needspace) {
                            needspace = false;
                            sb.Append (' ');
                        }
                        sb.Append ((char) b);
                    }
                }
            }
        } catch (EndOfStreamException) {
        }
        htmreader.Close ();
        string htmfile = sb.ToString ().Replace (" : ", ":").Replace (": ", ":").Replace ("semi- major_axis", "semi-major_axis");
        //File.WriteAllText ("x.htm", htmfile);

        if (spacename.StartsWith ("ENR ")) {
            int j = spacename.LastIndexOf (' ');
            begdate = int.Parse (spacename.Substring (++ j));
            DateTime dt = new DateTime (
                begdate / 10000, (begdate / 100) % 100, begdate % 100,
                12, 0, 0);
            dt = dt.AddDays (56);
            enddate = dt.Year * 10000 + dt.Month * 100 + dt.Day;
        } else {
            begdate = GetColonInteger (htmfile, "beginning_date");
            try {
                enddate = GetColonInteger (htmfile, "ending_date");
            } catch (FormatException) {
                if (!spacename.Contains (" HEL ")) throw;
                enddate = 0;
            }
        }

        centerLon = GetHtmDouble  (htmfile, "longitude_of_central_meridian");
        centerLat = GetHtmDouble  (htmfile, "latitude_of_projection_origin");
        stanPar1  = GetHtmDouble  (htmfile, "lambert_conformal_conic:standard_parallel");
        stanPar2  = GetHtmDouble  (htmfile, "standard_parallel");
        e_width   = GetHtmInteger (htmfile, "column_count");
        e_height  = GetHtmInteger (htmfile, "row_count");

        if (e_width < e_height) {
            int swap = e_height;
            e_height = e_width;
            e_width  = swap;
        }

        double invflat = GetHtmDouble (htmfile, "denominator_of_flattening_ratio");
        e_rada = GetHtmDouble  (htmfile, "semi-major_axis");
        e_radb = e_rada * (1.0 - 1.0 / invflat);

        /*
         * chartlist_all.htm contains more up-to-date ending date information.
         *   <td .../New_York_86.zip">86 - Nov 15 2012</a>
         *   </td>
         *   <td class="std" headers="header2">87 - Jul 25 2013</td>
         */
        StreamReader stmreader = new StreamReader (clistname);
        htmfile = stmreader.ReadToEnd ();
        stmreader.Close ();
        string undernons = spacename.Replace (' ', '_');
        if (undernons.EndsWith ("_North") || undernons.EndsWith ("_South")) {
            undernons = undernons.Substring (0, undernons.Length - 6);
        }
        int i = htmfile.IndexOf ("/" + undernons + ".zip\">");
        if (i >= 0) {
            try {
                i += 1 + undernons.Length + 6;
                int j = htmfile.IndexOf (" - ", i);
                int thisrev = int.Parse (htmfile.Substring (i, j - i));
                j += 3;
                i  = htmfile.IndexOf ('<', j);
                string thisdate = htmfile.Substring (j, i - j);
                int nextrev = thisrev + 1;
                j  = htmfile.IndexOf (">" + nextrev.ToString () + " - ", i);
                j += 1 + nextrev.ToString ().Length + 3;
                i  = htmfile.IndexOf ('<', j);
                string nextdate = htmfile.Substring (j, i - j);
                int bd = StrDateDecode (thisdate);
                int ed = StrDateDecode (nextdate);
                if (begdate < bd) begdate = bd;
                if (enddate > ed) enddate = ed;
            } catch (Exception) {
            }
        }

        /*
         * Read more image parameters from TFW file.
         *
         *   x = number of horizontal pixels from upper left (0,0)
         *   y = number of vertical pixels from upper left (0,0)
         *
         *   easting  = A * x + C * y + E   metres East  of centerLon
         *   northing = B * x + D * y + F   metres North of centerLat
         *
         *   E = easting of upper left pixel (0,0)
         *   F = northing of upper left pixel (0,0)
         *
         *   x = [D(easting - E) - C(northing - F)] / (AD - BC)
         *   y = [B(easting - E) - A(northing - F)] / (CB - AD)
         */
        StreamReader tfwreader = new StreamReader (tfwname);
        e_tfw_a = ReadDouble (tfwreader);
        e_tfw_b = ReadDouble (tfwreader);
        e_tfw_c = ReadDouble (tfwreader);
        e_tfw_d = ReadDouble (tfwreader);
        e_tfw_e = ReadDouble (tfwreader);
        e_tfw_f = ReadDouble (tfwreader);
        tfwreader.Close ();
    }

    private double ReadDouble (StreamReader tfwreader)
    {
        String str;
        while ((str = tfwreader.ReadLine ().Trim ()) == "") { }
        return Double.Parse (str);
    }

    /**
     * @brief Parse an integer meta value from the .htm file.
     *        <meta name="dc.coverage.t.min" content="20160303"/>
     */
    private static int GetMetaInteger (string htmfile, string metaname)
    {
        return int.Parse (GetMetaString (htmfile, metaname));
    }

    /**
     * @brief Parse a string meta value from the .htm file.
     *        <meta name="dc.coverage.t.min" content="20160303"/>
     */
    private static string GetMetaString (string htmfile, string metaname)
    {
        int i = htmfile.IndexOf ("metaname=\"" + metaname + "\"");
        if (i < 0) return null;
        int j = htmfile.IndexOf ("content=\"", i);
        if (j < 0) return null;
        j += 9;
        i  = htmfile.IndexOf ("\"", j);
        return htmfile.Substring (j, i - j);
    }

    /**
     * @brief Parse a double value from the .htm file.
     * @param htmfile = contents of the .htm file with all whitespace stripped
     * @param tagname = name of double value to extract
     * @returns parsed double value
     *
     *  >Longitude_of_Central_Meridian:-72.833333<
     *  >Latitude_of_Projection_Origin:42.100000<
     *  >Abscissa_Resolution:63.581364<
     *  >Ordinate_Resolution:63.581364<
     *  >Semi-major_Axis:6378137.000000<
     *  >Denominator_of_Flattening_Ratio:298.257222<
     */
    private static double GetHtmDouble (string htmfile, string tagname)
    {
        return GetHtmDouble (htmfile, tagname, 1);
    }
    private static double GetHtmDouble (string htmfile, string tagname, int index)
    {
        return double.Parse (GetHtmString (htmfile, tagname, index));
    }

    private static int GetHtmInteger (string htmfile, string tagname)
    {
        String str = GetHtmString (htmfile, tagname, 1);
        if (str.EndsWith (",")) str = str.Substring (0, str.Length - 1);
        try {
            return int.Parse (str);
        } catch (Exception e) {
            throw new Exception ("tagname=" + tagname + " str=" + str, e);
        }
    }

    /**
     * @brief Parse a string value from the .htm file.
     * @param htmfile = contents of the .htm file with all whitespace stripped
     * @param tagname = name of string value to extract
     * @param index   = which occurrence (1=first, 2=second, etc)
     * @returns string value
     */
    private static string GetHtmString (string htmfile, string tagname, int index)
    {
        int i = 0;
        do i = htmfile.IndexOf (" " + tagname + ":", i) + tagname.Length + 2;
        while (-- index > 0);
        int j = htmfile.IndexOf (" ", i);
        return htmfile.Substring (i, j - i);
    }

    /**
     * @brief Parse an integet value from the .htm file.
     * @param htmfile = contents of the .htm file with all whitespace stripped
     * @param tagname = name of double value to extract
     * @returns parsed integer value
     *
     *  ...beginning_date:20210812 ...
     */
    private static int GetColonInteger (string htmfile, string tagname)
    {
        String str = GetColonString (htmfile, tagname, 1);
        if (str.EndsWith (",")) str = str.Substring (0, str.Length - 1);
        try {
            return int.Parse (str);
        } catch (Exception e) {
            throw new Exception ("tagname=" + tagname + " str=" + str, e);
        }
    }

    /**
     * @brief Parse a string value from the .htm file.
     * @param htmfile = contents of the .htm file with all whitespace stripped
     * @param tagname = name of string value to extract
     * @param index   = which occurrence (1=first, 2=second, etc)
     * @returns string value
     */
    private static string GetColonString (string htmfile, string tagname, int index)
    {
        int i = 0;
        do i = htmfile.IndexOf (tagname + ":", i) + tagname.Length + 1;
        while (-- index > 0);
        int j = htmfile.IndexOf (" ", i);
        return htmfile.Substring (i, j - i);
    }

    /**
     * @brief Decode date string 'May 03 2013' => int 20130503
     */
    private static int StrDateDecode (string datestr)
    {
        if (datestr.Length != 11) throw new Exception ("bad string length");
        if (datestr[3] != ' ') throw new Exception ("bad separator");
        if (datestr[6] != ' ') throw new Exception ("bad separator");
        string mmm  = datestr.Substring (0, 3);
        string dd   = datestr.Substring (4, 2);
        string yyyy = datestr.Substring (7, 4);
        string mm   = MMM2MM[mmm];
        return int.Parse (yyyy + mm + dd);
    }
    private static Dictionary<string,string> MMM2MM = InitMMM2MM ();
    private static Dictionary<string,string> InitMMM2MM ()
    {
        Dictionary<string,string> d = new Dictionary<string,string> ();
        d["Jan"] = "01";
        d["Feb"] = "02";
        d["Mar"] = "03";
        d["Apr"] = "04";
        d["May"] = "05";
        d["Jun"] = "06";
        d["Jul"] = "07";
        d["Aug"] = "08";
        d["Sep"] = "09";
        d["Oct"] = "10";
        d["Nov"] = "11";
        d["Dec"] = "12";
        return d;
    }

    /**
     * Write ellipsoidal parameters to .csv file.
     */
    public void GenEllipseCsv ()
    {
        string undername = spacename.Replace (' ', '_');
        string csvname   = undername + ".csv";
        
        StringBuilder sb = new StringBuilder ();
        sb.Append (centerLat);
        sb.Append (',');
        sb.Append (centerLon);
        sb.Append (',');
        sb.Append (stanPar1);
        sb.Append (',');
        sb.Append (stanPar2);
        sb.Append (',');
        sb.Append (e_width);
        sb.Append (',');
        sb.Append (e_height);
        sb.Append (',');
        sb.Append (e_rada);
        sb.Append (',');
        sb.Append (e_radb);
        sb.Append (',');
        sb.Append (e_tfw_a);
        sb.Append (',');
        sb.Append (e_tfw_b);
        sb.Append (',');
        sb.Append (e_tfw_c);
        sb.Append (',');
        sb.Append (e_tfw_d);
        sb.Append (',');
        sb.Append (e_tfw_e);
        sb.Append (',');
        sb.Append (e_tfw_f);
        sb.Append (',');
        sb.Append (begdate);
        sb.Append (',');
        sb.Append (enddate);
        sb.Append (',');
        sb.Append (spacename);

        StreamWriter csvwriter = new StreamWriter (csvname);
        csvwriter.WriteLine (sb.ToString ());
        csvwriter.Close ();
    }
}
