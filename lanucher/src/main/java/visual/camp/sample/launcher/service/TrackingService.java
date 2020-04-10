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
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import camp.visual.truegaze.TrueGaze;
import camp.visual.truegaze.callback.CalibrationCallback;
import camp.visual.truegaze.callback.GazeCallback;
import camp.visual.truegaze.callback.LifeCallback;
import visual.camp.sample.launcher.R;
import visual.camp.sample.launcher.device.GazeDevice;
import visual.camp.sample.launcher.service.inter.TrackingServiceInterface;

abstract public class TrackingService extends Service {
    private static final String TAG = TrackingService.class.getSimpleName();

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private TrueGaze trueGaze;

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
            initGaze();
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
        releaseGaze();
        notificationHandler.removeMessages(MSG_INFO);
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

    // trueGaze
    private void initGaze() {
        // 화면의 스크린 좌표를 가져옴
        PointF screenOrigin = GazeDevice.getDeviceScreenOrigin(Build.MODEL);
        trueGaze = new TrueGaze(getApplicationContext(), screenOrigin, lifeCallback, gazeCallback, calibrationCallback);
        setRemoteViewTGStatus(TG_STATUS_TRY_INIT, true);
    }

    private boolean isGazeNonNull() {
        return trueGaze != null;
    }

    private boolean isTracking;

    // 카메라가 동작해 시선 추적을 하는지 체크
    private boolean checkTracking() {
        if (isGazeNonNull()) {
            isTracking = trueGaze.isTracking();
        } else {
            isTracking = false;
        }
        return isTracking;
    }

    // 카메라를 열고 시선 추적 시작
    protected void startTracking() {
        if (isGazeNonNull()) {
            Log.i(TAG, "startTracking");
            isTracking = true; // startTracking을 시작하면 카메라가 무조건 동작해 시선 추적을 한다 가정
            // 만약 문제가 생기면 LifeCallback의 onCameraError이 호출
            trueGaze.startTracking();
            updateNotiView();
        }
    }

    // 카메라를 닫고 시선 추적 중단
    protected void stopTracking() {
        if (isGazeNonNull()) {
            Log.i(TAG, "stopTracking");
            isTracking = false;
            trueGaze.stopTracking();
            updateNotiView();
        }
    }

    // 캘리브레이션 시작
    protected void startCalibration() {
        if (isGazeNonNull()) {
            Log.i(TAG, "startCalibration");
            if (checkTracking()) {
                // 전체화면 기준 캘리브레이션
                trueGaze.startCalibrationInWholeScreen();
            } else {
                showToast("Start Gaze Tracking!", true);
            }
            updateNotiView();
        }
    }
    protected void stopCalibration() {
        if (isGazeNonNull()) {
            Log.i(TAG, "stopCalibration");
            trueGaze.stopCalibration();
            updateNotiView();
        }
    }

    private void updateNotiView() {
        setRemoteViewButtonTracking(isTracking, isGazeNonNull(), true);
    }

    protected void releaseGaze() {
        if (isGazeNonNull()) {
            trueGaze.stopCalibration();
            trueGaze.release();
            trueGaze = null;
        }
    }

    private LifeCallback lifeCallback = new LifeCallback() {
        @Override
        public void onInitialized() {
            // 초기화 성공
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onInitialized();
            }
            setRemoteViewTGStatus(TG_STATUS_INITED, true);
            updateNotiView();
            startTracking();
        }

