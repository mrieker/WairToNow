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


import java.util.ArrayList;

public class Lib {
    public static final double KtPerMPS = 1.94384;
    public static final float NMPerDeg  = 60.0F;
    public static final float MMPerIn   = 25.4F;    // millimetres per inch
    public static final double SMPerNM  = 1.15078;
    public static final int   MPerNM    = 1852;     // metres per naut mile

    /**
     * Split a comma-separated value string into its various substrings.
     * @param line = comma-separated line
     * @return array of the various substrings
     */
    public static String[] QuotedCSVSplit (String line)
    {
        int len = line.length ();
        ArrayList<String> cols = new ArrayList<String> (len + 1);
        boolean quoted = false;
        boolean escapd = false;
        StringBuilder sb = new StringBuilder (len);
        for (int i = 0;; i ++) {
            char c = 0;
            if (i < len) c = line.charAt (i);
            if (!escapd && (c == '"')) {
                quoted = !quoted;
                continue;
            }
            if (!escapd && (c == '\\')) {
                escapd = true;
                continue;
            }
            if ((!escapd && !quoted && (c == ',')) || (c == 0)) {
                cols.add (sb.toString ());
                if (c == 0) break;
                sb.delete (0, sb.length ());
                continue;
            }
            if (escapd && (c == 'n')) c = '\n';
            if (escapd && (c == 'z')) c = 0;
            sb.append (c);
            escapd = false;
        }
        String[] array = new String[cols.size()];
        return cols.toArray (array);
    }

    /**
     * Create a quoted string suitable for QuotedCSVSplit().
     */
    public static String QuotedString (String unquoted)
    {
        int len = unquoted.length ();
        StringBuilder sb = new StringBuilder (len + 2);
        sb.append ('"');
        for (int i = 0; i < len; i ++) {
            char c = unquoted.charAt (i);
            switch (c) {
                case '\\': {
                    sb.append ("\\\\");
                    break;
                }
                case '\n': {
                    sb.append ("\\n");
                    break;
                }
                case 0: {
                    sb.append ("\\z");
                    break;
                }
                case '"': {
                    sb.append ("\\\"");
                    break;
                }
                default: {
                    sb.append (c);
                    break;
                }
            }
        }
        sb.append ('"');
        return sb.toString ();
    }

    /**
     * Return westmost longitude, normalized to -180..+179.999999999
     */
    public static double Westmost (double lon1, double lon2)
    {
        // make sure lon2 comes at or after lon1 by less than 360deg
        while (lon2 < lon1) lon2 += 360.0;
        while (lon2 - 360.0 >= lon1) lon2 -= 360.0;

        // if lon2 is east of lon1 by at most 180deg, lon1 is westmost
        // if lon2 is east of lon1 by more than 180, lon2 is westmost
        double westmost = (lon2 - lon1 < 180.0) ? lon1 : lon2;

        // normalize
        while (westmost < -180.0) westmost += 360.0;
        while (westmost >= 180.0) westmost -= 360.0;
        return westmost;
    }

    /**
     * Return eastmost longitude, normalized to -180..+179.999999999
     */
    public static double Eastmost (double lon1, double lon2)
    {
        // make sure lon2 comes at or after lon1 by less than 360deg
        while (lon2 < lon1) lon2 += 360.0;
        while (lon2 - 360.0 >= lon1) lon2 -= 360.0;

        // if lon2 is east of lon1 by at most 180deg, lon2 is eastmost
        // if lon2 is east of lon1 by more than 180, lon1 is eastmost
        double eastmost = (lon2 - lon1 < 180.0) ? lon2 : lon1;

        // normalize
        while (eastmost < -180.0) eastmost += 360.0;
        while (eastmost >= 180.0) eastmost -= 360.0;
        return eastmost;
    }

