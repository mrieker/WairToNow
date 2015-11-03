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
 * @brief Take an .html file retrieved by get_all_airportids.sh
 *        and convert it to be suitable for stand-alone display.
 *
 *  gmcs -debug -out:CleanArptInfoHtml.exe CleanArptInfoHtml.cs
 *  mono --debug CleanArptInfoHtml.exe <infile> <outfile>
 *      <infile>  = raw html file from FAA
 *      <outfile> = cooked html file suitable for standalone display
 */

using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

public class CleanArptInfoHtml {
    public static void Main (string[] args)
    {
        int i, j;

        string infile  = args[0];
        string outfile = args[1];

        string input = File.ReadAllText (infile);

        /*
         * Set up pointers to .css files.
         */
        input = input.Replace ("/nfdcApps/include/", "../aptinfo_css/");

        /*
         * Remove top FAA Homepage link.
         */
        i = input.IndexOf ("<a href=\"http://www.faa.gov/\" title=\"FAA Homepage\">");
        if (i >= 0) {
            j = input.IndexOf ("</a>", i);
            if (j >= 0) {
                input = input.Substring (0, i) + input.Substring (j + 4);
            }
        }

        /*
         * Remove NFDC Portal Home link.
         */
        i = input.IndexOf ("<a href=\"/xwiki/bin/view/NFDC/WebHome\">");
        if (i >= 0) {
            j = input.IndexOf ("</a>", i);
            if (j >= 0) {
                input = input.Substring (0, i) + input.Substring (j + 4);
            }
        }

        /*
         * Remove Lookup button and entry box.
         */
        i = input.IndexOf ("<div id=\"headerWidgetBar\">");
        if (i >= 0) {
            j = input.IndexOf ("</div>", i);
            if (j >= 0) {
                int k = input.IndexOf ("</div>", j + 6);
                if (k >= 0) {
                    input = input.Substring (0, i) + input.Substring (k + 6);
                }
            }
        }

        /*
         * Remove the useless link table
         *   New Airport Search
         *   Show All Sections
         *   Airport Operations
         *   Airport Contacts
         *   ...
         */
        i = input.IndexOf ("<div id=\"navDiv\"");
        if (i >= 0) {
            j = input.IndexOf ("</div>", i);
            if (j >= 0) {
                input = input.Substring (0, i) + input.Substring (j + 6);
            }
        }
        
        /*
         * Remove redundant whitespace.
         */
        StringBuilder sb = new StringBuilder (input.Length);
        bool skip = true;
        foreach (char c in input) {
            if (c > ' ') {
                skip = false;
                sb.Append (c);
            } else if (!skip) {
                sb.Append (' ');
                skip = true;
            }
        }
        input = sb.ToString ();
        input = input.Replace (" >", ">");

        /*
         * Remove large margin on left column.
         */
        i = input.IndexOf ("<td valign=\"top\" style=\"padding-left: 19px; margin-top: 0px;\">");
        if (i >= 0) {
            j = input.IndexOf ("</td>", i);
            if (j >= 0) {
                input = input.Substring (0, i) + input.Substring (j + 5);
            }
        }

        /*
         * Remove links at bottom.
         */
        i = input.IndexOf ("<tr> <td valign=\"bottom\"> <br> <div id=\"footer\">");
        if (i >= 0) {
            j = input.IndexOf ("</div> </td> </tr>", i);
            if (j >= 0) {
                input = input.Substring (0, i) + input.Substring (j + 18);
            }
        }

        /*
         * Write result to file.
         */
        Directory.CreateDirectory (outfile.Substring (0, outfile.LastIndexOf ('/')));
        File.WriteAllText (outfile, input);
    }
}
