package visual.camp.sample.launcher.service.inter;

public interface TrackingServiceInterface {
    void onInitialized();
    void onInitializeFailed(int err);
    void onGaze(long timestamp, float x, float y, int type);
    void onCalibrationProcess(float progress, float x, float y);
    void onCalibrationFinished();
}
