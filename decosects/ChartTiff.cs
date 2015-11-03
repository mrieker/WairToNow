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
 * @brief Read all about chart .tif files.
 */

using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

public class ChartTiff {
    public Bitmap bmp;
    public int height;
    public int width;
    public string prefix;
    public string spacename;

    public  double tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f;
    public  double _R, _o1, _o2, _o0, _l0, _n, _F, _p0;

    private const  double PI = Math.PI;
    private static double DegToRad (double deg) { return deg / 180.0 * PI; }
    private static double RadToDeg (double rad) { return rad / PI * 180.0; }
    private static double ln   (double x) { return Math.Log (x); }
    private static double cos  (double x) { return Math.Cos (x); }
    private static double sin  (double x) { return Math.Sin (x); }
    private static double tan  (double x) { return Math.Tan (x); }
    private static double pow  (double x, double y) { return Math.Pow (x, y); }
    private static double sq   (double x) { return x * x; }
    private static double sqrt (double x) { return Math.Sqrt (x); }
    private static double asin (double x) { return Math.Asin (x); }
    private static double atan (double x) { return Math.Atan (x); }

    public ChartTiff (string prefix, string spacename)
    {
        this.prefix = prefix;
        this.spacename = spacename;

        /*
         * Read lat/lon <-> pixel conversion parameters from CSV file.
         */
        StreamReader chartsReader = new StreamReader (prefix + spacename.Replace (' ', '_') + ".csv");
        string[] chartLineFields = null;
        while (true) {
            string chartLine = chartsReader.ReadLine ();
            if (chartLine == null) {
                throw new Exception ("no charts.csv line found for " + spacename);
            }
            chartLineFields = SplitQuotedCSVLine (chartLine);
            string bn = chartLineFields[15];
            if (bn == spacename) break;
        }
        chartsReader.Close ();
        double centerLat = Double.Parse (chartLineFields[ 0]);
        double centerLon = Double.Parse (chartLineFields[ 1]);
        double stanPar1  = Double.Parse (chartLineFields[ 2]);
        double stanPar2  = Double.Parse (chartLineFields[ 3]);
        double radius    = Double.Parse (chartLineFields[ 6]);
        tfw_a            = Double.Parse (chartLineFields[ 7]);
        tfw_b            = Double.Parse (chartLineFields[ 8]);
        tfw_c            = Double.Parse (chartLineFields[ 9]);
        tfw_d            = Double.Parse (chartLineFields[10]);
        tfw_e            = Double.Parse (chartLineFields[11]);
        tfw_f            = Double.Parse (chartLineFields[12]);

        /*
         * Read TIFF file into a bitmap.
         */
        bmp = new Bitmap (prefix + spacename + ".tif");
        width  = bmp.Width;
        height = bmp.Height;

        /*
         * Compute Lambert Conformal Projection parameters.
         */
        _R  = radius;                 // radius of sphere (metres)
        _o1 = DegToRad (stanPar1);    // standard parallel (from .htm file)
        _o2 = DegToRad (stanPar2);    // standard parallel (from .htm file)
        _o0 = DegToRad (centerLat);   // origin latitude
        _l0 = DegToRad (centerLon);   // origin longitude

        _n  = ln (cos (_o1) / cos (_o2)) / ln (tan (PI/4 + _o2/2) / tan (PI/4 + _o1/2));
        _F  = (cos (_o1) * pow (tan (PI/4 + _o1/2), _n)) / _n;
        _p0 = _R * _F / pow (tan (PI/4 + _o0/2), _n);
    }

    public void LatLon2Pixel (double lat, double lon, out int x, out int y)
    {
        double denom, easting, northing, numer;

        LatLon2Ings (lat, lon, out easting, out northing);

        /*
         * Compute how far north,east of upper left corner of image.
         */
        northing -= tfw_f;
        easting  -= tfw_e;

        /*
         * Compute corresponding pixel number.
         */
        numer = tfw_d * easting - tfw_c * northing;
        denom = tfw_a * tfw_d - tfw_b * tfw_c;
        x = (int)(numer / denom + 0.5);

        numer = tfw_b * easting - tfw_a * northing;
        denom = tfw_c * tfw_b - tfw_a * tfw_d;
        y = (int)(numer / denom + 0.5);
    }

    public void LatLon2Ings (double lat, double lon, out double easting, out double northing)
    {
        double o  = DegToRad (lat);
        double l  = DegToRad (lon);

        while (l - _l0 < -Math.PI) l += 2 * Math.PI;
        while (l - _l0 >= Math.PI) l -= 2 * Math.PI;

        double p  = _R * _F / pow (tan (PI/4 + o/2), _n);
        double t  = _n * (l - _l0);
        easting   = p * sin (t);
        northing  = _p0 - p * cos (t);
    }

    public void Pixel2LatLon (int x, int y, out double lat, out double lon)
    {
        double easting  = tfw_a * x + tfw_c * y + tfw_e;
        double northing = tfw_b * x + tfw_d * y + tfw_f;
        double _p  = sqrt (sq (easting) + sq (_p0 - northing));
        if (_F < 0) _p = -_p;
        double _t  = asin (easting / _p);
        double _o  = 2 * atan (pow (_R * _F / _p, 1.0 / _n)) - PI / 2;
        double _l  = _t / _n + _l0;
        lat = RadToDeg (_o);
        lon = RadToDeg (_l);
    }

    public void UpdateCSVFile (string outname)
    {
        StreamReader chartsReader = new StreamReader (prefix + spacename.Replace (' ', '_') + ".csv");
        string[] chartLineFields = null;
        while (true) {
            string chartLine = chartsReader.ReadLine ();
            if (chartLine == null) {
                throw new Exception ("no charts.csv line found for " + spacename);
            }
            chartLineFields = SplitQuotedCSVLine (chartLine);
            string bn = chartLineFields[15];
            if (bn == spacename) break;
        }
        chartsReader.Close ();

        chartLineFields[ 7] = tfw_a.ToString ();
        chartLineFields[ 8] = tfw_b.ToString ();
        chartLineFields[ 9] = tfw_c.ToString ();
        chartLineFields[10] = tfw_d.ToString ();
        chartLineFields[11] = tfw_e.ToString ();
        chartLineFields[12] = tfw_f.ToString ();

        StreamWriter chartsWriter = new StreamWriter (outname);
        bool first = true;
        foreach (string clf in chartLineFields) {
            if (!first) chartsWriter.Write (",");
            chartsWriter.Write (clf);
            first = false;
        }
        chartsWriter.WriteLine ("");
        chartsWriter.Close ();
    }

    public static string[] SplitQuotedCSVLine (string csvline)
    {
        bool backsl = false;
        bool quoted = false;
        List<string> tokens = new List<string> ();
        StringBuilder sb = new StringBuilder ();
        int len = csvline.Length;
        for (int i = 0; i < len; i ++) {
            char c = csvline[i];
            if ((c == ',') && !quoted && !backsl) {
                tokens.Add (sb.ToString ());
                sb = new StringBuilder ();
                continue;
            }
            if ((c == '"') && !backsl) {
                quoted = !quoted;
                continue;
            }
            if ((c == '\\') && !backsl) {
                backsl = true;
                continue;
            }
            if (backsl && (c == 'n')) c = '\n';
            sb.Append (c);
            backsl = false;
        }
        tokens.Add (sb.ToString ());
        return tokens.ToArray ();
    }
}
