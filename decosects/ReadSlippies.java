//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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
 * Convert openflightmap.org slippy zip tiles to wtn zip tiles.
 */

// javac ReadSlippies.java
// java ReadSlippies 20201203 20201231
// ./makewtnzipfiles.sh

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import javax.imageio.ImageIO;

public class ReadSlippies {
    public final static int instep  = 256;
    public final static int outstep = instep;
    public final static int maxthreads = 12;
    public final static int zoom = 10;
    public final static int size = 256;

    public static BufferedImage nullimage;
    public static HashMap<Long,BufferedImage> compositeimages;
    public static HashMap<Long,BufferedImage> streetimages;
    public static int nthreads;
    public static int[] nullarray;
    public static Object threadlock = new Object ();
    public static String effdate;
    public static String expdate;

    public static class RegAttrs {
        public int selcolor;
        public int centerx;
        public int centery;
        public boolean right;

        public RegAttrs (int sc, int cx, int cy, boolean r)
        {
            selcolor = sc;
            centerx  = cx;
            centery  = cy;
            right    = r;
        }
    }

    public static HashMap<String,RegAttrs> regattrss = getRegAttrss ();

    public static HashMap<String,RegAttrs> getRegAttrss ()
    {
        int RED = 0xFFFF0000;
        int GRN = 0xFF00FF00;
        int BLU = 0xFF0000FF;
        int WHT = 0xFFFFFFFF;
        int BLK = 0xFF000000;

        HashMap<String,RegAttrs> rass = new HashMap<> ();

        rass.put ("Austria",        new RegAttrs (RED, 134, 140, false));
        rass.put ("Belgium",        new RegAttrs (WHT, 104, 128, false));
        rass.put ("Bulgaria",       new RegAttrs (GRN, 168, 162, true));
        rass.put ("Croatia",        new RegAttrs (BLU, 141, 160, false));
        rass.put ("Czech_Republic", new RegAttrs (BLU, 134, 131, true));
        rass.put ("Denmark",        new RegAttrs (BLU, 114,  98, false));
        rass.put ("Finland",        new RegAttrs (RED, 169,  57, true));
        rass.put ("Germany",        new RegAttrs (GRN, 121, 123, false));
        rass.put ("Greece",         new RegAttrs (BLU, 160, 177, true));
        rass.put ("Hungary",        new RegAttrs (BLK, 149, 142, true));
        rass.put ("Italy",          new RegAttrs (GRN, 128, 178, false));
        rass.put ("Netherlands",    new RegAttrs (RED, 104, 112, false));
        rass.put ("Poland",         new RegAttrs (RED, 148, 119, true));
        rass.put ("Romania",        new RegAttrs (RED, 166, 145, true));
        rass.put ("Slovakia",       new RegAttrs (GRN, 149, 136, true));
        rass.put ("Slovenia",       new RegAttrs (WHT, 135, 147, false));
        rass.put ("Sweden",         new RegAttrs (GRN, 139,  61, false));
        rass.put ("Switzerland",    new RegAttrs (BLU, 110, 144, false));

        return rass;
    }

    public static class Region extends Thread {
        public int regidx;          // internal region number
        public File infile;         // OFM_<region>_<effdate>.zip
        public HashMap<Long,int[]> tiles;
        public int xlo;
        public int xhi;
        public int ylo;
        public int yhi;
        public RegAttrs attrs;
        public String basename;     // "Slovakia"

        /**
         * Read all tiles from the zip file into memory for the given size and zoom.
         */
        public void readTiles ()
                throws Exception
        {
            tiles = new HashMap<> ();
            xlo = 999999999;
            xhi = -99999999;
            ylo = 999999999;
            yhi = -99999999;
            ZipFile zipfile = new ZipFile (infile);
            for (Enumeration<? extends ZipEntry> it = zipfile.entries (); it.hasMoreElements ();) {
                ZipEntry zipfileentry = it.nextElement ();
                String[] zipfileentryname = zipfileentry.getName ().split ("/");
                if ((zipfileentryname.length == 7) && zipfileentryname[0].equals ("clip") && zipfileentryname[1].equals ("aero") &&
                    zipfileentryname[2].equals (Integer.toString (size)) && zipfileentryname[3].equals ("latest") &&
                    zipfileentryname[4].equals (Integer.toString (zoom)) && zipfileentryname[6].endsWith (".png")) {
                    int x = Integer.parseInt (zipfileentryname[5]);
                    int y = Integer.parseInt (zipfileentryname[6].substring (0, zipfileentryname[6].length () - 4));
                    if (xlo > x) xlo = x;
                    if (xhi < x) xhi = x;
                    if (ylo > y) ylo = y;
                    if (yhi < y) yhi = y;
                    long key = (((long) x) << 32) | y;
                    InputStream tilestream = zipfile.getInputStream (zipfileentry);
                    BufferedImage tileimage = ImageIO.read (tilestream);
                    tilestream.close ();
                    int[] tilearray = tileimage.getRGB (0, 0, size, size, null, 0, size);
                    tiles.put (key, tilearray);
                }
            }
            zipfile.close ();
        }

