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

package com.outerworldapps.wairtonow;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.util.TreeMap;

public class SQLiteDBs {
    private final static String TAG = "WairToNow";

    private final static TreeMap<String,ThreadLocal<SQLiteDatabase>> databases = MakeEmptyTreeMap ();

    private static TreeMap<String,ThreadLocal<SQLiteDatabase>> MakeEmptyTreeMap ()
    {
        // make sure to call the SQLiteThreadLocal.finalize() method on exit
        // ...so we close all the sqlite databases nicely
        System.runFinalizersOnExit (true);

        // get list of existing databases but don't open them yet
        TreeMap<String,ThreadLocal<SQLiteDatabase>> tm = new TreeMap<> ();
        File[] files = new File (WairToNow.dbdir).listFiles ();
        for (File file : files) {
            String dbname = file.getName ();
            if (dbname.endsWith (".db")) {
                tm.put (dbname, new ThreadLocal<SQLiteDatabase> ());
            }
        }

        return tm;
    }

    /**
     * Get names of all currently existing SQLite databases.
     * @return names including trailing ".db" but excluding any directory info
     */
    public static String[] Enumerate ()
    {
        synchronized (databases) {
            int num = databases.size ();
            String[] names = new String[num];
            databases.keySet ().toArray (names);
            return names;
        }
    }

    /**
     * See if a given database exists.
     */
    public static boolean Exists (String dbname)
    {
        if (dbname.contains ("/")) throw new IllegalArgumentException (dbname);
        if (!dbname.endsWith (".db")) throw new IllegalArgumentException (dbname);
        synchronized (databases) {
            return databases.containsKey (dbname);
        }
    }

    /**
     * Get or create a per-thread instance of a database.
     * @param dbname = name of database, including trailing ".db" but excluding directory info
     * @return database instance
     */
    public static SQLiteDatabase GetOrCreate (String dbname)
    {
        if (dbname.contains ("/")) throw new IllegalArgumentException (dbname);
        if (!dbname.endsWith (".db")) throw new IllegalArgumentException (dbname);

        ThreadLocal<SQLiteDatabase> tldb;
        synchronized (databases) {
            tldb = databases.get (dbname);
            if (tldb == null) {
                tldb = new ThreadLocal<> ();
                databases.put (dbname, tldb);
            }
        }

        SQLiteDatabase sqldb = tldb.get ();
        if (sqldb == null) {
            Log.i (TAG, Thread.currentThread ().getName () + " opening " + dbname);
            String dbpath = WairToNow.dbdir + "/" + dbname;
            sqldb = SQLiteDatabase.openDatabase (dbpath, null, SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            tldb.set (sqldb);
        }
        return sqldb;
    }

    /**
     * Close all databases accessed by calling thread.
     */
    public static void CloseAll ()
    {
        synchronized (databases) {
            for (String dbname : databases.keySet ()) {
                ThreadLocal<SQLiteDatabase> tldb = databases.get (dbname);
                SQLiteDatabase sqldb = tldb.get ();
                if (sqldb != null) {
                    Log.i (TAG, Thread.currentThread ().getName () + " closing " + dbname);
                    sqldb.close ();
                    tldb.set (null);
                }
            }
        }
    }
}
