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

package com.outerworldapps.wairtonow;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Point;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.nio.FloatBuffer;
import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Show a chart as a 3D object.
 */
@SuppressLint("ViewConstructor")
public class Chart3DView extends GLSurfaceView implements ChartView.Backing {
    public final static int BGCOLOR = 0x00BFFF;               // deep sky blue
    public final static int MAXSECTORS = 100;                 // max sectors to build textures for
    public final static float MTEVEREST = 8848.0F;            // metres msl
    public final static int MINIMUMAGL = 3;                   // metres agl
    public final static float NEARDIST = 1.0F / 1024 / 1024;  // earth radii
    public final static float GRAVACCEL = 9.806F;             // metres per sec**2
    public final static float MAXTILTUP = 30.0F;              // degrees
    public final static float MAXTILTDN = 60.0F;              // degrees
    public final static float MINSECONDS = 0.5F;              // accept points at least 1/2sec apart

    private ChartView chartView;
    private float mouseDispS;  // < 1: zoomed in; > 1: zoomed out
    private float mouseDispX;
    private float mouseDispY;
    private float mouseDownX;
    private float mouseDownY;
    private MyPanAndZoom myPanAndZoom;
    private MyRenderer myRenderer;
    private Vector3 canpixxyz = new Vector3 ();
    private WairToNow wairToNow;

    public Chart3DView (ChartView cv)
    {
        super (cv.wairToNow);

        chartView  = cv;
        mouseDispS = 1.0F;
        wairToNow  = cv.wairToNow;

        myPanAndZoom = new MyPanAndZoom ();
        myRenderer = new MyRenderer ();

        // request an OpenGL ES 2.0 compatible context
        setEGLContextClientVersion (2);

        // set how to draw onto the surface
        setRenderer (myRenderer);
        setRenderMode (RENDERMODE_WHEN_DIRTY);
    }

    @Override  // Object
    protected void finalize () throws Throwable
    {
        recycle ();
        super.finalize ();
    }

    /**
     * The selected chart was just changed.
     */
    @Override  // Backing
    public void ChartSelected ()
    {
        queueEvent (new Runnable () {
            @Override
            public void run ()
            {
                myRenderer.ChartSelected ();
            }
        });
    }

    /**
     * Just received a new GPS lat/lon.
     * Position the camera accordingly.
     */
    @Override  // Backing
    public void SetGPSLocation ()
    {
        if (myRenderer != null) {
            myRenderer.setLocation ();
        }
    }

    /**
     * This view is being activated (ie, switched to from Chart2DView).
     */
    @Override  // Backing
    public void Activate ()
    {
        chartView.scaling = 1.0F;
        chartView.ReCenter ();
    }

    @Override  // Backing
    public View getView ()
    {
        return this;
    }

    /**
     * Get what we consider to be the 'up' heading.
     */
    @Override  // Backing
    public float GetCanvasHdgRads ()
    {
        float hdg = wairToNow.currentGPSHdg;
        if ((myRenderer != null) && (myRenderer.mWidth != 0)) {
            hdg = myRenderer.cameraHdg;
        }
        return Mathf.toRadians (hdg);
    }

    /**
     * All done with this object, release all resources.
     * Object not usable any more after this.
     */
    @Override  // Backing
    public void recycle ()
    {
        if (myRenderer != null) {
            queueEvent (new Runnable () {
                @Override
                public void run ()
                {
                    if (myRenderer != null) {
                        myRenderer.recycle ();
                        myRenderer = null;
                    }
                }
            });
        }
    }

    /**
     * User just clicked 'Re-center' from the menu.
     * Undo mouse offsets and re-draw.
     */
    @Override  // Backing
    public void ReCenter ()
    {
        chartView.scaling = 1;
        mouseDispS = 1;
        mouseDispX = 0;
        mouseDispY = 0;
        mouseDownX = 0;
        mouseDownY = 0;
        requestRender ();
    }

    /**
     * Convert a lat/lon/alt to a current canvas pixel.
     * @param lat = latitude degrees
     * @param lon = longitude degrees
     * @param alt = altitude MSL metres
     * @param pix = canvas pixel X,Y
     */
    @Override  // Backing
    public boolean LatLonAlt2CanPixExact (float lat, float lon, float alt, Point pix)
    {
        EarthSector.LatLonAlt2XYZ (lat, lon, alt, canpixxyz);
        return myRenderer.WorldXYZ2PixelXY (canpixxyz, pix);
    }

    /**
     * Handle mouse touches for panning and zooming.
     */
    @Override  // GLSurfaceView
    public boolean onTouchEvent (MotionEvent event)
    {
        return myPanAndZoom.OnTouchEvent (event);
    }

    private class MyPanAndZoom extends PanAndZoom {
        public MyPanAndZoom ()
        {
            super (wairToNow);
        }

