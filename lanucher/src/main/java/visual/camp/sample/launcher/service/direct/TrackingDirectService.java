package visual.camp.sample.launcher.service.direct;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import visual.camp.sample.launcher.common.CONFIG;
import visual.camp.sample.launcher.service.TrackingService;
import visual.camp.sample.launcher.service.inter.TrackingServiceInterface;

public class TrackingDirectService extends TrackingService {
    private static final String TAG = TrackingDirectService.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    protected TrackingServiceInterface getTrackingServiceInterface() {
        return trackingServiceInterface;
    }

    private final IBinder binder = new TrackingServiceBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class TrackingServiceBinder extends Binder {
        public TrackingDirectService getService() {
            return TrackingDirectService.this;
        }
    }

    private TrackingServiceInterface trackingServiceInterface = new TrackingServiceInterface() {
        @Override
        public void onInitialized() {

        }

        @Override
        public void onInitializeFailed(int err) {

        }

        @Override
        public void onGaze(long timestamp, float x, float y, int type) {
            Intent gazeDataIntent = new Intent(CONFIG.RECEIVER_GAZE_INFO);
            gazeDataIntent.setAction(CONFIG.ACTION_ON_GAZE);
            gazeDataIntent.putExtra(CONFIG.INTENT_GAZE_TYPE, type);
            gazeDataIntent.putExtra(CONFIG.INTENT_GAZE_TIMESTAMP, timestamp);
            gazeDataIntent.putExtra(CONFIG.INTENT_GAZE_COORD, new float[]{x, y});
            sendBroadcast(gazeDataIntent);
        }
    };
}
