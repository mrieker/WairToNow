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
 * Lambert Conical Conformal chart projection.
 */
public class LCC {
    private double pixelsize;
    private double e_e, e_F_rada, e_lam0, e_n, e_phi0, e_rho0;
    private double tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f;
    private double wft_a, wft_b, wft_c, wft_d, wft_e, wft_f;

    public static class LatLon {
        public double lat, lon;
    }

    public static class PointF {
        public double x, y;
        @Override
        public String toString ()
        {
            return x + "," + y;
        }
    }

    public LCC (double centerLat, double centerLon,
                double stanPar1, double stanPar2,
                double rada, double radb, double[] tfws)
    {
        /*
         * Compute projection parameters.
         *
         * Map Projections -- A Working Manual
         * John P. Snyder
         * US Geological Survey Professional Paper 1395
         * http://pubs.usgs.gov/pp/1395/report.pdf
         * see pages viii, v107, v296, ellipsoidal projection
         */
        e_lam0     = Math.toRadians (centerLon);
        e_phi0     = Math.toRadians (centerLat);
        double phi1 = Math.toRadians (stanPar1);
        double phi2 = Math.toRadians (stanPar2);

        e_e = Math.sqrt (1 - (radb * radb) / (rada * rada));

        // v108: equation 14-15
        double m1 = eq1415 (phi1);
        double m2 = eq1415 (phi2);

        // v108: equation 15-9a
        double t0 = eq159a (e_phi0);
        double t1 = eq159a (phi1);
        double t2 = eq159a (phi2);

        // v108: equation 15-8
        e_n = (double) ((Math.log (m1) - Math.log (m2)) / (Math.log (t1) - Math.log (t2)));

        // v108: equation 15-10
        double F = (double) (m1 / (e_n * Math.pow (t1, e_n)));
        e_F_rada = F * rada;

        // v108: equation 15-7a
        e_rho0 = (double) (e_F_rada * Math.pow (t0, e_n));

        /*
         * tfw_a..f = convert pixel to easting/northing
         */
        tfw_a = tfws[0];
        tfw_b = tfws[1];
        tfw_c = tfws[2];
        tfw_d = tfws[3];
        tfw_e = tfws[4];
        tfw_f = tfws[5];

        pixelsize = Math.hypot (tfw_b, tfw_d);

        /*
         * Compute wft_a..f = convert easting/northing to pixel:
         *                [ tfw_a tfw_b 0 ]
         *    [ x y 1 ] * [ tfw_c tfw_d 0 ] = [ e n 1 ]
         *                [ tfw_e tfw_f 1 ]
         *
         * So if wft = inv (tfw):
         *
         *                [ tfw_a tfw_b 0 ]   [ wft_a wft_b 0 ]               [ wft_a wft_b 0 ]
         *    [ x y 1 ] * [ tfw_c tfw_d 0 ] * [ wft_c wft_d 0 ] = [ e n 1 ] * [ wft_c wft_d 0 ]
         *                [ tfw_e tfw_f 1 ]   [ wft_e wft_f 1 ]               [ wft_e wft_f 1 ]
         *
         *                            [ wft_a wft_b 0 ]
         *    [ x y 1 ] = [ e n 1 ] * [ wft_c wft_d 0 ]
         *                            [ wft_e wft_f 1 ]
         */
        double[][] mat = new double[][] {
                new double[] { tfw_a, tfw_b, 0, 1, 0 },
                new double[] { tfw_c, tfw_d, 0, 0, 1 },
                new double[] { tfw_e, tfw_f, 1, 0, 0 }
        };
        Lib.RowReduce (mat);
        wft_a = mat[0][3];
        wft_b = mat[0][4];
        wft_c = mat[1][3];
        wft_d = mat[1][4];
        wft_e = mat[2][3];
        wft_f = mat[2][4];
    }

