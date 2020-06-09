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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.security.InvalidParameterException;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provide a thread-safe wrapper for SQLite databases.
 */
public class SQLiteDBs {
    private final static String TAG = "WairToNow";

    private final static String[] columns_name = new String[] { "name" };
    private final static String[] columns_sql  = new String[] { "sql"  };

    // one pointer per thread accessing this database
    private NNThreadLocal<SQLiteDatabase> tlsqldb = new NNThreadLocal<> ();

    // used by various threads to modify database
    private ReentrantLock dblock = new ReentrantLock ();

    // number of threads that have database open
    private int refcount;

    // some thread has marked the database for delete
    // database will be deleted when refcount goes zero
    private boolean markedfordel;

    // name of database file relative to dbdir
    @NonNull
    public final String mydbname;

    // list of all databases we have in our directory
    private final static NNTreeMap<String,SQLiteDBs> databases = MakeEmptyTreeMap ();

    private static NNTreeMap<String,SQLiteDBs> MakeEmptyTreeMap ()
    {
        // make sure to call the SQLiteThreadLocal.finalize() method on exit
        // ...so we close all the sqlite databases nicely
        System.runFinalizersOnExit (true);

        // get list of existing databases but don't open them yet
        NNTreeMap<String,SQLiteDBs> tm = new NNTreeMap<> ();
        File[] files = new File (WairToNow.dbdir).listFiles ();
        for (File file : files) {
            String dbname = file.getName ();
            if (dbname.endsWith (".db")) {
                if (dbname.startsWith ("obstructions_") ||
                    dbname.startsWith ("plates_") ||
                    dbname.startsWith ("waypoints_")) {
                    Lib.Ignored (new File (WairToNow.dbdir, "nobudb").mkdirs ());
                    File nbfile = new File (WairToNow.dbdir, "nobudb/" + dbname);
                    if (! file.renameTo (nbfile)) {
                        Log.e (TAG, "error renaming " + file.getAbsolutePath () +
                                " to " + nbfile.getAbsolutePath ());
                    }
                } else {
                    SQLiteDBs db = new SQLiteDBs (dbname);
                    tm.put (dbname, db);
                }
            }
        }
        files = new File (WairToNow.dbdir, "nobudb").listFiles ();
        for (File file : files) {
            String dbname = file.getName ();
            if (dbname.endsWith (".db")) {
                SQLiteDBs db = new SQLiteDBs ("nobudb/" + dbname);
                tm.put ("nobudb/" + dbname, db);
            }
        }

        return tm;
    }

    /**
     * About to create a database externally, get its path.
     * @param dbname = name of database being created
     * @return full path to .db file
     */
    public static String creating (String dbname)
    {
        return WairToNow.dbdir + "/" + dbname;
    }

    /**
     * A database was created externally, catalog it internally.
     * @param dbname = name of database externally created
     */
    public static void created (String dbname)
    {
        synchronized (databases) {
            SQLiteDBs db = new SQLiteDBs (dbname);
            databases.put (dbname, db);
        }
    }

    /**
     * Get names of all currently existing SQLite databases.
     * @return names including trailing ".db" but excluding any directory info
     */
    public static String[] Enumerate ()
    {
        synchronized (databases) {
            int num = databases.size ();
            for (SQLiteDBs db : databases.values ()) {
                if (db.markedfordel) -- num;
            }
            String[] names = new String[num];
            num = 0;
            for (String name : databases.keySet ()) {
                if (!databases.nnget (name).markedfordel) {
                    names[num++] = name;
                }
            }
            return names;
        }
    }

    /**
     * Create database if it does not exist.
     * Otherwise just open it.
     */
    public static SQLiteDBs create (String dbname)
    {
        if (!dbname.endsWith (".db")) throw new IllegalArgumentException (dbname);

        synchronized (databases) {
            SQLiteDBs db = databases.get (dbname);
            if ((db == null) || db.markedfordel) {
                db = new SQLiteDBs (dbname);
                databases.put (dbname, db);
            }

            if (db.tlsqldb.get () == null) {
                Log.i (TAG, Thread.currentThread ().getName () + " opening " + dbname);
                String dbpath = WairToNow.dbdir + "/" + dbname;
                db.tlsqldb.set (SQLiteDatabase.openDatabase (dbpath, null, SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.NO_LOCALIZED_COLLATORS));
                db.refcount ++;
            }
            return db;
        }
    }

    /**
     * Open existing database.
     * If it doesn't exist, return null.
     */
    public static SQLiteDBs open (String dbname)
    {
        if (!dbname.endsWith (".db")) throw new IllegalArgumentException (dbname);

        synchronized (databases) {
            SQLiteDBs db = databases.get (dbname);
            if ((db == null) || db.markedfordel) return null;

            if (db.tlsqldb.get () == null) {
                Log.i (TAG, Thread.currentThread ().getName () + " opening " + dbname);
                String dbpath = WairToNow.dbdir + "/" + dbname;
                db.tlsqldb.set (SQLiteDatabase.openDatabase (dbpath, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS));
                db.refcount ++;
            }
            return db;
        }
    }

    private SQLiteDBs (@NonNull String mydbn)
    {
        mydbname = mydbn;
    }

    /**
     * See if the given table exists within the database.
     */
    public boolean tableExists (String table)
    {
        dblock.lock ();
        try {
            Cursor result = tlsqldb.nnget ().query (
                    "sqlite_master", columns_name,
                    "type='table' AND name=?", new String[] { table },
                    null, null, null, null);
            try {
                return result.getCount () > 0;
            } finally {
                result.close ();
            }
        } finally {
            dblock.unlock ();
        }
    }

