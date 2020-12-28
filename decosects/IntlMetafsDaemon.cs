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

// mcs -debug -out:IntlMetafsDaemon.exe IntlMetafsDaemon.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

// mono --debug IntlMetafsDaemon.exe <seconds>

using Mono.Data.Sqlite;
using System;
using System.Collections.Generic;
using System.Data;
using System.IO;
using System.Net;
using System.Text;
using System.Threading;
using System.Xml;

// main program
public class IntlMetafsDaemon {
    public static void Main (string[] args)
    {
        int seconds = int.Parse (args[0]);

        IDbConnection intlmetafsdbcon = new SqliteConnection ("URI=file:datums/intlmetafs.db");
        intlmetafsdbcon.Open ();

        long gap = 0;

        while (true) {
            long when = DateTimeOffset.UtcNow.ToUnixTimeSeconds ();

            // get airport of unknown status, but don't starve out re-checking ones we think we know about
            IDbCommand dbcmd1 = intlmetafsdbcon.CreateCommand ();
            dbcmd1.CommandText = "SELECT iamt_icaoid FROM intlmetafs WHERE iamt_metaf='?' AND iamt_when<" + (when - gap) + " ORDER BY iamt_when,iamt_icaoid LIMIT 1";
            String icaoid = (String) dbcmd1.ExecuteScalar ();
            if (icaoid != null) {
                gap += 5;
            } else {

                // none of those around, get one we think we know about and check it out again
                dbcmd1.CommandText = "SELECT iamt_icaoid FROM intlmetafs ORDER BY iamt_when,iamt_icaoid LIMIT 1";
                icaoid = (String) dbcmd1.ExecuteScalar ();

                gap -= 5;
                if (gap < 0) gap = 0;
            }

            if (icaoid != null) {

                // try to read METAR and TAF from internet
                String metaf = GetIntlMetaf (icaoid);
                Console.WriteLine (icaoid + " " + when + " " + metaf);

                // write the status to database
                IDbCommand dbcmd2 = intlmetafsdbcon.CreateCommand ();
                dbcmd2.CommandText = "UPDATE intlmetafs SET iamt_metaf=@iamt_metaf,iamt_when=" + when + " WHERE iamt_icaoid=@iamt_icaoid";
                dbcmd2.Parameters.Add (new SqliteParameter ("@iamt_icaoid", icaoid));
                dbcmd2.Parameters.Add (new SqliteParameter ("@iamt_metaf",  metaf));
                dbcmd2.ExecuteNonQuery ();
            }

            // try next in 30 seconds
            Thread.Sleep (seconds * 1000);
        }
    }

    // just like webmetaf.php
    //  http://wxweb.meteostar.com/cgi-bin/metartafsearch/both.cgi?choice=$icaoid
    // return 'M' if we get a metar
    //        'T' if we get a taf
    //       'MT' if we get both
    //         '' if we get neither
    //        '?' if unable to read web page
    public static String GetIntlMetaf (String icaoid)
    {
        // get the web page
        String reply;
        try {
            HttpWebRequest request = (HttpWebRequest) WebRequest.Create ("http://wxweb.meteostar.com/cgi-bin/metartafsearch/both.cgi?choice=" + icaoid);
            request.KeepAlive = false;
            HttpWebResponse response = (HttpWebResponse) request.GetResponse ();
            StreamReader reader = new StreamReader (response.GetResponseStream ());
            reply = reader.ReadToEnd ();
            reader.Close ();
        } catch (Exception e) {
            Console.WriteLine (icaoid + ": " + e.Message);
            return "?";
        }

        // break up into words and convert to upper case
        LinkedList<String> words = Wordize (reply);

        String capitals = ConcatWords (words);
        Console.WriteLine (icaoid + ": " + capitals);
        if (capitals.Contains ("OBSERVATION DATABASE IS DOWN")) return "?";

        // look for METAR: ... icaoid ... =
        //        and TAF: ... icaoid ... =
        // metars don't always have icaoid and are sometimes just a bunch of numbers
        // ... but they always seem to have the =
        // also assume metar present if we see a valid taf
        String type = "";
        bool hasmetar  = false;
        bool hastaf    = false;
        bool sawicaoid = false;
        foreach (String word in words) {
            if ((word == "METAR:") || (word == "TAF:")) {
                type = word;
                sawicaoid = false;
            }
            if (word == icaoid) sawicaoid = true;
            if (word == "=") {
                switch (type) {
                    case "METAR:": { hasmetar = true; break; }
                    case "TAF:": { hastaf = sawicaoid; break; }
                }
                type = "";
            }
        }

        return ((hasmetar | hastaf) ? "M" : "") + (hastaf ? "T" : "");
    }

    // split string into array of words, converted to upper case
    // ignore HTML tags and &nbsp;
    public static LinkedList<String> Wordize (String str)
    {
        str = str.ToUpperInvariant ().Replace ("\n", " ").Replace ("&NBSP;", " ");
        LinkedList<String> words = new LinkedList<String> ();
        int len = str.Length;
        String word = "";
        for (int i = 0; i < len; i ++) {
            char c = str[i];
            if (c == '<') {
                while ((i + 1 < len) && (str[++i] != '>')) { }
            } else if (c > ' ') {
                word += c;
                continue;
            }
            if (word != "") {
                words.AddLast (word);
                word = "";
            }
        }
        if (word != "") words.AddLast (word);
        return words;
    }

    public static String ConcatWords (LinkedList<String> words)
    {
        StringBuilder sb = new StringBuilder ();
        foreach (String word in words) {
            sb.Append (' ');
            sb.Append (word);
        }
        return sb.ToString ();
    }
}
