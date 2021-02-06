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

// some european plates have huge borders so trim them off
// - output png file is bigger than TrimEuroIAPPng.cs so use that instead

// apt-get install libpng12-dev
// cc -Wall -Werror -std=c99 -O2 -g -o trimeuroiappng trimeuroiappng.c -lpng
// ./trimeuroiappng in.png out.png

#include <errno.h>
#include <fcntl.h>
#include <png.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#define FATAL do { fprintf (stderr, "fatal error %d\n", __LINE__); exit (4); } while (0)

#define WHITE ((png_byte) -1)

typedef int S32;
typedef unsigned char U8;

void read_png_file (char *file_name, png_bytep **row_pointers_ret, int *width_ret, int *height_ret);

int main (int argc, char ** argv)
{
    FILE *pngfile;
    int width_in, height_in;
    int width_out, height_out;
    png_byte **row_pointers;

    read_png_file (argv[1], &row_pointers, &width_in, &height_in);

    for (height_out = height_in; height_out > 0; -- height_out) {
        int y = height_out - 1;
        for (int x = 0; x < width_in * 3; x ++) {
            if (row_pointers[y][x] != WHITE) goto got_height;
        }
    }
got_height:;

    for (width_out = width_in; width_out > 0; -- width_out) {
        for (int y = 0; y < height_out; y ++) {
            for (int x = width_out * 3; -- x >= width_out * 3 - 3;) {
                if (row_pointers[y][x] != WHITE) goto got_width;
            }
        }
    }
got_width:;

    width_out  += 225;
    height_out += 225;
    if (width_out  > width_in)  width_out  = width_in;
    if (height_out > height_in) height_out = height_in;

    if ((width_out == width_in) && (height_out == height_in)) return 0;

    /*
     * Write .PNG file.
     */
    png_structp png_ptr = png_create_write_struct (PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
    if (png_ptr == NULL) FATAL;
    png_infop info_ptr = png_create_info_struct (png_ptr);
    if (info_ptr == NULL) FATAL;
    if (setjmp (png_jmpbuf (png_ptr))) FATAL;

    png_set_IHDR (png_ptr, info_ptr, width_out, height_out, 8, PNG_COLOR_TYPE_RGB, PNG_INTERLACE_NONE, PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);

    pngfile = fopen (argv[2], "wb");
    if (pngfile == NULL) {
        fprintf (stderr, "error creating %s: %s\n", argv[2], strerror (errno));
        return 3;
    }

    png_init_io (png_ptr, pngfile);
    png_set_rows (png_ptr, info_ptr, row_pointers);
    png_write_png (png_ptr, info_ptr, PNG_TRANSFORM_IDENTITY, NULL);
    fclose (pngfile);

    return 0;
}

// http://zarb.org/~gc/html/libpng.html
void read_png_file (char *file_name, png_bytep **row_pointers_ret, int *width_ret, int *height_ret)
{
    char header[8];    // 8 is the maximum size that can be checked

    /* open file and test for it being a png */
    FILE *fp = fopen (file_name, "rb");
    if (!fp) FATAL;
    fread (header, 1, 8, fp);
    if (png_sig_cmp ((png_const_bytep) header, 0, 8)) FATAL;

    /* initialize stuff */
    png_structp png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
    if (!png_ptr) FATAL;

    png_infop info_ptr = png_create_info_struct(png_ptr);
    if (!info_ptr) FATAL;

    if (setjmp (png_jmpbuf (png_ptr))) FATAL;

    png_init_io(png_ptr, fp);
    png_set_sig_bytes(png_ptr, 8);

    png_read_info(png_ptr, info_ptr);

    int width = png_get_image_width(png_ptr, info_ptr);
    int height = png_get_image_height(png_ptr, info_ptr);
    int color_type = png_get_color_type(png_ptr, info_ptr);
    int bit_depth = png_get_bit_depth(png_ptr, info_ptr);
    int number_of_passes = png_set_interlace_handling(png_ptr);

    if (number_of_passes != 1) FATAL;

    switch (bit_depth) {
        case 8: {
            break;
        }
        case 16: {
            // http://www.libpng.org/pub/png/libpng-1.2.5-manual.html
            png_set_strip_16 (png_ptr);
            break;
        }
        default: FATAL;
    }

    switch (color_type) {
        case PNG_COLOR_TYPE_RGB: {
            break;
        }
        case PNG_COLOR_TYPE_RGBA: {
            // http://www.libpng.org/pub/png/libpng-1.2.5-manual.html
            png_set_strip_alpha (png_ptr);
            break;
        }
        default: FATAL;
    }

    png_read_update_info(png_ptr, info_ptr);

    /* read file */
    if (setjmp(png_jmpbuf(png_ptr))) FATAL;

    png_bytep *row_pointers = (png_bytep*) malloc(sizeof(png_bytep) * height);
    for (int y = 0; y < height; y ++) {
        row_pointers[y] = (png_byte*) malloc(png_get_rowbytes(png_ptr,info_ptr));
    }

    png_read_image(png_ptr, row_pointers);

    fclose(fp);

    *row_pointers_ret = row_pointers;
    *width_ret = width;
    *height_ret = height;
}
