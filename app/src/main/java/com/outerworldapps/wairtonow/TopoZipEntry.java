/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified Sep 17, 2014 by M.Rieker to use RAInputStream instead of File
 *   plus streamlined seeks.
 * Modified May 10, 2016 by M.Rieker for use with topo.zip topography files.
 */

package com.outerworldapps.wairtonow;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

/**
 * An instance of {@code TopoZipEntry} represents an entry within a <i>ZIP-archive</i>.
 * An entry has attributes such as name (= path) or the size of its data. While
 * an entry identifies data stored in an archive, it does not hold the data
 * itself. For example when reading a <i>ZIP-file</i> you will first retrieve
 * all its entries in a collection and then read the data for a specific entry
 * through an input stream.
 *
 * @see TopoZipFile
 */
public class TopoZipEntry implements ZipConstants {
    public String name;
    public int compressedSize;
    public int compressionMethod;
    public int size;
    public int nameLen;
    public int mLocalHeaderRelOffset;

    private byte[] mByteBuf = new byte[CENHDR];

    /**
     * Zip entry state: Deflated.
     */
    public static final int DEFLATED = 8;

    /**
     * Read the Central Directory Entry from "in", which must be positioned at
     * the CDE signature.
     *
     * On exit, "in" will be positioned at the start of the next entry.
     */
    public void readEntry (InputStream in) throws IOException
    {
        /*
         * We're seeing performance issues when we call readShortLE and
         * readIntLE, so we're going to read the entire header at once
         * and then parse the results out without using any function calls.
         * Uglier, but should be much faster.
         */

        byte[] hdrBuf = mByteBuf;

        int rc = in.read (hdrBuf, 0, CENHDR);
        if (rc != CENHDR) throw new IOException ("only read " + rc + " of " + CENHDR + " bytes");

        long sig = (hdrBuf[0] & 0xff) | ((hdrBuf[1] & 0xff) << 8) |
            ((hdrBuf[2] & 0xff) << 16) | ((hdrBuf[3] << 24) & 0xffffffffL);
        if (sig != CENSIG) {
             throw new ZipException ("Central Directory Entry not found");
        }

        compressionMethod = (hdrBuf[10] & 0xff) | ((hdrBuf[11] & 0xff) << 8);
        //time = (hdrBuf[12] & 0xff) | ((hdrBuf[13] & 0xff) << 8);
        //modDate = (hdrBuf[14] & 0xff) | ((hdrBuf[15] & 0xff) << 8);
        //crc = (hdrBuf[16] & 0xff) | ((hdrBuf[17] & 0xff) << 8)
        //        | ((hdrBuf[18] & 0xff) << 16)
        //        | ((hdrBuf[19] << 24) & 0xffffffffL);
        compressedSize = (hdrBuf[20] & 0xff) | ((hdrBuf[21] & 0xff) << 8)
                | ((hdrBuf[22] & 0xff) << 16)
                | (hdrBuf[23] << 24);
        size = (hdrBuf[24] & 0xff) | ((hdrBuf[25] & 0xff) << 8)
                | ((hdrBuf[26] & 0xff) << 16)
                | (hdrBuf[27] << 24);
        nameLen = (hdrBuf[28] & 0xff) | ((hdrBuf[29] & 0xff) << 8);
        int extraLen = (hdrBuf[30] & 0xff) | ((hdrBuf[31] & 0xff) << 8);
        int commentLen = (hdrBuf[32] & 0xff) | ((hdrBuf[33] & 0xff) << 8);
        mLocalHeaderRelOffset = (hdrBuf[42] & 0xff) | ((hdrBuf[43] & 0xff) << 8)
                | ((hdrBuf[44] & 0xff) << 16)
                | (hdrBuf[45] << 24);

        if (mByteBuf.length < nameLen) mByteBuf = new byte[(nameLen+31)&-32];
        rc = in.read (mByteBuf, 0, nameLen);
        if (rc != nameLen) throw new IOException ("only read " + rc + " of " + nameLen + " bytes");
        name = new String (mByteBuf, 0, nameLen);

        Lib.Ignored (in.skip (commentLen + extraLen));
    }
}
