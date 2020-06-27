//    Copyright (C) 2020, Mike Rieker, Beverly, MA USA
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

package com.outerworldapps.wairtonow;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class NetworkInterfaces {
    public static String get ()
    {
        boolean foundone = false;
        StringBuilder sb = new StringBuilder ();
        try {
            Enumeration<NetworkInterface> nifaceit = NetworkInterface.getNetworkInterfaces ();
            while (nifaceit.hasMoreElements ()) {
                NetworkInterface niface = nifaceit.nextElement ();
                if (niface.isLoopback ()) continue;
                StringBuilder namesb = new StringBuilder ();
                String name = niface.getName ();
                namesb.append (name);
                namesb.append (':');
                String dispname = niface.getDisplayName ();
                if (! name.equals (dispname)) {
                    namesb.append (' ');
                    namesb.append (niface.getDisplayName ());
                }
                byte[] hwabin = niface.getHardwareAddress ();
                if (hwabin != null) {
                    char sep = ' ';
                    for (byte hwabyte : hwabin) {
                        namesb.append (sep);
                        namesb.append (Integer.toHexString ((hwabyte & 0xFF) | 0x100).substring (1));
                        sep = ':';
                    }
                }
                namesb.append ('\n');
                Enumeration<InetAddress> ipaddrit = niface.getInetAddresses ();
                while (ipaddrit.hasMoreElements ()) {
                    if (namesb != null) {
                        sb.append (namesb);
                        namesb = null;
                    }
                    InetAddress ipaddr = ipaddrit.nextElement ();
                    sb.append ("  ");
                    sb.append (ipaddr.getHostAddress ());
                    sb.append ('\n');
                    foundone = true;
                }
            }
        } catch (Exception e) {
            sb.append ('\n');
            sb.append (e.toString ());
            sb.append ('\n');
        }
        if (! foundone) sb.append ("(no internet access)\n");
        return sb.toString ();
    }
}
