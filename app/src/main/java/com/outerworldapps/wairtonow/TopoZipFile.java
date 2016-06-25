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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * This class provides random read access to a <i>ZIP-archive</i> file.
 * <p>
 * While {@code ZipInputStream} provides stream based read access to a
 * <i>ZIP-archive</i>, this class implements more efficient (file based) access
 * and makes use of the <i>central directory</i> within a <i>ZIP-archive</i>.
 * <p>
 * Use {@code ZipOutputStream} if you want to create an archive.
 * <p>
 * A temporary ZIP file can be marked for automatic deletion upon closing it.
 *
 * @see TopoZipEntry
 */
public class TopoZipFile implements ZipConstants {
    private final static int TE_LHRO = 0;   // local header relative offset
    private final static int TE_NLEN = 1;   // name length
    private final static int TE_CSIZ = 2;   // compressed size
    private final static int TE_METH = 3;   // compression method
    private final static int TE_SIZE = 4;   // size of a mTopoEntries entry

    private final static int TOPO_SIZE = 7200;  // all files expand to this size

    private byte[] eocdBuffer;
    private int eocdOffset;
    private int numEntries;
    private int mIntLatDeg;
    private long centralDirOffset;
    private RandomAccessFile mRaf;
    private int[] mTopoEntries;
    private byte[] comprBuff = new byte[4096];

    /**
     * Opens a file as <i>ZIP-archive</i>.
     */
    public TopoZipFile (RandomAccessFile raf, int ilatdeg) throws IOException
    {
        mRaf = raf;
        mIntLatDeg = ilatdeg;
        mRaf.seek (0);
        byte[] b4 = new byte[4];
        mRaf.readFully (b4);
        if ((b4[0] != 0x50) || (b4[1] != 0x4B) || (b4[2] != 0x03) || (b4[3] != 0x04)) {
            throw new ZipException ("magic number doesn't match");
        }
        readCentralDir ();
        makeEntriesTable ();
    }

    /**
     * Closes this ZIP file. This method is idempotent.
     *
     * @throws IOException
     *             if an IOException occurs.
     */
    public void close() throws IOException
    {
        if (mRaf != null) { // Only close initialized instances
            mRaf.close();
            mRaf = null;
        }
    }

    /**
     * Returns a byte array of the data of the specified {@code TopoZipEntry}.
     *
     * @param ilon = longitude (degrees)
     * @return a byte array of the data contained in the {@code TopoZipEntry}.
     * @throws IOException
     *             if an {@code IOException} occurs.
     * @throws IllegalStateException if this ZIP file has been closed.
     */
    public byte[] getBytes (int ilon) throws DataFormatException, IOException
    {
        if ((ilon < -180) || (ilon >= 180)) return null;

        /*
         * Make sure this TopoZipEntry is in this Zip file.
         */
        int j = (ilon + 180) * TE_SIZE;
        int nameLen = mTopoEntries[j+TE_NLEN];
        if (nameLen == 0) {
            return null;
        }

        // We don't know the entry data's start position. All we have is the
        // position of the entry's local header. At position 28 we find the
        // length of the extra data. In some cases this length differs from
        // the one coming in the central header.
        mRaf.seek (mTopoEntries[j+TE_LHRO] + 28);
        int loLELOW = mRaf.read () & 0xFF;
        int hiLELOW = mRaf.read () & 0xFF;
        int localExtraLenOrWhatever = (hiLELOW << 8) | loLELOW;
        // Skip the name and this "extra" data or whatever it is:
        mRaf.skipBytes (nameLen + localExtraLenOrWhatever);

        byte[] bytes = new byte[TOPO_SIZE];

        if (mTopoEntries[j+TE_METH] == TopoZipEntry.DEFLATED) {
            int comprSize = mTopoEntries[j+TE_CSIZ];
            if (comprBuff.length < comprSize) comprBuff = new byte[(comprSize+4095)&-4096];
            mRaf.readFully (comprBuff, 0, comprSize);
            Inflater inf = new Inflater (true);
            inf.setInput (comprBuff, 0, comprSize);
            int rc = inf.inflate (bytes, 0, TOPO_SIZE);
            if (rc != TOPO_SIZE) throw new IOException ("only inflated " + rc + " out of " + TOPO_SIZE + " bytes");
        } else {
            mRaf.readFully (bytes, 0, TOPO_SIZE);
        }

        return bytes;
    }

