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

import android.hardware.GeomagneticField;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class Lib {

    // fine-tuned constants
    public static final float FtPerM    = 3.28084F;
    public static final float KtPerMPS  = 1.94384F;
    public static final float NMPerDeg  = 60.0F;
    public static final float MMPerIn   = 25.4F;    // millimetres per inch
    public static final float SMPerNM   = 1.15078F;
    public static final int   MPerNM    = 1852;     // metres per naut mile
    public static final float GRAVACCEL = 9.80665F;  // metres per second squared

    /**
     * Split a comma-separated value string into its various substrings.
     * @param line = comma-separated line
     * @return array of the various substrings
     */
    public static String[] QuotedCSVSplit (String line)
    {
        int len = line.length ();
        ArrayList<String> cols = new ArrayList<> (len + 1);
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
    public static float Westmost (float lon1, float lon2)
    {
        // make sure lon2 comes at or after lon1 by less than 360deg
        while (lon2 < lon1) lon2 += 360.0;
        while (lon2 - 360.0 >= lon1) lon2 -= 360.0;

        // if lon2 is east of lon1 by at most 180deg, lon1 is westmost
        // if lon2 is east of lon1 by more than 180, lon2 is westmost
        float westmost = (lon2 - lon1 < 180.0) ? lon1 : lon2;

        // normalize
        while (westmost < -180.0) westmost += 360.0;
        while (westmost >= 180.0) westmost -= 360.0;
        return westmost;
    }

    /**
     * Return eastmost longitude, normalized to -180..+179.999999999
     */
    public static float Eastmost (float lon1, float lon2)
    {
        // make sure lon2 comes at or after lon1 by less than 360deg
        while (lon2 < lon1) lon2 += 360.0;
        while (lon2 - 360.0 >= lon1) lon2 -= 360.0;

        // if lon2 is east of lon1 by at most 180deg, lon2 is eastmost
        // if lon2 is east of lon1 by more than 180, lon1 is eastmost
        float eastmost = (lon2 - lon1 < 180.0) ? lon2 : lon1;

        // normalize
        while (eastmost < -180.0) eastmost += 360.0;
        while (eastmost >= 180.0) eastmost -= 360.0;
        return eastmost;
    }

    /**
     * Compute great-circle distance between two lat/lon co-ordinates
     * @return distance between two points (in nm)
     */
    public static float LatLonDist (float srcLat, float srcLon, float dstLat, float dstLon)
    {
        return Mathf.toDegrees (LatLonDist_rad (srcLat, srcLon, dstLat, dstLon)) * NMPerDeg;
    }
    public static float LatLonDist_rad (float srcLat, float srcLon, float dstLat, float dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_distance
        float sLat = srcLat / 180 * Mathf.PI;
        float sLon = srcLon / 180 * Mathf.PI;
        float fLat = dstLat / 180 * Mathf.PI;
        float fLon = dstLon / 180 * Mathf.PI;
        float dLon = fLon - sLon;
        float t1   = Sq (Mathf.cos (fLat) * Mathf.sin (dLon));
        float t2   = Sq (Mathf.cos (sLat) * Mathf.sin (fLat) - Mathf.sin (sLat) * Mathf.cos (fLat) * Mathf.cos (dLon));
        float t3   = Mathf.sin (sLat) * Mathf.sin (fLat);
        float t4   = Mathf.cos (sLat) * Mathf.cos (fLat) * Mathf.cos (dLon);
        return Mathf.atan2 (Mathf.sqrt (t1 + t2), t3 + t4);
    }

    private static float Sq (float x) { return x*x; }

    /**
     * Compute great-circle true course from one lat/lon to another lat/lon
     * @return true course (in degrees) at source point
     */
    public static float LatLonTC (float srcLat, float srcLon, float dstLat, float dstLon)
    {
        return LatLonTC_rad (srcLat, srcLon, dstLat, dstLon) * 180.0F / Mathf.PI;
    }
    public static float LatLonTC_rad (float srcLat, float srcLon, float dstLat, float dstLon)
    {
        // http://en.wikipedia.org/wiki/Great-circle_navigation
        float sLat = srcLat / 180 * Mathf.PI;
        float sLon = srcLon / 180 * Mathf.PI;
        float fLat = dstLat / 180 * Mathf.PI;
        float fLon = dstLon / 180 * Mathf.PI;
        float dLon = fLon - sLon;
        float t1 = Mathf.cos (sLat) * Mathf.tan (fLat);
        float t2 = Mathf.sin(sLat) * Mathf.cos(dLon);
        return Mathf.atan2 (Mathf.sin (dLon), t1 - t2);
    }

    /**
     * Compute new lat/lon given old lat/lon, heading (degrees), distance (nautical miles)
     * http://stackoverflow.com/questions/7222382/get-lat-long-given-current-point-distance-and-bearing
     */
    public static float LatHdgDist2Lat (float latdeg, float hdgdeg, float distnm)
    {
        float distrad = Mathf.toRadians (distnm / NMPerDeg);
        float latrad  = Mathf.toRadians (latdeg);
        float hdgrad  = Mathf.toRadians (hdgdeg);

        latrad = Mathf.asin (Mathf.sin (latrad) * Mathf.cos (distrad) + Mathf.cos (latrad) * Mathf.sin (distrad) * Mathf.cos (hdgrad));
        return Mathf.toDegrees (latrad);
    }
    public static float LatLonHdgDist2Lon (float latdeg, float londeg, float hdgdeg, float distnm)
    {
        float distrad = Mathf.toRadians (distnm / NMPerDeg);
        float latrad  = Mathf.toRadians (latdeg);
        float hdgrad  = Mathf.toRadians (hdgdeg);

        float newlatrad = Mathf.asin (Mathf.sin (latrad) * Mathf.cos (distrad) + Mathf.cos (latrad) * Mathf.sin (distrad) * Mathf.cos (hdgrad));
        float lonrad = Mathf.atan2 (Mathf.sin (hdgrad) * Mathf.sin (distrad) * Mathf.cos (latrad), Mathf.cos (distrad) - Mathf.sin (latrad) * Mathf.sin (newlatrad));
        return Mathf.toDegrees (lonrad) + londeg;
    }

    /**
     * Given a longitude along a course, what is corresponding latitude?
     * Used for plotting courses that are primarily east/west.
     * @return latitude in range -90 to +90
     */
    public static float GCLon2Lat (float beglatdeg, float beglondeg, float endlatdeg, float endlondeg, float londeg)
    {
        // code below assumes westbound course
        while (beglondeg < -180.0) beglondeg += 360.0;
        while (beglondeg >= 180.0) beglondeg -= 360.0;
        while (endlondeg < -180.0) endlondeg += 360.0;
        while (endlondeg >= 180.0) endlondeg -= 360.0;
        if (endlondeg < beglondeg) endlondeg += 360.0;
        if (endlondeg - beglondeg < 180.0) {
            float lat = beglatdeg;
            float lon = beglondeg;
            beglatdeg = endlatdeg;
            beglondeg = endlondeg;
            endlatdeg = lat;
            endlondeg = lon;
        }

        // convert arguments to radians
        float beglatrad = Mathf.toRadians (beglatdeg);
        float beglonrad = Mathf.toRadians (beglondeg);
        float endlatrad = Mathf.toRadians (endlatdeg);
        float endlonrad = Mathf.toRadians (endlondeg);
        float lonrad    = Mathf.toRadians (londeg);

        // find course beg and end points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        float begX = Mathf.cos (beglonrad) * Mathf.cos (beglatrad);
        float begY = Mathf.sin (beglonrad) * Mathf.cos (beglatrad);
        float begZ = Mathf.sin (beglatrad);
        float endX = Mathf.cos (endlonrad) * Mathf.cos (endlatrad);
        float endY = Mathf.sin (endlonrad) * Mathf.cos (endlatrad);
        float endZ = Mathf.sin (endlatrad);

        // compute normal to plane containing course = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        float norX = begY * endZ - begZ * endY;
        float norY = begZ * endX - begX * endZ;
        float norZ = begX * endY - begY * endX;

        // compute normal to plane containing the given line of longitude
        // note that the plane crosses through the center of the earth, ie, (0,0,0)
        // if longitude =   0 deg => longitude plane is y=0 plane => (0,-1,0)
        // if longitude =  90 deg => longitude plane is x=0 plane => (1,0,0)
        // if longitude = 180 deg => (0,1,0)
        // if longitude = -90 deg => (-1,0,0)
        // equation of plane containing requested longitude: lonX * x + lonY * y + lonZ * z = 0
        float lonX =   Mathf.sin (lonrad);
        float lonY = - Mathf.cos (lonrad);
        float lonZ = 0.0F;

        // find intersection line = lon cross nor
        // note that the line passes through the center of the earth, ie, (0,0,0)
        // equation of line of intersection: t * (intX, intY, intZ)
        float intX = lonY * norZ - lonZ * norY;
        float intY = lonZ * norX - lonX * norZ;
        float intZ = lonX * norY - lonY * norX;
        float intR = Mathf.sqrt (intX * intX + intY * intY + intZ * intZ);

        // find corresponding latitude
        float latrad = Mathf.asin (intZ / intR);
        return Mathf.toDegrees (latrad);
    }

    /**
     * Given a latitude along a course, what is corresponding longitude?
     * Used for plotting courses that are primarily north/south.
     * Note that primarily east/west courses can have two solutions that we don't handle.
     * @return longitude in range -180 to +180
     */
    public static float GCLat2Lon (float beglatdeg, float beglondeg, float endlatdeg, float endlondeg, float latdeg)
    {
        // code below assumes southbound course
        if (endlatdeg > beglatdeg) {
            float lat = beglatdeg;
            float lon = beglondeg;
            beglatdeg = endlatdeg;
            beglondeg = endlondeg;
            endlatdeg = lat;
            endlondeg = lon;
        }

        // convert arguments to radians
        float beglatrad = Mathf.toRadians (beglatdeg);
        float beglonrad = Mathf.toRadians (beglondeg);
        float endlatrad = Mathf.toRadians (endlatdeg);
        float endlonrad = Mathf.toRadians (endlondeg);
        float latrad    = Mathf.toRadians (latdeg);

        // find course beg and end points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        float begX = Mathf.cos (beglonrad) * Mathf.cos (beglatrad);
        float begY = Mathf.sin (beglonrad) * Mathf.cos (beglatrad);
        float begZ = Mathf.sin (beglatrad);
        float endX = Mathf.cos (endlonrad) * Mathf.cos (endlatrad);
        float endY = Mathf.sin (endlonrad) * Mathf.cos (endlatrad);
        float endZ = Mathf.sin (endlatrad);

        // compute normal to plane containing course = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        float norX = begY * endZ - begZ * endY;
        float norY = begZ * endX - begX * endZ;
        float norZ = begX * endY - begY * endX;

        // find points on plane that are at the given latitude
        // equation of intersection line: norX * x + norY * Y + norZ * latZ = 0
        float latZ = Mathf.sin (latrad);

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

        float a  = norY * norY + norX * norX;
        float b  = 2 * norY * norZ * latZ;
        float c  = norZ * norZ * latZ * latZ + norX * norX * (latZ * latZ - 1);
        float d  = Mathf.sqrt (b * b - 4 * a * c);

        float y1 = (-b + d) / (2 * a);
        float y2 = (-b - d) / (2 * a);

        float x1_times_norX = -(norY * y1 + norZ * latZ);
        float x2_times_norX = -(norY * y2 + norZ * latZ);

        // find corresponding longitudes
        float londeg1 = Mathf.toDegrees (Mathf.atan2 (y1 * norX, x1_times_norX));
        float londeg2 = Mathf.toDegrees (Mathf.atan2 (y2 * norX, x2_times_norX));

        // find distances from beg->pt1->end and beg->pt2->end
        float dist1 = LatLonDist_rad (beglatdeg, beglondeg, latdeg, londeg1) +
                       LatLonDist_rad (latdeg, londeg1, endlatdeg, endlondeg);
        float dist2 = LatLonDist_rad (beglatdeg, beglondeg, latdeg, londeg2) +
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
    public static float GCOnCourseHdg (float beglatdeg, float beglondeg, float endlatdeg, float endlondeg, float curlatdeg, float curlondeg)
    {
        // convert arguments to radians
        float beglatrad = Mathf.toRadians (beglatdeg);
        float beglonrad = Mathf.toRadians (beglondeg);
        float endlatrad = Mathf.toRadians (endlatdeg);
        float endlonrad = Mathf.toRadians (endlondeg);
        float curlatrad = Mathf.toRadians (curlatdeg);
        float curlonrad = Mathf.toRadians (curlondeg);

        // find points on normalized sphere
        // +X axis goes through lat=0,lon=0
        // +Y axis goes through lat=0,lon=90
        // +Z axis goes through north pole
        float begX = Mathf.cos (beglonrad) * Mathf.cos (beglatrad);
        float begY = Mathf.sin (beglonrad) * Mathf.cos (beglatrad);
        float begZ = Mathf.sin (beglatrad);
        float endX = Mathf.cos (endlonrad) * Mathf.cos (endlatrad);
        float endY = Mathf.sin (endlonrad) * Mathf.cos (endlatrad);
        float endZ = Mathf.sin (endlatrad);
        float curX = Mathf.cos (curlonrad) * Mathf.cos (curlatrad);
        float curY = Mathf.sin (curlonrad) * Mathf.cos (curlatrad);
        float curZ = Mathf.sin (curlatrad);

        // compute normal to plane containing course, ie, containing beg, end, origin = beg cross end
        // note that the plane crosses through the center of earth, ie, (0,0,0)
        // equation of plane containing course: norX * x + norY * Y + norZ * z = 0
        float courseNormalX = begY * endZ - begZ * endY;
        float courseNormalY = begZ * endX - begX * endZ;
        float courseNormalZ = begX * endY - begY * endX;

        // compute normal to plane containing that normal, cur and origin = cur cross courseNormal
        float currentNormalX = curY * courseNormalZ - curZ * courseNormalY;
        float currentNormalY = curZ * courseNormalX - curX * courseNormalZ;
        float currentNormalZ = curX * courseNormalY - curY * courseNormalX;

        // we now have two planes that are perpendicular, both passing through the origin
        // one plane contains the course arc from beg to end
        // the other plane contains the current position cur
        // find one of the two points where the planes intersect along the course line, ie, on the normalized sphere
        float intersectX = courseNormalY * currentNormalZ - courseNormalZ * currentNormalY;
        float intersectY = courseNormalZ * currentNormalX - courseNormalX * currentNormalZ;
        float intersectZ = courseNormalX * currentNormalY - courseNormalY * currentNormalX;

        // find lat/lon of the intersection point
        float intersectLat = Mathf.toDegrees (Mathf.atan2 (intersectZ, Mathf.sqrt (intersectX * intersectX + intersectY * intersectY)));
        float intersectLon = Mathf.toDegrees (Mathf.atan2 (intersectY, intersectX));

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
    public static float GCOffCourseDist (float beglatdeg, float beglondeg, float endlatdeg, float endlondeg, float curlatdeg, float curlondeg)
    {
        // http://williams.best.vwh.net/avform.htm Cross track error
        float crs_AB  = LatLonTC_rad   (beglatdeg, beglondeg, endlatdeg, endlondeg);
        float crs_AD  = LatLonTC_rad   (beglatdeg, beglondeg, curlatdeg, curlondeg);
        float dist_AD = LatLonDist_rad (beglatdeg, beglondeg, curlatdeg, curlondeg);
        float xtd     = Mathf.asin (Mathf.sin (dist_AD) * Mathf.sin (crs_AD - crs_AB));

        return Mathf.toDegrees (xtd) * NMPerDeg;
    }

    /**
     * Compute magnetic variation at a given point.
     * @param lat = latitude of variation (degrees)
     * @param lon = longitude of variation (degrees)
     * @param alt = altitude (metres MSL)
     * @return amount to add to a true course to get a magnetic course (degrees)
     *         ie, >0 on east coast, <0 on west coast
     */
    public static float MagVariation (float lat, float lon, float alt)
    {
        GeomagneticField gmf = new GeomagneticField (lat, lon, alt, System.currentTimeMillis());
        return - gmf.getDeclination();
    }

    /**
     * Perform matrix multiply:  pr[r][c] = lh[r][c] * rh[r][c]
     */
    public static void MatMul (float[][] pr, float[][] lh, float[][] rh)
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
            float[] pr_i_ = pr[i];
            float[] lh_i_ = lh[i];
            for (int j = 0; j < prCols; j ++) {
                float sum = 0;
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
    public static void RowReduce (float[][] T)
    {
        float pivot;
        int trows = T.length;
        int tcols = T[0].length;

        for (int row = 0; row < trows; row ++) {
            float[] T_row_ = T[row];

            /*
             * Make this row's major diagonal colum one by
             * swapping it with a row below that has the
             * largest value in this row's major diagonal
             * column, then dividing the row by that number.
             */
            pivot = T_row_[row];
            int bestRow = row;
            for (int swapRow = row; ++ swapRow < trows;) {
                float swapPivot = T[swapRow][row];
                if (Math.abs (pivot) < Math.abs (swapPivot)) {
                    pivot   = swapPivot;
                    bestRow = swapRow;
                }
            }
            if (pivot == 0.0) throw new RuntimeException ("not invertable");
            if (bestRow != row) {
                float[] tmp = T_row_;
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
                float[] T_rr_ = T[rr];
                pivot = T_rr_[row];
                if (pivot != 0.0) {
                    for (int cc = row; cc < tcols; cc ++) {
                        T_rr_[cc] -= pivot * T_row_[cc];
                    }
                }
            }
        }

        for (int row = trows; -- row >= 0;) {
            float[] T_row_ = T[row];
            for (int rr = row; -- rr >= 0;) {
                float[] T_rr_ = T[rr];
                pivot = T_rr_[row];
                if (pivot != 0.0) {
                    for (int cc = row; cc < tcols; cc ++) {
                        T_rr_[cc] -= pivot * T_row_[cc];
                    }
                }
            }
        }
    }

    /**
     * Convert a value to corresponding string.
     * @param val = integer version of value
     * @param dpt = number of decimal places it contains
     * @return string to put in editing box
     */
    public static String ValToString (int val, int dpt)
    {
        String str = Integer.toString (val);
        if (dpt > 0) {
            while (str.length () <= dpt) str = "0" + str;
            int len = str.length ();
            str = str.substring (0, len - dpt) + "." + str.substring (len - dpt);
            while (str.charAt (str.length () - 1) == '0') str = str.substring (0, str.length () -1);
            if (str.charAt (str.length () - 1) == '.') str = str.substring (0, str.length () - 1);
        }
        return str;
    }

    /**
     * Convert a nm distance to displayable string.
     */
    public static String DistString (float dist, boolean sm)
    {
        if (sm) dist *= SMPerNM;
        String diststr;
        if (dist < 100) {
            int dist100ths = (int)(dist * 100.0 + 0.5);
            int distwhole  = dist100ths / 100;
            dist100ths %= 100;
            diststr = Integer.toString (distwhole) + "." + Integer.toString (dist100ths + 100).substring (1);
        } else if (dist < 1000) {
            int dist10ths = (int)(dist * 10.0 + 0.5);
            int distwhole = dist10ths / 10;
            dist10ths %= 10;
            diststr = Integer.toString (distwhole) + "." + Integer.toString (dist10ths);
        } else {
            int distwhole = (int)(dist + 0.5);
            diststr = Integer.toString (distwhole);
        }
        return diststr + (sm ? " sm" : " nm");
    }

    /**
     * Convert a kt speed to displayable string.
     */
    public static String SpeedString (float speed, boolean mph)
    {
        String suffix = " kts";
        if (mph) {
            speed *= SMPerNM;
            suffix = " mph";
        }
        if (speed >= 99.5) {
            speed += 0.5;
            return (int)speed + suffix;
        }
        speed += 0.05;
        int speedint = (int)speed;
        speed -= speedint;
        speed *= 10;
        int speeddec = (int)speed;
        String speedstr = speedint + "." + speeddec + suffix;
        if (speedstr.equals ("1.0 kts")) speedstr = "1.0 kt";
        return speedstr;
    }

    /**
     * Rename a file, throwing an exception on error.
     */
    public static void RenameFile (String oldname, String newname)
            throws IOException
    {
        if (!new File (oldname).renameTo (new File (newname))) {
            throw new IOException ("error renaming " + oldname + " => " + newname);
        }
    }

    /**
     * Read a line from the given input stream
     * @param is = stream to read from
     * @return string up to but not including \n
     * @throws IOException
     */
    public static String ReadStreamLine (InputStream is)
            throws IOException
    {
        int c;
        String s = "";
        while ((c = is.read ()) != '\n') {
            if (c < 0) throw new IOException ("EOF reading stream");
            s += (char)c;
        }
        return s;
    }

    /**
     * Write a given number of bytes from the input stream to the file
     * @param is = stream to read bytes from
     * @param bytelen = number of bytes to copy
     * @param filename = name of file to write to
     * @throws IOException
     */
    public static void WriteStreamToFile (InputStream is, int bytelen, String filename)
            throws IOException
    {
        Ignored (new File (filename.substring (0, filename.lastIndexOf ('/') + 1)).mkdirs ());
        FileOutputStream os = new FileOutputStream (filename);
        try {
            byte[] buf = new byte[4096];
            while (bytelen > 0) {
                int rc = buf.length;
                if (rc > bytelen) rc = bytelen;
                rc = is.read (buf, 0, rc);
                if (rc <= 0) throw new IOException ("EOF reading " + filename);
                os.write (buf, 0, rc);
                bytelen -= rc;
            }
        } finally {
            os.close ();
        }
    }

    /**
     * Various compiler warnings.
     */
    public static void Ignored () { }
    public static void Ignored (boolean x) { }
    public static void Ignored (int x) { }
}
