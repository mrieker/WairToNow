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
 * @brief Test Lambert projection
 *        Conversion from lat/lon to pixel
 *
 *        Map Projections -- A Working Manual
 *        John P. Snyder
 *        US Geological Survey Professional Paper 1395
 *
 *        http://pubs.usgs.gov/pp/1395/report.pdf
 *        see pages vviiip10, v107p119, v295p307
 *
 *  gmcs -debug -out:LambertTest.exe LambertTest.cs
 *  mono --debug LambertTest.exe
 */

using System;

public class LambertTest {

    private const double PI = Math.PI;
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

    public static void Main (string[] args)
    {
        double R  = 1.0;                // radius of sphere
        double o1 = DegToRad ( 33.0);   // standard parallel
        double o2 = DegToRad ( 45.0);   // standard parallel
        double o0 = DegToRad ( 23.0);   // origin latitude
        double l0 = DegToRad (-96.0);   // origin longitude

        double n  = ln (cos (o1) / cos (o2)) / ln (tan (PI/4 + o2/2) / tan (PI/4 + o1/2));
        double F  = (cos (o1) * pow (tan (PI/4 + o1/2), n)) / n;
        double p0 = R * F / pow (tan (PI/4 + o0/2), n);

        Console.WriteLine ("n  = " + n);                      // 0.630477697315426
        Console.WriteLine ("F  = " + F);                      // 1.95500020159379
        Console.WriteLine ("p0 = " + p0);                     // 1.50714288113113

        double o  = DegToRad ( 35.0);   // point to find latitude
        double l  = DegToRad (-75.0);   // point to find longitude

        double p  = R * F / pow (tan (PI/4 + o/2), n);
        double t  = n * (l - l0);
        double x  = p * sin (t);
        double y  = p0 - p * cos (t);

        Console.WriteLine ("p  = " + p);                      // 1.29536355479984
        Console.WriteLine ("t  = " + RadToDeg (t) + " deg");  // 13.2400316436239
        Console.WriteLine ("x  = " + x);                      // 0.296678459942507
        Console.WriteLine ("y  = " + y);                      // 0.246211229331627

        // reverse computation - given x,y,n,F,p0 compute o,l

        double _p = sqrt (sq (x) + sq (p0 - y));
        if (F < 0) _p = -_p;
        double _t = asin (x / _p);
        double _o = 2 * atan (pow (R * F / _p, 1.0 / n)) - PI / 2;
        double _l = _t / n + l0;

        Console.WriteLine ("_p = " + _p);                      // 1.29536355479984
        Console.WriteLine ("_t = " + RadToDeg (_t) + " deg");  // 13.2400316436239
        Console.WriteLine ("_o = " + RadToDeg (_o) + " deg");  //  35
        Console.WriteLine ("_l = " + RadToDeg (_l) + " deg");  // -75
    }
}