        public void MouseDown (float x, float y)
        {
            mouseDownX = x - mouseDispX;  // save where mouse was clicked
            mouseDownY = y - mouseDispY;
        }
        public void MouseUp (float x, float y)
        { }
        public void Panning (float x, float y, float dx, float dy)
        {
            mouseDispX = x - mouseDownX;  // update total displacement
            mouseDispY = y - mouseDownY;
            if (myRenderer != null) {
                myRenderer.rebuildCamera = true;
                requestRender ();        // rebuild transformation matrix
            }
        }
        public void Scaling (float fx, float fy, float sf)
        {
            mouseDispS /= sf;
            if (mouseDispS < 0.125F) mouseDispS = 0.125F;
            if (mouseDispS > 8.0F)   mouseDispS = 8.0F;
            chartView.scaling = 1.0F / mouseDispS;
            if (myRenderer != null) {
                myRenderer.rebuildCamera = true;
                requestRender ();        // rebuild transformation matrix
            }
        }
    }

    /**
     * Download rendering program to GPU chip.
     */
    private static int createProgram (String vertexSource, String fragmentSource)
    {
        int vertexShader = loadShader (GLES20.GL_VERTEX_SHADER, vertexSource);
        try {
            int pixelShader = loadShader (GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            try {
                int program = GLES20.glCreateProgram ();
                if (program == 0) throw new RuntimeException ("glCreateProgram failed");

                GLES20.glAttachShader (program, vertexShader);
                checkGlError ("glAttachShader");
                GLES20.glAttachShader (program, pixelShader);
                checkGlError ("glAttachShader");
                GLES20.glLinkProgram (program);

                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv (program, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] != GLES20.GL_TRUE) {
                    String msg = GLES20.glGetProgramInfoLog (program);
                    GLES20.glDeleteProgram (program);
                    throw new RuntimeException ("Could not link program: " + msg);
                }
                return program;
            } finally {
                GLES20.glDeleteShader (pixelShader);
            }
        } finally {
            GLES20.glDeleteShader (vertexShader);
        }
    }

