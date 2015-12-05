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
 * @brief Splits a sectional chart .tif file
 *        into many small .png files
 */

// yum install libgdiplus
// yum install libgdiplus-devel

// gmcs -debug -out:MakeImage.exe -reference:System.Drawing.dll MakeImage.cs
// mono --debug MakeImage.exe

using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Text;

public class MakeImage {
    public static void Main (string[] args)
    {
        Bitmap tilebm = new Bitmap ("sectionalclip.png");

        // http://www.graphicsfactory.com
        // Your order details:
        //   # Items in order:  1
        //   Amount:    $9.99
        //   Date of order:     21 Nov 2015

        Bitmap boybmp = new Bitmap ("158720/158720-gif_image.gif");

        for (int y = 0; y < 256; y ++) {
            for (int x = 0; x < 256; x ++) {
                Color t = tilebm.GetPixel (x, y);
                Color b = Color.White;
                if (y < 192) {
                    b = boybmp.GetPixel (x + 32, y + 32);
                }
                Color c = Color.FromArgb (
                        (2 * t.R + b.R) / 3,
                        (2 * t.G + b.G) / 3,
                        (2 * t.B + b.B) / 3);
                tilebm.SetPixel (x, y, c);
            }
        }

        for (int y = 0; y < 256; y ++) {
            for (int x = 0; x < 256; x ++) {
                double r = Math.Sqrt ((y - 128) * (y - 128) + (x - 128) * (x - 128));
                if (r > 128) {
                    tilebm.SetPixel (x, y, Color.Transparent);
                } else if (r > 103) {
                    Color c = tilebm.GetPixel (x, y);
                    Color m = Color.FromArgb ((int) (128 - r) * 10, c.R, c.G, c.B);
                    tilebm.SetPixel (x, y, m);
                }
            }
        }

        // Write 256x256 splash image

        tilebm.Save ("splash.png", System.Drawing.Imaging.ImageFormat.Png);

        // Write 64x64 icon image

        Bitmap iconbm = new Bitmap (64, 64);

        for (int y = 0; y < 64; y ++) {
            for (int x = 0; x < 64; x ++) {
                double r = Math.Sqrt ((x - 32) * (x - 32) + (y - 32) * (y - 32));
                if (r > 32) {
                    tilebm.SetPixel (x, y, Color.Transparent);
                } else {
                    double a = Math.Sqrt (Math.Sqrt (Math.Cos (r / 32 * Math.PI / 2)));
                    Color c = tilebm.GetPixel (58 + x, 158 + y);
                    Color m = Color.FromArgb ((int) (a * 255 + 0.5), c.R, c.G, c.B);
                    iconbm.SetPixel (x, y, m);
                }
            }
        }

        iconbm.Save ("icon.png", System.Drawing.Imaging.ImageFormat.Png);
    }
}
