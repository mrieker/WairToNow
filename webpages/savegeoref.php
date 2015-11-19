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
     * Save manual IAP georeference records from the client.
     */

    /*
     * Read csv line from post stream.
     * It does not have a newline char on it.
     */
    $csvline = file_get_contents ("php://input");

    /*
     * Append record to existing georefs/manual.csv file.
     * All previous records must remain intact, or at least their length.
     */
    $csvfile = fopen ('georefs/manual.csv', 'a');
    if (!$csvfile) die ("fopen error\n");
    if (!fputs ($csvfile, "$csvline\n")) die ("fwrite error\n");
    if (!fclose ($csvfile)) die ("fclose error\n");

    /*
     * Tell app we sucuessfully saved it.
     */
    echo "OK\n";
?>
