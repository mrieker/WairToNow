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

import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Holds the current Nexrad images from all sources.
 */
public class NexradRepo implements Iterable<NexradImage> {
    private final static int MAXIMAGES = 1620;
    private final static int TIMELIMIT = 60 * 60 * 1000;  // drop if not updated in an hour

    private volatile boolean amEmpty;

    private HashMap<Integer,NexradImage> imageConus = new HashMap<> ();
    private HashMap<Integer,NexradImage> imageRegnl = new HashMap<> ();
    private int lastSeqno = Integer.MIN_VALUE;
    private TreeMap<Integer,NexradImage> imageAges  = new TreeMap<> ();

    /**
     * See if there are any images before locking and looping.
     * @return true iff not
     */
    public boolean isEmpty ()
    {
        /*
         * Drawers can read it unlocked before locking and looping:
         *
         *  if (!repo.isEmpty()) {  // false empty, ie, writer just inserted
         *                          //                  writer will then postInvalidate()
         *                          //                      drawer gets called again to rescan
         *                          // false full, ie, writer just purged
         *                          //                 subsequent locked for loop will find nothing
         *      synchronize (repo) {
         *          for (NexradImage bm : repo) {
         *              ... process image
         *          }
         *      }
         *  }
         */
        return amEmpty;
    }

    /**
     * Iterate through the list from oldest image to newest one.
     * ** CALLER MUST HAVE the repo LOCKED **
     */
    public Iterator<NexradImage> iterator ()
    {
        return new MyIterator ();
    }

    private class MyIterator implements Iterator<NexradImage> {
        private Iterator<NexradImage> it = imageAges.values ().iterator ();
        private long timelimit = System.currentTimeMillis () - TIMELIMIT;
        private NexradImage nextOne;

        @Override
        public boolean hasNext ()
        {
            if (nextOne != null) return true;
            while (it.hasNext ()) {
                nextOne = it.next ();
                if (nextOne.time >= timelimit) return true;
                it.remove ();
                removedOldNexrad (nextOne);
            }
            nextOne = null;
            amEmpty = imageAges.isEmpty ();
            return false;
        }

        @Override
        public NexradImage next ()
        {
            NexradImage ni = nextOne;
            if (ni != null) {
                nextOne = null;
            } else {
                while ((ni = it.next ()).time < timelimit) {
                    it.remove ();
                    removedOldNexrad (ni);
                }
            }
            return ni;
        }

        @Override
        public void remove ()
        {
            throw new IllegalStateException ("remove not supported");
        }
    }

    /**
     * Purge the given blocks from the repo.
     * ** CALLER MUST HAVE the repo LOCKED **
     */
    public void deleteBlocks (boolean conus, int nblocks, int[] blocks)
    {
        HashMap<Integer,NexradImage> hm = conus ? imageConus : imageRegnl;

        for (int i = 0; i < nblocks; i ++) {
            int block = blocks[i];
            if (hm.containsKey (block)) {
                NexradImage ni = hm.get (block);
                ni.recycle ();
                hm.remove (block);
                imageAges.remove (ni.seqno);
            }
        }

        amEmpty = imageAges.isEmpty ();
    }

    /**
     * Insert a new image into the repo.
     * ** CALLER MUST HAVE the repo LOCKED **
     */
    public void insertNewNexrad (NexradImage newni)
    {
        newni.seqno = ++ lastSeqno;

        HashMap<Integer,NexradImage> hm = newni.conus ? imageConus : imageRegnl;

        // delete old bitmap for same lat/lon and resolution
        NexradImage oldni = hm.get (newni.block);
        if (oldni != null) {
            imageAges.remove (oldni.seqno);
            removedOldNexrad (oldni);
        }

        // delete oldest bitmap overall to make room if we have too many
        while (imageAges.size () >= MAXIMAGES) {
            int oldseqno = imageAges.keySet ().iterator ().next ();
            oldni = imageAges.remove (oldseqno);
            removedOldNexrad (oldni);
        }

        // insert on lists
        hm.put (newni.block, newni);
        imageAges.put (newni.seqno, newni);

        // if just added an high-res (conus=false) image, make
        // any overlapping low-res (conus=true) pixels transparent
        if (!newni.conus) {
            for (Iterator<Integer> it = imageConus.keySet ().iterator (); it.hasNext ();) {
                int block = it.next ();
                NexradImage lores = imageConus.get (block);
                if (Lib.LatOverlap (newni.southLat, newni.northLat,
                        lores.southLat, lores.northLat) &&
                        Lib.LonOverlap (newni.westLon, newni.eastLon,
                                lores.westLon, lores.eastLon)) {
                    if (!lores.blockout (newni)) {
                        it.remove ();
                        lores.recycle ();
                        imageAges.remove (lores.seqno);
                    }
                }
            }
        }

        // conversely, if we just added a low-res (conus=true) image,
        // make any of its pixels that are overlapped by an high-res
        // (conus=false) image transparent
        else {
            for (NexradImage hires : imageRegnl.values ()) {
                if (Lib.LatOverlap (newni.southLat, newni.northLat,
                        hires.southLat, hires.northLat) &&
                        Lib.LonOverlap (newni.westLon, newni.eastLon,
                                hires.westLon, hires.eastLon)) {
                    if (!newni.blockout (hires)) {
                        imageConus.remove (newni.block);
                        imageAges.remove (newni.seqno);
                        break;
                    }
                }
            }
        }

        amEmpty = false;
    }

    /**
     * Caller just removed an entry from imageAges.
     * Delete it from the other lists.
     */
    private void removedOldNexrad (NexradImage oldni)
    {
        oldni.recycle ();
        if (oldni.conus) {
            imageConus.remove (oldni.block);
        } else {
            imageRegnl.remove (oldni.block);
        }
    }
}