    /**
     * See if the given table is empty.
     */
    public boolean tableEmpty (String table)
    {
        dblock.lock ();
        try {
            Cursor result = tlsqldb.nnget ().rawQuery ("SELECT * FROM " + table + " LIMIT 1", null);
            try {
                return result.getCount () <= 0;
            } finally {
                result.close ();
            }
        } finally {
            dblock.unlock ();
        }
    }

    /**
     * See if the given column exists within the given table of the database.
     */
    @SuppressWarnings("SameParameterValue")
    public boolean columnExists (String table, String column)
    {
        dblock.lock ();
        try {
            Cursor result = tlsqldb.nnget ().query (
                    "sqlite_master", columns_sql,
                    "type='table' AND name=?", new String[] { table },
                    null, null, null, null);
            try {
                // CREATE TABLE intersections (int_num INTEGER PRIMARY KEY, int_lat REAL NOT NULL,
                //   int_lon REAL NOT NULL, int_type TEXT NOT NULL)
                if (!result.moveToFirst ()) {
                    throw new InvalidParameterException ("table not found " + table);
                }
                String cretab = result.getString (0);
                String[] words = cretab.split (" ");
                String pcolumn = "(" + column;
                for (String word : words) {
                    if (word.equals (pcolumn) || word.equals (column)) return true;
                }
                return false;
            } finally {
                result.close ();
            }
        } finally {
            dblock.unlock ();
        }
    }

    /**
     * SQLite wrappers...
     */
    public void beginTransaction ()
    {
        boolean keep = false;
        dblock.lock ();
        try {
            tlsqldb.nnget ().beginTransaction ();
            keep = true;
        } finally {
            if (!keep) dblock.unlock ();
        }
    }

    public int delete (String table, String where, String[] whargs)
    {
        dblock.lock ();
        try {
            return tlsqldb.nnget ().delete (table, where, whargs);
        } finally {
            dblock.unlock ();
        }
    }

    public void endTransaction ()
    {
        try {
            tlsqldb.nnget ().endTransaction ();
        } finally {
            dblock.unlock ();
        }
    }

    public void execSQL (String stmt)
    {
        dblock.lock ();
        try {
            tlsqldb.nnget ().execSQL (stmt);
        } finally {
            dblock.unlock ();
        }
    }

    public long insert (String table, String dummy1, ContentValues values)
    {
        dblock.lock ();
        try {
            return tlsqldb.nnget ().insert (table, dummy1, values);
        } finally {
            dblock.unlock ();
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public long insertWithOnConflict (String table, ContentValues values, int conflict)
    {
        dblock.lock ();
        try {
            return tlsqldb.nnget ().insertWithOnConflict (table, null, values, conflict);
        } finally {
            dblock.unlock ();
        }
    }

    public Cursor query (String table, String[] columns, String where, String[] whargs, String groupBy, String having, String orderBy, String limit)
    {
        dblock.lock ();
        try {
            return tlsqldb.nnget ().query (table, columns, where, whargs, groupBy, having, orderBy, limit);
        } finally {
            dblock.unlock ();
        }
    }

    public Cursor query (boolean distinct, String table, String[] columns, String where, String[] whargs, String groupBy, String having, String orderBy, String limit)
    {
        dblock.lock ();
        try {
            return tlsqldb.nnget ().query (distinct, table, columns, where, whargs, groupBy, having, orderBy, limit);
        } finally {
            dblock.unlock ();
        }
    }

    public void setTransactionSuccessful ()
    {
        tlsqldb.nnget ().setTransactionSuccessful ();
    }

    public int update (String table, ContentValues values, String where, String[] whargs)
    {
        dblock.lock ();
        try {
            return tlsqldb.nnget ().update (table, values, where, whargs);
        } finally {
            dblock.unlock ();
        }
    }

    public void yieldIfContendedSafely ()
    {
        setTransactionSuccessful ();
        endTransaction ();
        beginTransaction ();
    }

    /**
     * Mark the database for delete.
     * Delete the file(s) when all threads (including this one) have closed it.
     * SQLite doesn't seem to like the file being deleted while it is open.
     */
    public void markForDelete ()
    {
        synchronized (databases) {
            markedfordel = true;
        }
    }

    /**
     * Close all databases accessed by calling thread.
     * If the last reference to a database marked for delete, delete the database.
     */
    public static void CloseAll ()
    {
        synchronized (databases) {
            for (Iterator<String> it = databases.keySet ().iterator (); it.hasNext ();) {
                String dbname = it.next ();
                SQLiteDBs db = databases.nnget (dbname);
                NNThreadLocal<SQLiteDatabase> tldb = db.tlsqldb;
                SQLiteDatabase sqldb = tldb.get ();
                if (sqldb != null) {
                    Log.i (TAG, Thread.currentThread ().getName () + " closing " + dbname);
                    String path = sqldb.getPath ();
                    sqldb.close ();
                    tldb.set (null);
                    if ((-- db.refcount == 0) && db.markedfordel) {
                        Log.i (TAG, Thread.currentThread ().getName () + " deleting " + dbname);
                        Lib.Ignored (new File (path).delete ());
                        Lib.Ignored (new File (path + "-journal").delete ());
                        it.remove ();
                    }
                }
            }
        }
    }
}
