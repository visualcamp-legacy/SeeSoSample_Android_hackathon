package visual.camp.sample.app.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import camp.visual.truegaze.TrueGaze;
import camp.visual.truegaze.callback.CalibrationCallback;
import camp.visual.truegaze.callback.EyeMovementCallback;
import camp.visual.truegaze.callback.GazeCallback;
import camp.visual.truegaze.callback.LifeCallback;
import camp.visual.truegaze.device.GazeDevice;
import camp.visual.truegaze.state.EyeMovementState;
import camp.visual.truegaze.state.GazeState;
import camp.visual.truegaze.util.ViewLayoutChecker;
import visual.camp.sample.app.R;
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
    private HandlerThread backgroundThread = new HandlerThread("background");
    private Handler backgroundHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        checkPermission();
        initHandler();
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
        releaseHandler();
        viewLayoutChecker.releaseChecker();
        releaseGaze();
    }

    // handler

    private void initHandler() {
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void releaseHandler() {
        backgroundThread.quitSafely();
    }

    // handler end

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
        initGaze();
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

    // 시선 좌표 필터 관련
    private SwitchCompat swUseGazeFilter;
    private boolean isUseGazeFilter = true;
    // 캘리브레이션 방식 관련
    private RadioGroup rgCalibration;
    private static final int CALIBRATION_ONE_POINT = 1;
    private static final int CALIBRATION_FIVE_POINTS = 2;
    private int calibrationType = CALIBRATION_ONE_POINT;
    private void initView() {
        layoutProgress = findViewById(R.id.layout_progress);
        layoutProgress.setOnClickListener(null);

        preview = findViewById(R.id.preview);
        preview.setSurfaceTextureListener(surfaceTextureListener);

        btnInitGaze = findViewById(R.id.btn_init_gaze);
        btnReleaseGaze = findViewById(R.id.btn_release_gaze);
        btnInitGaze.setOnClickListener(onClickListener);
        btnReleaseGaze.setOnClickListener(onClickListener);

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

        swUseGazeFilter = findViewById(R.id.sw_use_gaze_filter);
        rgCalibration = findViewById(R.id.rg_calibration);

        swUseGazeFilter.setChecked(isUseGazeFilter);
        RadioButton rbCalibrationOne = findViewById(R.id.rb_calibration_one);
        RadioButton rbCalibrationFive = findViewById(R.id.rb_calibration_five);
        if (calibrationType == CALIBRATION_ONE_POINT) {
            rbCalibrationOne.setChecked(true);
        } else {
            rbCalibrationFive.setChecked(true);
        }

        swUseGazeFilter.setOnCheckedChangeListener(onCheckedChangeSwitch);
        rgCalibration.setOnCheckedChangeListener(onCheckedChangeRadioButton);
        setOffsetOfView();
    }

    private RadioGroup.OnCheckedChangeListener onCheckedChangeRadioButton = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (group == rgCalibration) {
                if (checkedId == R.id.rb_calibration_one) {
                    calibrationType = CALIBRATION_ONE_POINT;
                } else if (checkedId == R.id.rb_calibration_five) {
                    calibrationType = CALIBRATION_FIVE_POINTS;
                }
            }
        }
    };
    private SwitchCompat.OnCheckedChangeListener onCheckedChangeSwitch = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (buttonView == swUseGazeFilter) {
                isUseGazeFilter = isChecked;
            }
        }
    };

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // textureView가 사용가능할때만 설정 가능
            if (isGazeNonNull()) {
                trueGaze.setDisplay(preview);
            }
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

    private void setView() {
        Log.i(TAG, "gaze : " + isGazeNonNull() + ", tracking " + isTracking());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnInitGaze.setEnabled(!isGazeNonNull());
                btnReleaseGaze.setEnabled(isGazeNonNull());
                btnStartTracking.setEnabled(isGazeNonNull() && !isTracking());
                btnStopTracking.setEnabled(isGazeNonNull() && isTracking());
                btnStartCalibration.setEnabled(isGazeNonNull() && isTracking());
                btnStopCalibration.setEnabled(isGazeNonNull() && isTracking());
                if (!isTracking()) {
                    hideCalibrationView();
                }
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
            hideProgress();
        }

        @Override
        public void onInitializeFailed(int i) {
            String err = "";
            if (i == TrueGaze.ERROR_PERMISSION) {
                // 카메라 퍼미션이 없는 경우
                err = "required permission not granted";
            } else if (i == TrueGaze.ERROR_AUTHENTICATE) {
                // 인증 실패
                err = "authentication failed";
            } else  {
                // gaze library 초기화 실패(메모리 부족등의 이유로 초기화 실패)
                err = "init gaze library fail";
            }
            Log.w(TAG, "error description: " + err);
            trueGaze = null;
            hideProgress();
        }

        @Override
        public void onCameraClose(boolean b) {
            setView();
        }

    };
    private GazeCallback gazeCallback = new GazeCallback() {
        @Override
        public void onGaze(long timestamp, float x, float y, int state) {
            if (!isUseGazeFilter) {
                if (state != GazeState.FACE_MISSING && state != GazeState.CALIBRATING) {
                    showGazePoint(x, y, state);
                }
            }
        }

        @Override
        public void onFilteredGaze(long timestamp, float x, float y, int state) {
            if (isUseGazeFilter) {
                if (state != GazeState.CALIBRATING) {
                    showGazePoint(x, y, state);
                }
            }
        }
    };
    private CalibrationCallback calibrationCallback = new CalibrationCallback() {
        @Override
        public void onCalibrationProcess(float progress, float x, float y) {
            if (progress == 1) {
                setCalibrationPoint(x, y);
                // 캘리브레이션 좌표가 설정된후 1초간 대기한후 샘플을 수집, 눈이 좌표를 찾고나서 캘리브레이션을 진행해야함
                backgroundHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startCollectSamples();
                    }
                }, 1000);
            } else {
                setCalibrationProgress(progress);
            }
        }

        @Override
        public void onCalibrationFinished() {
            hideCalibrationView();
        }
    };

    private EyeMovementCallback eyeMovementCallback = new EyeMovementCallback() {
        @Override
        public void onEyeMovement(long timestamp, float x, float y, int state) {
            String type = "UNKNOWN";
            if (state == EyeMovementState.FIXATION) {
                type = "FIXATION";
            } else if (state == EyeMovementState.SACCADE) {
                type = "SACCADE";
            } else {
                type = "UNKNOWN";
            }
            Log.i(TAG, "check eyeMovement timestamp: " + timestamp + " (" + x + "x" + y + ") : " + type);
        }
    };

    private void initGaze() {
        showProgress();
        GazeDevice gazeDevice = new GazeDevice();
        // todo 라이센스 키 변경 필요
        String licenseKey = "dev_1o9b6j3w8e0py27a95p8yfz85n3u6snyu2j04m3x";
        trueGaze = new TrueGaze(getApplicationContext(), gazeDevice, licenseKey, lifeCallback, gazeCallback, calibrationCallback, eyeMovementCallback);
    }

    private void releaseGaze() {
        if (isGazeNonNull()) {
            trueGaze.release();
            trueGaze = null;
        }
        setView();
    }

    private void startTracking() {
        if (isGazeNonNull()) {
            trueGaze.startTracking();
        }
        setView();
    }

    private void stopTracking() {
        if (isGazeNonNull()) {
            trueGaze.stopTracking();
        }
        setView();
    }

    // 5점 캘리브레이션 시작
    private void startCalibration() {
        if (isGazeNonNull()) {
            if (calibrationType == CALIBRATION_ONE_POINT) {
                startOnePointCalibration();
            } else {
                startFivePointCalibration();
            }
        }
        setView();
    }

    private void startFivePointCalibration() {
        if (isGazeNonNull()) {
            trueGaze.startCalibrationInWholeScreen();
        }
    }

    // 1점 캘리브레이션 시작
    private void startOnePointCalibration() {
        if (isGazeNonNull()) {
            trueGaze.startOnePointCalibrationInWholeScreen();
        }
    }

    // 캘리브레이션에 사용되는 샘플을 수집
    private void startCollectSamples() {
        if (isGazeNonNull()) {
            trueGaze.startCollectSamples();
        }
        setView();
    }

    private void stopCalibration() {
        if (isGazeNonNull()) {
            trueGaze.stopCalibration();
        }
        hideCalibrationView();
        setView();
    }
}
