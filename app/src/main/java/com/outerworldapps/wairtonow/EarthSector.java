//    Copyright (C) 2016, Mike Rieker, Beverly, MA USA
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

import android.database.Cursor;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.support.annotation.NonNull;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;

/**
 * OpenGL 3D sector of the earth.
 */
public class EarthSector {
    public final static double EARTHRADIUS = Lib.NMPerDeg * Lib.MPerNM * 180.0 / Math.PI;
    public final static int FUNNYFACTOR = 4;  // exaggerate MSL altitudes by this factor
    public final static int MBMSIZE = 512;    // macro bitmap size
    public final static int MAXSTEPS = 32;    // maximum steps (intervals between vertices)

    private static class Obstr {
        public double lat, lon;
        public int agl, msl;
    }

    private final static int FLOAT_SIZE_BYTES = 4;
    private final static int TRIANGLES_DATA_STRIDE = 5;

    private final static Object loaderLock = new Object ();
    private static EarthSector loaderSectorsHead, loaderSectorsTail;
    private static LoaderThread loaderThread;

    public boolean refd;        // seen by camera and is one of MAXSECTORS closest to camera
    public double slat, wlon;   // southwest corner lat/lon
    public double nlat, elon;   // northeast corner lat/lon
    public double nmfromcam;    // naut miles from camera
    public int hashkey;

    public EarthSector nextAll;
    public EarthSector nextDist;

    private Bitmap mBitmap;     // bitmap that all the tiles get loaded to
    private boolean corrupt;    // flat bitmap file is corrupt
    private boolean loaded;     // all loaded in memory
    private DisplayableChart displayableChart;
    private EarthSector nextLoad;
    private FloatBuffer mTriangles;
    private int mTextureID;
    private PointD intpoint;
    private Runnable onLoad;
    private WairToNow wairToNow;

    private final static String[] obstrs_col_names = new String[] { "ob_agl", "ob_msl", "ob_lat", "ob_lon" };

    public EarthSector (@NonNull WairToNow wtn, @NonNull DisplayableChart dc, double slat, double nlat, double wlon, double elon)
    {
        wairToNow = wtn;
        displayableChart = dc;
        this.slat = slat;
        this.nlat = nlat;
        this.wlon = wlon;
        this.elon = elon;
        intpoint = new PointD ();
    }

    /**
     * Clear out all sectors queued for loading.
     * If one is in progress, leave it alone.
     */
    public static void ClearLoader ()
    {
        synchronized (loaderLock) {
            loaderSectorsHead = null;
            loaderSectorsTail = null;
        }
    }

    /**
     * Set up the sector contents.
     * Can be called from any thread.
     * @return true if all sector contents are now valid
     */
    public boolean setup (Runnable onLoad)
    {
        synchronized (loaderLock) {
            if (corrupt) return false;
            if (loaded) return true;
            this.onLoad   = onLoad;
            this.nextLoad = null;
            if (loaderSectorsTail == null) {
                loaderSectorsHead = this;
            } else {
                loaderSectorsTail.nextLoad = this;
            }
            loaderSectorsTail = this;
            if (loaderThread == null) {
                loaderThread = new LoaderThread ();
                loaderThread.start ();
            }
        }
        return false;
    }

    /**
     * Called in loader thread to load as much as we can in a non-GL thread.
     * We can read in the bitmap image and make the FloatBuffer with the vertices.
     * But we cannot allocate a texture-ID.
     */
    private void loadIt ()
    {
        try {

            // get triangles for all the obstructions in the sector
            float[] obstrtris = GetObstructions ();
            // get bitmap for chart overlaying the sector
            mBitmap = displayableChart.GetMacroBitmap (slat, nlat, wlon, elon);
            // get triangles for the surface of the sector
            float[] surfctris = MakeEarthSector ();
            // put in a FloatBuffer
            int nfloats = ((obstrtris == null) ? 0 : obstrtris.length) + surfctris.length;
            FloatBuffer tribuf = ByteBuffer.allocateDirect (nfloats * FLOAT_SIZE_BYTES).
                    order (ByteOrder.nativeOrder ()).asFloatBuffer ();
            tribuf.position (0);
            if (obstrtris != null) tribuf.put (obstrtris);
            tribuf.put (surfctris);

            // publish it
            mTriangles = tribuf;
            synchronized (loaderLock) {
                loaded = true;
            }
            if (onLoad != null) {
                onLoad.run ();
                onLoad = null;
            }
        } catch (Throwable t) {
            Log.e ("EarthSector", "error loading bitmap", t);
            recycle ();
            synchronized (loaderLock) {
                corrupt = true;
            }
        }
    }

