package com.thunder.ktv.camera2test;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    CameraManager cameraManager;
    TextureView cameraPreview;
    Surface previewSurface;
    CameraDevice cameraDevice;
    ImageReader imageReader;
    SurfaceView surfaceView;

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    Range<Integer>[] fpsRanges;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        initView();
        startBackgroundThread();

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //遍历所有摄像头
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Log.d(TAG, "onCreate: StreamConfigurationMap " + map);
                Log.d(TAG, "onCreate: getOutputSizes TextureView " + Arrays.toString(map.getOutputSizes(SurfaceTexture.class)));
                Log.d(TAG, "onCreate: INFO_SUPPORTED_HARDWARE_LEVEL " + characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));
                fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                Log.d("FPS", "SYNC_MAX_LATENCY_PER_FRAME_CONTROL: " + Arrays.toString(fpsRanges));
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        Log.d(TAG, "xxx onOpened: ");
                        Toast.makeText(getApplicationContext(),"相机打开成功",Toast.LENGTH_SHORT).show();
                        cameraDevice = camera;
                        cameraPreview.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        Log.d(TAG, "xxx onDisconnected: ");
                        Toast.makeText(getApplicationContext(),"相机断开链接",Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.d(TAG, "xxx onError: ");
                        Toast.makeText(getApplicationContext(),"相机打开失败",Toast.LENGTH_SHORT).show();
                    }
                }, mBackgroundHandler);
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private int draw2Surface(Image image ,Surface surface) {
        if (image == null)
            return -1;
        if (surface == null)
            return -2;
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byte[] buffer = new byte[byteBuffer.limit()];
        byteBuffer.get(buffer);
        final Bitmap bitmap = BitmapFactory.decodeByteArray(buffer,0,buffer.length);
        Canvas canvas = surface.lockCanvas(new Rect());
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        canvas.drawARGB(0,0,0,0);
        canvas.drawBitmap(bitmap,
                new Rect(0, 0, image.getWidth(), image.getHeight()),
                new Rect(0, 0, w, h),
                null);
        surface.unlockCanvasAndPost(canvas);
        return 0;
    }
    CaptureRequest captureRequest;
    void initView()
    {
        cameraPreview = findViewById(R.id.texture_view);
        surfaceView = findViewById(R.id.surface_view);
        cameraPreview.setVisibility(View.GONE);
        cameraPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureAvailable: wh " + width + "x" + height);
                surface.setDefaultBufferSize(width,height);
                imageReader = ImageReader.newInstance(width,height, ImageFormat.JPEG,10);

                imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireNextImage();
                        if(image == null)
                            return;
                        Log.d(TAG, "onImageAvailable: " + image.getFormat());
//                        draw2Surface(image,surfaceView.getHolder().getSurface());
                        image.close();
                    }
                },mBackgroundHandler);
                previewSurface = new Surface(surface);
                try {
                    List<Surface> list = new ArrayList();
                    list.add(previewSurface);
                    list.add(imageReader.getSurface());
                    list.add(surfaceView.getHolder().getSurface());
                    cameraDevice.createCaptureSession(list,new SessionStateCallback(),mBackgroundHandler);
                    CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.addTarget(previewSurface);
                    builder.addTarget(imageReader.getSurface());
                    builder.addTarget(surfaceView.getHolder().getSurface());
                    builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRanges[0]);
                    captureRequest = builder.build();

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureSizeChanged: ");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.d(TAG, "onSurfaceTextureDestroyed: ");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//                Log.d(TAG, "onSurfaceTextureUpdated: ");
            }
        });
    }
    private static final int RC_PERMISSION = 1;

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
        List<String> list = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                list.add(permission);
            }
        }
        String[] requestList = new String[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            requestList[i] = list.get(i);
        }
        if (requestList.length > 0) {
            ActivityCompat.requestPermissions(this, requestList, RC_PERMISSION);
        }
    }

    class SessionStateCallback extends CameraCaptureSession.StateCallback {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            try {
                session.setRepeatingRequest(
                        captureRequest,
                        new CameraCaptureSession.CaptureCallback() {},
                        mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
        }
    }
}
