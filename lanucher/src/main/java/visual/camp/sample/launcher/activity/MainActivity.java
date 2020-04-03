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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;

import visual.camp.sample.launcher.R;
import visual.camp.sample.launcher.common.CONFIG;
import visual.camp.sample.launcher.data.AppData;
import visual.camp.sample.launcher.data.AppDataManager;
import visual.camp.sample.launcher.service.direct.TrackingDirectService;
import visual.camp.sample.launcher.view.AppAdapter;
import visual.camp.sample.launcher.view.PointView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String[] PERMISSIONS = new String[]{
            Manifest.permission.CAMERA, // 시선 추적 input
            Manifest.permission.SYSTEM_ALERT_WINDOW
    };
    private static final int REQ_PERMISSION = 1000;
    private static final int REQ_OVERLAY_PERMISSION = 1001;
    private Class clazz = TrackingDirectService.class;
    private AppDataManager appDataManager;

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
        appAdapter.release();
        unregisterServiceReceiver();
        unregisterAppReceiver();
        releaseAppDataManager();
    }

    // view
    private View layoutProgress;
    private PointView viewPoint;
    private Button btnStartService, btnStopService;
    private RecyclerView rcyApp;
    private AppAdapter appAdapter;
    private void initView() {
        layoutProgress = findViewById(R.id.layout_progress);
        layoutProgress.setOnClickListener(null);

        btnStartService = findViewById(R.id.btn_start_service);
        btnStopService = findViewById(R.id.btn_stop_service);
        btnStartService.setOnClickListener(onClickListener);
        btnStopService.setOnClickListener(onClickListener);

        rcyApp = findViewById(R.id.rcy_app);
        rcyApp.setLayoutManager(new LinearLayoutManager(this));
        appAdapter = new AppAdapter();
        appAdapter.setItemClickListener(itemClickListener);
        rcyApp.setAdapter(appAdapter);

        viewPoint = findViewById(R.id.view_point);
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
    // view end

    // permission
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //퍼미션 상태 확인
            if (!hasPermissions(PERMISSIONS)) {

                //퍼미션 허가 안되어있다면 사용자에게 요청
                if (!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
                }
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
                        viewPoint.setPosition(gazeCoord[0], gazeCoord[1]);
                    }
                }
            }
        }
    };

    private void registerServiceReceiver() {
        IntentFilter intentFilter = new IntentFilter(CONFIG.RECEIVER_GAZE_INFO);
        intentFilter.addAction(CONFIG.ACTION_ON_GAZE);
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
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        // deprecate되었어도 자신의 서비스만은 가져올수 있음, 본래는 원격 서비스도 되던건가? 다른 대안은 없는듯 그냥 써야할듯
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