    @Override
    protected void finalize () throws Throwable
    {
        recycle ();
        super.finalize ();
    }

    /**
     * All done with sector.
     * Should only be called from the GL thread.
     */
    public void recycle ()
    {
        if (mBitmap != null) {
            mBitmap.recycle ();
            mBitmap = null;
        }
        mTriangles = null;
        if (mTextureID != 0) {
            int[] textureIDs = new int[] { mTextureID };
            GLES20.glDeleteTextures (1, textureIDs, 0);
            mTextureID = 0;
        }
    }

    /**
     * Retrieve triangles that make up the sector.
     * Can be called from any thread.
     */
    public @NonNull FloatBuffer getTriangles ()
    {
        return mTriangles;
    }

    /**
     * Retrieve texture for the sector.
     * Should only be called from the GL thread.
     */
    public int getTextureID ()
    {
        if (mTextureID == 0) {
            mTextureID = MakeTextureFromBitmap (mBitmap);
            mBitmap.recycle ();
            mBitmap = null;
            if (mTextureID == 0) throw new RuntimeException ("texture ID zero");
        }
        return mTextureID;
    }

    /**
     * Get all the obstructions in the sector.
     */
    private float[] GetObstructions ()
    {
        int expdate = wairToNow.maintView.GetCurentObstructionExpDate ();
        if (expdate == 0) return null;
        SQLiteDBs sqldb = SQLiteDBs.open ("nobudb/obstructions_" + expdate + ".db");
        if (sqldb == null) return null;
        Cursor result = sqldb.query ("obstrs", obstrs_col_names,
                "ob_lat>" + slat + " AND ob_lat<" + nlat + " AND ob_lon>" + wlon +
                        " AND ob_lon<" + elon,
                null, null, null, null, null);
        try {
            if (result.moveToFirst ()) {
                LinkedList<Obstr> obstrs = new LinkedList<> ();
                do {
                    Obstr ob = new Obstr ();
                    ob.agl = result.getInt (0);
                    ob.msl = result.getInt (1);
                    ob.lat = result.getDouble (2);
                    ob.lon = result.getDouble (3);
                    obstrs.add (ob);
                } while (result.moveToNext ());
                if (! obstrs.isEmpty ()) {
                    Vector3 southxyz = new Vector3 ();
                    Vector3 northxyz = new Vector3 ();
                    Vector3 eastxyz = new Vector3 ();
                    Vector3 westxyz = new Vector3 ();
                    Vector3 tipxyz = new Vector3 ();
                    float[] triangles = new float[obstrs.size () * 6 * 5 * FLOAT_SIZE_BYTES];
                    int j = 0;
                    for (Obstr obstr : obstrs) {
                        LatLonAlt2XYZ (obstr.lat, obstr.lon, obstr.msl / Lib.FtPerM, tipxyz);
                        double dlat = 0.5 / 60.0 * obstr.msl / Lib.FtPerNM;
                        LatLonAlt2XYZ (obstr.lat + dlat, obstr.lon, 0.0, northxyz);
                        LatLonAlt2XYZ (obstr.lat - dlat, obstr.lon, 0.0, southxyz);
                        double dlon = dlat / Math.cos (Math.toRadians (obstr.lat));
                        LatLonAlt2XYZ (obstr.lat, obstr.lon - dlon, 0.0, westxyz);
                        LatLonAlt2XYZ (obstr.lat, obstr.lon + dlon, 0.0, eastxyz);
                        j = copyObstToTriangles (triangles, j, tipxyz);
                        j = copyObstToTriangles (triangles, j, eastxyz);
                        j = copyObstToTriangles (triangles, j, westxyz);
                        j = copyObstToTriangles (triangles, j, tipxyz);
                        j = copyObstToTriangles (triangles, j, northxyz);
                        j = copyObstToTriangles (triangles, j, southxyz);
                    }
                    return triangles;
                }
            }
        } finally {
            result.close ();
        }
        return null;
    }