    private static int loadShader (int shaderType, String source)
    {
        int shader = GLES20.glCreateShader (shaderType);
        if (shader == 0) throw new RuntimeException ("glCreateShader failed");

        GLES20.glShaderSource (shader, source);
        GLES20.glCompileShader (shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv (shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String msg = GLES20.glGetShaderInfoLog (shader);
            GLES20.glDeleteShader (shader);
            throw new RuntimeException ("Could not compile shader " + shaderType + ":" + msg);
        }

        return shader;
    }

    private static void checkGlError (String op)
    {
        int error = GLES20.glGetError ();
        if (error != GLES20.GL_NO_ERROR) {
            throw new RuntimeException (op + ": glError " + error);
        }
    }

    private class MyRenderer implements GLSurfaceView.Renderer {
        private final static int FLOAT_SIZE_BYTES = 4;
        private final static int TRIANGLES_DATA_STRIDE = 5;
        private final static int TRIANGLES_DATA_POS_OFFSET = 0;
        private final static int TRIANGLES_DATA_UV_OFFSET = 3;
        private final static int TRIANGLES_DATA_STRIDE_BYTES = TRIANGLES_DATA_STRIDE * FLOAT_SIZE_BYTES;

        /*
         * See how many degrees away Mt. Everest can be and still see the peak from sea level.
         * About 3 deg or 180 nm.
         */
        private final float mevsightang = Mathf.acos (EarthSector.EARTHRADIUS / (MTEVEREST + EarthSector.EARTHRADIUS));

        private final static String mVertexShader =
                "uniform mat4 uMVPMatrix;\n" +
                        "attribute vec4 aPosition;\n" +
                        "attribute vec2 aTextureCoord;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "void main() {\n" +
                        "  gl_Position = uMVPMatrix * aPosition;\n" +
                        "  vTextureCoord = aTextureCoord;\n" +
                        "}\n";

        private final static String mFragmentShader =
                "precision mediump float;\n" +
                        "varying vec2 vTextureCoord;\n" +
                        "uniform sampler2D sTexture;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                        "}\n";

        /*private final static String mFragmentShaderRed =
                "precision mediump float;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
                        "}\n";*/

        private int mProgramHandle;
        private int muMVPMatrixHandle;
        private int maPositionHandle;
        private int maTextureHandle;

        private boolean rebuildCamera;
        private boolean rebuildObjects;
        private boolean surfaceCreated;
        private DisplayableChart displayableChart;
        private float camsightang;
        private float cameraPosLat, cameraPosLon, cameraPosAlt;
        private float cameraLookLat, cameraLookLon, cameraLookAlt;
        public  float cameraHdg;
        private float lastGPSHdg;
        private float sightnm;
        private float[] mIdnMatrix = new float[16];
        private float[] mMVPMatrix = new float[16];
        private float[] projMatrix = new float[16];
        private float[] viewMatrix = new float[16];
        private EarthSector closeSectors;
        private EarthSector knownSectors;
        private HashMap<Integer,Short> knownHighestElevations = new HashMap<> ();
        private int cameraPointed;
        private int slatMin, nlatMin, wlonMin, elonMin;
        public  int mWidth, mHeight;
        private int[] l2stepLimits = new int[2];
        private long lastGPSTime;
        private Point nepoint = new Point ();
        private Point nwpoint = new Point ();
        private Point sepoint = new Point ();
        private Point swpoint = new Point ();
        private Vector3 cameraLook = new Vector3 ();
        private Vector3 cameraLookMouse;
        private Vector3 cameraPos  = new Vector3 ();
        private Vector3 cameraUp;
        private Vector3 llvxyz = new Vector3 ();

        public MyRenderer ()
        {
            Matrix.setIdentityM (mIdnMatrix, 0);
            cameraLookMouse = cameraLook;
        }

        @Override
        protected void finalize () throws Throwable
        {
            recycle ();
            super.finalize ();
        }

        /**
         * Set up chart being used to build tiles from.
         */
        public void ChartSelected ()
        {
            displayableChart = chartView.selectedChart;
            for (EarthSector es = closeSectors; es != null; es = es.nextDist) {
                es.recycle ();
            }
            closeSectors = null;
            rebuildObjects = true;
            requestRender ();
        }

        /**
         * All done with this object, free everything off.
         */
        public void recycle ()
        {
            for (EarthSector es = closeSectors; es != null; es = es.nextDist) {
                es.recycle ();
            }
            closeSectors = null;
            displayableChart = null;
            cameraPointed = 0;
            surfaceCreated = false;
            mHeight = 0;

            GLES20.glDeleteProgram (mProgramHandle);
            mProgramHandle = 0;
            muMVPMatrixHandle = 0;
            maPositionHandle = 0;
            maTextureHandle = 0;

            Topography.purge ();
        }

        /**
         * Point camera using the given location and previously given location.
         */
        public void setLocation ()
        {
            WairToNow wtn = wairToNow;
            float lat = wtn.currentGPSLat;
            float lon = wtn.currentGPSLon;
            long time = wtn.currentGPSTime;

            // don't accept two points very close to each other
            // cuz we won't be able to compute a direction between them
            // 0.0625 = we can take points at least 0.0625sec apart
            float seconds = (time - lastGPSTime) / 1000.0F;
            float distmet = Lib.LatLonDist (lat, lon, cameraLookLat, cameraLookLon) * Lib.MPerNM;
            if (seconds > MINSECONDS) {
                if (distmet > WairToNow.gpsMinSpeedMPS * seconds) {

                    // place camera at the previously given lat/lon/alt
                    cameraPosLat = cameraLookLat;
                    cameraPosLon = cameraLookLon;
                    cameraPosAlt = cameraLookAlt;
                    cameraPos.set (cameraLook);

                    // make sure new altitude is above ground a little bit
                    // ...so we aren't looking up at or down from bottom side
                    int slatmin = (int) Mathf.floor (lat * 60.0F);
                    int nlatmin = slatmin + 1;
                    int wlonmin = (int) Mathf.floor (lon * 60.0F);
                    int elonmin = (wlonmin + 1 + 180 * 60) % (360 * 60) - 180 * 60;
                    int swelev = Topography.getElevMetres (slatmin / 60.0F, wlonmin / 60.0F);
                    int nwelev = Topography.getElevMetres (nlatmin / 60.0F, wlonmin / 60.0F);
                    int seelev = Topography.getElevMetres (slatmin / 60.0F, elonmin / 60.0F);
                    int neelev = Topography.getElevMetres (nlatmin / 60.0F, elonmin / 60.0F);
                    float alt = wtn.currentGPSAlt;
                    if (alt < swelev + MINIMUMAGL) alt = swelev + MINIMUMAGL;
                    if (alt < nwelev + MINIMUMAGL) alt = nwelev + MINIMUMAGL;
                    if (alt < seelev + MINIMUMAGL) alt = seelev + MINIMUMAGL;
                    if (alt < neelev + MINIMUMAGL) alt = neelev + MINIMUMAGL;

                    // point the camera at the new GPS position
                    cameraLookLat = lat;
                    cameraLookLon = lon;
                    cameraLookAlt = alt;
                    EarthSector.LatLonAlt2XYZ (lat, lon, alt, cameraLook);

                    // bank the camera using a computed bank angle
                    // ie, rotate the up vector around the position-to-lookat vector
                    float bankrad = CalcBankAngle ();
                    cameraUp = cameraPos.rotate (cameraLook.minus (cameraPos), bankrad);

                    // update the display
                    cameraPointed ++;
                    rebuildCamera = true;
                    requestRender ();
                }

                // save this sample for next time
                lastGPSHdg  = wtn.currentGPSHdg;
                lastGPSTime = time;
            }
        }

        /**
         * Compute bank angle for a co-ordinated turn from previous GPS sample heading
         * to the current GPS sample heading.
         * @return bank angle in radians (positive is turning right)
         */
        private float CalcBankAngle ()
        {
            WairToNow wtn = wairToNow;
            float hdg = wtn.currentGPSHdg;
            float spd = wtn.currentGPSSpd;
            long time = wtn.currentGPSTime;

            /*
             * Rate of turn can be computed from two most recent GPS reports.
             * Units are radians per second.
             */
            float prevHdg = lastGPSHdg;
            if (prevHdg - hdg >  180) prevHdg -= 360;
            if (prevHdg - hdg < -180) prevHdg += 360;
            float rateOfTurn = Mathf.toRadians ((hdg - prevHdg) / (time - lastGPSTime) * 1000.0F);

            /*
             * Calculate turn radius (metres).
             */
            //float turnRadius = spd / rateOfTurn;

            /*
             * Calculate centripetal acceleration (metres per second squared).
             */
            float centripAccel = spd * rateOfTurn;

            /*
             * Bank angle is arctan (centripAccel / gravity).
             */
            return Mathf.atan2 (centripAccel, GRAVACCEL);
        }

        /**
         * Surface was created, do one-time initializations in GL thread.
         */
        @Override
        public void onSurfaceCreated (GL10 glUnused, EGLConfig config)
        {
            ////// don't display backsides of triangles
            ////GLES20.glEnable (GLES20.GL_CULL_FACE);

            // don't display triangle if another triangle is in front of it
            GLES20.glEnable (GLES20.GL_DEPTH_TEST);

            // compile the rendering program and get references to variables therein
            mProgramHandle    = createProgram (mVertexShader, mFragmentShader);
            maPositionHandle  = getAttrLoc ("aPosition");
            maTextureHandle   = getAttrLoc ("aTextureCoord");
            muMVPMatrixHandle = getUnifLoc ("uMVPMatrix");

            // try to draw the scene now
            surfaceCreated = true;
            requestRender ();
        }

        private int getAttrLoc (String attrname)
        {
            int handle = GLES20.glGetAttribLocation (mProgramHandle, attrname);
            checkGlError ("glGetAttribLocation " + attrname);
            if (handle == -1) {
                throw new RuntimeException ("could not get attrib location for " + attrname);
            }
            return handle;
        }

        private int getUnifLoc (String attrname)
        {
            int handle = GLES20.glGetUniformLocation (mProgramHandle, attrname);
            checkGlError ("glGetUniformLocation " + attrname);
            if (handle == -1) {
                throw new RuntimeException ("could not get uniform location for " + attrname);
            }
            return handle;
        }

        /**
         * Size of the surface first known or has changed.
         */
        @Override
        public void onSurfaceChanged (GL10 glUnused, int width, int height)
        {
            // set what part of canvas the world is mapped to
            // 0,0 is in lower left corner (not upper left corner)
            GLES20.glViewport (0, 0, width, height);

            // save screen dimensions
            mWidth  = width;
            mHeight = height;

            // maybe update camera
            rebuildCamera = true;
            requestRender ();
        }

        /**
         * Draw triangles to the screen.
         */
        @Override
        public void onDrawFrame (GL10 glUnused)
        {
            /*
             * Tell EarthSector to stop downloading any more tiles.
             * We will re-request them if we still want them.
             * Helps avoid out-of-memory errors.
             */
            EarthSector.ClearLoader ();

            /*
             * Make sure we are all initialized.
             * Also detects if chart has been set to "None".
             */
            if ((displayableChart == null) || (cameraPointed < 2) || !surfaceCreated || (mHeight == 0)) {
                GLES20.glClearColor (0.125F, 0.125F, 0.125F, 1.0F);
                GLES20.glClear (GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
                return;
            }

            /*
             * Maybe update camera transform matrix.
             */
            if (rebuildCamera) {
                rebuildCamera = false;
                updateCamera ();
            }

            /*
             * Maybe the object list needs to be rebuilt.
             */
            if (rebuildObjects) {
                rebuildObjects = false;
                updateObjects ();
            }

            /*
             * Initialize OpenGL to start drawing the scene.
             */
            GLES20.glClearColor (
                    Color.red (BGCOLOR) / 255.0F,
                    Color.green (BGCOLOR) / 255.0F,
                    Color.blue (BGCOLOR) / 255.0F, 1.0F);
            GLES20.glClear (GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram (mProgramHandle);
            checkGlError ("glUseProgram");

            /*
             * Set up transformation (camera) matrix.
             */
            GLES20.glUniformMatrix4fv (muMVPMatrixHandle, 1, false, mMVPMatrix, 0);

            /*
             * Output at most MAXSECTORS sector tiles to the scene.
             * Count unloaded sectors as well cuz they will eventually come in
             * and we don't want to include far away sectors in the MAXSECTORS
             * count.
             */
            int nsectors = 0;
            chartView.undrawn = 0;
            for (EarthSector es = closeSectors; es != null; es = es.nextDist) {
                if (es.setup (reRender)) {
                    drawAnObject (es.getTextureID (), es.getTriangles ());
                } else {
                    chartView.undrawn ++;
                }
                if (++ nsectors >= MAXSECTORS) break;
            }
            chartView.stateView.postInvalidate ();  // for undrawn
        }

        /**
         * A tile was just loaded, re-draw the screen with the new tile.
         */
        private Runnable reRender = new Runnable () {
            @Override
            public void run ()
            {
                // trigger onDrawFrame() to be called soon
                requestRender ();
            }
        };

        /**
         * Draw the object with the given texture.
         */
        private void drawAnObject (int textureID, FloatBuffer triangles)
        {
            // select texture which has been bound to the bitmap
            GLES20.glActiveTexture (GLES20.GL_TEXTURE0);
            GLES20.glBindTexture (GLES20.GL_TEXTURE_2D, textureID);

            // tell program where to get vertex x,y,z's from (aPosition)
            triangles.position (TRIANGLES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer (maPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLES_DATA_STRIDE_BYTES, triangles);
            checkGlError ("glVertexAttribPointer maPosition");

            // tell program where to get image u,v's from (aPosition)
            triangles.position (TRIANGLES_DATA_UV_OFFSET);
            GLES20.glEnableVertexAttribArray (maPositionHandle);
            checkGlError ("glEnableVertexAttribArray maPositionHandle");

            GLES20.glVertexAttribPointer (maTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLES_DATA_STRIDE_BYTES, triangles);
            checkGlError ("glVertexAttribPointer maTextureHandle");

            GLES20.glEnableVertexAttribArray (maTextureHandle);
            checkGlError ("glEnableVertexAttribArray maTextureHandle");

            GLES20.glDrawArrays (GLES20.GL_TRIANGLES, 0,
                    triangles.capacity () / TRIANGLES_DATA_STRIDE);
            checkGlError ("glDrawArrays");
        }

        /**
         * Update camera to position described by cameraPos,cameraLook,cameraUp.
         */
        private void updateCamera ()
        {
            /*
             * Calculate how many degrees along earth surface can be seen from camera
             * to earth's edge assuming earth's edge is at sea level, no matter where
             * the camera is pointed.
             */
            camsightang = Mathf.acos (EarthSector.EARTHRADIUS / (cameraPosAlt + EarthSector.EARTHRADIUS));

            /*
             * See how far away from the camera Mt. Everest could possibly be to see the peak
             * that is not obscured by a sea-level surface between camera and peak.
             */
            sightnm = Mathf.toDegrees (camsightang + mevsightang) * Lib.NMPerDeg;

            /*
             * Set up camera frustum far plane so that the camera can see that far away.
             */
            float fardist = sightnm * Lib.MPerNM / EarthSector.EARTHRADIUS;

            /*
             * Set up camera frustum.
             */
            float scale  = mouseDispS * NEARDIST;
            float near   = NEARDIST;
            //noinspection UnnecessaryLocalVariable
            float far    = fardist;
            float aspect = (float) mHeight / (float) mWidth;
            //noinspection UnnecessaryLocalVariable
            float right  = scale;
            float top    = scale * aspect;
            float left   = -right;
            float bottom = -top;
            Matrix.frustumM (projMatrix, 0, left, right, bottom, top, near, far);

            /*
             * Apply mouse-driven displacement to modify the look-at point.
             * X-displacement rotates about the up axis.
             * Y-displacement rotates about the normal to the position x look plane.
             */
            cameraLookMouse = cameraLook;
            float dispX = mouseDispX;
            float dispY = mouseDispY;
            if ((dispX != 0) || (dispY != 0)) {
                Vector3 diff = cameraLook.minus (cameraPos);
                Vector3 rotated = diff.rotate (cameraUp, dispX * mouseDispS * Math.PI / 2.0F / mWidth);
                cameraLookMouse = cameraPos.plus (rotated);

                Vector3 normal = cameraPos.cross (cameraLookMouse);
                float anglY = dispY / -2.0F / mWidth;
                if (anglY < -MAXTILTUP / 180.0F) anglY = -MAXTILTUP / 180.0F;
                if (anglY >  MAXTILTDN / 180.0F) anglY =  MAXTILTDN / 180.0F;
                mouseDispY = Math.round (anglY * mWidth * -2.0F);
                rotated = rotated.rotate (normal, anglY * mouseDispS * Mathf.PI);
                cameraLookMouse = cameraPos.plus (rotated);
            }

            /*
             * Re-compute the view matrix.
             */
            Matrix.setLookAtM (viewMatrix, 0,
                    cameraPos.x, cameraPos.y, cameraPos.z,
                    cameraLookMouse.x, cameraLookMouse.y, cameraLookMouse.z,
                    cameraUp.x, cameraUp.y, cameraUp.z);

            /*
             * Compute camera true heading.
             */
            Vector3 lookLLA = new Vector3 ();
            EarthSector.XYZ2LatLonAlt (cameraLookMouse, lookLLA);
            cameraHdg = Lib.LatLonTC (cameraPosLat, cameraPosLon, lookLLA.x, lookLLA.y);

            /*
             * Make a composite matrix from camera frustum and camera position.
             */
            Matrix.multiplyMM (mMVPMatrix, 0, projMatrix, 0, viewMatrix, 0);

            /*
             * Get range of lat/lon (whole STEPs) visible to camera.
             * We can't possibly see anything farther away than this.
             */
            float sightdeg  = sightnm / Lib.NMPerDeg;
            float camlatcos = Mathf.cos (Math.toRadians (cameraPosLat));
            slatMin = (int) Math.floor ((cameraPosLat - sightdeg) * 60.0F);
            nlatMin = (int) Math.ceil ((cameraPosLat + sightdeg) * 60.0F);
            wlonMin = (int) Math.floor (Lib.NormalLon (cameraPosLon - sightdeg * camlatcos) * 60.0F);
            elonMin = (int) Math.ceil  (Lib.NormalLon (cameraPosLon + sightdeg * camlatcos) * 60.0F);

            /*
             * Save limits of what is currently visible.
             */
            float nlat = nlatMin / 60.0F;
            float slat = slatMin / 60.0F;
            float elon = elonMin / 60.0F;
            float wlon = wlonMin / 60.0F;
            try {
                chartView.pmap.setup (mWidth, mHeight,
                        nlat, wlon, nlat, elon, slat, wlon, slat, elon);
            } catch (ArithmeticException ae) {
                Log.w ("Chart3DView", "pmap error");
            }

            /*
             * Objects in view of camera need updating in GL thread.
             */
            rebuildObjects = true;
        }

        /**
         * Update objects viewable by camera.
         * Called only in the GL thread.
         */
        private void updateObjects ()
        {
            /*
             * Try once, purge everything if oom and try just once again.
             */
            try {
                updateObjectsWork ();
            } catch (OutOfMemoryError oome) {
                Log.w ("Chart3DView", "first out of memory error");
                Topography.purge ();
                for (EarthSector es = closeSectors; es != null; es = es.nextDist) {
                    es.recycle ();
                }
                closeSectors = null;
                System.gc ();
                System.runFinalization ();
                try {
                    updateObjectsWork ();
                } catch (OutOfMemoryError oome2) {
                    Log.w ("Chart3DView", "second out of memory error");
                }
            }
        }

        private void updateObjectsWork ()
        {
            /*
             * Mark any loaded sectors as unreferenced cuz we will mark them if still needed.
             */
            for (EarthSector es = closeSectors; es != null; es = es.nextDist) {
                es.refd = false;
                es.nextAll = knownSectors;
                knownSectors = es;
            }
            closeSectors = null;

            /*
             * Generate EarthSector objects within sight of screen.
             */
            displayableChart.GetL2StepLimits (l2stepLimits);
            int l2stepmin   = l2stepLimits[1];  // start with largest sectors
            int stepmin     = 1 << l2stepmin;   // size of a sector edge (in minutes)
            int slatMinStep = slatMin & -stepmin;
            int nlatMinStep = (nlatMin & -stepmin) + stepmin;
            int wlonMinStep = wlonMin & -stepmin;
            int elonMinStep = (elonMin & -stepmin) + stepmin;
            generateSectors (l2stepmin, slatMinStep, nlatMinStep, wlonMinStep, elonMinStep);

            /*
             * Destroy any unreferenced sector objects to save memory.
             * These are ones not in view of camera.
             */
            for (EarthSector sector = knownSectors; sector != null; sector = sector.nextAll) {
                if (!sector.refd) {
                    sector.recycle ();
                }
            }
            knownSectors = null;
        }

        /**
         * Generate EarthSector objects for the given lat/lon range.
         * @param l2stepmin = log2 sector size in minutes
         * @param slatMinStep = south latitude of sector range in minutes, multiple of 1<<l2stepmin
         * @param nlatMinStep = north latitude of sector range in minutes, multiple of 1<<l2stepmin
         * @param wlonMinStep = west longitude of sector range in minutes, multiple of 1<<l2stepmin
         * @param elonMinStep = east longitude of sector range in minutes, multiple of 1<<l2stepmin
         * returns with sectors pushed to closeSectors list and marked as referenced
         */
        private void generateSectors (int l2stepmin, int slatMinStep, int nlatMinStep, int wlonMinStep, int elonMinStep)
        {
            /*
             * Get length of side of sector (in minutes).
             * The sector gets drawn to a bitmap of EarthSector.MBMSIZE x EarthSector.MBMSIZE.
             */
            int stepmin = 1 << l2stepmin;

            /*
             * Loop through all lat/lon given by caller.
             * Build list of those actually in view sorted by ascending distance from camera.
             */
            int elonminstep = elonMinStep;
            if (elonminstep < wlonMinStep) elonminstep += 360 * 60;

            for (int ilatmin = slatMinStep; ilatmin < nlatMinStep; ilatmin += stepmin) {
                for (int jlonmin = wlonMinStep; jlonmin < elonminstep; jlonmin += stepmin) {

                    int ilonmin = jlonmin;
                    if (ilonmin >= 180 * 60) ilonmin -= 360 * 60;

                    /*
                     * Calculate how far center of sector is from camera.
                     */
                    float slat = ilatmin / 60.0F;
                    float nlat = (ilatmin + stepmin) / 60.0F;
                    float wlon = ilonmin / 60.0F;
                    float elon = (ilonmin + stepmin) / 60.0F;
                    float nmfromcam = Lib.LatLonDist (cameraPosLat, cameraPosLon,
                            (slat + nlat) / 2.0F, (wlon + elon) / 2.0F);
                    if (elon >= 180.0F) elon -= 360.0F;

                    /*
                     * Do sector at next zoomed-in level if too close to camera for this zoom level.
                     */
                    // l2stepmin=5: stepmin=32: nmfromcam=20.0 .. 40.0  zoomed-in 2x: 40.0 .. 80.0
                    // l2stepmin=4; stepmin=16: nmfromcam=10.0 .. 20.0  zoomed-in 2x: 20.0 .. 40.0
                    // l2stepmin=3: stepmin= 8: nmfromcam= 5.0 .. 10.0  zoomed-in 2x: 10.0 .. 20.0
                    // l2stepmin=2: stepmin= 4: nmfromcam= 2.5 ..  5.0  zoomed-in 2x:  5.0 .. 10.0
                    float stepmindist = stepmin * 20.0F / 32.0F;
                    if ((nmfromcam * mouseDispS < stepmindist) && (l2stepmin > l2stepLimits[0])) {
                        generateSectors (l2stepmin - 1, ilatmin, ilatmin + stepmin, ilonmin, ilonmin + stepmin);
                        continue;
                    }

                    /*
                     * Skip sector if too far away from camera to be visible due to curvature of Earth.
                     * Only do this for outermost level cuz zoomed-in levels are within a few miles of camera.
                     */
                    if (l2stepmin == l2stepLimits[1]) {
                        int secmaxelev = GetHighestElev (ilatmin, ilonmin, l2stepmin);
                        float secsightang = Mathf.acos (EarthSector.EARTHRADIUS / (secmaxelev + EarthSector.EARTHRADIUS));
                        float secsightnm = Mathf.toDegrees (camsightang + secsightang) * Lib.NMPerDeg;
                        if (nmfromcam > secsightnm) continue;
                    }

                    /*
                     * Skip sector if not in front of camera, ie, not visible on screen.
                     * Doesn't have to be exact, just can't skip any that are visible.
                     * But it might allow some invisible ones to be processed.
                     */
                    LatLon2PixelXY (slat, wlon, swpoint);
                    LatLon2PixelXY (nlat, wlon, nwpoint);
                    LatLon2PixelXY (slat, elon, sepoint);
                    LatLon2PixelXY (nlat, elon, nepoint);
                    int minx = Math.min (Math.min (swpoint.x, nwpoint.x), Math.min (sepoint.x, nepoint.x));
                    int maxx = Math.max (Math.max (swpoint.x, nwpoint.x), Math.max (sepoint.x, nepoint.x));
                    int miny = Math.min (Math.min (swpoint.y, nwpoint.y), Math.min (sepoint.y, nepoint.y));
                    int maxy = Math.max (Math.max (swpoint.y, nwpoint.y), Math.max (sepoint.y, nepoint.y));
                    if (maxx <= 0) continue;        // whole thing is off to left of screen
                    if (minx >= mWidth) continue;   // whole thing is off to right of screen
                    if (maxy <= 0) continue;        // whole thing is above top of screen
                    if (miny >= mHeight) continue;  // whole thing is below bottom of screen

                    /*
                     * Make sure we have the earth sector object for that lat/lon.
                     * Don't waste time reading in the bitmap yet cuz we might not want it.
                     */
                    int hashkey = (l2stepmin << 29) + ((ilatmin & 0x3FFF) << 15) + (ilonmin & 0x7FFF);
                    EarthSector sector;
                    for (sector = knownSectors; sector != null; sector = sector.nextAll) {
                        if (sector.hashkey == hashkey) break;
                    }
                    if (sector == null) {
                        sector = new EarthSector (displayableChart, slat, nlat, wlon, elon);
                        sector.hashkey = hashkey;
                        sector.nextAll = knownSectors;
                        knownSectors = sector;
                    }

                    /*
                     * Remember how far it is from the camera and put in list sorted by distance.
                     */
                    sector.nmfromcam = nmfromcam;
                    EarthSector prev, next;
                    prev = null;
                    for (next = closeSectors; next != null; next = next.nextDist) {
                        if (next.nmfromcam > nmfromcam) break;
                        prev = next;
                    }
                    if (prev == null) closeSectors = sector;
                    else prev.nextDist = sector;
                    sector.nextDist = next;

                    /*
                     * Mark it as referenced as it is in view of the camera.
                     */
                    sector.refd = true;
                }
            }
        }

        /**
         * Get highest elevation in a sector.
         * @param ilatmin = south latitude (in minutes)
         * @param ilonmin = west longitude (in minutes)
         * @param l2stepmin = log2 sector size (in minutes)
         * @return highest elevation (metres MSL)
         */
        private short GetHighestElev (int ilatmin, int ilonmin, int l2stepmin)
        {
            int key = (l2stepmin << 29) + ((ilatmin & 0x3FFF) << 15) + (ilonmin & 0x7FFF);
            if (knownHighestElevations.containsKey (key)) {
                return knownHighestElevations.get (key);
            }
            int stepmin = 1 << l2stepmin;
            short highest = Topography.INVALID_ELEV;
            for (int ilat = ilatmin; ilat < ilatmin + stepmin; ilat ++) {
                for (int jlon = ilonmin; jlon <= ilonmin + stepmin; jlon ++) {
                    int ilon = jlon;
                    if (ilon >= 180 * 60) ilon -= 360 * 60;
                    short elev = Topography.getElevMetres (ilat / 60.0F, ilon / 60.0F);
                    if (highest < elev) highest = elev;
                }
            }
            knownHighestElevations.put (key, highest);
            return highest;
        }

        /**
         * Convert a lat/lon to an on-the-screen pixel XY
         * @param lat = latitude (degrees)
         * @param lon = longitude (degrees)
         * @param xy  = where to put the resultant XY
         * @return true iff pixel is visible
         */
        private boolean LatLon2PixelXY (float lat, float lon, Point xy)
        {
            int alt = Topography.getElevMetres (lat, lon);
            EarthSector.LatLonAlt2XYZ (lat, lon, alt, llvxyz);
            return WorldXYZ2PixelXY (llvxyz, xy);
        }

        /**
         * Convert a world XYZ to an on-the-screen pixel XY
         * @return whether the point is visible or not
         */
        private boolean WorldXYZ2PixelXY (Vector3 xyz, Point xy)
        {
            // simplified version of GLU.gluProject()
            float[] m = mMVPMatrix;
            float rawx = m[ 0] * xyz.x + m[ 4] * xyz.y + m[ 8] * xyz.z + m[12];
            float rawy = m[ 1] * xyz.x + m[ 5] * xyz.y + m[ 9] * xyz.z + m[13];
            float rawh = m[ 3] * xyz.x + m[ 7] * xyz.y + m[11] * xyz.z + m[15];
            if (rawh == 0) return false;
            rawx = (1 + rawx / rawh) / 2 * mWidth;
            rawy = (1 - rawy / rawh) / 2 * mHeight;
            xy.x = Math.round (rawx);
            xy.y = Math.round (rawy);
            if (rawx < Integer.MIN_VALUE) xy.x = Integer.MIN_VALUE;
            if (rawy < Integer.MIN_VALUE) xy.y = Integer.MIN_VALUE;
            if (rawx > Integer.MAX_VALUE) xy.x = Integer.MAX_VALUE;
            if (rawy > Integer.MAX_VALUE) xy.y = Integer.MAX_VALUE;

            // see if pixel number is within range of the screen
            if ((xy.x < 0) || (xy.x >= mWidth) || (xy.y < 0) || (xy.y >= mHeight)) return false;

            // points behind the camera can appear within limits of pixels yet not be on screen
            // so we check the dot product = (cameraLookMouse - cameraPos) dot (xyz - cameraPos) > 0
            float dotprod =
                    (cameraLookMouse.x - cameraPos.x) * (xyz.x - cameraPos.x) +
                    (cameraLookMouse.y - cameraPos.y) * (xyz.y - cameraPos.y) +
                    (cameraLookMouse.z - cameraPos.z) * (xyz.z - cameraPos.z);
            return dotprod > 0.0F;
        }
    }
}