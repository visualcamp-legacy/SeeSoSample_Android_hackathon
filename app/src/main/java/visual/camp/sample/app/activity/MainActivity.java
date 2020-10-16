package visual.camp.sample.app.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import camp.visual.gazetracker.GazeTracker;
import camp.visual.gazetracker.callback.CalibrationCallback;
import camp.visual.gazetracker.callback.GazeCallback;
import camp.visual.gazetracker.callback.InitializationCallback;
import camp.visual.gazetracker.callback.StatusCallback;
import camp.visual.gazetracker.constant.CalibrationModeType;
import camp.visual.gazetracker.constant.InitializationErrorType;
import camp.visual.gazetracker.constant.StatusErrorType;
import camp.visual.gazetracker.device.GazeDevice;
import camp.visual.gazetracker.gaze.GazeInfo;
import camp.visual.gazetracker.state.DeviceState;
import camp.visual.gazetracker.state.TrackingState;
import camp.visual.gazetracker.util.ViewLayoutChecker;
import visual.camp.sample.app.R;
import visual.camp.sample.app.calibration.CalibrationDataStorage;
import visual.camp.sample.view.CalibrationViewer;
import visual.camp.sample.view.PointView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String[] PERMISSIONS = new String[]{
            Manifest.permission.CAMERA // 시선 추적 input
    };
    private static final int REQ_PERMISSION = 1000;
    private GazeTracker gazeTracker;
    private ViewLayoutChecker viewLayoutChecker = new ViewLayoutChecker();
    private HandlerThread backgroundThread = new HandlerThread("background");
    private Handler backgroundHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "gazeTracker version: " + GazeTracker.getVersionName());

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
    private View viewWarningFaceMissing;
    private View viewDeviceState;
    private PointView viewPoint;
    private Button btnInitGaze, btnReleaseGaze;
    private Button btnStartTracking, btnStopTracking;
    private Button btnStartCalibration, btnStopCalibration, btnSetCalibration;
    private CalibrationViewer viewCalibration;

    // 시선 좌표 필터 관련
    private SwitchCompat swUseGazeFilter;
    private boolean isUseGazeFilter = true;
    // 캘리브레이션 방식 관련
    private RadioGroup rgCalibration;
    private CalibrationModeType calibrationType = CalibrationModeType.DEFAULT;

    private AppCompatTextView txtGazeVersion;
    private void initView() {
        txtGazeVersion = findViewById(R.id.txt_gaze_version);
        txtGazeVersion.setText("version: " + GazeTracker.getVersionName());

        layoutProgress = findViewById(R.id.layout_progress);
        layoutProgress.setOnClickListener(null);

        viewWarningFaceMissing = findViewById(R.id.view_warning_face_missing);
        viewDeviceState = findViewById(R.id.view_device_state);

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

        btnSetCalibration = findViewById(R.id.btn_set_calibration);
        btnSetCalibration.setOnClickListener(onClickListener);

        viewPoint = findViewById(R.id.view_point);
        viewCalibration = findViewById(R.id.view_calibration);

        swUseGazeFilter = findViewById(R.id.sw_use_gaze_filter);
        rgCalibration = findViewById(R.id.rg_calibration);

        swUseGazeFilter.setChecked(isUseGazeFilter);
        RadioButton rbCalibrationOne = findViewById(R.id.rb_calibration_one);
        RadioButton rbCalibrationFive = findViewById(R.id.rb_calibration_five);
        if (calibrationType == CalibrationModeType.ONE_POINT) {
            rbCalibrationOne.setChecked(true);
        } else {
            // default, five_point는 5점
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
                    calibrationType = CalibrationModeType.ONE_POINT;
                } else if (checkedId == R.id.rb_calibration_five) {
                    calibrationType = CalibrationModeType.FIVE_POINT;
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
            setCameraPreview(preview);
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

    // 시선 좌표나 캘리브레이션 좌표는 전체 스크린 화면인 절대 좌표로만 전달되는데
    // 안드로이드 뷰의 좌표계는 액션바, 상태바, 네비게이션바를 고려안한 상대 좌표계라
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

    private void showFaceMissingWarning() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewWarningFaceMissing.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideFaceMissingWarning() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewWarningFaceMissing.setVisibility(View.INVISIBLE);
            }
        });
    }

    private static final int COLOR_DEVICE_STABLE = Color.argb(0, 0, 0, 0);
    private static final int COLOR_DEVICE_SHAKING = Color.argb(0x80, 0xff, 0, 0);
    private static final int COLOR_DEVICE_LYING = Color.argb(0x80, 0, 0, 0xff);
    private void showDeviceState(final DeviceState deviceState) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (deviceState) {
                    case SHAKING:
                        // 기기가 흔들릴때
                        viewDeviceState.setBackgroundColor(COLOR_DEVICE_SHAKING);
                        break;
                    case LYING:
                        // 기기가 누워있을때
                        viewDeviceState.setBackgroundColor(COLOR_DEVICE_LYING);
                        break;
                    case STABLE:
                        viewDeviceState.setBackgroundColor(COLOR_DEVICE_STABLE);
                        break;

                }
            }
        });
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
            } else if (v == btnSetCalibration) {
                setCalibration();
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

    private void showGazePoint(final float x, final float y, final TrackingState type) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                viewPoint.setType(type == TrackingState.TRACKING ? PointView.TYPE_DEFAULT : PointView.TYPE_OUT_OF_SCREEN);
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

    // gazeTracker와 Tracking 상태에 따라 뷰 변경
    private void setViewAtGazeTrackerState() {
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
                btnSetCalibration.setEnabled(isGazeNonNull());
                if (!isTracking()) {
                    hideCalibrationView();
                }
            }
        });
    }

    // view end

    // gazeTracker
    private boolean isTracking() {
        if (isGazeNonNull()) {
            return gazeTracker.isTracking();
        }
        return false;
    }
    private boolean isGazeNonNull() {
        return gazeTracker != null;
    }

    private InitializationCallback initializationCallback = new InitializationCallback() {
        @Override
        public void onInitialized(GazeTracker gazeTracker, InitializationErrorType error) {
            if (gazeTracker != null) {
                initSuccess(gazeTracker);
            } else {
                initFail(error);
            }
        }
    };

    private void initSuccess(GazeTracker gazeTracker) {
        this.gazeTracker = gazeTracker;
        if (preview.isAvailable()) {
            setCameraPreview(preview);
            this.gazeTracker.setCallbacks(gazeCallback, calibrationCallback, statusCallback);
        }
        startTracking();
        hideProgress();
    }

    private void initFail(InitializationErrorType error) {
        String err = "";
        if (error == InitializationErrorType.ERROR_CAMERA_PERMISSION) {
            // 카메라 퍼미션이 없는 경우
            err = "required permission not granted";
        } else if (error == InitializationErrorType.ERROR_AUTHENTICATE) {
            // 인증 실패
            err = "authentication failed";
        } else  {
            // gaze library 초기화 실패(메모리 부족등의 이유로 초기화 실패)
            err = "init gaze library fail";
        }
        showToast(err, false);
        Log.w(TAG, "error description: " + err);
        hideProgress();
    }

    private GazeCallback gazeCallback = new GazeCallback() {
        @Override
        public void onGaze(GazeInfo gazeInfo) {
            TrackingState state = gazeInfo.trackingState;
            if (state != TrackingState.FACE_MISSING) {
                hideFaceMissingWarning();
                if (!gazeTracker.isCalibrating()) {
                    if (isUseGazeFilter) {
                        showGazePoint(gazeInfo.filteredX, gazeInfo.filteredY, state);
                    } else {
                        showGazePoint(gazeInfo.x, gazeInfo.y, state);
                    }
                }
            } else {
                showFaceMissingWarning();
            }
            showDeviceState(gazeInfo.deviceState);
            Log.i(TAG, "check eyeMovement duration: " + gazeInfo.eyeMovementDuration + " (" + gazeInfo.eyeMovementX + "x" + gazeInfo.eyeMovementY + ") : " + gazeInfo.eyeMovementState);
        }
    };
    private CalibrationCallback calibrationCallback = new CalibrationCallback() {
        @Override
        public void onCalibrationProgress(float progress) {
            setCalibrationProgress(progress);
        }

        @Override
        public void onCalibrationNextPoint(final float x, final float y) {
            setCalibrationPoint(x, y);
            // 캘리브레이션 좌표가 설정된후 1초간 대기한후 샘플을 수집, 눈이 좌표를 찾고나서 캘리브레이션을 진행해야함
            backgroundHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startCollectSamples();
                }
            }, 1000);
        }

        @Override
        public void onCalibrationFinished(double[] calibrationData) {
            // 캘리브레이션이 완료되면 캘리브레이션 데이터를 SharedPreference에 저장함
            CalibrationDataStorage.saveCalibrationData(getApplicationContext(), calibrationData);
            hideCalibrationView();
        }
    };

    private StatusCallback statusCallback = new StatusCallback() {
        @Override
        public void onStarted() {
            // isTracking true
            // 카메라 스트림이 시작될때 호출
            setViewAtGazeTrackerState();
        }

        @Override
        public void onStopped(StatusErrorType error) {
            // isTracking false
            // 카메라 스트림이 중단될때 호출
            setViewAtGazeTrackerState();
            if (error != StatusErrorType.ERROR_NONE) {
                switch (error) {
                    case ERROR_CAMERA_START:
                        // 카메라 스트림이 시작하지 못할때
                        showToast("ERROR_CAMERA_START ", false);
                        break;
                    case ERROR_CAMERA_INTERRUPT:
                        // 카메라 포커스를 빼앗길때
                        showToast("ERROR_CAMERA_INTERRUPT ", false);
                        break;
                }
            }
        }
    };

    private void initGaze() {
        showProgress();
        GazeDevice gazeDevice = new GazeDevice();
        // todo 라이센스 키 변경 필요
        /*
        local license key
        X4qHkMW8JCF1m2kOt3JtWIDKOAG1ojPkUUG2N2DqnUzTuvpHiDlnEz8pBBWcMKuow86bVV1MA_yXPqeiopjZ9g4HOr07CCXIGrPlWKsllWmlRGW6cNAQ86XX9Dr8NgYP3i-XLF5x2fYS19z4wIPt79FjhARCmE4OmGbq1RhK3sy=

        server license key
        dev_r6lhgvzp6c9qrujmix67580y3r207itavw0fonmf
         */

        String licenseKey = "X4qHkMW8JCF1m2kOt3JtWIDKOAG1ojPkUUG2N2DqnUzTuvpHiDlnEz8pBBWcMKuow86bVV1MA_yXPqeiopjZ9g4HOr07CCXIGrPlWKsllWmlRGW6cNAQ86XX9Dr8NgYP3i-XLF5x2fYS19z4wIPt79FjhARCmE4OmGbq1RhK3sy=";
        GazeTracker.initGazeTracker(getApplicationContext(), gazeDevice, licenseKey, initializationCallback);
    }

    private void releaseGaze() {
        if (isGazeNonNull()) {
            GazeTracker.deinitGazeTracker(gazeTracker);
            gazeTracker = null;
        }
        setViewAtGazeTrackerState();
    }

    private void startTracking() {
        if (isGazeNonNull()) {
            gazeTracker.startTracking();
        }
    }

    private void stopTracking() {
        if (isGazeNonNull()) {
            gazeTracker.stopTracking();
        }
    }

    private boolean startCalibration() {
        boolean isSuccess = false;
        if (isGazeNonNull()) {
            isSuccess = gazeTracker.startCalibration(calibrationType);
        }
        setViewAtGazeTrackerState();
        return isSuccess;
    }

    // 캘리브레이션에 사용되는 샘플을 수집
    private boolean startCollectSamples() {
        boolean isSuccess = false;
        if (isGazeNonNull()) {
            isSuccess = gazeTracker.startCollectSamples();
        }
        setViewAtGazeTrackerState();
        return isSuccess;
    }

    private void stopCalibration() {
        if (isGazeNonNull()) {
            gazeTracker.stopCalibration();
        }
        hideCalibrationView();
        setViewAtGazeTrackerState();
    }

    private void setCalibration() {
        if (isGazeNonNull()) {
            double[] calibrationData = CalibrationDataStorage.loadCalibrationData(getApplicationContext());
            if (calibrationData != null) {
                // 저장한 데이터가 있을때
                if (!gazeTracker.setCalibrationData(calibrationData)) {
                    // 캘리브레이션 도중 데이터를 설정하면 false를 리턴하며 데이터를 설정하지 않음
                    showToast("calibrating", false);
                } else {
                    // 캘리브레이션 데이터 설정 성공
                    showToast("setCalibrationData success", false);
                }
            } else {
                // 저장한 데이터가 없을때
                showToast("Calibration data is null", true);
            }
        }
        setViewAtGazeTrackerState();
    }

    private void setCameraPreview(TextureView preview) {
        if (isGazeNonNull()) {
            gazeTracker.setCameraPreview(preview);
        }
    }

    // deinitGazeTracker를 호출하면 gazeTracker 내부에 남아있던 프리뷰를 지움, 프리뷰만 제거하려면 removeCameraPreview를 사용
    private void removeCameraPreview() {
        if (isGazeNonNull()) {
            gazeTracker.removeCameraPreview();
        }
    }
}
