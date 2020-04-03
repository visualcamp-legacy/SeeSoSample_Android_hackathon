package visual.camp.sample.launcher.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import visual.camp.sample.launcher.common.CONFIG;

public class AppDataManager {
    private static final String TAG = AppDataManager.class.getSimpleName();
    private PackageManager packageManager;
    private HandlerThread backgroundThread = new HandlerThread("AppDataManager");
    private Handler backgroundHandler;
    public interface DataListener {
        void onDataLoaded(List<AppData> dataList);
    }
    private DataListener dataListener;

    public AppDataManager(Context context) {
        packageManager = context.getPackageManager();
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void setDataListener(DataListener dataListener) {
        this.dataListener = dataListener;
    }
    public void removeDataListener() {
        dataListener = null;
    }

    public void release() {
        removeDataListener();
        backgroundThread.quitSafely();
        packageManager = null;
    }

    private Runnable loadCallback = new Runnable() {
        @Override
        public void run() {
            List<AppData> appDataList = new ArrayList<>();
            List<ApplicationInfo> appInfoList = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            List<AppData> camContains = new ArrayList<>();
            List<AppData> compatibles = new ArrayList<>();
            int idx = 0;
            for (ApplicationInfo applicationInfo : appInfoList) {
                applicationInfo.loadDescription(packageManager);
                if (packageManager.getLaunchIntentForPackage(applicationInfo.packageName) != null) {
                    AppData appData = new AppData();
                    appData.type = CONFIG.APP_DATA_TYPE_APP;
                    appData.idx = idx;
                    appData.appInfo = applicationInfo;
                    appData.label = applicationInfo.loadLabel(packageManager).toString();
                    appData.icon = applicationInfo.loadIcon(packageManager);
                    Log.i(TAG, idx + ": AppName: " + appData.label + " Package: " + applicationInfo.packageName + " source: " + applicationInfo.sourceDir);
                    try {
                        PackageInfo packageInfo = packageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_PERMISSIONS);

                        //Get Permissions
                        String[] requestedPermissions = packageInfo.requestedPermissions;
                        if (requestedPermissions != null) {
                            for (String perm : requestedPermissions) {
                                if (perm.equals(Manifest.permission.CAMERA)) {
                                    Log.w(TAG, idx + ": AppName: " + appData.label + " contains Permission Camera");
                                    appData.isContainCamPerm = true;
                                    camContains.add(appData);
                                    break;
                                }
                            }
                            if (!appData.isContainCamPerm) {
                                Log.v(TAG, idx + ": AppName: " + appData.label + " compatible app");
                                compatibles.add(appData);
                            }
                        } else {
                            Log.v(TAG, idx + ": AppName: " + appData.label + " compatible app");
                            compatibles.add(appData);
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    idx += 1;
                }
            }

            Log.i(TAG, "chk compatibles " + compatibles.size());
            Log.i(TAG, "chk camContains " + camContains.size());
            AppData appSeparator = new AppData();
            appSeparator.label = "App";
            appSeparator.type = CONFIG.APP_DATA_TYPE_SEPARATOR;
            appDataList.add(appSeparator);
            appDataList.addAll(compatibles);
            AppData camSeparator = new AppData();
            camSeparator.label = "App (Require Camera Permission)";
            camSeparator.type = CONFIG.APP_DATA_TYPE_SEPARATOR;
            appDataList.add(camSeparator);
            appDataList.addAll(camContains);

            if (dataListener != null) {
                dataListener.onDataLoaded(appDataList);
            }
        }
    };

    public void loadAppData() {
        backgroundHandler.post(loadCallback);
    }
}