    /**
     * Compute great-circle distance between two lat/lon co-ordinates
     * @return distance between two points (in nm)
     */
    public static double LatLonDist (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        return Math.toDegrees (LatLonDist_rad (srcLat, srcLon, dstLat, dstLon)) * NMPerDeg;
    }
    public static double LatLonDist_rad (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_distance
        double sLat = srcLat / 180 * Math.PI;
        double sLon = srcLon / 180 * Math.PI;
        double fLat = dstLat / 180 * Math.PI;
        double fLon = dstLon / 180 * Math.PI;
        double dLon = fLon - sLon;
        double t1   = Sq (Math.cos (fLat) * Math.sin (dLon));
        double t2   = Sq (Math.cos (sLat) * Math.sin (fLat) - Math.sin (sLat) * Math.cos (fLat) * Math.cos (dLon));
        double t3   = Math.sin (sLat) * Math.sin (fLat);
        double t4   = Math.cos (sLat) * Math.cos (fLat) * Math.cos (dLon);
        return Math.atan2 (Math.sqrt (t1 + t2), t3 + t4);
    }

    private static double Sq (double x) { return x*x; }

    /**
     * Compute great-circle true course from one lat/lon to another lat/lon
     * @return true course (in degrees) at source point
     */
    public static double LatLonTC (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        return LatLonTC_rad (srcLat, srcLon, dstLat, dstLon) * 180.0 / Math.PI;
    }
    public static double LatLonTC_rad (double srcLat, double srcLon, double dstLat, double dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_navigation
        double sLat = srcLat / 180 * Math.PI;
        double sLon = srcLon / 180 * Math.PI;
        double fLat = dstLat / 180 * Math.PI;
        double fLon = dstLon / 180 * Math.PI;
        double dLon = fLon - sLon;
        double t1 = Math.cos(sLat) * Math.tan(fLat);
        double t2 = Math.sin(sLat) * Math.cos(dLon);
        return Math.atan2 (Math.sin (dLon), t1 - t2);
    }

    /**
     * Compute new lat/lon given old lat/lon, heading (degrees), distance (nautical miles)
     * http://stackoverflow.com/questions/7222382/get-lat-long-given-current-point-distance-and-bearing
     */
    public static double LatHdgDist2Lat (double latdeg, double hdgdeg, double distnm)
    {
        double distrad = Math.toRadians (distnm / NMPerDeg);
        double latrad  = Math.toRadians (latdeg);
        double hdgrad  = Math.toRadians (hdgdeg);

        latrad = Math.asin (Math.sin (latrad) * Math.cos (distrad) + Math.cos (latrad) * Math.sin (distrad) * Math.cos (hdgrad));
        return Math.toDegrees (latrad);
    }
    public static double LatLonHdgDist2Lon (double latdeg, double londeg, double hdgdeg, double distnm)
    {
        double distrad = Math.toRadians (distnm / NMPerDeg);
        double latrad  = Math.toRadians (latdeg);
        double hdgrad  = Math.toRadians (hdgdeg);

        double newlatrad = Math.asin (Math.sin (latrad) * Math.cos (distrad) + Math.cos (latrad) * Math.sin (distrad) * Math.cos (hdgrad));
        double lonrad = Math.atan2 (Math.sin (hdgrad) * Math.sin (distrad) * Math.cos (latrad), Math.cos (distrad) - Math.sin (latrad) * Math.sin (newlatrad));
        return Math.toDegrees (lonrad) + londeg;
    }

