package visual.camp.sample.launcher.activity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;

import camp.visual.truegaze.GazeState;
import camp.visual.truegaze.util.ViewLayoutChecker;
import visual.camp.sample.launcher.R;
import visual.camp.sample.launcher.common.CONFIG;
import visual.camp.sample.launcher.data.AppData;
import visual.camp.sample.launcher.data.AppDataManager;
import visual.camp.sample.launcher.service.direct.TrackingDirectService;
import visual.camp.sample.launcher.view.AppAdapter;
import visual.camp.sample.view.CalibrationViewer;
import visual.camp.sample.view.PointView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String[] PERMISSIONS = new String[]{
            Manifest.permission.CAMERA // 시선 추적 input
    };
    private static final int REQ_PERMISSION = 1000;
    private Class clazz = TrackingDirectService.class;
    private AppDataManager appDataManager;
    private ViewLayoutChecker viewLayoutChecker = new ViewLayoutChecker(); // view의 offset을 구하는 유틸

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        checkPermission();
        checkServiceConnection();
        registerServiceReceiver();
        registerAppReceiver();
        initAppDataManager();

        loadApplications();
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
        checkServiceConnection();
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
        appAdapter.release();
        unregisterServiceReceiver();
        unregisterAppReceiver();
        releaseAppDataManager();
    }

    // view
    private View layoutProgress;
    private PointView viewPoint;
    private Button btnStartService, btnStopService;
    private Button btnStartCalibration, btnStopCalibration;
    private RecyclerView rcyApp;
    private AppAdapter appAdapter;
    private CalibrationViewer viewCalibration;
    private void initView() {
        layoutProgress = findViewById(R.id.layout_progress);
        layoutProgress.setOnClickListener(null);

        btnStartService = findViewById(R.id.btn_start_service);
        btnStopService = findViewById(R.id.btn_stop_service);
        btnStartService.setOnClickListener(onClickListener);
        btnStopService.setOnClickListener(onClickListener);

        btnStartCalibration = findViewById(R.id.btn_start_calibration);
        btnStopCalibration = findViewById(R.id.btn_stop_calibration);
        btnStartCalibration.setOnClickListener(onClickListener);
        btnStopCalibration.setOnClickListener(onClickListener);

        rcyApp = findViewById(R.id.rcy_app);
        rcyApp.setLayoutManager(new LinearLayoutManager(this));
        appAdapter = new AppAdapter();
        appAdapter.setItemClickListener(itemClickListener);
        rcyApp.setAdapter(appAdapter);

        viewPoint = findViewById(R.id.view_point);
        viewCalibration = findViewById(R.id.view_calibration);
        setOffsetOfView();
    }

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

    private AppAdapter.ItemClickListener itemClickListener = new AppAdapter.ItemClickListener() {
        @Override
        public void onItemClick(AppData data) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(data.appInfo.packageName);

            if (intent != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
//                Rect rect = new Rect(0, 0, metrics.widthPixels / 2, metrics.heightPixels);
                Rect rect = new Rect(100, 100, 800, 800);

                ActivityOptionsCompat activityOptions = ActivityOptionsCompat.makeBasic();
                activityOptions.setLaunchBounds(rect);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // window 형태
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                } else {
                    // 24 이전은 window 형태 지원 안함
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                }
                startActivity(intent, activityOptions.toBundle());

            }
        }
    };

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
            if (v == btnStartService) {
                startService();
            } else if (v == btnStopService) {
                stopService();
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

    // service
    private BroadcastReceiver directServiceActivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                if (action.equals(CONFIG.ACTION_ON_GAZE)) {
                    float[] gazeCoord = intent.getFloatArrayExtra(CONFIG.INTENT_GAZE_COORD);
                    long timestamp = intent.getLongExtra(CONFIG.INTENT_GAZE_TIMESTAMP, -1);
                    int state = intent.getIntExtra(CONFIG.INTENT_GAZE_TYPE, -1);
                    if (gazeCoord != null) {
                        if (state != GazeState.FACE_MISSING && state != GazeState.CALIBRATING) {
                            showGazePoint(gazeCoord[0], gazeCoord[1], state);
                        }
                    }
                } else if (action.equals(CONFIG.ACTION_CALIBRATING)) {
                    final float progress = intent.getFloatExtra(CONFIG.INTENT_CALIB_PROGRESS, -1);
                    final float[] calibCoord = intent.getFloatArrayExtra(CONFIG.INTENT_CALIB_COORD);
                    if (progress != -1 && calibCoord != null) {
                        if (progress == 1) {
                            // 캘리브레이션 좌표 설정
                            setCalibrationPoint(calibCoord[0], calibCoord[1]);
                        } else {
                            // 캘리브레이션 진행도 보여줌
                            setCalibrationProgress(progress);
                        }
                    }
                } else if (action.equals(CONFIG.ACTION_CALIBRATED)) {
                    // 캘리브레이션 종료
                    hideCalibrationView();
                } else if (action.equals(CONFIG.ACTION_CAMERA_STOPPED)) {
                    // 카메라가 종료되거나 에러가 발생해 멈춘상황, 캘리브레이션 도중 발생할수도 있어서 추가됨
                    hideCalibrationView();
                }
            }
        }
    };

    private void registerServiceReceiver() {
        IntentFilter intentFilter = new IntentFilter(CONFIG.RECEIVER_GAZE_INFO);
        intentFilter.addAction(CONFIG.ACTION_ON_GAZE);
        intentFilter.addAction(CONFIG.ACTION_CALIBRATING);
        intentFilter.addAction(CONFIG.ACTION_CALIBRATED);
        intentFilter.addAction(CONFIG.ACTION_CAMERA_STOPPED);
        registerReceiver(directServiceActivityReceiver, intentFilter);
    }

    private void unregisterServiceReceiver() {
        unregisterReceiver(directServiceActivityReceiver);
    }

    private void startService() {
        Log.i(TAG, "startService ");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), clazz));
        } else {
            startService(new Intent(getApplicationContext(), clazz));
        }
        checkServiceConnection();
    }

    private void stopService() {
        Log.i(TAG, "stopService ");
        stopService(new Intent(getApplicationContext(), clazz));
        checkServiceConnection();
        hideCalibrationView();
    }

    private void startCalibration() {
        Intent calibIntent = new Intent(CONFIG.RECEIVER_DIRECT_SERVICE);
        calibIntent.setAction(CONFIG.ACTION_START_CALIBRATION);
        sendBroadcast(calibIntent);
    }

    private void stopCalibration() {
        Intent calibIntent = new Intent(CONFIG.RECEIVER_DIRECT_SERVICE);
        calibIntent.setAction(CONFIG.ACTION_STOP_CALIBRATION);
        sendBroadcast(calibIntent);
        hideCalibrationView();
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (clazz.getName().equals(service.service.getClassName()))
            {
                return true;
            }
        }
        return false;
    }

    private void checkServiceConnection() {
        btnStartService.setEnabled(!isServiceRunning());
        btnStopService.setEnabled(isServiceRunning());
        btnStartCalibration.setEnabled(isServiceRunning());
        btnStopCalibration.setEnabled(isServiceRunning());
    }
    // service end

    // launcher
    private AppDataManager.DataListener dataListener = new AppDataManager.DataListener() {
        @Override
        public void onDataLoaded(List<AppData> dataList) {
            hideProgress();
            appAdapter.loadAppInfoList(dataList);
        }
    };
    private void initAppDataManager() {
        appDataManager = new AppDataManager(getApplicationContext());
        appDataManager.setDataListener(dataListener);
    }

    private void releaseAppDataManager() {
        appDataManager.release();
        appDataManager = null;
    }

    private BroadcastReceiver appInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
                    Log.i(TAG, "chk app info receive ACTION_PACKAGE_ADDED");
                } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_CHANGED)) {
                    Log.i(TAG, "chk app info receive ACTION_PACKAGE_CHANGED");
                } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                    Log.i(TAG, "chk app info receive ACTION_PACKAGE_REMOVED");
                } else if (intent.getAction().equals(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)) {
                    Log.i(TAG, "chk app info receive ACTION_EXTERNAL_APPLICATIONS_AVAILABLE");
                } else if (intent.getAction().equals(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)) {
                    Log.i(TAG, "chk app info receive ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE");
                }
            }
            loadApplications();
        }
    };

    void registerAppReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(appInfoReceiver, filter);

        // Register for events related to sdcard installation.
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        registerReceiver(appInfoReceiver, sdFilter);
    }

    void unregisterAppReceiver() {
        unregisterReceiver(appInfoReceiver);
    }


    void loadApplications() {
        Log.i(TAG, "loadApplication");
        if (appDataManager != null) {
            showProgress();
            appDataManager.loadAppData();
        }
    }
    // launcher end
}
