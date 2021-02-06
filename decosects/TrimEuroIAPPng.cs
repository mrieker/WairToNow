//    Copyright (C) 2021, Mike Rieker, Beverly, MA USA
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
 *  Some european plates have huge right/bottom border padding.
 *  Strip it all off.
 *  eg, datums/europlatepngs/ED/ED_AD_2_EDDN_2-7_en.png.2018-09-13.p1
 *   original size 9933 x 14042
 *    trimmed size 4952 x 3629
 *
 * output file created only if different than input
 *  mono --debug TrimEuroIAPPng.exe in.png out.png
 */

using System.Drawing;
using System.Drawing.Imaging;

public class TrimEuroIAPPng {
    public const int BORDER = 225;  // 0.75" at 300dpi

    public static void Main (string[] args)
    {
        string inpng  = args[0];
        string outpng = args[1];

        Bitmap inbmp = new Bitmap (inpng);
        int inwidth  = inbmp.Width;
        int inheight = inbmp.Height;

        Color white = Color.FromArgb (255, 255, 255, 255);

        int outwidth;
        for (outwidth = inwidth; outwidth > 0; -- outwidth) {
            int x = outwidth - 1;
            for (int y = 0; y < inheight; y ++) {
                if (inbmp.GetPixel (x, y) != white) {
                    goto gotwidth;
                }
            }
        }
    gotwidth:;

        int outheight;
        for (outheight = inheight; outheight > 0; -- outheight) {
            int y = outheight - 1;
            for (int x = 0; x < outwidth; x ++) {
                if (inbmp.GetPixel (x, y) != white) {
                    goto gotheight;
                }
            }
        }
    gotheight:;

        outwidth  += BORDER;
        outheight += BORDER;

        if (outwidth  > inwidth)  outwidth  = inwidth;
        if (outheight > inheight) outheight = inheight;

        if ((outwidth < inwidth) || (outheight < inheight)) {

            Bitmap outbmp = new Bitmap (outwidth, outheight);
            for (int y = 0; y < outheight; y ++) {
                for (int x = 0; x < outwidth; x ++) {
                    outbmp.SetPixel (x, y, inbmp.GetPixel (x, y));
                }
            }

            outbmp.Save (outpng, ImageFormat.Png);
        }
    }
}
