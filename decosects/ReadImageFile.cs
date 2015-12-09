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

/**
 * @brief Splits a chart file into many small .png files
 */

// yum install libgdiplus
// yum install libgdiplus-devel

// gmcs -debug -out:ReadImageFile.exe -reference:System.Drawing.dll ReadImageFile.cs

// mono --debug ReadImageFile.exe inputfile outputdirectory

using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

public class ReadImageFile {
    public const int hstep = 512;
    public const int wstep = 512;
    public const int overlap = 0;

    private static Bitmap chartbmp;

    public static void Main (string[] args)
    {
        chartbmp = new Bitmap (args[0]);

        /*
         * Split it up into wstep/hstep-sized .png files.
         */
        string dirname = args[1];
        Directory.CreateDirectory (dirname);
        for (int hhh = 0; hhh < chartbmp.Height; hhh += hstep) {
            int thish = chartbmp.Height - hhh;
            if (thish > hstep + overlap) thish = hstep + overlap;
            string subdirname = dirname + "/" + (hhh / hstep).ToString ();
            Directory.CreateDirectory (subdirname);
            for (int www = 0; www < chartbmp.Width; www += wstep) {
                int thisw = chartbmp.Width  - www;
                if (thisw > wstep + overlap) thisw = wstep + overlap;
                string savename = subdirname + "/" + (www / wstep).ToString () + ".png";
                Bitmap thisbm = new Bitmap (thisw, thish);
                for (int h = 0; h < thish; h ++) {
                    for (int w = 0; w < thisw; w ++) {
                        thisbm.SetPixel (w, h, chartbmp.GetPixel (www + w, hhh + h));
                    }
                }
                thisbm.Save (savename, System.Drawing.Imaging.ImageFormat.Png);
            }
        }

        /*
         * Make thumbnail 256 pixels high with correct width to maintain aspect ratio.
         */
        int iconh = 256;
        int iconw = chartbmp.Width * iconh / chartbmp.Height;
        Bitmap iconbm = new Bitmap (chartbmp, iconw, iconh);
        string iconname = dirname + "/icon.png";
        iconbm.Save (iconname, System.Drawing.Imaging.ImageFormat.Png);
    }
}