    /**
     * Given a longitude along a course, what is corresponding latitude?
     * Used for plotting courses that are primarily east/west.
     * @return latitude in range -90 to +90
     */
    public static double GCLon2Lat (double beglatdeg, double beglondeg, double endlatdeg, double endlondeg, double londeg)
    {
        // code below assumes westbound course
        while (beglondeg < -180.0) beglondeg += 360.0;
        while (beglondeg >= 180.0) beglondeg -= 360.0;
        while (endlondeg < -180.0) endlondeg += 360.0;
        while (endlondeg >= 180.0) endlondeg -= 360.0;
        if (endlondeg < beglondeg) endlondeg += 360.0;
        if (endlondeg - beglondeg < 180.0) {
            double lat = beglatdeg;
            double lon = beglondeg;
            beglatdeg = endlatdeg;
            beglondeg = endlondeg;
            endlatdeg = lat;
            endlondeg = lon;
        }

        // convert arguments to radians
        double beglatrad = Math.toRadians (beglatdeg);
        double beglonrad = Math.toRadians (beglondeg);
        double endlatrad = Math.toRadians (endlatdeg);
        double endlonrad = Math.toRadians (endlondeg);
        double lonrad    = Math.toRadians (londeg);

        // find course beg and end points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        double begX = Math.cos (beglonrad) * Math.cos (beglatrad);
        double begY = Math.sin (beglonrad) * Math.cos (beglatrad);
        double begZ = Math.sin (beglatrad);
        double endX = Math.cos (endlonrad) * Math.cos (endlatrad);
        double endY = Math.sin (endlonrad) * Math.cos (endlatrad);
        double endZ = Math.sin (endlatrad);

        // compute normal to plane containing course = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        double norX = begY * endZ - begZ * endY;
        double norY = begZ * endX - begX * endZ;
        double norZ = begX * endY - begY * endX;

        // compute normal to plane containing the given line of longitude
        // note that the plane crosses through the center of the earth, ie, (0,0,0)
        // if longitude =   0 deg => longitude plane is y=0 plane => (0,-1,0)
        // if longitude =  90 deg => longitude plane is x=0 plane => (1,0,0)
        // if longitude = 180 deg => (0,1,0)
        // if longitude = -90 deg => (-1,0,0)
        // equation of plane containing requested longitude: lonX * x + lonY * y + lonZ * z = 0
        double lonX =   Math.sin (lonrad);
        double lonY = - Math.cos (lonrad);
        double lonZ = 0.0;

        // find intersection line = lon cross nor
        // note that the line passes through the center of the earth, ie, (0,0,0)
        // equation of line of intersection: t * (intX, intY, intZ)
        double intX = lonY * norZ - lonZ * norY;
        double intY = lonZ * norX - lonX * norZ;
        double intZ = lonX * norY - lonY * norX;
        double intR = Math.sqrt (intX * intX + intY * intY + intZ * intZ);

        // find corresponding latitude
        double latrad = Math.asin (intZ / intR);
        return Math.toDegrees (latrad);
    }

