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

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Contains topography info (ground elevation at a given lat/lon).
 */
public class Topography {
    public final static String TAG = "WairToNow";
    public final static short INVALID_ELEV = (short) 0x8000;

    private final static HashMap<Integer,short[]> loadedTopos = new HashMap<> ();
    private final static HashSet<Integer> requestedTopos = new HashSet<> ();
    private static LoaderThread loaderThread;

    public static short getElevMetres (float lat, float lon)
    {
        if (lon < -180) lon += 360;
        if (lon >= 180) lon -= 360;

        /*
         * Split given lat,lon into degrees and minutes.
         */
        int ilatmin = Math.round (lat * 60.0F) + 60000;
        int ilonmin = Math.round (lon * 60.0F) + 60000;
        int ilatdeg = ilatmin / 60 - 1000;
        int ilondeg = ilonmin / 60 - 1000;

        /*
         * See if corresponding file is already loaded in memory.
         * If not, put on requested queue and return that it is invalid.
         */
        int key = (ilatdeg << 16) + (ilondeg & 0xFFFF);
        short[] topos;
        synchronized (loadedTopos) {
            topos = loadedTopos.get (key);
            if (topos == null) {
                requestedTopos.add (key);
                if (loaderThread == null) {
                    loaderThread = new LoaderThread ();
                    loaderThread.start ();
                }
                return INVALID_ELEV;
            }
        }

        /*
         * Loaded in memory, return value.
         */
        ilatmin %= 60;
        ilonmin %= 60;
        return topos[ilatmin*60+ilonmin];
    }

    private static class LoaderThread extends Thread {
        @Override
        public void run ()
        {
            final int bsize = 7200;
            byte[] bytes = new byte[bsize];
            int rc;

            while (true) {

                /*
                 * See if there is a file being requested.
                 */
                int key;
                synchronized (loadedTopos) {
                    Iterator<Integer> it = requestedTopos.iterator ();
                    if (!it.hasNext ()) {
                        loaderThread = null;
                        return;
                    }
                    key = it.next ();
                    it.remove ();
                }

                /*
                 * Build filename and see if it exists.
                 */
                short ilatdeg = (short) (key >> 16);
                short ilondeg = (short) key;
                String subname = "/datums/topo/" + ilatdeg + "/" + ilondeg;
                String name = WairToNow.dbdir + subname;
                File file = new File (name);
                if (!file.exists ()) {
                    try {

                        /*
                         * File doesn't exist, read data from web server.
                         */
                        URL url = new URL (MaintView.dldir + subname);
                        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection ();
                        httpCon.setRequestMethod ("GET");
                        httpCon.connect ();
                        rc = httpCon.getResponseCode ();
                        if (rc != HttpURLConnection.HTTP_OK) {
                            throw new IOException ("http response code " + rc + " on " + subname);
                        }
                        InputStream wis = httpCon.getInputStream ();
                        try {
                            for (int ofs = 0; ofs < bsize; ofs += rc) {
                                rc = wis.read (bytes, ofs, bsize - ofs);
                                if (rc <= 0) throw new IOException ("read web file error");
                            }
                        } finally {
                            wis.close ();
                        }

                        /*
                         * Write data to local file.
                         */
                        Lib.Ignored (file.getParentFile ().mkdirs ());
                        FileOutputStream fos = new FileOutputStream (file);
                        try {
                            fos.write (bytes);
                        } finally {
                            fos.close ();
                        }
                    } catch (IOException ioe) {
                        Log.e (TAG, "error downloading " + name, ioe);
                    }
                }

                /*
                 * File (now) exists, read into memory and put in cache.
                 */
                try {
                    FileInputStream fis = new FileInputStream (file);
                    try {
                        rc = fis.read (bytes, 0, bsize);
                        if (rc != bsize) {
                            throw new IOException ("only read " + rc + " of " + bsize + " bytes");
                        }
                        short[] shorts = new short[bsize/2];
                        int j = 0;
                        for (int i = 0; i < bsize / 2; i ++) {
                            int lo = bytes[j++] & 0xFF;
                            int hi = bytes[j++] & 0xFF;
                            shorts[i] = (short) ((hi << 8) + lo);
                        }
                        synchronized (loadedTopos) {
                            loadedTopos.put (key, shorts);
                        }
                    } finally {
                        fis.close ();
                    }

                } catch (IOException ioe) {
                    Log.e (TAG, "error reading " + name, ioe);
                }
            }
        }
    }
}
