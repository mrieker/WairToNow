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

package com.outerworldapps.wairtonow;


/**
 * Map lat/lon to canvas pixel x,y.
 */
public class PixelMapper {
    public double canvasNorthLat, canvasSouthLat;
    public double canvasEastLon, canvasWestLon;
    public double centerLat, centerLon;
    public int canvasWidth, canvasHeight;

    public double lastTlLat;
    public double lastTlLon;
    public double lastTrLat;
    public double lastTrLon;
    public double lastBlLat;
    public double lastBlLon;
    public double lastBrLat;
    public double lastBrLon;

    private double ll2pix_0_0, ll2pix_0_1, ll2pix_0_2;
    private double ll2pix_1_0, ll2pix_1_1, ll2pix_1_2;
    private double ll2pix_2_0, ll2pix_2_1, ll2pix_2_2;
    private double pix2ll_0_0, pix2ll_0_1, pix2ll_0_2;
    private double pix2ll_1_0, pix2ll_1_1, pix2ll_1_2;
    private double pix2ll_2_0, pix2ll_2_1, pix2ll_2_2;
    private double[][] mat89  = new double[][] { new double[9], new double[9], new double[9], new double[9],
                                               new double[9], new double[9], new double[9], new double[9] };
    private double[][] mat36  = new double[][] { new double[6], new double[6], new double[6] };
    private double[][] ll2pix = new double[][] { new double[3], new double[3], new double[3] };

