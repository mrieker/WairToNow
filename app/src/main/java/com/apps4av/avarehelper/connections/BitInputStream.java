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
 * A class that reads bits from a data stream.
 * @author zkhan
 * mods mrieker
 *
 */
public class BitInputStream {

    private byte mBuffer[];
    private byte mIBuffer;   // contents of mBuffer[mLocation-1]
    private int  mLocation;  // where to read if something needed and mBitsLeft is zero
    private int  mBitsLeft;  // number of bits in bottom of mIBuffer yet to be read
    private int  mOffset;    // offset we started at in mBuffer[]
    private int  mLength;    // length of valid bytes including mOffset
 
    public BitInputStream (byte buffer[], int offset, int length) {
        mBuffer = buffer;
        mLocation = mOffset = offset;
        mLength = length + offset;
    }

    /**
     * Get some bits from the stream.
     * BIG ENDIAN style, we start at the top of low numbered byte
     * then fill from tops of successive bytes in the stream.
     * @param num = number of bits wanted, le 32
     * @return those bits packed into an int
     */
    public int getBits(int num) {
        int value = 0;
        while (num > mBitsLeft) {
            value   <<= mBitsLeft;
            value    |= mIBuffer & ((1 << mBitsLeft) - 1);
            num      -= mBitsLeft;
            if (mLocation >= mLength) throw new ArrayIndexOutOfBoundsException ();
            mIBuffer  = mBuffer[mLocation++];
            mBitsLeft = 8;
        }
        mBitsLeft -= num;
        value    <<= num;
        value     |= (mIBuffer >> mBitsLeft) & ((1 << num) - 1);
        return value;
    }

    /**
     * Get next single bit and see if one.
     * @return true iff next bit is one
     */
    public boolean getBoolean () {
        if (-- mBitsLeft < 0) {
            if (mLocation >= mLength) throw new ArrayIndexOutOfBoundsException ();
            mIBuffer = mBuffer[mLocation++];
            mBitsLeft = 7;
        }
        return ((mIBuffer >> mBitsLeft) & 1) != 0;
    }
    
    /**
     * Get number of bytes read, including any partial byte on the end.
     */
    public int totalRead() {
        return mLocation - mOffset;
    }
}
