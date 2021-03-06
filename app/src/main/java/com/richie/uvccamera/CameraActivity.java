package com.richie.uvccamera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.serenegiant.usb.*;
import com.serenegiant.usb.widget.UVCCameraTextureView;

import java.io.*;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.richie.uvccamera.BuildConfig.DEBUG;

/**
 * Created by lylaut on 2022/06/29
 */
public class CameraActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = CameraActivity.class.getName();

    private static boolean DEBUG = true;

    // for thread pool
    private static final int CORE_POOL_SIZE = 1;        // initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;            // maximum threads
    private static final int KEEP_ALIVE_TIME = 10;        // time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER = new ThreadPoolExecutor(CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;
    private UVCCamera mCamera = null;
    private UVCCameraTextureView mUVCCameraView;

    private SurfaceTexture mUVCCameraViewSurfaceTexture;
    private Surface mPreviewSurface;
    private Bitmap bitmap;
    private boolean isNeedCallBack = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mUVCCameraView = findViewById(R.id.texture_camera_view);
        mUVCCameraView.setSurfaceTextureListener(this);
        mUVCCameraView.setAspectRatio(
                UVCCamera.DEFAULT_PREVIEW_WIDTH * 1.0f / UVCCamera.DEFAULT_PREVIEW_HEIGHT);
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //???????????????????????????????????????
            ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else {
            Log.d(TAG, "requestMyPermissions: ??????SD??????");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mUVCCameraView.setSurfaceTextureListener(this);
        mUSBMonitor.register();
//        final List<DeviceFilter> filter = DeviceFilter.getDeviceFilters(this, com.serenegiant.usb.R.xml.device_filter);
//        for (DeviceFilter filter1 : filter) {
//            List<UsbDevice> deviceList = mUSBMonitor.getDeviceList(filter1);
//            if (deviceList.size() > 0) {
//                mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList((filter.get(0))).get(0));
//                if (mCamera != null) {
//                    mCamera.startPreview();//????????????
//                }
//                break;
//            }
//        }
        List<UsbDevice> deviceList = mUSBMonitor.getDeviceList();
        if (deviceList.size() > 0) {
            for (UsbDevice usbDevice : deviceList) {
                if (usbDevice.getVendorId() == 3034 && usbDevice.getProductId() == 8466) {
                    mUSBMonitor.requestPermission(usbDevice);
                    if (mCamera != null) {
                        mCamera.startPreview();//????????????
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected void onPause() {
        mUSBMonitor.unregister();
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
        }
        if (mCamera != null) {
            mCamera.destroy();
        }
        super.onDestroy();
    }

    private USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {

            Toast.makeText(CameraActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT)
                    .show();//???????????????????????????
        }

        @Override
        public void onDettach(UsbDevice device) {

            Toast.makeText(CameraActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT)
                    .show();
        }

        @Override
        public void onConnect(UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            //?????????USB ????????????????????????USBMonitor???processConnect?????????Handler???????????????USB ?????????????????????????????????????????????Monitor?????????onConnect
            if (mCamera != null) {
                return;
            }
            final UVCCamera camera = new UVCCamera();
            EXECUTER.execute(new Runnable() {

                @Override
                public void run() {
                    // Open Camera
                    if (Looper.myLooper() == null) {
                        Looper.prepare();//??????????????????????????????????????????Handler ??????
                    }

                    try {
                        // open camera
                        camera.open(ctrlBlock);
                        // Set Preview Mode
                        camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH,
                                UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                                UVCCamera.FRAME_FORMAT_MJPEG, 0.5f);
                    } catch (UnsupportedOperationException e1) {
                        return;
                    } catch (IllegalArgumentException e1) {
                        e1.printStackTrace();
                        try {
                            camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH,
                                    UVCCamera.DEFAULT_PREVIEW_HEIGHT,
                                    UVCCamera.DEFAULT_PREVIEW_MODE, 0.5f);
                        } catch (IllegalArgumentException e2) {
                            //                            camera.destroy();
                            releaseUVCCamera();
                            e2.printStackTrace();
                        }
                    }
                    // Start Preview
                    if (mCamera == null) {
                        mCamera = camera;
                        if (mPreviewSurface != null) {
                            mPreviewSurface.release();
                            mPreviewSurface = null;
                        }
                        final SurfaceTexture surfaceTexture = mUVCCameraView.getSurfaceTexture();
                        if (surfaceTexture != null) {
                            mPreviewSurface = new Surface(surfaceTexture);
                        } else if (mUVCCameraViewSurfaceTexture != null) {
                            mPreviewSurface = new Surface(mUVCCameraViewSurfaceTexture);
                        }
//                        Toast.makeText(CameraActivity.this, " Open Camera??????>  SurfaceTexture surfaceTexture = mUVCCameraView.getSurfaceTexture();" + camera, Toast.LENGTH_SHORT).show();

                        camera.setPreviewDisplay(mPreviewSurface);
//                        camera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_RGB565);
                        camera.startPreview();

                    }
                }
            });
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) {
                Log.v(TAG, "onDisconnect" + device);
            }
            if (mCamera != null && device.equals(mCamera.getDevice())) {
                releaseUVCCamera();
            }
        }

        @Override
        public void onCancel(UsbDevice device) {

        }
    };

    private void releaseUVCCamera() {
        if (DEBUG) {
            Log.v(TAG, "releaseUVCCamera");
        }
        if (mCamera != null) {
            mCamera.close();
        }


        if (mPreviewSurface != null) {
            mPreviewSurface.release();
            mPreviewSurface = null;
        }
        if (mCamera != null) {
            mCamera.destroy();
            mCamera = null;
        }
    }

    public byte[] bitmabToBytes(Bitmap bitmap) {
        //????????????????????????
        ///Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        int size = bitmap.getWidth() * bitmap.getHeight() * 4;
        //?????????????????????????????????,???????????????size
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size);
        try {
            //???????????????????????????????????????100%????????????????????????????????????
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            //?????????????????????????????????????????????byte[]
            byte[] imagedata = baos.toByteArray();
            return imagedata;
        } catch (Exception e) {
        } finally {
            try {
                bitmap.recycle();
                baos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new byte[0];
    }

    /**
     * ??????????????????????????????SD??????
     *
     * @param data
     * @throws IOException
     */
    public String saveToSDCard(byte[] data) throws IOException {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss"); // ???????????????
        String filename = format.format(date) + ".jpg";
        // File fileFolder = new File(getTrueSDCardPath()
        //       + "/rebot/cache/");

        File fileFolder = new File("/sdcard" + "/rebot/");

        if (!fileFolder.exists()) {
            fileFolder.mkdir();
        }
        File jpgFile = new File(fileFolder, filename);
        FileOutputStream outputStream = new FileOutputStream(jpgFile); // ???????????????
        outputStream.write(data); // ??????sd??????
        outputStream.close(); // ???????????????
        return jpgFile.getName().toString();
    }

    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    // if you need frame data as byte array on Java side, you can use this callback method with UVCCamera#setFrameCallback
    // if you need to create Bitmap in IFrameCallback, please refer following snippet.
    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
            frame.clear();
            bitmap = Bitmap.createBitmap(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, Bitmap.Config.RGB_565);
            bitmap.copyPixelsFromBuffer(frame);
            bitmap = comp(bitmap);

            try {
                if (isNeedCallBack) {
                    saveToSDCard(bitmabToBytes(bitmap));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.e("USBCamera", "??????BitMap CameraView");
        }
    };

    private Bitmap comp(Bitmap image) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        if (baos.toByteArray().length / 1024 >
                1024) {//????????????????????????1M,????????????????????????????????????BitmapFactory.decodeStream????????????
            baos.reset();//??????baos?????????baos
            image.compress(Bitmap.CompressFormat.JPEG, 50, baos);//????????????50%?????????????????????????????????baos???
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        //??????????????????????????????options.inJustDecodeBounds ??????true???
        newOpts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(isBm, null, newOpts);
        newOpts.inJustDecodeBounds = false;
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        float hh = 720f;
        float ww = 1280f;
        //????????????????????????????????????????????????????????????????????????????????????????????????
        int be = 1;//be=1???????????????
        if (w > h && w > ww) {//???????????????????????????????????????????????????
            be = (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) {//???????????????????????????????????????????????????
            be = (int) (newOpts.outHeight / hh);
        }
        if (be <= 0) {
            be = 1;
        }
        newOpts.inSampleSize = be;//??????????????????
        newOpts.inPreferredConfig = Bitmap.Config.RGB_565;//???????????????ARGB888???RGB565
        //??????????????????????????????????????????options.inJustDecodeBounds ??????false???
        isBm = new ByteArrayInputStream(baos.toByteArray());
        bitmap = BitmapFactory.decodeStream(isBm, null, newOpts);
        return compressImage(bitmap);//?????????????????????????????????????????????
    }

    private Bitmap compressImage(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);//???????????????????????????100????????????????????????????????????????????????baos???
        int options = 100;
        while (baos.toByteArray().length / 1024 > 100) {    //?????????????????????????????????????????????100kb,??????????????????
            baos.reset();//??????baos?????????baos
            options -= 10;//???????????????10
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);//????????????options%?????????????????????????????????baos???

        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(
                baos.toByteArray());//?????????????????????baos?????????ByteArrayInputStream???
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);//???ByteArrayInputStream??????????????????
        return bitmap;
    }


    //?????????Surface
    private void initSurfaceView(SurfaceTexture surfaceTexture) {
        mUVCCameraViewSurfaceTexture = surfaceTexture;
        try {
            if (mPreviewSurface != null) {
                mPreviewSurface.release();
                mPreviewSurface = null;
            }
            if (surfaceTexture != null) {
                mPreviewSurface = new Surface(surfaceTexture);
            }
            mCamera.setPreviewDisplay(mPreviewSurface);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        initSurfaceView(surface);//???????????????????????????getSurfaceTexture???null ??????????????????????????????????????????
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