    /**
     * Generate triangles for a sector of the earth.
     */
    private float[] MakeEarthSector ()
    {
        // do vertices in steps of a minute cuz that's Topography.getElevMetres() resolution
        int islat = (int) Math.floor (slat * 60);   // get southern minute limit just outside area
        int inlat = (int) Math.ceil (nlat * 60);    // get northern minute limit just outside area
        int iwlon = (int) Math.floor (wlon * 60);   // get western minute limit just outside area
        int jelon = (int) Math.ceil (elon * 60);    // get eastern minute limit just outside area
        if (jelon < iwlon) jelon += 360 * 60;       // wrap the eastern limit gt western limit
        int nlats = inlat - islat;                  // number of minutes of latitude
        int nlons = jelon - iwlon;                  // number of minutes of longitude

        // calculate minutes per step to get at most MAXSTEPS steps for lat,lon
        int minsperlat  = (nlats + MAXSTEPS - 1) / MAXSTEPS;     // minutes per step latitude
        int minsperlon  = (nlons + MAXSTEPS - 1) / MAXSTEPS;     // minutes per step longitude
        int minsperstep = Math.max (minsperlat, minsperlon);     // minutes per step either way
        nlats = (inlat - islat + minsperlat - 1) / minsperstep;  // get number of latitude steps
        nlons = (jelon - iwlon + minsperlon - 1) / minsperstep;  // get number of longitude steps
        inlat = islat + minsperstep * nlats;                     // re-compute northern edge for full steps
        jelon = iwlon + minsperstep * nlons;                     // re-compute eastern edge for full steps

        // make array of vertices in x,y,z and where they are in the bitmap image.
        int numVertices = (nlats + 1) * (nlons + 1);
        float[] vertices = new float[numVertices * TRIANGLES_DATA_STRIDE];
        Vector3 xyz = new Vector3 ();

        float mbmWidth  = mBitmap.getWidth  ();
        float mbmHeight = mBitmap.getHeight ();
        int k = 0;

        for (int ilat = islat; ilat <= inlat; ilat += minsperstep) {
            for (int jlon = iwlon; jlon <= jelon; jlon += minsperstep) {
                int ilon  = jlon;
                if (ilon >= 180 * 60) ilon -= 360 * 60;
                double lat = ilat / 60.0;
                double lon = ilon / 60.0;
                int alt = Topography.getElevMetresZ (lat, lon);

                // fill in x,y,z for the lat/lon
                LatLonAlt2XYZ (lat, lon, alt, xyz);
                vertices[k++] = (float) xyz.x;
                vertices[k++] = (float) xyz.y;
                vertices[k++] = (float) xyz.z;

                // fill in x,y of corresponding point of mBitmap
                displayableChart.LatLon2MacroBitmap (lat, lon, intpoint);
                vertices[k++] = (float) intpoint.x / mbmWidth;
                vertices[k++] = (float) intpoint.y / mbmHeight;
            }
        }

        /*
         * Make triangles out of those vertices.
         * The vertices form squares and we make two triangles out of each square.
         */
        float[] triangles = new float[nlats*nlons*6*TRIANGLES_DATA_STRIDE];
        k = 0;
        for (int j = 0; j < nlats; j ++) {
            for (int i = 0; i < nlons; i ++) {
                int a = (nlons + 1) * (j + 1) + i + 1;
                //noinspection PointlessArithmeticExpression
                int b = (nlons + 1) * (j + 1) + i + 0;
                //noinspection PointlessArithmeticExpression
                int c = (nlons + 1) * (j + 0) + i + 0;
                //noinspection PointlessArithmeticExpression
                int d = (nlons + 1) * (j + 0) + i + 1;

                k = copySurfToTriangles (triangles, k, vertices, a);
                k = copySurfToTriangles (triangles, k, vertices, b);
                k = copySurfToTriangles (triangles, k, vertices, c);
                k = copySurfToTriangles (triangles, k, vertices, a);
                k = copySurfToTriangles (triangles, k, vertices, c);
                k = copySurfToTriangles (triangles, k, vertices, d);
            }
        }
        return triangles;
    }

