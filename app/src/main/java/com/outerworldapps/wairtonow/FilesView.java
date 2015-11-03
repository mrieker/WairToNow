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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Display a menu to display file system contents.
 */
public class FilesView
        extends LinearLayout
        implements Handler.Callback, WairToNow.CanBeMainView {

    private DeleteFileThread deleteFileThread;
    private Handler filesViewHandler;
    private LinearLayout currentFileList;
    private WairToNow wairToNow;
    private String currentFileName;

    private static final int FilesViewHandlerWhat_OPENFILE = 0;
    private static final int FilesViewHandlerWhat_DELFILE  = 1;
    private static final int FilesViewHandlerWhat_DFPROG   = 2;

    public FilesView (WairToNow ctx)
    {
        super (ctx);
        wairToNow = ctx;
        filesViewHandler = new Handler (this);
        setOrientation (VERTICAL);
        currentFileName = WairToNow.dbdir;
    }

    @Override  // WairToNow.CanBeMainView
    public String GetTabName ()
    {
        return "Files";
    }

    @Override  // WairToNow.CanBeMainView
    public void OpenDisplay()
    {
        wairToNow.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_USER);
        FillList (currentFileName);
    }

    @Override  // WairToNow.CanBeMainView
    public void CloseDisplay()
    {
        removeAllViews ();
    }

    /**
     * Tab is being re-clicked when already active.
     */
    @Override  // WairToNow.CanBeMainView
    public void ReClicked ()
    {
        /*
         * Set to top-level directory.
         */
        FillList (WairToNow.dbdir);
    }

    @Override  // WairToNow.CanBeMainView
    public View GetBackPage()
    {
        /*
         * If already at our top-level directory, go back to chart page.
         */
        if (currentFileName.equals (WairToNow.dbdir)) {
            return wairToNow.chartView;
        }
        int i = currentFileName.lastIndexOf ('/');
        if (i < 0) {
            return wairToNow.chartView;
        }

        /*
         * Not at top-level directory, pop up one level and display it.
         */
        currentFileName = currentFileName.substring (0, i);
        return this;
    }

    @Override
    public boolean handleMessage (Message msg)
    {
        switch (msg.what) {

            case FilesViewHandlerWhat_OPENFILE: {
                FillList (msg.obj.toString ());
                break;
            }

            case FilesViewHandlerWhat_DELFILE: {
                if (deleteFileThread == null) {
                    deleteFileThread = new DeleteFileThread ((FileButton)msg.obj);
                }
                break;
            }

            case FilesViewHandlerWhat_DFPROG: {
                if (deleteFileThread != null) {
                    deleteFileThread.UpdateProg (msg.obj);
                }
                break;
            }
        }
        return true;
    }

    /**
     * Open and display the named file on the screen.
     * If it is a directory, the contents are a list of buttons
     * that can be clicked to open the corresponding file.
     */
    private void FillList (String name)
    {
        currentFileName = name;

        removeAllViews ();

        TextView tv1 = new TextView (wairToNow);
        tv1.setText ("Files Browser");
        wairToNow.SetTextSize (tv1);
        addView (tv1);

        File currentFile = new File (name);
        if (currentFile.isDirectory ()) {

            /*
             * Directory, display list of buttons the user can click to open the file.
             */
            TextView tv2 = new TextView (wairToNow);
            tv2.setText (name + "/...");
            wairToNow.SetTextSize (tv2);
            addView (tv2);

            ScrollView sv1 = new ScrollView (wairToNow);
            LinearLayout ll1 = new LinearLayout (wairToNow);
            ll1.setOrientation (VERTICAL);
            File[] fileList = currentFile.listFiles ();
            Arrays.sort (fileList);
            for (File file : fileList) {
                if (!file.getName ().startsWith (".")) {
                    FileButton fileButton = new FileButton (file);
                    ll1.addView (fileButton);
                }
            }
            sv1.addView (ll1);
            addView (sv1);
            currentFileList = ll1;
        } else {
            currentFileList = null;

            String namenotmp = name;
            if (namenotmp.endsWith (".tmp")) namenotmp = namenotmp.substring (0, namenotmp.length () - 4);

            /*
             * If one of our .csv or .txt files, show file contents in a text view.
             */
            if (namenotmp.endsWith (".csv") || namenotmp.endsWith (".txt")) {
                TextView tv2 = new TextView (wairToNow);
                tv2.setText (name);
                wairToNow.SetTextSize (tv2);
                addView (tv2);
                SaveTextButton stb = new SaveTextButton ();
                stb.name = name;
                addView (stb);

                try {
                    char[] data = new char[(int)currentFile.length()];
                    FileReader reader = new FileReader (name);
                    reader.read (data, 0, data.length);
                    reader.close ();

                    EditText editor = new EditText (wairToNow);
                    editor.setTypeface (Typeface.MONOSPACE);
                    editor.setText (new String (data));
                    wairToNow.SetTextSize (editor);
                    stb.editor = editor;
                    HorizontalScrollView hsv1 = new HorizontalScrollView (wairToNow);
                    hsv1.addView (editor);
                    ScrollView vsv1 = new ScrollView (wairToNow);
                    vsv1.addView (hsv1);
                    addView (vsv1);
                } catch (IOException ioe) {
                    TextView viewer = new TextView (wairToNow);
                    viewer.setText ("error reading file: " + ioe.getMessage ());
                    wairToNow.SetTextSize (viewer);
                    addView (viewer);
                }

                return;
            }

            /*
             * If one of our .gif or .png files, show file contents in an image view.
             */
            if (namenotmp.endsWith (".gif") || namenotmp.endsWith (".png")) {
                Bitmap bitmap = BitmapFactory.decodeFile (name);
                if (bitmap == null) {
                    TextView tv2 = new TextView (wairToNow);
                    tv2.setText (name);
                    wairToNow.SetTextSize (tv2);
                    addView (tv2);
                    TextView viewer = new TextView (wairToNow);
                    viewer.setText ("unable to decode image file");
                    addView (viewer);
                } else {
                    TextView tv2 = new TextView (wairToNow);
                    tv2.setText (name + " (" + bitmap.getWidth () + " x " + bitmap.getHeight () + ")");
                    wairToNow.SetTextSize (tv2);
                    addView (tv2);
                    ImageView viewer = new ImageView (wairToNow);
                    viewer.setImageBitmap (bitmap);
                    addView (viewer);
                }
                return;
            }

            /*
             * Last resort, try an intent.
             */
            try {
                Intent intent = new Intent (Intent.ACTION_VIEW);
                intent.setData (Uri.parse ("file://" + name));
                wairToNow.startActivity (intent);
            } catch (ActivityNotFoundException anfe) {
                TextView viewer = new TextView (wairToNow);
                viewer.setText ("unable to open file: " + anfe.getMessage ());
                wairToNow.SetTextSize (viewer);
                addView (viewer);
            }
        }
    }

    private class SaveTextButton extends Button implements OnClickListener {
        public EditText editor;
        public String name;

        public SaveTextButton ()
        {
            super (wairToNow);
            setText ("Save");
            wairToNow.SetTextSize (this);
            setOnClickListener (this);
        }

        public void onClick (View v)
        {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            try {
                OutputStreamWriter osw = new OutputStreamWriter (new FileOutputStream (name));
                try {
                    osw.write (editor.getText ().toString ());
                } finally {
                    osw.close ();
                }
                adb.setMessage (name + " updated");
            } catch (IOException ioe) {
                Log.e ("FilesView", "error writing file", ioe);
                adb.setMessage ("error writing " + name + ": " + ioe.getMessage ());
            }
            adb.setPositiveButton ("OK", null);
            adb.show ();
        }
    }

    /**
     * Thread to delete files in the background.
     * Deletes the files with a progress dialog then
     * removes file from file button list when done.
     * Delete can be canceled by pressing the Back button.
     */
    private class DeleteFileThread extends Thread implements DialogInterface.OnCancelListener {
        private boolean canceled;
        private boolean completed;
        private FileButton fileButton;
        private ProgressDialog progress;
        private volatile boolean updateMsgPending;

        public DeleteFileThread (FileButton fb)
        {
            fileButton = fb;

            progress = new ProgressDialog (wairToNow);
            progress.setTitle ("Deleting...");
            progress.setProgressStyle (ProgressDialog.STYLE_SPINNER);
            progress.setOnCancelListener (this);
            progress.show ();

            start ();
        }

        public void UpdateProg (Object obj)
        {
            if (obj != null) {
                updateMsgPending = false;
                if (progress != null) {
                    progress.setMessage (obj.toString ());
                }
            } else {
                deleteFileThread = null;
                progress.dismiss ();
                progress = null;
                if (completed && (currentFileList != null)) {
                    currentFileList.removeView (fileButton);
                }
            }
        }

        @Override
        public void onCancel (DialogInterface dialog)
        {
            canceled = true;
        }

        public void run ()
        {
            try {
                DeleteFile (fileButton.file);
                completed = true;
            } catch (CanceledException ce) {
            } finally {
                filesViewHandler.sendEmptyMessage (FilesViewHandlerWhat_DFPROG);
            }
        }

        /**
         * Delete the given file.  If it is a directory,
         * delete all the files it contains as well as
         * the directory itself.
         */
        private void DeleteFile (File file) throws CanceledException
        {
            if (file.isDirectory ()) {
                File[] fileList = file.listFiles ();
                Arrays.sort (fileList);
                for (File f : fileList) {
                    if (canceled) throw new CanceledException ();
                    DeleteFile (f);
                }
            }
            if (!updateMsgPending) {
                updateMsgPending = true;
                Message msg = filesViewHandler.obtainMessage (FilesViewHandlerWhat_DFPROG, 0, 0, file.getPath ());
                filesViewHandler.sendMessageDelayed (msg, 25);
            }
            file.delete ();
        }

        @SuppressWarnings("serial")
        private class CanceledException extends Exception { }
    }

    /**
     * One of these buttons per file listed in the current directory.
     */
    private class FileButton extends Button implements DialogInterface.OnClickListener, OnClickListener {
        public File file;

        public FileButton (File file)
        {
            super (wairToNow);
            this.file = file;
            if (file.isDirectory ()) {
                setText ("<dir> : " + file.getName ());
            } else {
                setText (file.length () + " : " + file.getName ());
            }
            wairToNow.SetTextSize (this);
            setOnClickListener (this);

        }

        @Override  // OnClickListner
        public void onClick (View v)
        {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle (file.getName ());
            String[] names = new String[] { "Open", "Delete" };
            adb.setItems (names, this);
            AlertDialog alert = adb.create ();
            alert.show ();
        }

        @Override  // DialogInterface.OnClickListener
        public void onClick (DialogInterface dialog, int which)
        {
            dialog.dismiss ();
            switch (which) {
                case 0: {
                    Message msg = filesViewHandler.obtainMessage (FilesViewHandlerWhat_OPENFILE, 0, 0, file.getPath ());
                    filesViewHandler.sendMessage (msg);
                    break;
                }
                case 1: {
                    Message msg = filesViewHandler.obtainMessage (FilesViewHandlerWhat_DELFILE, 0, 0, this);
                    filesViewHandler.sendMessage (msg);
                    break;
                }
            }
        }
    }
}