    /**
     * Given a latitude along a course, what is corresponding longitude?
     * Used for plotting courses that are primarily north/south.
     * Note that primarily east/west courses can have two solutions that we don't handle.
     * @return longitude in range -180 to +180
     */
    public static double GCLat2Lon (double beglatdeg, double beglondeg, double endlatdeg, double endlondeg, double latdeg)
    {
        // code below assumes southbound course
        if (endlatdeg > beglatdeg) {
            double lat = beglatdeg;
            double lon = beglondeg;
            beglatdeg = endlatdeg;
            beglondeg = endlondeg;
            endlatdeg = lat;
            endlondeg = lon;
        }

        // convert arguments to radians
        double beglatrad = Math.toRadians (beglatdeg);
        double beglonrad = Math.toRadians (beglondeg);
        double endlatrad = Math.toRadians (endlatdeg);
        double endlonrad = Math.toRadians (endlondeg);
        double latrad    = Math.toRadians (latdeg);

        // find course beg and end points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        double begX = Math.cos (beglonrad) * Math.cos (beglatrad);
        double begY = Math.sin (beglonrad) * Math.cos (beglatrad);
        double begZ = Math.sin (beglatrad);
        double endX = Math.cos (endlonrad) * Math.cos (endlatrad);
        double endY = Math.sin (endlonrad) * Math.cos (endlatrad);
        double endZ = Math.sin (endlatrad);

        // compute normal to plane containing course = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        double norX = begY * endZ - begZ * endY;
        double norY = begZ * endX - begX * endZ;
        double norZ = begX * endY - begY * endX;

        // find points on plane that are at the given latitude
        // equation of intersection line: norX * x + norY * Y + norZ * latZ = 0
        double latZ = Math.sin (latrad);

        // find the two points along that line that are on the normalized sphere
        // ie, those that intersect x**2 + y**2 + latZ**2 = 1

        //   norX * x + norY * y + norZ * latZ = 0  =>  norX * x = -(norY * y + norZ * latZ)
        //                                          =>  (norX * x)**2 = (norY * y + norZ * latZ)**2

        //   x**2 + y**2 + latZ**2 = 1.0  =>  x**2 = 1 - y**2 - latZ**2
        //                                =>  (norX * x)**2 = norX**2 * (1 - y**2 - latZ**2)

        //   (norY * y + norZ * latZ)**2 = norX**2 * (1 - y**2 - latZ**2)

        //   norY**2 * y**2 + norZ**2 * latZ**2 + 2 * norY * y * norZ * latZ = norX**2 - norX**2 * y**2 - norX**2 * latZ**2

        //   (norY**2 + norX**2) * y**2 + (2 * norY * norZ * latZ) * y + norZ**2 * latZ**2 + norX**2 * latZ**2 - norX**2 = 0
        //   (norY**2 + norX**2) * y**2 + (2 * norY * norZ * latZ) * y + norZ**2 * latZ**2 + norX**2 * (latZ**2 - 1) = 0

        double a  = norY * norY + norX * norX;
        double b  = 2 * norY * norZ * latZ;
        double c  = norZ * norZ * latZ * latZ + norX * norX * (latZ * latZ - 1);
        double d  = Math.sqrt (b * b - 4 * a * c);

        double y1 = (-b + d) / (2 * a);
        double y2 = (-b - d) / (2 * a);

        double x1_times_norX = -(norY * y1 + norZ * latZ);
        double x2_times_norX = -(norY * y2 + norZ * latZ);

        // find corresponding longitudes
        double londeg1 = Math.toDegrees (Math.atan2 (y1 * norX, x1_times_norX));
        double londeg2 = Math.toDegrees (Math.atan2 (y2 * norX, x2_times_norX));

        // find distances from beg->pt1->end and beg->pt2->end
        double dist1 = LatLonDist_rad (beglatdeg, beglondeg, latdeg, londeg1) +
                       LatLonDist_rad (latdeg, londeg1, endlatdeg, endlondeg);
        double dist2 = LatLonDist_rad (beglatdeg, beglondeg, latdeg, londeg2) +
                       LatLonDist_rad (latdeg, londeg2, endlatdeg, endlondeg);

        // use whichever one is the shortest
        return (dist1 < dist2) ? londeg1 : londeg2;
    }

    /**
     * Given a course from beg to end and a current poistion cur, find what the on-course heading is at the
     * point on the course adjacent to the current position
     * @param beglatdeg = course beginning latitude
     * @param beglondeg = course beginning longitude
     * @param endlatdeg = course ending latitude
     * @param endlondeg = course ending longitude
     * @param curlatdeg = current position latitude
     * @param curlondeg = current position longitude
     * @return on-course true heading (degrees)
     */
    public static double GCOnCourseHdg (double beglatdeg, double beglondeg, double endlatdeg, double endlondeg, double curlatdeg, double curlondeg)
    {
        // convert arguments to radians
        double beglatrad = Math.toRadians (beglatdeg);
        double beglonrad = Math.toRadians (beglondeg);
        double endlatrad = Math.toRadians (endlatdeg);
        double endlonrad = Math.toRadians (endlondeg);
        double curlatrad = Math.toRadians (curlatdeg);
        double curlonrad = Math.toRadians (curlondeg);

        // find points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        double begX = Math.cos (beglonrad) * Math.cos (beglatrad);
        double begY = Math.sin (beglonrad) * Math.cos (beglatrad);
        double begZ = Math.sin (beglatrad);
        double endX = Math.cos (endlonrad) * Math.cos (endlatrad);
        double endY = Math.sin (endlonrad) * Math.cos (endlatrad);
        double endZ = Math.sin (endlatrad);
        double curX = Math.cos (curlonrad) * Math.cos (curlatrad);
        double curY = Math.sin (curlonrad) * Math.cos (curlatrad);
        double curZ = Math.sin (curlatrad);

        // compute normal to plane containing course, ie, containing beg, end, origin = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        double courseNormalX = begY * endZ - begZ * endY;
        double courseNormalY = begZ * endX - begX * endZ;
        double courseNormalZ = begX * endY - begY * endX;

        // compute normal to plane containing that normal, cur and origin = cur cross courseNormal
        double currentNormalX = curY * courseNormalZ - curZ * courseNormalY;
        double currentNormalY = curZ * courseNormalX - curX * courseNormalZ;
        double currentNormalZ = curX * courseNormalY - curY * courseNormalX;

        // we now have two planes that are perpendicular, both passing through the origin
        // one plane contains the course arc from beg to end
        // the other plane contains the current position cur
        // find one of the two points where the planes intersect along the course line, ie, on the normalized sphere
        double intersectX = courseNormalY * currentNormalZ - courseNormalZ * currentNormalY;
        double intersectY = courseNormalZ * currentNormalX - courseNormalX * currentNormalZ;
        double intersectZ = courseNormalX * currentNormalY - courseNormalY * currentNormalX;

        // find lat/lon of the intersection point
        double intersectLat = Math.toDegrees (Math.atan2 (intersectZ, Math.sqrt (intersectX * intersectX + intersectY * intersectY)));
        double intersectLon = Math.toDegrees (Math.atan2 (intersectY, intersectX));

        // return true course from intersection point to end point
        return LatLonTC (intersectLat, intersectLon, endlatdeg, endlondeg);
    }