    /**
     * Given a lat/lon, compute pixel within the chart
     *
     * @param lat = latitude within the chart
     * @param lon = longitude within the chart
     * @param p   = where to return corresponding pixel
     */
    public void LatLon2ChartPixelExact (double lat, double lon, PointF p)
    {
        double phi = Math.toRadians (lat);
        double lam = Math.toRadians (lon);

        while (lam < e_lam0 - Math.PI) lam += Math.PI * 2.0F;
        while (lam > e_lam0 + Math.PI) lam -= Math.PI * 2.0F;

        /*
         * Calculate number of metres east of Longitude_of_Central_Meridian
         * and metres north of Latitude_of_Projection_Origin using Lambert
         * Conformal Ellipsoidal Projection formulae.
         */
        // v108: equation 15-9a
        double t = eq159a (phi);

        // v108: equation 15-7
        double rho = (double) (e_F_rada * Math.pow (t, e_n));

        // v108: equation 14-4
        double theta = e_n * (lam - e_lam0);

        // v107: equation 14-1 -> how far east of centerLon
        double easting = rho * Math.sin (theta);

        // v107: equation 14-2 -> how far north of centerLat
        double northing = e_rho0 - rho * Math.cos (theta);

        /*
         * Compute corresponding image pixel number.
         */
        p.x = easting * wft_a + northing * wft_c + wft_e;
        p.y = easting * wft_b + northing * wft_d + wft_f;
    }

    /**
     * Given a pixel within the chart, compute corresponding lat/lon
     *
     * @param x  = pixel within the entire side of the sectional
     * @param y  = pixel within the entire side of the sectional
     * @param ll = where to return corresponding lat/lon
     */
    public void ChartPixel2LatLonExact (double x, double y, LatLon ll)
    {
        // opposite steps of LatLon2ChartPixelExact()

        double easting  = tfw_a * x + tfw_c * y + tfw_e;
        double northing = tfw_b * x + tfw_d * y + tfw_f;

        // easting = rho * sin (theta)
        // easting / rho = sin (theta)

        // northing = e_rho0 - rho * cos (theta)
        // rho * cos (theta) = e_rho0 - northing
        // cos (theta) = (e_rho0 - northing) / rho

        double theta = (double) Math.atan (easting / (e_rho0 - northing));

        // theta = e_n * (lam - e_lam0)
        // theta / e_n = lam - e_lam0
        // theta / e_n + e_lam0 = lam

        double lam = theta / e_n + e_lam0;

        // v108: equation 14-4
        double costheta = Math.cos (e_n * (lam - e_lam0));

        // must calculate phi (latitude) with successive approximation
        // usually takes 3 or 4 iterations to resolve latitude within one pixel

        double phi = e_phi0;
        double metresneedtogonorth;
        do {
            // v108: equation 15-9a
            double t = eq159a (phi);

            // v108: equation 15-7
            double rho = (double) (e_F_rada * Math.pow (t, e_n));

            // v107: equation 14-2 -> how far north of centerLat
            double n = e_rho0 - rho * costheta;

            // update based on how far off our guess is
            // - we are trying to get phi that gives us 'northing'
            // - but we have phi that gives us 'n'
            metresneedtogonorth = northing - n;
            phi += metresneedtogonorth / (Lib.MPerNM * Lib.NMPerDeg * 180.0F / Math.PI);
        } while (Math.abs (metresneedtogonorth) > pixelsize);

        ll.lat = Math.toDegrees (phi);
        ll.lon = Lib.NormalLon (Math.toDegrees (lam));
    }

    private double eq1415 (double phi)
    {
        double w = e_e * Math.sin (phi);
        return Math.cos (phi) / Math.sqrt (1 - w * w);
    }

    private double eq159a (double phi)
    {
        double sinphi = Math.sin (phi);
        double u = (1 - sinphi) / (1 + sinphi);
        double v = (1 + e_e * sinphi) / (1 - e_e * sinphi);
        return Math.sqrt (u * Math.pow (v, e_e));
    }
}
