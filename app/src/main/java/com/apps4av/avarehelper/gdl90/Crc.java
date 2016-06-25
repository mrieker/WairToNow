/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.apps4av.avarehelper.gdl90;

/**
 * 
 * @author zkhan
 *
 */
public class Crc {

    private static final short[] CRC_TABLE = {
        (short)0x0000,(short)0x1021,(short)0x2042,(short)0x3063,(short)0x4084,(short)0x50a5,(short)0x60c6,(short)0x70e7,
        (short)0x8108,(short)0x9129,(short)0xa14a,(short)0xb16b,(short)0xc18c,(short)0xd1ad,(short)0xe1ce,(short)0xf1ef,
        (short)0x1231,(short)0x0210,(short)0x3273,(short)0x2252,(short)0x52b5,(short)0x4294,(short)0x72f7,(short)0x62d6,
        (short)0x9339,(short)0x8318,(short)0xb37b,(short)0xa35a,(short)0xd3bd,(short)0xc39c,(short)0xf3ff,(short)0xe3de,
        (short)0x2462,(short)0x3443,(short)0x0420,(short)0x1401,(short)0x64e6,(short)0x74c7,(short)0x44a4,(short)0x5485,
        (short)0xa56a,(short)0xb54b,(short)0x8528,(short)0x9509,(short)0xe5ee,(short)0xf5cf,(short)0xc5ac,(short)0xd58d,
        (short)0x3653,(short)0x2672,(short)0x1611,(short)0x0630,(short)0x76d7,(short)0x66f6,(short)0x5695,(short)0x46b4,
        (short)0xb75b,(short)0xa77a,(short)0x9719,(short)0x8738,(short)0xf7df,(short)0xe7fe,(short)0xd79d,(short)0xc7bc,
        (short)0x48c4,(short)0x58e5,(short)0x6886,(short)0x78a7,(short)0x0840,(short)0x1861,(short)0x2802,(short)0x3823,
        (short)0xc9cc,(short)0xd9ed,(short)0xe98e,(short)0xf9af,(short)0x8948,(short)0x9969,(short)0xa90a,(short)0xb92b,
        (short)0x5af5,(short)0x4ad4,(short)0x7ab7,(short)0x6a96,(short)0x1a71,(short)0x0a50,(short)0x3a33,(short)0x2a12,
        (short)0xdbfd,(short)0xcbdc,(short)0xfbbf,(short)0xeb9e,(short)0x9b79,(short)0x8b58,(short)0xbb3b,(short)0xab1a,
        (short)0x6ca6,(short)0x7c87,(short)0x4ce4,(short)0x5cc5,(short)0x2c22,(short)0x3c03,(short)0x0c60,(short)0x1c41,
        (short)0xedae,(short)0xfd8f,(short)0xcdec,(short)0xddcd,(short)0xad2a,(short)0xbd0b,(short)0x8d68,(short)0x9d49,
        (short)0x7e97,(short)0x6eb6,(short)0x5ed5,(short)0x4ef4,(short)0x3e13,(short)0x2e32,(short)0x1e51,(short)0x0e70,
        (short)0xff9f,(short)0xefbe,(short)0xdfdd,(short)0xcffc,(short)0xbf1b,(short)0xaf3a,(short)0x9f59,(short)0x8f78,
        (short)0x9188,(short)0x81a9,(short)0xb1ca,(short)0xa1eb,(short)0xd10c,(short)0xc12d,(short)0xf14e,(short)0xe16f,
        (short)0x1080,(short)0x00a1,(short)0x30c2,(short)0x20e3,(short)0x5004,(short)0x4025,(short)0x7046,(short)0x6067,
        (short)0x83b9,(short)0x9398,(short)0xa3fb,(short)0xb3da,(short)0xc33d,(short)0xd31c,(short)0xe37f,(short)0xf35e,
        (short)0x02b1,(short)0x1290,(short)0x22f3,(short)0x32d2,(short)0x4235,(short)0x5214,(short)0x6277,(short)0x7256,
        (short)0xb5ea,(short)0xa5cb,(short)0x95a8,(short)0x8589,(short)0xf56e,(short)0xe54f,(short)0xd52c,(short)0xc50d,
        (short)0x34e2,(short)0x24c3,(short)0x14a0,(short)0x0481,(short)0x7466,(short)0x6447,(short)0x5424,(short)0x4405,
        (short)0xa7db,(short)0xb7fa,(short)0x8799,(short)0x97b8,(short)0xe75f,(short)0xf77e,(short)0xc71d,(short)0xd73c,
        (short)0x26d3,(short)0x36f2,(short)0x0691,(short)0x16b0,(short)0x6657,(short)0x7676,(short)0x4615,(short)0x5634,
        (short)0xd94c,(short)0xc96d,(short)0xf90e,(short)0xe92f,(short)0x99c8,(short)0x89e9,(short)0xb98a,(short)0xa9ab,
        (short)0x5844,(short)0x4865,(short)0x7806,(short)0x6827,(short)0x18c0,(short)0x08e1,(short)0x3882,(short)0x28a3,
        (short)0xcb7d,(short)0xdb5c,(short)0xeb3f,(short)0xfb1e,(short)0x8bf9,(short)0x9bd8,(short)0xabbb,(short)0xbb9a,
        (short)0x4a75,(short)0x5a54,(short)0x6a37,(short)0x7a16,(short)0x0af1,(short)0x1ad0,(short)0x2ab3,(short)0x3a92,
        (short)0xfd2e,(short)0xed0f,(short)0xdd6c,(short)0xcd4d,(short)0xbdaa,(short)0xad8b,(short)0x9de8,(short)0x8dc9,
        (short)0x7c26,(short)0x6c07,(short)0x5c64,(short)0x4c45,(short)0x3ca2,(short)0x2c83,(short)0x1ce0,(short)0x0cc1,
        (short)0xef1f,(short)0xff3e,(short)0xcf5d,(short)0xdf7c,(short)0xaf9b,(short)0xbfba,(short)0x8fd9,(short)0x9ff8,
        (short)0x6e17,(short)0x7e36,(short)0x4e55,(short)0x5e74,(short)0x2e93,(short)0x3eb2,(short)0x0ed1,(short)0x1ef0
    };

    public static int calcCrc (byte[] bytes, int offset, int length)
    {
        int crc = 0;

        /*
         * From GDL90 spec
         */
        int endoff = offset + length;
        for (int i = offset; i < endoff; i++) {
           int entry1 = bytes[i] & 0xFF;
           int entry2 = CRC_TABLE[crc >> 8];
           int entry3 = crc << 8;
           crc = (entry1 ^ entry2 ^ entry3) & 0xFFFF;
        }
        
        return crc;
    }
}