    private static int copySurfToTriangles (float[] triangles, int k, float[] squares, int m)
    {
        m *= TRIANGLES_DATA_STRIDE;
        triangles[k++] = (squares[m++]);
        triangles[k++] = (squares[m++]);
        triangles[k++] = (squares[m++]);
        triangles[k++] = (squares[m++]);
        triangles[k++] = (squares[m]);
        return k;
    }

    private static int copyObstToTriangles (float[] triangles, int j, Vector3 xyz)
    {
        triangles[j++] = ((float) xyz.x);
        triangles[j++] = ((float) xyz.y);
        triangles[j++] = ((float) xyz.z);
        triangles[j++] = (-999.0F);  // shader makes it solid red
        triangles[j++] = (-999.0F);
        return j;
    }

    /**
     * Create an OpenGL texture from the bitmap.
     * Should only be called from the GL thread.
     */
    public static int MakeTextureFromBitmap (Bitmap bitmap)
    {
        int[] textures = new int[1];
        GLES20.glGenTextures (1, textures, 0);

        int textureID = textures[0];
        GLES20.glBindTexture (GLES20.GL_TEXTURE_2D, textureID);

        GLES20.glTexParameterf (GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf (GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);

        GLES20.glTexParameteri (GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_REPEAT);
        GLES20.glTexParameteri (GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_REPEAT);

        GLUtils.texImage2D (GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        return textureID;
    }

    /**
     * Convert lat, lon, alt to xyz
     * @param lat = latitude degrees
     * @param lon = longitude degrees
     * @param alt = altitude msl metres
     * @param xyz = x,y,z on the earthSphere
     *   +X comes out indian ocean
     *   +Y comes out north pole
     *   +Z comes out near ghana
     */
    public static void LatLonAlt2XYZ (double lat, double lon, double alt, @NonNull Vector3 xyz)
    {
        double r = alt * (FUNNYFACTOR / EARTHRADIUS) + 1.0;
        double latrad = Math.toRadians (lat);
        double lonrad = Math.toRadians (lon);
        double latcos = Math.cos (latrad);
        xyz.x = r * Math.sin (lonrad) * latcos;
        xyz.y = r * Math.sin (latrad);
        xyz.z = r * Math.cos (lonrad) * latcos;
    }

    /**
     * Convert xyz to lat, lon, alt
     * @param xyz = given x,y,z on the earthSphere
     * @param lla = x:lat (deg); y:lon (deg); z:alt (met msl)
     */
    public static void XYZ2LatLonAlt (@NonNull Vector3 xyz, @NonNull Vector3 lla)
    {
        double x = xyz.x;
        double y = xyz.y;
        double z = xyz.z;
        double r = Math.sqrt (x * x + y * y + z * z);
        double latrad = Math.atan2 (y, Math.hypot (x, z));
        double lonrad = Math.atan2 (x, z);
        lla.x = Math.toDegrees (latrad);
        lla.y = Math.toDegrees (lonrad);
        lla.z = (r - 1.0) * (EARTHRADIUS / FUNNYFACTOR);
    }

    /**
     * Thread that reads in bitmaps that we use to make textures from.
     */
    private static class LoaderThread extends Thread {
        @Override
        public void run ()
        {
            while (true) {
                EarthSector es;
                synchronized (loaderLock) {
                    es = loaderSectorsHead;
                    if (es == null) {
                        SQLiteDBs.CloseAll ();
                        loaderThread = null;
                        return;
                    }
                    loaderSectorsHead = es.nextLoad;
                    if (loaderSectorsHead == null) {
                        loaderSectorsTail = null;
                    }
                }
                es.loadIt ();
            }
        }
    }
}
