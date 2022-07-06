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
            //没有授权，编写申请权限代码
            ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } else {
            Log.d(TAG, "requestMyPermissions: 有写SD权限");
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
//                    mCamera.startPreview();//开启预览
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
                        mCamera.startPreview();//开启预览
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
                    .show();//获取到权限之后触发
        }

        @Override
        public void onDettach(UsbDevice device) {

            Toast.makeText(CameraActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT)
                    .show();
        }

        @Override
        public void onConnect(UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            //接收到USB 事件的广播之后再USBMonitor的processConnect里通过Handler尝试建立与USB 摄像头的连接，建立连接之后会由Monitor去触发onConnect
            if (mCamera != null) {
                return;
            }
            final UVCCamera camera = new UVCCamera();
            EXECUTER.execute(new Runnable() {

                @Override
                public void run() {
                    // Open Camera
                    if (Looper.myLooper() == null) {
                        Looper.prepare();//这一步也很重要否则会引发内部Handler 异常
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
//                        Toast.makeText(CameraActivity.this, " Open Camera——>  SurfaceTexture surfaceTexture = mUVCCameraView.getSurfaceTexture();" + camera, Toast.LENGTH_SHORT).show();

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
        //将图片转化为位图
        ///Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        int size = bitmap.getWidth() * bitmap.getHeight() * 4;
        //创建一个字节数组输出流,流的大小为size
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size);
        try {
            //设置位图的压缩格式，质量为100%，并放入字节数组输出流中
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            //将字节数组输出流转化为字节数组byte[]
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
     * 将拍下来的照片存放在SD卡中
     *
     * @param data
     * @throws IOException
     */
    public String saveToSDCard(byte[] data) throws IOException {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss"); // 格式化时间
        String filename = format.format(date) + ".jpg";
        // File fileFolder = new File(getTrueSDCardPath()
        //       + "/rebot/cache/");

        File fileFolder = new File("/sdcard" + "/rebot/");

        if (!fileFolder.exists()) {
            fileFolder.mkdir();
        }
        File jpgFile = new File(fileFolder, filename);
        FileOutputStream outputStream = new FileOutputStream(jpgFile); // 文件输出流
        outputStream.write(data); // 写入sd卡中
        outputStream.close(); // 关闭输出流
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
            Log.e("USBCamera", "获取BitMap CameraView");
        }
    };

    private Bitmap comp(Bitmap image) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        if (baos.toByteArray().length / 1024 >
                1024) {//判断如果图片大于1M,进行压缩避免在生成图片（BitmapFactory.decodeStream）时溢出
            baos.reset();//重置baos即清空baos
            image.compress(Bitmap.CompressFormat.JPEG, 50, baos);//这里压缩50%，把压缩后的数据存放到baos中
        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        //开始读入图片，此时把options.inJustDecodeBounds 设回true了
        newOpts.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(isBm, null, newOpts);
        newOpts.inJustDecodeBounds = false;
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        float hh = 720f;
        float ww = 1280f;
        //缩放比。由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        int be = 1;//be=1表示不缩放
        if (w > h && w > ww) {//如果宽度大的话根据宽度固定大小缩放
            be = (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) {//如果高度高的话根据宽度固定大小缩放
            be = (int) (newOpts.outHeight / hh);
        }
        if (be <= 0) {
            be = 1;
        }
        newOpts.inSampleSize = be;//设置缩放比例
        newOpts.inPreferredConfig = Bitmap.Config.RGB_565;//降低图片从ARGB888到RGB565
        //重新读入图片，注意此时已经把options.inJustDecodeBounds 设回false了
        isBm = new ByteArrayInputStream(baos.toByteArray());
        bitmap = BitmapFactory.decodeStream(isBm, null, newOpts);
        return compressImage(bitmap);//压缩好比例大小后再进行质量压缩
    }

    private Bitmap compressImage(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);//质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        int options = 100;
        while (baos.toByteArray().length / 1024 > 100) {    //循环判断如果压缩后图片是否大于100kb,大于继续压缩
            baos.reset();//重置baos即清空baos
            options -= 10;//每次都减少10
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);//这里压缩options%，把压缩后的数据存放到baos中

        }
        ByteArrayInputStream isBm = new ByteArrayInputStream(
                baos.toByteArray());//把压缩后的数据baos存放到ByteArrayInputStream中
        Bitmap bitmap = BitmapFactory.decodeStream(isBm, null, null);//把ByteArrayInputStream数据生成图片
        return bitmap;
    }


    //初始化Surface
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
        initSurfaceView(surface);//这一步很重要否则，getSurfaceTexture为null 也就永远无法自动显示预览界面
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