        /**
         * The tiles in the zip file leave many holes in the interior of the region.
         * So fill the holes with a null image so the selection image looks good.
         */
        public void fillHoles ()
        {
            int xsz = xhi - xlo + 1;
            int ysz = yhi - ylo + 1;
            boolean[][] filled = new boolean[xsz][ysz];

            for (Long key : tiles.keySet ()) {
                int x = (int) (key >> 32) - xlo;
                int y = (int) (long) key - ylo;
                filled[x][y] = true;
            }

            boolean repeat;
            do {
                repeat = false;
                for (int y = 0; y < ysz; y ++) {
                    for (int x = 0; x < xsz; x ++) {
                        if (! filled[x][y]) {
                            int fc = 0;
                            for (int xx = x; -- xx >= 0;)  if (filled[xx][y]) { fc ++; break; }
                            for (int xx = x; ++ xx < xsz;) if (filled[xx][y]) { fc ++; break; }
                            for (int yy = y; -- yy >= 0;)  if (filled[x][yy]) { fc ++; break; }
                            for (int yy = y; ++ yy < ysz;) if (filled[x][yy]) { fc ++; break; }
                            if (fc == 4) {
                                filled[x][y] = true;
                                long key = (((long) x + xlo) << 32) | (y + ylo);
                                tiles.put (key, nullarray);
                                repeat = true;
                            }
                        }
                    }
                }
            } while (repeat);
        }

        /**
         * Generate all output files.
         *   charts/OFM_<chart>_{Street,White}_<effdate>/     - tiles & icon.png
         *   charts/OFM_<chart>_{Street,White}_<effdate>.csv  - georef info
         */
        @Override
        public void run ()
        {
            try {
                genOutput ();
            } catch (Throwable t) {
                System.err.println ("error processing region " + infile.getPath ());
                t.printStackTrace (System.err);
            } finally {
                synchronized (threadlock) {
                    -- nthreads;
                    threadlock.notifyAll ();
                }
            }
        }

