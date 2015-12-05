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
 * @brief Read all about chart .tif files downloaded from FAA
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

    public double tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f;
    public double wft_a, wft_b, wft_c, wft_d, wft_e, wft_f;
    public int niter;

    private double pixelsize;
    private double e_e, e_F_rada, e_lam0, e_n, e_phi0, e_rho0;

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
            string bn = chartLineFields[16];
            if (bn == spacename) break;
        }
        chartsReader.Close ();
        double centerLat = Double.Parse (chartLineFields[ 0]);
        double centerLon = Double.Parse (chartLineFields[ 1]);
        double stanPar1  = Double.Parse (chartLineFields[ 2]);
        double stanPar2  = Double.Parse (chartLineFields[ 3]);
        double rada      = Double.Parse (chartLineFields[ 6]);
        double radb      = Double.Parse (chartLineFields[ 7]);
        tfw_a            = Double.Parse (chartLineFields[ 8]);
        tfw_b            = Double.Parse (chartLineFields[ 9]);
        tfw_c            = Double.Parse (chartLineFields[10]);
        tfw_d            = Double.Parse (chartLineFields[11]);
        tfw_e            = Double.Parse (chartLineFields[12]);
        tfw_f            = Double.Parse (chartLineFields[13]);

        double[][] mat = new double[][] {
                new double[] { tfw_a, tfw_b, 0, 1, 0 },
                new double[] { tfw_c, tfw_d, 0, 0, 1 },
                new double[] { tfw_e, tfw_f, 1, 0, 0 }
        };
        RowReduce (mat);
        wft_a = mat[0][3];
        wft_b = mat[0][4];
        wft_c = mat[1][3];
        wft_d = mat[1][4];
        wft_e = mat[2][3];
        wft_f = mat[2][4];

        /*
         * Read TIF file into a bitmap.
         */
        bmp = new Bitmap (prefix + spacename + ".tif");
        width  = bmp.Width;
        height = bmp.Height;

        /*
         * Compute Lambert Conformal Projection parameters.
         *
         * Map Projections -- A Working Manual
         * John P. Snyder
         * US Geological Survey Professional Paper 1395
         * http://pubs.usgs.gov/pp/1395/report.pdf
         * see pages viii, v107, v296, ellipsoidal projection
         */
        pixelsize   = Math.Sqrt (tfw_b * tfw_b + tfw_d * tfw_d);
        e_lam0      = ToRadians (centerLon);
        e_phi0      = ToRadians (centerLat);
        double phi1 = ToRadians (stanPar1);
        double phi2 = ToRadians (stanPar2);

        e_e = Math.Sqrt (1 - (radb * radb) / (rada * rada));

        // v108: equation 14-15
        double m1 = eq1415 (phi1);
        double m2 = eq1415 (phi2);

        // v108: equation 15-9a
        double t0 = eq159a (e_phi0);
        double t1 = eq159a (phi1);
        double t2 = eq159a (phi2);

        // v108: equation 15-8
        e_n = (Math.Log (m1) - Math.Log (m2)) / (Math.Log (t1) - Math.Log (t2));

        // v108: equation 15-10
        double F = m1 / (e_n * Math.Pow (t1, e_n));
        e_F_rada = F * rada;

        // v108: equation 15-7a
        e_rho0 = e_F_rada * Math.Pow (t0, e_n);
    }

    public void LatLon2Pixel (double lat, double lon, out int x, out int y)
    {
        double phi = ToRadians (lat);
        double lam = ToRadians (lon);

        /*
         * Calculate number of metres east of Longitude_of_Central_Meridian
         * and metres north of Latitude_of_Projection_Origin using Lambert
         * Conformal Ellipsoidal Projection formulae.
         */
        // v108: equation 15-9a
        double t = eq159a (phi);

        // v108: equation 15-7
        double rho = (double) (e_F_rada * Math.Pow (t, e_n));

        // v108: equation 14-4
        double theta = e_n * (lam - e_lam0);

        // v107: equation 14-1 -> how far east of centerLon
        double easting = rho * Math.Sin (theta);

        // v107: equation 14-2 -> how far north of centerLat
        double northing = e_rho0 - rho * Math.Cos (theta);

        /*
         * Compute corresponding image pixel number.
         */
        x = (int) Math.Round (easting * wft_a + northing * wft_c + wft_e);
        y = (int) Math.Round (easting * wft_b + northing * wft_d + wft_f);
    }

    public void Pixel2LatLon (int x, int y, out double lat, out double lon)
    {
        // opposite steps of LatLon2Pixel()

        double easting  = tfw_a * x + tfw_c * y + tfw_e;
        double northing = tfw_b * x + tfw_d * y + tfw_f;

        // easting = rho * sin (theta)
        // easting / rho = sin (theta)

        // northing = e_rho0 - rho * cos (theta)
        // rho * cos (theta) = e_rho0 - northing
        // cos (theta) = (e_rho0 - northing) / rho

        double theta = Math.Atan (easting / (e_rho0 - northing));

        // theta = e_n * (lam - e_lam0)
        // theta / e_n = lam - e_lam0
        // theta / e_n + e_lam0 = lam

        double lam = theta / e_n + e_lam0;

        // v108: equation 14-4
        double costheta = Math.Cos (e_n * (lam - e_lam0));

        // must calculate phi (latitude) with successive approximation
        // usually takes 3 or 4 iterations to resolve latitude within one pixel

        double phi = e_phi0;
        double metresneedtogonorth;
        niter = 0;
        do {
            niter ++;

            // v108: equation 15-9a
            double t = eq159a (phi);

            // v108: equation 15-7
            double rho = e_F_rada * Math.Pow (t, e_n);
 
            // v107: equation 14-2 -> how far north of centerLat
            double n = e_rho0 - rho * costheta;

            // update based on how far off our guess is
            // - we are trying to get phi that gives us 'northing'
            // - but we have phi that gives us 'n'
            metresneedtogonorth = northing - n;
            phi += metresneedtogonorth / (MPerNM * NMPerDeg * 180.0 / Math.PI);
        } while (Math.Abs (metresneedtogonorth) > pixelsize);

        lat = ToDegrees (phi);
        lon = ToDegrees (lam);
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

    private static double ToRadians (double d) { return d / 180.0 * Math.PI; }
    private static double ToDegrees (double r) { return r / Math.PI * 180.0; }

    private const double MPerNM   = 1852.0;
    private const double NMPerDeg = 60.0;

    private double eq1415 (double phi)
    {
        double w = e_e * Math.Sin (phi);
        return Math.Cos (phi) / Math.Sqrt (1 - w * w);
    }

    private double eq159a (double phi)
    {
        double sinphi = Math.Sin (phi);
        double u = (1 - sinphi) / (1 + sinphi);
        double v = (1 + e_e * sinphi) / (1 - e_e * sinphi);
        return Math.Sqrt (u * Math.Pow (v, e_e));
    }

    public static void RowReduce (double[][] T)
    {
        double pivot;
        int trows = T.Length;
        int tcols = T[0].Length;

        for (int row = 0; row < trows; row ++) {
            double[] T_row_ = T[row];

            /*
             * Make this row's major diagonal colum one by
             * swapping it with a row below that has the
             * largest value in this row's major diagonal
             * column, then dividing the row by that number.
             */
            pivot = T_row_[row];
            int bestRow = row;
            for (int swapRow = row; ++ swapRow < trows;) {
                double swapPivot = T[swapRow][row];
                if (Math.Abs (pivot) < Math.Abs (swapPivot)) {
                    pivot   = swapPivot;
                    bestRow = swapRow;
                }
            }
            if (pivot == 0.0) throw new Exception ("not invertable");
            if (bestRow != row) {
                double[] tmp = T_row_;
                T[row] = T_row_ = T[bestRow];
                T[bestRow] = tmp;
            }
            if (pivot != 1.0) {
                for (int col = row; col < tcols; col ++) {
                    T_row_[col] /= pivot;
                }
            }

            /*
             * Subtract this row from all below it such that we zero out
             * this row's major diagonal column in all rows below.
             */
            for (int rr = row; ++ rr < trows;) {
                double[] T_rr_ = T[rr];
                pivot = T_rr_[row];
                if (pivot != 0.0) {
                    for (int cc = row; cc < tcols; cc ++) {
                        T_rr_[cc] -= pivot * T_row_[cc];
                    }
                }
            }
        }

        for (int row = trows; -- row >= 0;) {
            double[] T_row_ = T[row];
            for (int rr = row; -- rr >= 0;) {
                double[] T_rr_ = T[rr];
                pivot = T_rr_[row];
                if (pivot != 0.0) {
                    for (int cc = row; cc < tcols; cc ++) {
                        T_rr_[cc] -= pivot * T_row_[cc];
                    }
                }
            }
        }
    }
}
