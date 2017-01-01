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

package com.outerworldapps.wairtonow;

import android.graphics.Point;
import android.support.annotation.NonNull;

/**
 * Lambert Conical Conformal chart projection.
 */
public class LambertConicalConformal {
    private float pixelsize;
    private float e_e, e_F_rada, e_lam0, e_n, e_phi0, e_rho0;
    private float tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f;
    private float wft_a, wft_b, wft_c, wft_d, wft_e, wft_f;

    public LambertConicalConformal (float centerLat, float centerLon,
                                    float stanPar1, float stanPar2,
                                    float rada, float radb, float[] tfws)
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
        e_lam0     = Mathf.toRadians (centerLon);
        e_phi0     = Mathf.toRadians (centerLat);
        float phi1 = Mathf.toRadians (stanPar1);
        float phi2 = Mathf.toRadians (stanPar2);

        e_e = Mathf.sqrt (1 - (radb * radb) / (rada * rada));

        // v108: equation 14-15
        float m1 = eq1415 (phi1);
        float m2 = eq1415 (phi2);

        // v108: equation 15-9a
        float t0 = eq159a (e_phi0);
        float t1 = eq159a (phi1);
        float t2 = eq159a (phi2);

        // v108: equation 15-8
        e_n = (float) ((Math.log (m1) - Math.log (m2)) / (Math.log (t1) - Math.log (t2)));

        // v108: equation 15-10
        float F = (float) (m1 / (e_n * Math.pow (t1, e_n)));
        e_F_rada = F * rada;

        // v108: equation 15-7a
        e_rho0 = (float) (e_F_rada * Math.pow (t0, e_n));

        /*
         * tfw_a..f = convert pixel to easting/northing
         */
        tfw_a = tfws[0];
        tfw_b = tfws[1];
        tfw_c = tfws[2];
        tfw_d = tfws[3];
        tfw_e = tfws[4];
        tfw_f = tfws[5];

        pixelsize = Mathf.hypot (tfw_b, tfw_d);

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
        float[][] mat = new float[][] {
                new float[] { tfw_a, tfw_b, 0, 1, 0 },
                new float[] { tfw_c, tfw_d, 0, 0, 1 },
                new float[] { tfw_e, tfw_f, 1, 0, 0 }
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
    public void LatLon2ChartPixelExact (float lat, float lon, @NonNull Point p)
    {
        float phi = Mathf.toRadians (lat);
        float lam = Mathf.toRadians (lon);

        while (lam < e_lam0 - Mathf.PI) lam += Mathf.PI * 2.0F;
        while (lam > e_lam0 + Mathf.PI) lam -= Mathf.PI * 2.0F;

        /*
         * Calculate number of metres east of Longitude_of_Central_Meridian
         * and metres north of Latitude_of_Projection_Origin using Lambert
         * Conformal Ellipsoidal Projection formulae.
         */
        // v108: equation 15-9a
        float t = eq159a (phi);

        // v108: equation 15-7
        float rho = (float) (e_F_rada * Math.pow (t, e_n));

        // v108: equation 14-4
        float theta = e_n * (lam - e_lam0);

        // v107: equation 14-1 -> how far east of centerLon
        float easting = rho * Mathf.sin (theta);

        // v107: equation 14-2 -> how far north of centerLat
        float northing = e_rho0 - rho * Mathf.cos (theta);

        /*
         * Compute corresponding image pixel number.
         */
        p.x = Math.round (easting * wft_a + northing * wft_c + wft_e);
        p.y = Math.round (easting * wft_b + northing * wft_d + wft_f);
    }

    /**
     * Given a pixel within the chart, compute corresponding lat/lon
     *
     * @param x  = pixel within the entire side of the sectional
     * @param y  = pixel within the entire side of the sectional
     * @param ll = where to return corresponding lat/lon
     */
    public void ChartPixel2LatLonExact (float x, float y, @NonNull LatLon ll)
    {
        // opposite steps of LatLon2ChartPixelExact()

        float easting  = tfw_a * x + tfw_c * y + tfw_e;
        float northing = tfw_b * x + tfw_d * y + tfw_f;

        // easting = rho * sin (theta)
        // easting / rho = sin (theta)

        // northing = e_rho0 - rho * cos (theta)
        // rho * cos (theta) = e_rho0 - northing
        // cos (theta) = (e_rho0 - northing) / rho

        float theta = (float) Math.atan (easting / (e_rho0 - northing));

        // theta = e_n * (lam - e_lam0)
        // theta / e_n = lam - e_lam0
        // theta / e_n + e_lam0 = lam

        float lam = theta / e_n + e_lam0;

        // v108: equation 14-4
        float costheta = Mathf.cos (e_n * (lam - e_lam0));

        // must calculate phi (latitude) with successive approximation
        // usually takes 3 or 4 iterations to resolve latitude within one pixel

        float phi = e_phi0;
        float metresneedtogonorth;
        do {
            // v108: equation 15-9a
            float t = eq159a (phi);

            // v108: equation 15-7
            float rho = (float) (e_F_rada * Math.pow (t, e_n));

            // v107: equation 14-2 -> how far north of centerLat
            float n = e_rho0 - rho * costheta;

            // update based on how far off our guess is
            // - we are trying to get phi that gives us 'northing'
            // - but we have phi that gives us 'n'
            metresneedtogonorth = northing - n;
            phi += metresneedtogonorth / (Lib.MPerNM * Lib.NMPerDeg * 180.0F / Mathf.PI);
        } while (Math.abs (metresneedtogonorth) > pixelsize);

        ll.lat = Mathf.toDegrees (phi);
        ll.lon = Lib.NormalLon (Mathf.toDegrees (lam));
    }

    private float eq1415 (float phi)
    {
        float w = e_e * Mathf.sin (phi);
        return Mathf.cos (phi) / Mathf.sqrt (1 - w * w);
    }

    private float eq159a (float phi)
    {
        float sinphi = Mathf.sin (phi);
        float u = (1 - sinphi) / (1 + sinphi);
        float v = (1 + e_e * sinphi) / (1 - e_e * sinphi);
        return Mathf.sqrt (u * Math.pow (v, e_e));
    }
}
