package visual.camp.sample.launcher.service;

import android.Manifest;
import android.Manifest.permission;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import visual.camp.sample.launcher.R;
import visual.camp.sample.launcher.service.inter.TrackingServiceInterface;
import visual.camp.vmex.SCREEN_ORIENTATION;
import visual.camp.vmex.VMEX;
import visual.camp.vmex.callback.GazeCallback;
import visual.camp.vmex.callback.VMEXCallback;

// 일단 구버전으로
abstract public class TrackingService extends Service {
    private static final String TAG = TrackingService.class.getSimpleName();

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    protected VMEX vmex;
    private WindowManager windowManager;

    private TrackingServiceInterface trackingServiceInterface;

    private HandlerThread notificationThread = new HandlerThread("notiView");
    private Handler notificationHandler;
    private static final int MSG_INFO = 1;
    private static final long TIME_INFO = 1000;

    // life cycle
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        windowManager = (WindowManager)getApplicationContext().getSystemService(WINDOW_SERVICE);
        trackingServiceInterface = getTrackingServiceInterface();
        startNotification();
        if (!checkNotificationEnable()) {
            showToast("Notification disable", true);
            // 노티피케이션 설정이 안되어있으면 서비스 초기화시 설정화면 호출
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                showNotificationSetting();
            }
        }
        notificationThread.start();
        notificationHandler = new Handler(notificationThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case MSG_INFO:
                        updateNotification();
                        sendEmptyMessageDelayed(MSG_INFO, TIME_INFO);
                        break;
                }
            }
        };
        notificationHandler.sendEmptyMessageDelayed(MSG_INFO, TIME_INFO);

        if (checkPermission()) {
            initVMEX();
        }
    }

    abstract protected TrackingServiceInterface getTrackingServiceInterface();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        removeNotification();
        releaseVMEX();
        notificationThread.quitSafely();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Nullable
    @Override
    abstract public IBinder onBind(Intent intent);

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }
    // life cycle end

    // permission

    private boolean checkPermission() {
        return isGrantedPermissions(
                permission.SYSTEM_ALERT_WINDOW,
                permission.CAMERA);
    }

    private boolean isGrantedPermissions(String... permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        int result;
        for (String p : permissions) {
            // SYSTEM_ALERT_WINDOW는 따로 다뤄야함
            if (p.equals(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                if (!Settings.canDrawOverlays(getApplicationContext())) {
                    return false;
                }
            } else {
                result = ContextCompat.checkSelfPermission(getApplicationContext(), p);
                if (result == PackageManager.PERMISSION_DENIED) {
                    return false;
                }
            }
        }
        return true;
    }
    // permission end

    private SCREEN_ORIENTATION getOrientation() {
        // other => gal tab s4
        // portrait => landscape_r
        // landscape => portrait
        // portrait_r => landscape
        // landscape_r => portrait_r
        // 이미 릴리즈 되어 windowManager가 null이 된 상태에서 openCamera가 늦게 호출되어 이게 호출되면 에러가 발생하는데, 일단 그런경우 카메라가 열려있으면 닫는걸로 처리함

        // portrait: 0, portrait_r: 1, landscape: 2, landscape_r: 3
        final Display display = windowManager.getDefaultDisplay();
        final int rotation = display.getRotation();
        final Point size = new Point();
        display.getSize(size);
        Log.d(TAG, "chk orientation displaySize: " + size.x + "x" + size.y + ", rotation: " + rotation);
        int result;
        if (rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) {
            // if rotation is 0 or 180 and width is greater than height, we have
            // a tablet
            if (size.x > size.y) {
                if (rotation == Surface.ROTATION_0) {
                    return SCREEN_ORIENTATION.LANDSCAPE;
                } else {
                    return SCREEN_ORIENTATION.LANDSCAPE_R;
                }
            } else {
                // we have a phone
                if (rotation == Surface.ROTATION_0) {
                    return SCREEN_ORIENTATION.PORTRAIT;
                } else {
                    return SCREEN_ORIENTATION.PORTRAIT_R;
                }
            }
        } else {
            // if rotation is 90 or 270 and width is greater than height, we
            // have a phone
            if (size.x > size.y) {
                if (rotation == Surface.ROTATION_90) {
                    return SCREEN_ORIENTATION.LANDSCAPE;
                } else {
                    return SCREEN_ORIENTATION.LANDSCAPE_R;
                }
            } else {
                // we have a tablet
                if (rotation == Surface.ROTATION_90) {
                    return SCREEN_ORIENTATION.PORTRAIT_R;
                } else {
                    return SCREEN_ORIENTATION.PORTRAIT;
                }
            }
        }
    }

    // vmex
    private void initVMEX() {
        vmex = new VMEX(this, getOrientation(), false, vmexCallback, gazeCallback);
        setRemoteViewTGStatus(TG_STATUS_TRY_INIT, true);
    }

    private boolean isVmexNonNull() {
        return vmex != null;
    }

    private boolean isTracking;
    private boolean isCalibrating;

    public void startTracking() {
        if (isVmexNonNull()) {
            Log.i(TAG, "startTracking");
            vmex.startTracking();
            isTracking = true;
            updateNotiView();
        }
    }
    public void stopTracking() {
        if (isVmexNonNull()) {
            Log.i(TAG, "stopTracking");
            vmex.stopTracking();
            isTracking = false;
            isCalibrating = false;
            updateNotiView();
        }
    }
    public void startCalibration() {
        if (isVmexNonNull()) {
            Log.i(TAG, "startCalibration");
            vmex.startCalibration();
            isCalibrating = true;
            updateNotiView();
        }
    }
    public void stopCalibration() {
        if (isVmexNonNull()) {
            Log.i(TAG, "stopCalibration");
            vmex.stopCalibration();
            isCalibrating = false;
            updateNotiView();
        }
    }

    public void updateNotiView() {
        setRemoteViewButtonTracking(isTracking, isVmexNonNull(), false);
        setRemoteViewButtonCalibration(isCalibrating, isVmexNonNull() && isTracking, true);
    }
//    public void setFilterType(int type) {
//        if (isVmexNonNull()) {
//            Log.i(TAG, "setFilterType " + type);
//            vmex.setFilterType(type);
//        }
//    }

    public void releaseVMEX() {
        if (isVmexNonNull()) {
            vmex.stopCalibration();
            vmex.release();
            vmex = null;
        }
    }

    private VMEXCallback vmexCallback = new VMEXCallback() {
        @Override
        public void onInitialized() {
            vmex.setCalibrationBackgroundColor(Color.argb(255, 0, 0, 0), Color.argb(80, 255, 0, 0));
            vmex.setCalibrationBackgroundColorVisibility(true);
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onInitialized();
            }
            setRemoteViewTGStatus(TG_STATUS_INITED, true);
            updateNotiView();
//            startTracking();
        }

        @Override
        public void onInitializeFailed(int i) {
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onInitializeFailed(i);
            }
            vmex = null;
            setRemoteViewTGStatus(TG_STATUS_INIT_FAIL, true);
            updateNotiView();
        }
    };
    private GazeCallback gazeCallback = new GazeCallback() {
        @Override
        public void onGaze(long timestamp, float x, float y, int type) {
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onGaze(timestamp, x, y, type);
            }
        }

        @Override
        public void onCalibrationStarted() {
            isCalibrating = true;
            updateNotiView();
        }

        @Override
        public void onCalibrationProcess(float v, float v1, float v2) {

        }

        @Override
        public void onCalibrationFinished() {
            isCalibrating = false;
            updateNotiView();
        }

        @Override
        public void onPortraitLookUp() {

        }

        @Override
        public void onPortraitLookCenter() {

        }

        @Override
        public void onPortraitLookDown() {

        }

        @Override
        public void onLandscapeLookLeft() {

        }

        @Override
        public void onLandscapeLookCenter() {

        }

        @Override
        public void onLandscapeLookRight() {

        }
    };
    // vmex end

    // ui
    // ------notification------
    // 귀찮으니 일단 remote 것 그대로 사용
    // foreground notification
    private static final String CHANNEL_ID = "TrueGazeServiceChannel";
    private static final String CHANNEL_NAME = "TrueGazeRemote Notification";
    private static final String NOTIFICATION_ACTION_RECEIVER = "notificationActionReceiver";
    private static final int NOTIFICATION_ID = 1;
    private RemoteViews mContentView;
    private NotificationCompat.Builder notificationBuilder;
    private boolean isStopForeground;

    private boolean checkNotificationEnable() {
        // 알람과 알람 채널 활성화되어있나 여부
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
            if (notificationManagerCompat.areNotificationsEnabled()) {
                Log.i(TAG, "check notification enabled " + notificationManagerCompat.getNotificationChannel(CHANNEL_ID).getImportance());
                return notificationManagerCompat.getNotificationChannel(CHANNEL_ID).getImportance() != NotificationManager.IMPORTANCE_NONE;
            }
            Log.i(TAG, "check notification disabled ");
        }
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private void showNotificationSetting() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);