        public void genOutput ()
                throws Exception
        {
            for (String backgnd : new String[] { "Street", "White" }) {

                // charts/OFM_Czech_Republic_Street_20201203
                String outbase = infile.getPath ().replace ("_" + effdate + ".zip", "_" + backgnd + "_" + effdate);
                System.out.println ("  " + outbase);

                File csvfile = new File (outbase + ".csv");
                if (! csvfile.exists ()) {

                    // delete partial outputs from an old run
                    recursivedelete (new File (outbase));
                    recursivedelete (new File (outbase + ".wtn.zip"));

                    // make bitmap large enough to hold the whole region
                    // it will be used to make the icon later
                    int bigiconwidth  = size * (this.xhi - this.xlo + 1);
                    int bigiconheight = size * (this.yhi - this.ylo + 1);
                    BufferedImage bigiconimage = new BufferedImage (bigiconwidth, bigiconheight, BufferedImage.TYPE_4BYTE_ABGR);
                    Graphics2D bigicongraphics = bigiconimage.createGraphics ();

                    // loop through all x,y's for the whole region
                    for (int y = this.ylo; y <= this.yhi; y ++) {
                        File yfile = new File (outbase, Integer.toString (y - this.ylo));
                        yfile.mkdirs ();
                        for (int x = this.xlo; x <= this.xhi; x ++) {
                            long key = (((long) x) << 32) | y;

                            // write out a tile for that x,y for the region
                            // always write a tile cuz sometimes there are missing tiles in the middle of a region
                            BufferedImage image = new BufferedImage (size, size, BufferedImage.TYPE_4BYTE_ABGR);
                            Graphics2D graphics = image.createGraphics ();

                            Color bgcolor;
                            switch (backgnd) {
                                case "Street": {
                                    BufferedImage streetimg;
                                    synchronized (streetimages) {
                                        while ((streetimg = streetimages.get (key)) == nullimage) {
                                            streetimages.wait ();
                                        }
                                        if (streetimg == null) {
                                            streetimages.put (key, nullimage);
                                        }
                                    }
                                    if (streetimg == null) {
                                        String streetstr = "http://www.outerworldapps.com/WairToNow/streets.php?tile=" + zoom + "/" + x + "/" + y + ".png";
                                        try {
                                            URL streeturl = new URL (streetstr);
                                            streetimg = ImageIO.read (streeturl);
                                        } catch (Exception e) {
                                            URL streeturl = new URL (streetstr);
                                            streetimg = ImageIO.read (streeturl);
                                        } finally {
                                            synchronized (streetimages) {
                                                streetimages.put (key, streetimg);
                                                streetimages.notifyAll ();
                                            }
                                        }
                                    }
                                    if (! graphics.drawImage (streetimg, 0, 0, size, size, Color.WHITE, null)) {
                                        throw new Exception ("error drawing street tile");
                                    }
                                    bigicongraphics.drawImage (streetimg, (x - this.xlo) * size, (y - this.ylo) * size, size, size, Color.WHITE, null);
                                    bgcolor = null;
                                    break;
                                }
                                case "White": {
                                    bgcolor = Color.WHITE;
                                    break;
                                }
                                default: throw new Exception ("bad backgnd");
                            }

                            BufferedImage compimage = compositeimages.get (key);
                            if (compimage == null) compimage = nullimage;

                            graphics.drawImage (compimage, 0, 0, size, size, bgcolor, null);
                            bigicongraphics.drawImage (compimage, (x - this.xlo) * size, (y - this.ylo) * size, size, size, bgcolor, null);

                            File tilefile = new File (yfile, Integer.toString (x - this.xlo) + ".png");
                            if (! ImageIO.write (image, "png", tilefile)) {
                                throw new IOException ("failed to write " + tilefile.getPath ());
                            }
                        }
                    }

                    // write icon image
                    int iconheight = 256;
                    int iconwidth  = iconheight * bigiconwidth / bigiconheight;
                    BufferedImage iconimage = new BufferedImage (iconwidth, iconheight, BufferedImage.TYPE_4BYTE_ABGR);
                    Graphics2D icongraphics = iconimage.createGraphics ();
                    icongraphics.drawImage (bigiconimage, 0, 0, iconwidth, iconheight, 0, 0, bigiconwidth, bigiconheight, null);
                    File iconfile = new File (outbase, "icon.png");
                    if (! ImageIO.write (iconimage, "png", iconfile)) {
                        throw new IOException ("failed to write icon");
                    }

                    // write csv file (see AirChart.java subclass Slp)
                    // - do this last cuz it is used as a completion marker
                    File csvtempfile = new File (csvfile.getPath () + ".tmp");
                    FileWriter csvwriter = new FileWriter (csvtempfile);
                    String snwr = outbase.replace ("charts/", "").replace ("_", " ");
                    csvwriter.write ("slp:" + this.xlo + "," + this.xhi + "," + this.ylo + "," + this.yhi + "," +
                                                zoom + "," + size + "," + effdate + "," + expdate + "," + snwr + "\n");
                    csvwriter.close ();
                    if (! csvtempfile.renameTo (csvfile)) throw new IOException ("error renaming to " + csvfile.getPath ());
                }
            }
        }
    }