    /**
     * Find the central directory and read the contents.
     *
     * <p>The central directory can be followed by a variable-length comment
     * field, so we have to scan through it backwards.  The comment is at
     * most 64K, plus we have 18 bytes for the end-of-central-dir stuff
     * itself, plus apparently sometimes people throw random junk on the end
     * just for the fun of it.
     *
     * <p>This is all a little wobbly.  If the wrong value ends up in the EOCD
     * area, we're hosed. This appears to be the way that everybody handles
     * it though, so we're in good company if this fails.
     */
    private void readCentralDir() throws IOException
    {
        /*
         * Scan back, looking for the End Of Central Directory field.  If
         * the archive doesn't have a comment, we'll hit it on the first
         * try.
         *
         * No need to synchronize mRaf here -- we only do this when we
         * first open the Zip file.
         */
        long scanOffset = mRaf.length() - ENDHDR;
        if (scanOffset < 0) {
            throw new ZipException ("too short to be Zip");
        }

        long stopOffset = scanOffset - 65536;
        if (stopOffset < 0) {
            stopOffset = 0;
        }

        eocdBuffer = new byte[(int)(mRaf.length()-stopOffset)];
        mRaf.seek (stopOffset);
        mRaf.readFully (eocdBuffer);

        while (true) {
            eocdOffset = (int)(scanOffset - stopOffset);
            int intLE = (int)readEOCDIntLE ();
            if (intLE == 101010256) {
                break;
            }

            if (-- scanOffset < stopOffset) {
                throw new ZipException("EOCD not found; not a Zip archive?");
            }
        }

        /*
         * Found it, read the EOCD.
         */
        int diskNumber = readEOCDShortLE ();
        int diskWithCentralDir = readEOCDShortLE ();
        numEntries = readEOCDShortLE ();
        int totalNumEntries = readEOCDShortLE ();
        /*centralDirSize =*/ readEOCDIntLE ();
        centralDirOffset = readEOCDIntLE ();
        /*commentLen =*/ readEOCDShortLE ();

        if (numEntries != totalNumEntries ||
            diskNumber != 0 ||
            diskWithCentralDir != 0) {
            throw new ZipException("spanned archives not supported");
        }

        eocdBuffer = null;
    }

    private int readEOCDShortLE ()
    {
        int b0 = eocdBuffer[eocdOffset++] & 0xFF;
        int b1 = eocdBuffer[eocdOffset++] & 0xFF;
        return (b1 << 8) | b0;
    }

    private long readEOCDIntLE ()
    {
        int  b0 = eocdBuffer[eocdOffset++] & 0xFF;
        int  b1 = eocdBuffer[eocdOffset++] & 0xFF;
        int  b2 = eocdBuffer[eocdOffset++] & 0xFF;
        long b3 = eocdBuffer[eocdOffset++] & 0xFF;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    /**
     * Read all entries from the central directory.
     */
    private void makeEntriesTable () throws IOException
    {
        /*
         * Seek to the first CDE and read all entries.
         *
         * For performance we want to use buffered I/O when reading the
         * file.  We wrap a buffered stream around the random-access file
         * object.  If we just read from the RandomAccessFile we'll be
         * doing a read() system call every time.
         */
        mTopoEntries = new int[360*TE_SIZE];
        mRaf.seek (centralDirOffset);
        BufferedInputStream bin = new BufferedInputStream (new FileInputStream (mRaf.getFD ()));
        TopoZipEntry newEntry = new TopoZipEntry ();
        for (int i = 0; i < numEntries; i++) {
            newEntry.readEntry (bin);
            String[] parts = newEntry.name.split ("/");
            if ((parts.length == 2) && (newEntry.size == TOPO_SIZE)) {
                try {
                    int ilat = Integer.parseInt (parts[0]);
                    int ilon = Integer.parseInt (parts[1]);
                    if ((ilat == mIntLatDeg) && (ilon >= -180) && (ilon < 180)) {
                        int j = (ilon + 180) * TE_SIZE;
                        mTopoEntries[j+TE_LHRO] = newEntry.mLocalHeaderRelOffset;
                        mTopoEntries[j+TE_NLEN] = newEntry.nameLen;
                        mTopoEntries[j+TE_CSIZ] = newEntry.compressedSize;
                        mTopoEntries[j+TE_METH] = newEntry.compressionMethod;
                    }
                } catch (NumberFormatException nfe) {
                    Lib.Ignored ();
                }
            }
        }
    }
}
