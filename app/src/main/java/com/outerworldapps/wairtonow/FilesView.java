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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Stack;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Display a menu to display file system contents.
 */
@SuppressLint({ "SetTextI18n", "ViewConstructor" })
public class FilesView
        extends LinearLayout
        implements WairToNow.CanBeMainView {
    private final static String TAG = "WairToNow";

    private Stack<FilePage> filePageStack;
    private WairToNow wairToNow;

    private final static String[] textends = { ".csv", ".txt", ".xml" };

    public FilesView (WairToNow ctx)
    {
        super (ctx);
        wairToNow = ctx;
        setOrientation (VERTICAL);
        filePageStack = new Stack<> ();
    }

    @Override  // CanBeMainView
    public String GetTabName ()
    {
        return "Files";
    }

    @Override  // CanBeMainView
    public int GetOrientation ()
    {
        return ActivityInfo.SCREEN_ORIENTATION_USER;
    }

    @Override  // CanBeMainView
    public boolean IsPowerLocked ()
    {
        return false;
    }

    @Override  // CanBeMainView
    public void OpenDisplay()
    {
        if (filePageStack.isEmpty ()) {
            filePageStack.push (new DirFilePage (new File (WairToNow.dbdir)));
        }
        ReshowPage ();
    }

    @Override  // CanBeMainView
    public void CloseDisplay()
    {
        removeAllViews ();
    }

    /**
     * Tab is being re-clicked when already active.
     * Set to top-level directory.
     */
    @Override  // CanBeMainView
    public void ReClicked ()
    {
        filePageStack.clear ();
        DirFilePage toppage = new DirFilePage (new File (WairToNow.dbdir));
        filePageStack.push (toppage);
        ReshowPage ();
    }

    @Override  // CanBeMainView
    public View GetBackPage()
    {
        if (filePageStack.isEmpty ()) {
            return wairToNow.chartView;
        }
        filePageStack.pop ();
        if (filePageStack.isEmpty ()) {
            return wairToNow.chartView;
        }
        ReshowPage ();
        return this;
    }

    /*
     * Display title and contents of current file.
     */
    private void ReshowPage ()
    {
        FilePage fp = filePageStack.peek ();

        removeAllViews ();

        TextView tv2 = new TextView (wairToNow);
        tv2.setText (fp.getPath ());
        wairToNow.SetTextSize (tv2);
        addView (tv2);

        try {
            fp.showPage ();
        } catch (IOException ioe) {
            AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
            adb.setTitle (fp.getPath ());
            adb.setMessage ("error displaying page: " + ioe.getMessage ());
        }
    }

    /*
     * Page displaying file contents.
     */
    private static abstract class FilePage {
        public abstract String getPath ();
        public abstract void showPage ()
                throws IOException;
    }

    /*
     * Displays directory contents as a list of buttons.
     * Each button can open or delete a file.
     */
    private class DirFilePage extends FilePage {
        private DeleteFileThread deleteFileThread;
        private File dirfile;

        public DirFilePage (File dirfile)
        {
            this.dirfile = dirfile;
        }

        @Override
        public String getPath ()
        {
            return dirfile.getPath ();
        }

        @Override
        public void showPage ()
        {
            ScrollView sv1 = new ScrollView (wairToNow);
            LinearLayout ll1 = new LinearLayout (wairToNow);
            ll1.setOrientation (VERTICAL);
            CreateFileButton cfb = new CreateFileButton ();
            ll1.addView (cfb);
            File[] fileList = dirfile.listFiles ();
            Arrays.sort (fileList);
            for (File file : fileList) {
                if (! file.getName ().equals (".") && ! file.getName ().equals ("..")) {
                    FileButton fileButton = new FileButton (file);
                    ll1.addView (fileButton);
                }
            }
            sv1.addView (ll1);
            addView (sv1);
        }

        /**
         * One of these buttons at the top to create a text file.
         */
        private class CreateFileButton extends Button implements DialogInterface.OnClickListener, OnClickListener {
            private EditText nameTextView;

            public CreateFileButton ()
            {
                super (wairToNow);
                setText ("create text file");
                wairToNow.SetTextSize (this);

                StringBuilder sb = new StringBuilder ();
                sb.append ("end with");
                for (String textend : textends) {
                    sb.append (' ');
                    sb.append (textend);
                }

                nameTextView = new EditText (wairToNow);
                nameTextView.setHint (sb);
                nameTextView.setSingleLine ();
                wairToNow.SetTextSize (nameTextView);

                setOnClickListener (this);
            }

            // 'create text file' button clicked
            @Override  // OnClickListner
            public void onClick (View v)
            {
                ViewParent vp = nameTextView.getParent ();
                if (vp instanceof ViewGroup) {
                    ((ViewGroup) vp).removeAllViews ();
                }

                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Create Text File");
                adb.setView (nameTextView);
                adb.setPositiveButton ("Create", this);
                adb.setNegativeButton ("Cancel", null);
                adb.show ();
            }

            // 'Create' button clicked
            @Override  // DialogInterface.OnClickListener
            public void onClick (DialogInterface dialog, int which)
            {
                // if name is good, open editing box with a Save button at the top
                String name = nameTextView.getText ().toString ().trim ();
                if (!name.contains ("/")) {
                    for (String textend : textends) {
                        if (name.endsWith (textend)) {
                            String path = new File (dirfile, name).getPath ();
                            TextFilePage tfp = new TextFilePage (path);
                            filePageStack.push (tfp);
                            ReshowPage ();
                            return;
                        }
                    }
                }

                // name is bad, display error message and retry
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle ("Create Text File");
                StringBuilder sb = new StringBuilder ();
                sb.append ("no / and must end with");
                for (String textend : textends) {
                    sb.append (' ');
                    sb.append (textend);
                }
                adb.setMessage (sb);
                adb.setPositiveButton ("OK", new DialogInterface.OnClickListener () {
                    @Override
                    public void onClick (DialogInterface dialog, int which) {
                        CreateFileButton.this.onClick (null);
                    }
                });
                adb.setNegativeButton ("Cancel", null);
                adb.show ();
            }
        }

        /**
         * One of these buttons per file listed in the directory.
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
                adb.show ();
            }

            @Override  // DialogInterface.OnClickListener
            public void onClick (DialogInterface dialog, int which)
            {
                Lib.dismiss (dialog);
                switch (which) {
                    case 0: {
                        OpenFile (file);
                        break;
                    }
                    case 1: {
                        AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                        adb.setTitle (file.getName ());
                        adb.setMessage ("Confirm DELETE");
                        adb.setPositiveButton ("DELETE", new DialogInterface.OnClickListener () {
                            @Override
                            public void onClick (DialogInterface dialog, int which)
                            {
                                if (deleteFileThread == null) {
                                    deleteFileThread = new DeleteFileThread (FileButton.this);
                                }
                            }
                        });
                        adb.setNegativeButton ("KEEP IT", null);
                        adb.show ();
                        break;
                    }
                }
            }
        }

        /**
         * Open and display the named file on the screen.
         */
        @SuppressLint("SetJavaScriptEnabled")
        private void OpenFile (File file)
        {
            try {
                FilePage fp;
                if (file.isDirectory ()) {
                    fp = new DirFilePage (file);
                } else {
                    FileInputStream fis = new FileInputStream (file);
                    try {
                        fp = OpenStream (file.getPath (), fis, false);
                    } finally {
                        fis.close ();
                    }
                }
                if (fp != null) {
                    filePageStack.push (fp);
                    ReshowPage ();
                } else {

                    /*
                     * Last resort, try an intent.
                     */
                    Intent intent = new Intent (Intent.ACTION_VIEW);
                    intent.setData (Uri.parse ("file://" + file.getAbsolutePath ()));
                    wairToNow.startActivity (intent);
                }
            } catch (Exception e) {
                AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                adb.setTitle (file.getPath ());
                adb.setMessage ("error opening file: " + e.getMessage ());
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

            @Override
            public void onCancel (DialogInterface dialog)
            {
                canceled = true;
            }

            public void run ()
            {
                try {
                    DeleteFile (fileButton.file);
                } catch (CanceledException ce) {
                    Lib.Ignored ();
                } finally {
                    WairToNow.wtnHandler.runDelayed (0, new Runnable () {
                        @Override
                        public void run ()
                        {
                            deleteFileThread = null;
                            Lib.dismiss (progress);
                            progress = null;
                            ReshowPage ();
                        }
                    });
                }
            }

            /**
             * Delete the given file.  If it is a directory,
             * delete all the files it contains as well as
             * the directory itself.
             */
            private void DeleteFile (final File file) throws CanceledException
            {
                if (file.isDirectory ()) {
                    File[] fileList = file.listFiles ();
                    Arrays.sort (fileList);
                    for (File f : fileList) {
                        if (canceled) throw new CanceledException ();
                        DeleteFile (f);
                    }
                }
                if (! updateMsgPending) {
                    updateMsgPending = true;
                    WairToNow.wtnHandler.runDelayed (25, new Runnable () {
                        @Override
                        public void run ()
                        {
                            updateMsgPending = false;
                            if (progress != null) {
                                progress.setMessage (file.getPath ());
                            }
                        }
                    });
                }
                Lib.Ignored (file.delete ());
            }

            @SuppressWarnings("serial")
            private class CanceledException extends Exception { }
        }
    }

    /*
     * Displays HTML file in a WebView.
     */
    private class HtmlFilePage extends FilePage {
        private String path;
        private String text;

        public HtmlFilePage (String path, InputStream istream)
                throws IOException
        {
            this.path = path;
            this.text = ReadWholeStream (istream);
        }

        @Override
        public String getPath ()
        {
            return path;
        }

        @SuppressLint("SetJavaScriptEnabled")
        @Override
        public void showPage ()
        {
            WebView wv = new WebView (wairToNow);
            wv.getSettings ().setBuiltInZoomControls (true);
            wv.getSettings ().setDefaultFontSize (Math.round (wairToNow.textSize / 2.0F));
            wv.getSettings ().setDefaultFixedFontSize (Math.round (wairToNow.textSize / 2.0F));
            wv.getSettings ().setJavaScriptEnabled (true);
            wv.loadData (text, "text/html", null);
            addView (wv);
        }
    }

    /*
     * Displays image file in an ImageView.
     */
    private class ImageFilePage extends FilePage {
        private Bitmap bitmap;
        private String path;

        public ImageFilePage (String path, InputStream istream)
                throws IOException
        {
            bitmap = BitmapFactory.decodeStream (istream);
            if (bitmap == null) throw new IOException ("bitmap corrupt");
            this.path = path;
        }

        @Override
        public String getPath ()
        {
            return path;
        }

        @Override
        public void showPage ()
        {
            ImageView viewer = new ImageView (wairToNow);
            viewer.setImageBitmap (bitmap);
            addView (viewer);
        }
    }

    /*
     * Displays text file in an editable text view box.
     */
    private class TextFilePage extends FilePage {
        private boolean readonly;
        private String path;
        private String text;

        public TextFilePage (String path, InputStream istream, boolean readonly)
                throws IOException
        {
            this.path = path;
            this.text = ReadWholeStream (istream);
            this.readonly = readonly;
        }

        public TextFilePage (String path)
        {
            this.path = path;
            this.text = "";
        }

        @Override
        public String getPath ()
        {
            return path;
        }

        @Override
        public void showPage ()
        {
            SaveTextButton stb = null;
            if (! readonly) {
                stb = new SaveTextButton ();
                addView (stb);
            }

            EditText editor = new EditText (wairToNow);
            editor.setTypeface (Typeface.MONOSPACE);
            editor.setText (text);
            wairToNow.SetTextSize (editor);
            if (stb != null) stb.editor = editor;

            HorizontalScrollView hsv1 = new HorizontalScrollView (wairToNow);
            hsv1.addView (editor);
            ScrollView vsv1 = new ScrollView (wairToNow);
            vsv1.addView (hsv1);
            addView (vsv1);
        }

        private class SaveTextButton extends Button implements OnClickListener {
            public EditText editor;

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
                    OutputStreamWriter osw = new OutputStreamWriter (new FileOutputStream (path));
                    try {
                        osw.write (editor.getText ().toString ());
                    } finally {
                        osw.close ();
                    }
                    adb.setMessage (path + " updated");
                } catch (IOException ioe) {
                    Log.e (TAG, "error writing file", ioe);
                    adb.setMessage ("error writing " + path + ": " + ioe.getMessage ());
                }
                adb.setPositiveButton ("OK", null);
                adb.show ();
            }
        }
    }

    /*
     * Displays zip file contents as a list of buttons that can open the files.
     */
    private class ZipFilePage extends FilePage {
        private String path;
        private ZipFile zipfile;

        public ZipFilePage (String path)
        {
            this.path = path;
        }

        @Override
        public String getPath ()
        {
            return path;
        }

        @Override
        public void showPage ()
                throws IOException
        {
            zipfile = new ZipFile (path);  //TODO stream?
            Enumeration<? extends ZipEntry> entries = zipfile.entries ();

            ScrollView sv1 = new ScrollView (wairToNow);
            LinearLayout ll1 = new LinearLayout (wairToNow);
            ll1.setOrientation (VERTICAL);

            while (entries.hasMoreElements ()) {
                ZipEntry zipent = entries.nextElement ();
                ll1.addView (new ZEntButton (zipent));
            }

            sv1.addView (ll1);
            addView (sv1);
        }

        /**
         * One of these buttons per zip entry in the zip file.
         */
        private class ZEntButton extends Button implements OnClickListener {
            public ZipEntry zipent;

            public ZEntButton (ZipEntry zipent)
            {
                super (wairToNow);
                this.zipent = zipent;
                setText (zipent.getSize () + " : " + zipent.getName ());
                wairToNow.SetTextSize (this);
                setOnClickListener (this);
            }

            @Override  // OnClickListner
            public void onClick (View v)
            {
                String entpath = path + "/" + zipent.getName ();
                try {
                    InputStream fis = zipfile.getInputStream (zipent);
                    try {
                        FilePage fp = OpenStream (entpath, fis, true);
                        if (fp != null) {
                            filePageStack.push (fp);
                            ReshowPage ();
                        }
                    } finally {
                        fis.close ();
                    }
                } catch (IOException ioe) {
                    AlertDialog.Builder adb = new AlertDialog.Builder (wairToNow);
                    adb.setTitle (entpath);
                    adb.setMessage ("error displaying page: " + ioe.getMessage ());
                }
            }
        }
    }

    /**
     * Display the given file from the stream contents.
     * @param name = path name of the file
     * @param istream = file contents
     * @param readonly = contents are read-only (no mods allowed)
     *                   true: istream is processed path
     *                  false: istream matches path exactly
     */
    @SuppressLint("SetJavaScriptEnabled")
    private FilePage OpenStream (String name, InputStream istream, boolean readonly)
            throws IOException
    {
        String namenotmp = name;
        if (namenotmp.endsWith (".tmp")) namenotmp = namenotmp.substring (0, namenotmp.length () - 4);

        /*
         * If ends in .gz, wrap with an unzipper
         */
        if (namenotmp.endsWith (".gz")) {
            istream = new GZIPInputStream (istream);
            namenotmp = namenotmp.substring (0, namenotmp.length () - 3);
            readonly = true;
        }

        /*
         * If one of our .csv, .txt or .xml files, show file contents in a text view.
         */
        for (String textend : textends) {
            if (namenotmp.endsWith (textend)) {
                return new TextFilePage (name, istream, readonly);
            }
        }

        /*
         * If one of our .gif or .png files, show file contents in an image view.
         */
        if (namenotmp.endsWith (".gif") || namenotmp.endsWith (".png") || namenotmp.contains (".gif.p")) {
            return new ImageFilePage (name, istream);
        }

        /*
         * We have .html.gz files, so display those in a web view.
         */
        if (namenotmp.endsWith (".html")) {
            return new HtmlFilePage (name, istream);
        }

        /*
         * Open a .zip file
         */
        if (! readonly && namenotmp.endsWith (".zip")) {
            return new ZipFilePage (name);
        }

        return null;
    }

    /**
     * Read whole stream into a string
     */
    private static String ReadWholeStream (InputStream istream)
            throws IOException
    {
        int total = 0;
        LinkedList<byte[]> blocks = new LinkedList<> ();
        while (true) {
            byte[] block = new byte[8000];
            int rc = istream.read (block, 4, block.length - 4);
            if (rc <= 0) break;
            block[0] = (byte) rc;
            block[1] = (byte) (rc >> 8);
            blocks.add (block);
            total += rc;
        }
        byte[] whole = new byte[total];
        int offs = 0;
        for (byte[] block : blocks) {
            int rc = (block[0] & 0xFF) | ((block[1] & 0xFF) << 8);
            System.arraycopy (block, 4, whole, offs, rc);
            offs += rc;
        }
        return new String (whole);
    }
}
