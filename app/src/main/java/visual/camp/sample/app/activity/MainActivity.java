package visual.camp.sample.app.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;

import camp.visual.truegaze.GazeState;
import camp.visual.truegaze.TrueGaze;
import camp.visual.truegaze.callback.CalibrationCallback;
import camp.visual.truegaze.callback.GazeCallback;
import camp.visual.truegaze.callback.LifeCallback;
import camp.visual.truegaze.util.ViewLayoutChecker;
import visual.camp.sample.app.R;
import visual.camp.sample.app.device.GazeDevice;
import visual.camp.sample.view.CalibrationViewer;
import visual.camp.sample.view.PointView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String[] PERMISSIONS = new String[]{
            Manifest.permission.CAMERA // 시선 추적 input
    };
    private static final int REQ_PERMISSION = 1000;
    private TrueGaze trueGaze;
    private ViewLayoutChecker viewLayoutChecker = new ViewLayoutChecker();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        checkPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        // 화면 전환후에도 체크하기 위해
        setOffsetOfView();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewLayoutChecker.releaseChecker();
        releaseGaze();
    }

    // permission
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //퍼미션 상태 확인
            if (!hasPermissions(PERMISSIONS)) {

                requestPermissions(PERMISSIONS, REQ_PERMISSION);
            } else {
                checkPermission(true);
            }
        }else{
            checkPermission(true);
        }
    }
    @RequiresApi(Build.VERSION_CODES.M)
    private boolean hasPermissions(String[] permissions) {
        int result;
        //스트링 배열에 있는 퍼미션들의 허가 상태 여부 확인
        for (String perms : permissions) {
            if (perms.equals(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                if (!Settings.canDrawOverlays(this)) {
                    return false;
                }
            }
            result = ContextCompat.checkSelfPermission(this, perms);
            if (result == PackageManager.PERMISSION_DENIED) {
                //허가 안된 퍼미션 발견
                return false;
            }
        }
        //모든 퍼미션이 허가되었음
        return true;
    }

    private void checkPermission(boolean isGranted) {
        if (isGranted) {
            permissionGranted();
        } else {
            showToast("not granted permissions", true);
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQ_PERMISSION:
                if (grantResults.length > 0) {
                    boolean cameraPermissionAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if (cameraPermissionAccepted) {
                        checkPermission(true);
                    } else {
                        checkPermission(false);
                    }
                }
                break;
        }
    }

    private void permissionGranted() {

    }
    // permission end

    // view
    private TextureView preview;
    private View layoutProgress;
    private PointView viewPoint;
    private Button btnInitGaze, btnReleaseGaze;
    private Button btnStartTracking, btnStopTracking;
    private Button btnStartCalibration, btnStopCalibration;
    private CalibrationViewer viewCalibration;
    private void initView() {
        layoutProgress = findViewById(R.id.layout_progress);
        layoutProgress.setOnClickListener(null);

        preview = findViewById(R.id.preview);
        preview.setSurfaceTextureListener(surfaceTextureListener);

        btnInitGaze = findViewById(R.id.btn_init_gaze);
        btnReleaseGaze = findViewById(R.id.btn_release_gaze);
        btnInitGaze.setOnClickListener(onClickListener);
        btnStopTracking.setOnClickListener(onClickListener);

        btnStartTracking = findViewById(R.id.btn_start_tracking);
        btnStopTracking = findViewById(R.id.btn_stop_tracking);
        btnStartTracking.setOnClickListener(onClickListener);
        btnStopTracking.setOnClickListener(onClickListener);

        btnStartCalibration = findViewById(R.id.btn_start_calibration);
        btnStopCalibration = findViewById(R.id.btn_stop_calibration);
        btnStartCalibration.setOnClickListener(onClickListener);
        btnStopCalibration.setOnClickListener(onClickListener);

        viewPoint = findViewById(R.id.view_point);
        viewCalibration = findViewById(R.id.view_calibration);
        setOffsetOfView();
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // textureView가 사용가능할때만 설정 가능

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
    };

    // 시선 좌표나 캘리브레이션 좌표는 전체 스크린 좌표로만 전달되는데
    // 안드로이드 스크린 좌표계는 액션바, 상태바, 네비게이션바를 고려안한 좌표계라
    // 이 offset을 구해 보정해줘야 제대로 스크린에 정보를 보여줄수 있음
    private void setOffsetOfView() {
        viewLayoutChecker.setOverlayView(viewPoint, new ViewLayoutChecker.ViewLayoutListener() {
            @Override
            public void getOffset(int x, int y) {
                viewPoint.setOffset(x, y);
                viewCalibration.setOffset(x, y);
            }
        });
    }

    private void showProgress() {
        if (layoutProgress != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    layoutProgress.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void hideProgress() {
        if (layoutProgress != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    layoutProgress.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v == btnInitGaze) {
                initGaze();
            } else if (v == btnReleaseGaze) {
                releaseGaze();
            } else if (v == btnStartTracking) {
                startTracking();
            } else if (v == btnStopTracking) {
                stopTracking();
            } else if (v == btnStartCalibration) {
                startCalibration();
            } else if (v == btnStopCalibration) {
                stopCalibration();
            }
        }
    };

    private void showToast(final String msg, final boolean isShort) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, isShort ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showGazePoint(final float x, final float y, final int type) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewPoint.setType(type == GazeState.TRACKING ? PointView.TYPE_DEFAULT : PointView.TYPE_OUT_OF_SCREEN);
                viewPoint.setPosition(x, y);
            }
        });
    }

    private void setCalibrationPoint(final float x, final float y) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewCalibration.setVisibility(View.VISIBLE);
                viewCalibration.changeDraw(true, null);
                viewCalibration.setPointPosition(x, y);
                viewCalibration.setPointAnimationPower(0);
            }
        });
    }

    private void setCalibrationProgress(final float progress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewCalibration.setPointAnimationPower(progress);
            }
        });
    }

    private void hideCalibrationView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewCalibration.setVisibility(View.INVISIBLE);
            }
        });
    }

    // view end

    // trueGaze
    private boolean isTracking() {
        if (isGazeNonNull()) {
            return trueGaze.isTracking();
        }
        return false;
    }
    private boolean isGazeNonNull() {
        return trueGaze != null;
    }

    private LifeCallback lifeCallback = new LifeCallback() {
        @Override
        public void onInitialized() {
            if (preview.isAvailable()) {
                trueGaze.setDisplay(preview);
            }
            startTracking();
        }

        @Override
        public void onInitializeFailed(int i) {
            trueGaze = null;
        }

        @Override
        public void onCameraError() {

        }
    };
    private GazeCallback gazeCallback = new GazeCallback() {
        @Override
        public void onGaze(long timestamp, float x, float y, int state) {

        }
    };
    private CalibrationCallback calibrationCallback = new CalibrationCallback() {
        @Override
        public void onCalibrationProcess(float progress, float x, float y) {

        }

        @Override
        public void onCalibrationFinished() {

        }
    };

    private void initGaze() {
        PointF screenOrigin = GazeDevice.getDeviceScreenOrigin(Build.MODEL);
        trueGaze = new TrueGaze(getApplicationContext(), screenOrigin, lifeCallback, gazeCallback, calibrationCallback);
    }

    private void releaseGaze() {

    }

    private void startTracking() {

    }

    private void stopTracking() {

    }

    private void startCalibration() {

    }

    private void stopCalibration() {

    }
}