    /**
     * Set up transformation to map the 4 corners of the canvas to the given lat/lons.
     * @param width  = canvas width
     * @param height = canvas height
     * @param tlLat  = top left corner latitude
     * @param tlLon  = top left corner longitude
     * @param trLat  = top right corner latitude
     * @param trLon  = top right corner longitude
     * @param blLat  = bottom left corner latitude
     * @param blLon  = bottom left corner longitude
     * @param brLat  = bottom right corner latitude
     * @param brLon  = bottom right corner longitude
     */
    @SuppressWarnings("ConstantConditions")
    public void setup (int width, int height,
            double tlLat, double tlLon, double trLat, double trLon,
            double blLat, double blLon, double brLat, double brLon)
    {
        tlLon = Lib.NormalLon (tlLon);
        trLon = Lib.NormalLon (trLon);
        blLon = Lib.NormalLon (blLon);
        brLon = Lib.NormalLon (brLon);

        if ((width == canvasWidth) && (height == canvasHeight) &&
            (tlLat == lastTlLat) && (tlLon == lastTlLon) && 
            (trLat == lastTrLat) && (trLon == lastTrLon) && 
            (blLat == lastBlLat) && (blLon == lastBlLon) && 
            (brLat == lastBrLat) && (brLon == lastBrLon)) return;

        canvasWidth  = width;
        canvasHeight = height;
        lastTlLat    = tlLat;
        lastTlLon    = tlLon;
        lastTrLat    = trLat;
        lastTrLon    = trLon;
        lastBlLat    = blLat;
        lastBlLon    = blLon;
        lastBrLat    = brLat;
        lastBrLon    = brLon;

        /*
         * Wrap longitudes if necessary to make abs (difference) < 180 degrees.
         * Note that the map might be rotated downside-up or sideways so right
         * isn't necessarily east of left.  But we can assume they are all
         * within 180 degrees of each other.
         */
        if ((tlLon < -90.0 || trLon < -90.0 || blLon < -90.0 || brLon < -90.0) &&
            (tlLon >= 90.0 || trLon >= 90.0 || blLon >= 90.0 || brLon >= 90.0)) {
            if (tlLon < 0.0) tlLon += 360.0;
            if (trLon < 0.0) trLon += 360.0;
            if (blLon < 0.0) blLon += 360.0;
            if (brLon < 0.0) brLon += 360.0;
        }

        /*
         * Find north/south limits of canvas no matter how it is twisted on the charts.
         */
        canvasSouthLat = Math.min (Math.min (tlLat, trLat), Math.min (blLat, brLat));
        canvasNorthLat = Math.max (Math.max (tlLat, trLat), Math.max (blLat, brLat));
        if (canvasNorthLat - canvasSouthLat > 90.0) throw new RuntimeException ("too much latitude");

        /*
         * Find east/west limits of canvas no matter how it is twisted on the charts.
         */
        canvasWestLon = Math.min (Math.min (tlLon, trLon), Math.min (blLon, brLon));
        canvasEastLon = Math.max (Math.max (tlLon, trLon), Math.max (blLon, brLon));
        if (canvasEastLon - canvasWestLon > 90.0) throw new RuntimeException ("too much longitude");

        /*
         * Calculate latitude and longitude in center of canvas.
         */
        centerLat = (canvasNorthLat + canvasSouthLat) / 2.0;
        centerLon = (canvasEastLon + canvasWestLon) / 2.0;

        /*
         * Set up matrix to convert latlon -> canvas pixel.
         */
        double tlCanX = 0;
        double tlCanY = 0;
        double trCanX = canvasWidth;
        double trCanY = 0;
        double blCanX = 0;
        double blCanY = canvasHeight;
        double brCanX = canvasWidth;
        double brCanY = canvasHeight;

        // [ a b c ]   [ lat ]   [ x * w ]
        // [ d e f ] * [ lon ] = [ y * w ]
        // [ g h 1 ]   [  1  ]   [   w   ]

        //   a * lat + b * lon +_c = x * w
        //   d * lat + e * lon + f = y * w
        //   g * lat + h * lon + 1 =   w

        //   a * lat + b * lon +_c = x * (g * lat + h * lon + 1)
        //   a * lat + b * lon + c = x * g * lat + x * h * lon + x
        //   a * lat + b * lon + c - g * x * lat - h * x * lon = x

        //   d * lat + e + lon + f - g * y * lat - h * y * lon = y

        //    a      b    c    d      e    f         g             h      =   1
        //  tlLat  tlLon  1    0      0    0  -tlCanX*tlLat -tlCanX*tlLon   tlCanX
        //  trLat  trLon  1    0      0    0  -trCanX*tlLat -trCanX*tlLon   trCanX
        //  blLat  blLon  1    0      0    0  -blCanX*tlLat -blCanX*tlLon   blCanX
        //  brLat  brLon  1    0      0    0  -brCanX*tlLat -brCanX*tlLon   brCanX
        //    0      0    0  tlLat  tlLon  1  -tlCanY*tlLat -tlCanY*tlLon   tlCanY
        //    0      0    0  trLat  trLon  1  -trCanY*trLat -trCanY*trLon   trCanY
        //    0      0    0  blLat  blLon  1  -blCanY*blLat -blCanY*blLon   blCanY
        //    0      0    0  brLat  brLon  1  -brCanY*brLat -brCanY*brLon   brCanY

        double[] m89r;
        m89r = mat89[0]; m89r[0] = tlLat; m89r[1] = tlLon; m89r[2] = 1; m89r[3] =     0; m89r[4] =     0; m89r[5] = 0; m89r[6] = -tlCanX * tlLat; m89r[7] = -tlCanX * tlLon; m89r[8] = tlCanX;
        m89r = mat89[1]; m89r[0] = trLat; m89r[1] = trLon; m89r[2] = 1; m89r[3] =     0; m89r[4] =     0; m89r[5] = 0; m89r[6] = -trCanX * trLat; m89r[7] = -trCanX * trLon; m89r[8] = trCanX;
        m89r = mat89[2]; m89r[0] = blLat; m89r[1] = blLon; m89r[2] = 1; m89r[3] =     0; m89r[4] =     0; m89r[5] = 0; m89r[6] = -blCanX * blLat; m89r[7] = -blCanX * blLon; m89r[8] = blCanX;
        m89r = mat89[3]; m89r[0] = brLat; m89r[1] = brLon; m89r[2] = 1; m89r[3] =     0; m89r[4] =     0; m89r[5] = 0; m89r[6] = -brCanX * brLat; m89r[7] = -brCanX * brLon; m89r[8] = brCanX;
        m89r = mat89[4]; m89r[0] =     0; m89r[1] =     0; m89r[2] = 0; m89r[3] = tlLat; m89r[4] = tlLon; m89r[5] = 1; m89r[6] = -tlCanY * tlLat; m89r[7] = -tlCanY * tlLon; m89r[8] = tlCanY;
        m89r = mat89[5]; m89r[0] =     0; m89r[1] =     0; m89r[2] = 0; m89r[3] = trLat; m89r[4] = trLon; m89r[5] = 1; m89r[6] = -trCanY * trLat; m89r[7] = -trCanY * trLon; m89r[8] = trCanY;
        m89r = mat89[6]; m89r[0] =     0; m89r[1] =     0; m89r[2] = 0; m89r[3] = blLat; m89r[4] = blLon; m89r[5] = 1; m89r[6] = -blCanY * blLat; m89r[7] = -blCanY * blLon; m89r[8] = blCanY;
        m89r = mat89[7]; m89r[0] =     0; m89r[1] =     0; m89r[2] = 0; m89r[3] = brLat; m89r[4] = brLon; m89r[5] = 1; m89r[6] = -brCanY * brLat; m89r[7] = -brCanY * brLon; m89r[8] = brCanY;

        Lib.RowReduce (mat89);

        //  a b c d e f g h = 1
        //  1 0 0 0 0 0 0 0   a
        //  0 1 0 0 0 0 0 0   b
        //  0 0 1 0 0 0 0 0   c
        //  0 0 0 1 0 0 0 0   d
        //  0 0 0 0 1 0 0 0   e
        //  0 0 0 0 0 1 0 0   f
        //  0 0 0 0 0 0 1 0   g
        //  0 0 0 0 0 0 0 1   h

        int j = 0;
        for (int r = 0; r < 3; r ++) {
            double[] ll2pixrow = ll2pix[r];
            for (int c = 0; c < 3; c ++) {
                ll2pixrow[c] = (j < 8) ? mat89[j++][8] : 1;
            }
        }

        /*
         * Invert the matrix to convert from canvas pixel -> latlon.
         */
        for (int r = 0; r < 3; r ++) {
            double[] mat36row = mat36[r];
            double[] ll2pixrow = ll2pix[r];
            for (int c = 0; c < 3; c ++) {
                mat36row[c] = ll2pixrow[c];
                mat36row[c+3] = 0;
            }
            mat36row[r+3] = 1;
        }
        ll2pix_0_0 = mat36[0][0];
        ll2pix_0_1 = mat36[0][1];
        ll2pix_0_2 = mat36[0][2];
        ll2pix_1_0 = mat36[1][0];
        ll2pix_1_1 = mat36[1][1];
        ll2pix_1_2 = mat36[1][2];
        ll2pix_2_0 = mat36[2][0];
        ll2pix_2_1 = mat36[2][1];
        ll2pix_2_2 = mat36[2][2];

        Lib.RowReduce (mat36);

        pix2ll_0_0 = mat36[0][3];
        pix2ll_0_1 = mat36[0][4];
        pix2ll_0_2 = mat36[0][5];
        pix2ll_1_0 = mat36[1][3];
        pix2ll_1_1 = mat36[1][4];
        pix2ll_1_2 = mat36[1][5];
        pix2ll_2_0 = mat36[2][3];
        pix2ll_2_1 = mat36[2][4];
        pix2ll_2_2 = mat36[2][5];
    }

