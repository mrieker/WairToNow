//    Copyright (C) 2016, Mike Rieker, Beverly, MA USA
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

package com.apps4av.avarehelper.connections;

/**
 * Process incoming dump978 format messages.
 *   +hexdigits[;rs=code];\n  hexdigits=uplink data including UAT header
 *   -hexdigits[;rs=code];\n  hexdigits=basic/long message including UAT header
 *     code = reed-solomon # of corrected bits (9999 is uncorrectable)
 */
public class Dump978Decoder extends MidDecoder {
    private TopDecoder topDecoder;

    public Dump978Decoder (TopDecoder td)
    {
        topDecoder = td;
    }

    /**
     * See if topDecoder.mBuf[] contains our start character anywhere.
     */
    @Override
    public boolean findStartIndex ()
    {
        byte[] buf = topDecoder.mBuf;
        int end = topDecoder.mInsert;
        int i;
        for (i = msgStartIndex; i < end; i ++) {
            byte c = buf[i];
            if ((c == '+') || (c == '-')) break;
        }
        msgStartIndex = i;
        return i < end;
    }

    /**
     * topDecoder.mbuf[msgStartIndex] points to our start character.
     * If we have a whole message, remove from buffer and process it.
     * @return true: we removed a message; false: nothing removed
     */
    @Override
    public boolean midDecode ()
    {
        byte[] mbuf = topDecoder.mBuf;
        int end = topDecoder.mInsert;
        int saveRem = msgStartIndex;

        /*
         * Try to get dump978 message starting at the '+' or '-' ending with ';\n'.
         * Note that message can have embedded ';' so check for ';\n'.
         */
        int semiNl = saveRem;
        while (true) {
            if (++ semiNl + 2 > end) return false;
            byte c = mbuf[semiNl];
            if ((c == ';') && (mbuf[semiNl+1] == '\n')) break;
            if ((c == '+') || (c == '-')) {
                msgStartIndex = semiNl;
                return true;
            }
        }

        /*
         * Found a whole message, good or bad, remove from buffer so we don't try to parse it again.
         */
        msgStartIndex = semiNl + 2;

        /*
         * Convert from hex digits to binary, up to the first ';'.
         */
        byte[] tbuf = topDecoder.tBuf;   // where to put binary bytes
        byte msgtype = mbuf[saveRem++];  // '+' or '-'
        int len = 4;                     // leave 4 bytes for GDL-90 style parsing
        while (true) {                   // convert hex digits to binary
            if (saveRem + 2 > end) return true;
            byte topbyte = mbuf[saveRem++];
            if (topbyte == ';') break;
            int topdig = TopDecoder.hexDigit (topbyte);
            int botdig = TopDecoder.hexDigit (mbuf[saveRem++]);
            if ((topdig < 0) || (botdig < 0)) return true;
            tbuf[len++] = (byte) ((topdig << 4) | botdig);
        }

        /*
         * After the first ';' is either the '\n' or 'rs=code;\n'
         * where code is number of reed-solomon errors, with 9999
         * indicating message was uncorrectable.
         */
        if (mbuf[saveRem] != '\n') {
            if (end - saveRem < 6) return true;
            if (mbuf[saveRem++] != 'r') return true;
            if (mbuf[saveRem++] != 's') return true;
            if (mbuf[saveRem++] != '=') return true;
            int rscode = 0;
            while (true) {
                byte chr = mbuf[saveRem++];
                if (chr == ';') break;
                if ((chr < '0') || (chr > '9')) return true;
                rscode = rscode * 10 + chr - '0';
            }
            if (rscode > 99) return true;
        }

        /*
         * If dump978 message had a bad checksum or runt length,
         * don't increment other pointers cuz there could be a good
         * other message before end of bad dump978 message.
         */
        if (len < 4) return true;

        /*
         * Complete valid dump978 message found.
         * Make sure other pointers point past end of this message.
         */
        topDecoder.validMessageEndsAt (msgStartIndex);

        /*
         * Process the dump978 message.
         * dump978 only outputs three message types:
         *    '+' : uplink messasge
         *    '-' : basic or long message
         */
        Reporter reporter = topDecoder.mReporter;
        switch (msgtype) {
            case (byte) '+': {
                if (len >= 436) {   // dump978's app_data[] is 424 (the 8 comes from UAT header)
                    reporter.adsbGpsInstance ("dump978-uplink");
                    UplinkMessage.parse (tbuf, len, reporter);
                } else {
                    reporter.adsbGpsLog ("short dump978 uplink message " + len);
                }
                break;
            }
            case (byte) '-': {
                long now = System.currentTimeMillis ();
                tbuf[1] = (byte) 0xFF;      // no timeOfReception field, just use 'now'
                tbuf[2] = (byte) 0xFF;
                tbuf[3] = (byte) 0xFF;
                if (len >= 38) {            // dump978's LONG_FRAME_DATA_BYTES + 4
                    reporter.adsbGpsInstance ("dump978-long");
                    TopDecoder.commonBasicLongReport (now, tbuf, len, reporter);
                } else if (len >= 22) {     // dump978's SHORT_FRAME_DATA_BYTES + 4
                    reporter.adsbGpsInstance ("dump978-basic");
                    TopDecoder.commonBasicLongReport (now, tbuf, len, reporter);
                } else {
                    reporter.adsbGpsLog ("short dump978 downlink message " + len);
                }
                break;
            }
        }

        return true;
    }
}