    /**
     * Given a course from beg to end and a current poistion cur, find out how many miles a given
     * current point is off course
     * @param beglatdeg = course beginning latitude
     * @param beglondeg = course beginning longitude
     * @param endlatdeg = course ending latitude
     * @param endlondeg = course ending longitude
     * @param curlatdeg = current position latitude
     * @param curlondeg = current position longitude
     * @return off-course distance (nm)
     */
    public static double GCOffCourseDist (double beglatdeg, double beglondeg, double endlatdeg, double endlondeg, double curlatdeg, double curlondeg)
    {
        // http://williams.best.vwh.net/avform.htm Cross track error
        double crs_AB  = LatLonTC_rad   (beglatdeg, beglondeg, endlatdeg, endlondeg);
        double crs_AD  = LatLonTC_rad   (beglatdeg, beglondeg, curlatdeg, curlondeg);
        double dist_AD = LatLonDist_rad (beglatdeg, beglondeg, curlatdeg, curlondeg);
        double xtd     = Math.asin (Math.sin (dist_AD) * Math.sin (crs_AD - crs_AB));

        return Math.toDegrees (xtd) * NMPerDeg;
    }

    /**
     * Perform matrix multiply:  pr[r][c] = lh[r][c] * rh[r][c]
     */
    public static void MatMul (double[][] pr, double[][] lh, double[][] rh)
    {
        int prRows = pr.length;
        int prCols = pr[0].length;
        int lhRows = lh.length;
        int lhCols = lh[0].length;
        int rhRows = rh.length;
        int rhCols = rh[0].length;

        if (prRows != lhRows) throw new RuntimeException ();
        if (prCols != rhCols) throw new RuntimeException ();
        if (lhCols != rhRows) throw new RuntimeException ();

        for (int i = 0; i < prRows; i ++) {
            double[] pr_i_ = pr[i];
            double[] lh_i_ = lh[i];
            for (int j = 0; j < prCols; j ++) {
                double sum = 0;
                for (int k = 0; k < lhCols; k ++) {
                    sum += lh_i_[k] * rh[k][j];
                }
                pr_i_[j] = sum;
            }
        }
    }

    /**
     * Row reduce the given matrix.
     */
    public static void RowReduce (double[][] T)
    {
        double pivot;
        int trows = T.length;
        int tcols = T[0].length;

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
                if (Math.abs (pivot) < Math.abs (swapPivot)) {
                    pivot   = swapPivot;
                    bestRow = swapRow;
                }
            }
            if (pivot == 0.0) throw new RuntimeException ("not invertable");
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