        @Override
        public void onInitializeFailed(int i) {
            // 초기화 실패
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onInitializeFailed(i);
            }
            trueGaze = null;
            setRemoteViewTGStatus(TG_STATUS_INIT_FAIL, true);
            updateNotiView();
        }

        @Override
        public void onCameraClose(boolean isError) {
            // isError가 true면 카메라 에러 발생한 것, 대표적으로 시선 추적 중 다른 카메라 사용 어플리케이션을 동작시킬경우 에러가 발생하는 경우가 있음
            // 카메라2에서 발생하는 모든에러는 전부 발생가능함
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onCameraClosed();
            }
            isTracking = false;
            updateNotiView();
            if (isError) {
                Log.e(TAG, "camera error occur " + isTracking);
                showToast("Camera Error Occur!!", true);
            }
        }
    };

    // 시선 정보를 받는 콜백, 기능 추가나 수정 예정
    private GazeCallback gazeCallback = new GazeCallback() {
        @Override
        public void onGaze(long timestamp, float x, float y, int type) {
            // type 설명
            // GazeState.TRACKING: 시선 좌표
            // GazeState.CALIBRATING: 캘리브레이션 중
            // GazeState.FACE_MISSING: 얼굴이 잡히지 못한 상태(시선좌표는 NaN)
            // GazeState.OUT_OF_SCREEN: 시선 좌표가 화면 밖에 위치할시
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onGaze(timestamp, x, y, type);
            }
        }
    };

    // 캘리브레이션을 진행하기 위한 콜백
    private CalibrationCallback calibrationCallback = new CalibrationCallback() {
        @Override
        public void onCalibrationProcess(float progress, float x, float y) {
            // progress 1: 좌표를 설정
            // progress 1미만: 캘리브레이션 진행도(0~1)
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onCalibrationProcess(progress, x, y);
            }
        }

        @Override
        public void onCalibrationFinished() {
            // 캘리브레이션 중단
            if (trackingServiceInterface != null) {
                trackingServiceInterface.onCalibrationFinished();
            }
        }
    };
    // trueGaze end

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
        Intent notificationIntent = new Intent(NOTIFICATION_ACTION_RECEIVER);
        notificationIntent.setAction(ACTION_CHECK_STATUS);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, 0);

        mContentView = new RemoteViews(getPackageName(), R.layout.noti_truegaze);
        Intent trackIntent = new Intent(NOTIFICATION_ACTION_RECEIVER);
        trackIntent.setAction(ACTION_TRACKING);
        PendingIntent pTrackIntent = PendingIntent.getBroadcast(this, 0, trackIntent, 0);
        mContentView.setOnClickPendingIntent(R.id.btn_tracking, pTrackIntent);

        Intent quitIntent = new Intent(NOTIFICATION_ACTION_RECEIVER);
        quitIntent.setAction(ACTION_QUIT);
        PendingIntent pQuitIntent = PendingIntent.getBroadcast(this, 0, quitIntent, 0);
        mContentView.setOnClickPendingIntent(R.id.btn_quit, pQuitIntent);

        notificationBuilder.setContentTitle("TrueGaze Service")
                .setSmallIcon(R.drawable.vc_logo_new)
                .setContentIntent(pendingIntent)
                .setCustomContentView(mContentView);
        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        // 노티피케이션 이벤트 리시버 등록
        IntentFilter intentFilter = new IntentFilter(NOTIFICATION_ACTION_RECEIVER);
        intentFilter.addAction(ACTION_CHECK_STATUS);
        intentFilter.addAction(ACTION_TRACKING);
        intentFilter.addAction(ACTION_QUIT);
        registerReceiver(notificationActionReceiver, intentFilter);
    }

    private void removeNotification() {
        isStopForeground = true;
        // 노티피케이션 리시버 삭제
        unregisterReceiver(notificationActionReceiver);

        // cancel이후 노티를 업데이트 하면 stopForeground호출해도 노티가 그대로 남아버림, 이상태에서 앱의 알람 설정에서 채널을 비활성했다가 활성화시 노티가 날라가는걸로 보아 그냥 유령처럼 실제로는 없는데 보이는 상태인듯
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        // startForeground했으니 stopForeground해야할것 같음
        stopForeground(true);
        Log.i(TAG, "Service stop foreground");
    }

    // 노티는 1초 이내에 자주 업데이트하면 누락된다고 문서에 써있음, 그러니 일단 노티 업데이트를 최소화하고 서비스가 살아있는 동안 백그라운드로 노티를 1초마다 갱신시킨다.
    private void updateNotification() {
        if (!isStopForeground) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notificationBuilder.build());
        } else {
            Log.i(TAG, "Service foreground stopped");
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

    // notification event broadcast receiver
    public static final String ACTION_CHECK_STATUS = "action.checkStatus";
    public static final String ACTION_TRACKING = "action.tracking";
    public static final String ACTION_QUIT = "action.quit";

    // 노티피케이션 버튼 액션 처리 리시버
    private BroadcastReceiver notificationActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            assert action != null;
            Log.i(TAG, "onReceive " + action + " on " + Thread.currentThread());
            if (action.equals(ACTION_TRACKING)) {
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
                    if (!isGazeNonNull()) {
                        // 초기화 안되어있으면 초기화
                        initGaze();
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