//for Android 5-7
        intent.putExtra("app_package", getPackageName());
        intent.putExtra("app_uid", getApplicationInfo().uid);

// for Android 8 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 서비스에선 new task로 액티비티 시작해야 에러 안나고 진행되는듯

        startActivity(intent);
    }

    private void createNotificationChannel() {
        // 안드로이드 스튜디오 최소 타겟 버전이 26이라 분기 탈일은 없음
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 오레오부터 노티 채널 추가해야만 함
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManagerCompat.from(this).createNotificationChannel(serviceChannel);
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
        } else {
            // 26이전에는 channel id를 받는 생성자가 없음
            notificationBuilder = new NotificationCompat.Builder(this);
        }
    }

    // foreground permission 필요, 런타임 퍼미션이 아니라 선언만 해도 된다.
    private void startNotification(){
        createNotificationChannel();
        // 알려줄곳이 액티비티면 아래처럼 해야함 원격서비스라 액티비티를 안쓰고 브로드캐스트로 처리함
//        Intent notificationIntent = new Intent(RemoteApplication.getContext(), TrackingService.class);
//        PendingIntent pendingIntent = PendingIntent.getActivity(RemoteApplication.getContext(),
//                0, notificationIntent, 0);

        Intent notificationIntent = new Intent(NOTIFICATION_ACTION_RECEIVER);
        notificationIntent.setAction(ACTION_CHECK_STATUS);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, 0);

        mContentView = new RemoteViews(getPackageName(), R.layout.noti_truegaze);
        Intent trackIntent = new Intent(NOTIFICATION_ACTION_RECEIVER);
        trackIntent.setAction(ACTION_TRACKING);
        PendingIntent pTrackIntent = PendingIntent.getBroadcast(this, 0, trackIntent, 0);
        mContentView.setOnClickPendingIntent(R.id.btn_tracking, pTrackIntent);

        Intent calibIntent = new Intent(NOTIFICATION_ACTION_RECEIVER);
        calibIntent.setAction(ACTION_CALIBRATION);
        PendingIntent pCalibIntent = PendingIntent.getBroadcast(this, 0, calibIntent, 0);
        mContentView.setOnClickPendingIntent(R.id.btn_calibration, pCalibIntent);

        Intent quitIntent = new Intent(NOTIFICATION_ACTION_RECEIVER);
        quitIntent.setAction(ACTION_QUIT);
        PendingIntent pQuitIntent = PendingIntent.getBroadcast(this, 0, quitIntent, 0);
        mContentView.setOnClickPendingIntent(R.id.btn_quit, pQuitIntent);

        notificationBuilder.setContentTitle("TrueGaze Service")
                .setSmallIcon(R.drawable.vc_logo_new)
                .setContentIntent(pendingIntent)
                .setCustomContentView(mContentView);
