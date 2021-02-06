<?php
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
     * Split a comma-separated value string into its various substrings.
     * @param line = comma-separated line
     * @return array of the various substrings
     */
    function QuotedCSVSplit ($line)
    {
        $len    = strlen ($line);
        $cols   = array ();
        $quoted = FALSE;
        $escapd = FALSE;
        $sb     = '';
        for ($i = 0;; $i ++) {
            $c = ($i < $len) ? $line[$i] : FALSE;
            if (!$escapd && ($c == '"')) {
                $quoted = !$quoted;
                continue;
            }
            if (!$escapd && ($c == '\\')) {
                $escapd = TRUE;
                continue;
            }
            if ((!$escapd && !$quoted && ($c == ',')) || ($c === FALSE)) {
                $cols[] = $sb;
                if ($c === FALSE) break;
                $sb = '';
                continue;
            }
            if ($escapd && ($c == 'n')) $c = "\n";
            if ($escapd && ($c == 'z')) $c = 0;
            $sb .= $c;
            $escapd = FALSE;
        }
        return $cols;
    }
?>