    /**
     * Convert a canvas pixel x,y to corresponding lat/lon
     * using linear interpolation from the four corners.
     * @param canvasPixX = canvas pixel X
     * @param canvasPixY = canvas pixel Y
     * @param ll = where to put resultant lat/lon
     */
    public void CanPix2LatLonAprox (double canvasPixX, double canvasPixY, LatLon ll)
    {
        double w =  pix2ll_2_0 * canvasPixX + pix2ll_2_1 * canvasPixY + pix2ll_2_2;
        ll.lat  = (pix2ll_0_0 * canvasPixX + pix2ll_0_1 * canvasPixY + pix2ll_0_2) / w;
        ll.lon  = Lib.NormalLon ((pix2ll_1_0 * canvasPixX + pix2ll_1_1 * canvasPixY + pix2ll_1_2) / w);
    }

    /**
     * Convert a lat/lon to corresponding canvas pixel x,y
     * using linear interpolation from the four corners.
     * @param lat = latitude to look for
     * @param lon = longitude to look for
     * @param canpix = where to return resultant canvas x,y
     * @return true iff lat/lon is on display
     */
    public boolean LatLon2CanPixAprox (double lat, double lon, PointD canpix)
    {
        lon = Lib.NormalLon (lon);
        double  w =  ll2pix_2_0 * lat + ll2pix_2_1 * lon + ll2pix_2_2;
        canpix.x = (ll2pix_0_0 * lat + ll2pix_0_1 * lon + ll2pix_0_2) / w;
        canpix.y = (ll2pix_1_0 * lat + ll2pix_1_1 * lon + ll2pix_1_2) / w;
        return (canpix.x >= 0) && (canpix.x < canvasWidth) && (canpix.y >= 0) && (canpix.y < canvasHeight);
    }
}