//        .setCustomBigContentView(mContentView); // 큰 노티피케이션, 크긴 한데 직접 확대해야하는 문제가 있음 높이 제한은 256dp라고 하는데 일단 200dp 넘어가는건 확인함

//        setNotInitStatus();
        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        // 노티피케이션 이벤트 리시버 등록
        IntentFilter intentFilter = new IntentFilter(NOTIFICATION_ACTION_RECEIVER);
        intentFilter.addAction(ACTION_CHECK_STATUS);
        intentFilter.addAction(ACTION_TRACKING);
        intentFilter.addAction(ACTION_CALIBRATION);
        intentFilter.addAction(ACTION_QUIT);
        registerReceiver(notificationActionReceiver, intentFilter);
    }

    private void removeNotification() {
        isStopForeground = true;
        // 노티피케이션 리시버 삭제
        unregisterReceiver(notificationActionReceiver);

        // 일반적으로 채널은 안지우는게 맞기 때문에 주석처리
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            // 채널을 삭제하면 그냥 날라가는것 같음, 버그로 안날라가는걸 한번 봤는데 서비스 다시 키고 끄면 날라갔음 애매한데
//            // 다른 앱을 보니 채널은 일단 남아있는것으로 보이고 그걸로 설정할수 있도록 하는데 없애는게 맞는지 모르겠다..
//            NotificationManagerCompat.from(this).deleteNotificationChannel(CHANNEL_ID);
//        }

        // cancel이후 노티를 업데이트 하면 stopForeground호출해도 노티가 그대로 남아버림, 이상태에서 앱의 알람 설정에서 채널을 비활성했다가 활성화시 노티가 날라가는걸로 보아 그냥 유령처럼 실제로는 없는데 보이는 상태인듯
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        // startForeground했으니 stopForeground해야할것 같음
        stopForeground(true);
        Log.i(TAG, "RT Service stop foreground");
    }

    // 노티는 1초 이내에 자주 업데이트하면 누락된다고 문서에 써있음, 그러니 일단 노티 업데이트를 최소화하고 서비스가 살아있는 동안 백그라운드로 노티를 1초마다 갱신시킨다.
    private void updateNotification() {
        if (!isStopForeground) {
//            Log.i(TAG, "RT Service chk notification " + NotificationManagerCompat.from(this).areNotificationsEnabled() + ", " + NotificationManagerCompat.from(this).getImportance());
//            Log.i(TAG, "RT Service chk notification channel " + NotificationManagerCompat.from(this).getNotificationChannel(CHANNEL_ID).getImportance());
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notificationBuilder.build());
        } else {
            Log.i(TAG, "RT Service foreground stopped");
        }
    }

    // notification view control

    // tg rt 상태
    private static final String TG_STATUS_DEFAULT = "Not Initialized"; // tg가 초기화되지 않은 상태
    private static final String TG_STATUS_TRY_INIT = "Initializing"; // tg 초기화중
    private static final String TG_STATUS_INIT_FAIL = "Initialize Failed"; // tg 초기화 실패
    private static final String TG_STATUS_INITED = "Initialized"; // tg 초기화 완료
    private static final String TG_STATUS_TRY_DEINIT = "releasing"; // tg 종료중


    // 트루게이즈 상태를 보여줌
    private void setRemoteViewTGStatus(String desc, boolean isUpdate) {
        if (mContentView != null) {
            Log.i(TAG, "notification status TG " + desc);
            mContentView.setTextViewText(R.id.txt_tg_status, desc);
            if (isUpdate) {
                updateNotification();
            }
        }
    }
    // 카메라 상태를 보여줌
    private void setRemoteViewButtonTracking(boolean isTracking, boolean enable, boolean isUpdate) {
        if (mContentView != null) {
            Log.i(TAG, "notification btn Track, isTracking: " + isTracking + ", enable: " + enable);
            if (isTracking) {
                mContentView.setImageViewResource(R.id.btn_tracking, enable ? R.drawable.ic_stop_black_48dp : R.drawable.ic_play_arrow_white_48dp);
            } else {
                mContentView.setImageViewResource(R.id.btn_tracking, enable ? R.drawable.ic_play_arrow_black_48dp : R.drawable.ic_play_arrow_white_48dp);
            }

            if (isUpdate) {
                updateNotification();
            }
        }
    }

    // 노티에 캘리브레이션 조작 버튼 설정
    private void setRemoteViewButtonCalibration(boolean isCalibrationProcess, boolean enable, boolean isUpdate) {
        if (mContentView != null) {
            Log.i(TAG, "notification btn Calib, isCalibrationProcess: " + isCalibrationProcess + ", enable: " + enable);
            if (isCalibrationProcess) {
                mContentView.setImageViewResource(R.id.btn_calibration, enable ? R.drawable.ic_clear_black_48dp : R.drawable.ic_clear_white_48dp);
            } else {
                mContentView.setImageViewResource(R.id.btn_calibration, enable ? R.drawable.ic_remove_red_eye_black_48dp : R.drawable.ic_remove_red_eye_white_48dp);
            }
            if (isUpdate) {
                updateNotification();
            }
        }
    }

    // notification event broadcast receiver
    public static final String ACTION_CHECK_STATUS = "action.checkStatus";
    public static final String ACTION_TRACKING = "action.tracking";
    public static final String ACTION_CALIBRATION = "action.calibration";
    public static final String ACTION_QUIT = "action.quit";

    // 노티피케이션 버튼 액션 처리 리시버
    private BroadcastReceiver notificationActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            assert action != null;
            Log.i(TAG, "onReceive " + action + " on " + Thread.currentThread());
            if (action.equals(ACTION_CALIBRATION)) {
                if (isCalibrating) {
                    // 캘리브레이션 도중이니 중단
                    stopCalibration();
                } else {
                    // 캘리브레이션 시작
                    startCalibration();
                }
            }if (action.equals(ACTION_TRACKING)) {
                if (isTracking) {
                    // 트래킹 도중이니 중단
                    stopTracking();
                } else {
                    // 트래킹 시작
                    startTracking();
                }
            } else if (action.equals(ACTION_QUIT)) {
                // 서비스를 직접 시작할때만 동작, 바인딩되어있는 클라이언트가 있다면 모두 언바인드된후 종료됨
                stopSelf();
            } else if (action.equals(ACTION_CHECK_STATUS)) {
                if (checkPermission()) {
                    // 모든 퍼미션이 있을때
                    if (!isVmexNonNull()) {
                        // 초기화 안되어있으면 초기화
                        initVMEX();
                    } else {
                        showToast("TrueGaze is initialized", true);
                    }
                } else {
                    showToast("TrueGaze need permission", false);
                }
            }
        }
    };
    // ------notification end------

    private void showToast(@NonNull final String msg, final boolean isShort) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(TrackingService.this, msg, isShort? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
            }
        });
    }
    // ui end
}
