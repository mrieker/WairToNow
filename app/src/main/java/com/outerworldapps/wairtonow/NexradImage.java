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

import android.graphics.Bitmap;

import com.apps4av.avarehelper.connections.Nexrad;

/**
 * Contains an Android-style bitmap of a Nexrad image received from ADS-B.
 */
public class NexradImage {
    public final static int WIDTH  = Nexrad.WIDTH;
    public final static int HEIGHT = Nexrad.HEIGHT;

    // scale 3 is undefined so don't show anything if seen
    private final static int[] scalefactors = new int[] { 1, 5, 9, 0 };

    private Bitmap bitmap;
    private int[] data;

    public boolean conus;
    public double northLat, southLat;
    public double eastLon, westLon;
    public int block;  // <22>: southern hemisphere flag; <21:20>: scale; <19:00>: block number
    public int seqno;
    public long time;

    /**
     * Set up Nexrad bitmap
     * @param time  = time image created
     * @param data  = pixels for the image
     * @param block = <22>: southern hemisphere flag; <21:20>: scale; <19:00>: block number
     * @param conus = true: low-res; false: high-res
     */
    public NexradImage (long time, int[] data, int block, boolean conus)
    {
        if (data.length != WIDTH * HEIGHT) throw new IllegalArgumentException ("data");

        this.block = block;
        this.conus = conus;
        this.data  = data;
        this.time  = time;

        int blockno = block & 0xFFFFF;  // <19:00> contains block number

        int scale = scalefactors[(block>>20)&3];  // <21:20> contains scale factor

        // block 0's west edge is on prime meridian
        // low numbered blocks are 48*scale minutes wide
        // high numbered blocks are 96*scale minutes wide
        westLon = Lib.NormalLon ((blockno % 450) * 48.0 / 60.0);
        eastLon = Lib.NormalLon (westLon + (blockno >= 405000 ? 96.0 : 48.0) * scale / 60.0);

        // all blocks are 4*scale minutes high
        int blkn = blockno / 450;
        if ((block & 0x400000) != 0) {  // <22> contains hemisphere flag
            // southern hemisphere - block 0's northern edge is on the equator
            northLat = blkn * -4.0 / 60.0;
            southLat = northLat - 4.0 * scale / 60.0;
        } else {
            // northern hemisphere - block 0's southern edge is on the equator
            southLat = blkn *  4.0 / 60.0;
            northLat = southLat + 4.0 * scale / 60.0;
        }
    }

    public void recycle ()
    {
        data = null;
        if (bitmap != null) {
            bitmap.recycle ();
            bitmap = null;
        }
    }

    /**
     * Get bitmap.  Defer building until needed as user likely will never view it.
     */
    public Bitmap getBitmap ()
    {
        if (bitmap == null) {
            // use ARGB_4444 to allow for transparent pixels
            // and 16 levels per color are plenty good enough
            // (see Nexrad.INTENSITY[] colors)
            bitmap = Bitmap.createBitmap (data, WIDTH, HEIGHT, Bitmap.Config.ARGB_4444);
            data = null;
        }
        return bitmap;
    }

    /**
     * Block out (make transparent) any pixels in this image
     * that overlap pixels in the given image.
     * @param block = lat/lon range that we are to block out
     * @return true: this image still has some opaque pixels
     *        false: this image is all transparent
     */
    public boolean blockout (NexradImage block)
    {
        // if we threw our data away, re-create it by extracting from the bitmap
        if (data == null) {
            data = new int[WIDTH*HEIGHT];
            bitmap.getPixels (data, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        }

        // get pixel size in lat/lon for stepping in +X and +Y directions
        double pixStepLat = (southLat - northLat) / HEIGHT;
        double pixStepLon = (eastLon - westLon) / WIDTH;

        // loop through X and Y pixels
        boolean keepda = false;
        boolean tossbm = false;
        for (int y = 0; y < HEIGHT; y ++) {
            double pixNorthLat = pixStepLat * y + northLat;
            double pixSouthLat = pixNorthLat + pixStepLat;
            for (int x = 0; x < WIDTH; x ++) {

                // skip long computation if pixel already transparent
                if (data[y*WIDTH+x] != 0) {
                    double pixWestLon = pixStepLon * x + westLon;
                    double pixEastLon = pixWestLon + pixStepLon;

                    // if pixel overlaps blocked-out area at all, make it transparent
                    if (Lib.LatOverlap (pixSouthLat, pixNorthLat, block.southLat, block.northLat) &&
                            Lib.LonOverlap (pixWestLon, pixEastLon, block.westLon, block.eastLon)) {
                        data[y*WIDTH+x] = 0;
                        tossbm = true;
                    } else {
                        keepda = true;
                    }
                }
            }
        }

        // if the whole thing is now transparent,
        // get rid of everything
        if (!keepda) {
            data = null;
            tossbm = true;
        }

        // if we made any pixels transparent,
        // throw away the old bitmap if any
        if (tossbm && (bitmap != null)) {
            bitmap.recycle ();
            bitmap = null;
        }

        return keepda;
    }
}
