//    Copyright (C) 2021, Mike Rieker, Beverly, MA USA
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

import android.support.annotation.NonNull;

/**
 * Bi-Linear LatLon <-> ChartPixel projection.
 */
public class BiLinProj implements IExactMapper {
    public double tfw_a, tfw_b, tfw_c, tfw_d, tfw_e, tfw_f, tfw_g, tfw_h;
    public double wft_a, wft_b, wft_c, wft_d, wft_e, wft_f, wft_g, wft_h;

    public BiLinProj (String[] csvs, int i)
    {
        tfw_a = Double.parseDouble (csvs[i++]);
        tfw_b = Double.parseDouble (csvs[i++]);
        tfw_c = Double.parseDouble (csvs[i++]);
        tfw_d = Double.parseDouble (csvs[i++]);
        tfw_e = Double.parseDouble (csvs[i++]);
        tfw_f = Double.parseDouble (csvs[i++]);
        tfw_g = Double.parseDouble (csvs[i++]);
        tfw_h = Double.parseDouble (csvs[i++]);

        wft_a = Double.parseDouble (csvs[i++]);
        wft_b = Double.parseDouble (csvs[i++]);
        wft_c = Double.parseDouble (csvs[i++]);
        wft_d = Double.parseDouble (csvs[i++]);
        wft_e = Double.parseDouble (csvs[i++]);
        wft_f = Double.parseDouble (csvs[i++]);
        wft_g = Double.parseDouble (csvs[i++]);
        wft_h = Double.parseDouble (csvs[i]);
    }

    public BiLinProj ()
    { }

    /**
     * Given a lat/lon, compute pixel within the chart
     *
     * @param lat = latitude within the chart
     * @param lon = longitude within the chart
     * @param p   = where to return corresponding pixel
     */
    public void LatLon2ChartPixelExact (double lat, double lon, @NonNull PointD p)
    {
        p.x = wft_a * lon + wft_c * lat + wft_e + wft_g * lon * lat;
        p.y = wft_b * lon + wft_d * lat + wft_f + wft_h * lon * lat;
    }

    /**
     * Given a pixel within the chart, compute corresponding lat/lon
     *
     * @param x  = pixel within the entire side of the sectional
     * @param y  = pixel within the entire side of the sectional
     * @param ll = where to return corresponding lat/lon
     */
    public void ChartPixel2LatLonExact (double x, double y, @NonNull LatLon ll)
    {
        ll.lon = tfw_a * x + tfw_c * y + tfw_e + tfw_g * x * y;
        ll.lat = tfw_b * x + tfw_d * y + tfw_f + tfw_h * x * y;
    }
}