    public static void main (String[] args)
            throws Exception
    {
        effdate = args[0];  // eg "20201203"
        expdate = args[1];  // eg "20201231"

        nullarray = new int[size*size];
        nullimage = new BufferedImage (size, size, BufferedImage.TYPE_4BYTE_ABGR);

        // read all tiles from all the regions
        //  charts/OFM_<region>_<effdate>.zip
        // get range of x and y for zoom level 10, pixel size 256 x 256 for each region
        // entries are named clip/aero/256/latest/10/x/y.png

        System.out.println ("reading regions");
        ArrayList<Region> regions = new ArrayList<> ();
        int overallxlo = 999999999;
        int overallxhi = -99999999;
        int overallylo = 999999999;
        int overallyhi = -99999999;
        int regionidx  = 0;
        for (File chartfile : new File ("charts").listFiles ()) {
            String chartname = chartfile.getName ();
            if (chartname.startsWith ("OFM_")) {
                if (chartname.endsWith ("_" + effdate + ".zip")) {
                    System.out.println ("  " + chartfile.getPath ());
                    Region region = new Region ();
                    region.regidx = regionidx ++;
                    region.infile = chartfile;
                    region.basename = chartname.substring (4, chartname.length () - 1 - effdate.length () - 4);
                    region.attrs  = regattrss.get (region.basename);
                    regions.add (region);
                    region.readTiles ();
                    region.fillHoles ();
                    if (overallxlo > region.xlo) overallxlo = region.xlo;
                    if (overallxhi < region.xhi) overallxhi = region.xhi;
                    if (overallylo > region.ylo) overallylo = region.ylo;
                    if (overallyhi < region.yhi) overallyhi = region.yhi;
                }
            }
        }

        // make composite tiles for all the tiles we have

        System.out.println ("compositing tiles");

        int overallwidth   = overallxhi - overallxlo + 1;
        int overallheight  = overallyhi - overallylo + 1;
        long[][] tilemasks = new long[overallwidth][overallheight];
        HashMap<Long,int[]> compositetiles = new HashMap<> ();
        for (Region region : regions) {
            System.out.println ("  " + region.infile.getPath ());
            for (Long key : region.tiles.keySet ()) {
                int x = (int) (key >> 32);
                int y = (int) (long) key;
                tilemasks[x-overallxlo][y-overallylo] |= 1L << region.regidx;
                int[] tilergb = region.tiles.get (key);
                int[] comprgb = compositetiles.get (key);
                if (comprgb == null) {
                    comprgb = new int[size*size];
                    compositetiles.put (key, comprgb);
                }
                for (int i = 0; i < size * size; i ++) {
                    comprgb[i] |= tilergb[i];
                }
                region.tiles.put (key, null);
            }
        }

        compositeimages = new HashMap<> ();
        for (Long key : compositetiles.keySet ()) {
            int[] comprgb = compositetiles.get (key);
            BufferedImage image = new BufferedImage (size, size, BufferedImage.TYPE_4BYTE_ABGR);
            image.setRGB (0, 0, size, size, comprgb, 0, size);
            compositeimages.put (key, image);
        }

        // output a chart selection image
        System.out.println ("writing ofmselector.png");
        int margin = 10;
        int selimwidth = overallwidth * 3 + 2 * margin;
        int selimheight = overallheight + 2 * margin;
        BufferedImage selimage = new BufferedImage (selimwidth, selimheight, BufferedImage.TYPE_4BYTE_ABGR);
        for (Region region : regions) {
            RegAttrs ras = region.attrs;
            if (ras == null) continue;
            if (ras == null) continue;
            int newcolor = ras.selcolor;
            for (Long key : region.tiles.keySet ()) {
                int x = (int) (key >> 32) - overallxlo;
                int y = (int) (long) key  - overallylo;
                int oldcolor = selimage.getRGB (x + margin + overallwidth, y + margin);
                if (oldcolor == 0) {
                    selimage.setRGB (x + margin + overallwidth, y + margin, newcolor);
                } else {
                    boolean odd = ((x ^ y) & 1) != 0;
                    if (odd ^ (newcolor > oldcolor)) {
                        selimage.setRGB (x + margin + overallwidth, y + margin, newcolor);
                    }
                }
            }
        }
        for (int y = 0; y < overallheight + 2 * margin; y ++) {
            for (int x = 0; x < overallwidth * 3 + 2 * margin; x ++) {
                int oldcolor = selimage.getRGB (x, y);
                if (oldcolor == 0) selimage.setRGB (x, y, 0xFFCCCCCC);
            }
        }
        Graphics2D selgraphics = selimage.createGraphics ();
        for (Region region : regions) {
            RegAttrs ras = region.attrs;
            if (ras == null) continue;
            int cx = ras.centerx;
            int cy = ras.centery;
            int tx = ras.right ? margin * 2 + overallwidth * 2 : margin;
            char[] chars = region.basename.toCharArray ();
            selgraphics.setColor (new Color (ras.selcolor));
            selgraphics.drawChars (chars, 0, chars.length, tx, cy);
        }
        File selfile = new File ("ofmselector.png");
        if (! ImageIO.write (selimage, "png", selfile)) {
            throw new IOException ("failed to write selector");
        }

        // write out a rectangular sized array of tiles for each region from the composite tiles
        // also generate the .csv file and icon.png image for the region

        System.out.println ("writing regions");
        streetimages = new HashMap<> ();
        for (Region region : regions) {
            synchronized (threadlock) {
                while (nthreads >= maxthreads) {
                    threadlock.wait ();
                }
                nthreads ++;
            }
            region.start ();
        }

        synchronized (threadlock) {
            while (nthreads > 0) {
                System.out.println ("waiting for " + nthreads);
                threadlock.wait ();
            }
        }
    }

    public static void recursivedelete (File file)
    {
        if (file.isDirectory ()) {
            for (File child : file.listFiles ()) {
                recursivedelete (child);
            }
        }
        file.delete ();
    }
}
