/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.apps4av.avarehelper.connections;

/**
 * 
 * @author zkhan
 * mods mrieker
 *
 */
public class Id6364Product {
    public static final int WIDTH  = Nexrad.WIDTH;
    public static final int HEIGHT = Nexrad.HEIGHT;

    public static void parse (long timems, byte[] msg, int beg, int end,
                  Reporter reporter, boolean conus)
    {
        /*
         * flags byte:
         *     <7> = 0: clear blocks
         *           1: new image
         *     <6> = 0: northern hemisphere
         *           1: southern hemisphere
         *   <5:4> = scale factor
         *   <3:0> = block_num<19:16>
         */
        int flags = msg[beg++] & 0xFF;
        int schem = (flags & 0x40) << 16;  // hemisphere
        if (conus) schem |= (flags & 0x30) << 16;  // scale (valid for conus only)

        int block_num = (flags & 0x0F) << 16;
        block_num += (msg[beg++] & 0xFF) << 8;
        block_num += msg[beg++] & 0xFF;

        /*
         * Decode blocks RLE encoded
         */
        if ((flags & 0x80) != 0) {
            int[] pixels = new int[WIDTH*HEIGHT];
            int j = 0;
            while (beg < end) {
                // tag<7:3> = number of repeats - 1
                // tag<2:0> = intensity index
                byte tag = msg[beg++];
                int intensity = Nexrad.INTENSITY[tag&0x07];
                int repeats_1 = (tag & 0xF8) >> 3;
                if (j + repeats_1 >= WIDTH * HEIGHT) break;
                do {
                    pixels[j++] = intensity;
                } while (-- repeats_1 >= 0);
            }
            if (Nexrad.INTENSITY_0 != 0) {
                while (j < WIDTH * HEIGHT) {
                    pixels[j++] = Nexrad.INTENSITY_0;
                }
            }

            reporter.adsbGpsNexradImage (timems, conus, block_num | schem, pixels);
        }
        else {
            /*
             * Make a list of empty blocks
             */
            byte bits = msg[beg];
            int nfullbytes = bits & 0x0F;
            int[] blknums = new int[5+8*nfullbytes];
            int nblknums = 0;
            blknums[nblknums++] = block_num | schem;

            // from dump978/extract_nexrad.c
            // Copyright 2015, Oliver Jowett <oliver@mutability.co.uk> [GPL]
            // find the lowest-numbered block of this row
            final int row_size  = 450;
            final int row_start = block_num - (block_num % row_size);
            final int row_end   = row_start + row_size;

            // start testing at <4> of the first byte
            // cuz <3:0> contains count of subsequent full bytes
            byte test = 0x10;
            while (true) {

                // check 'test' to 0x80 of 'bits'
                do {

                    // increment and wrap to stay on same row
                    block_num ++;  //TODO inc by 2 if at or over 405000?
                    if (block_num >= row_end) block_num -= row_size;

                    // if bit is set, the block should be cleared
                    if ((bits & test) != 0) {
                        blknums[nblknums++] = block_num | schem;
                    }

                    // check next higher bit in the byte if any
                    test += test;
                } while (test != 0);

                // stop if no more full bytes of flag bits remaining
                if (-- nfullbytes < 0) break;

                // more to go, get full byte and test all 8 bits
                bits = msg[++beg];
                test = 0x01;
            }

            // pass resultant block number list to UI layer
            reporter.adsbGpsNexradClear (timems, conus, nblknums, blknums);
        }
    }
}
