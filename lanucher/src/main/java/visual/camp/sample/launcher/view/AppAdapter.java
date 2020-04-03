package visual.camp.sample.launcher.view;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import visual.camp.sample.launcher.R;
import visual.camp.sample.launcher.common.CONFIG;
import visual.camp.sample.launcher.data.AppData;

public class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = AppAdapter.class.getSimpleName();
    private List<AppData> appInfoList = new ArrayList<>();
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public interface ItemClickListener {
        void onItemClick(AppData data);
    }
    private ItemClickListener itemClickListener;

    public AppAdapter() {}

    public void setItemClickListener(ItemClickListener itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    public void removeItemClickListener() {
        itemClickListener = null;
    }

    public void release() {
        removeItemClickListener();
        appInfoList.clear();
        appInfoList = null;
    }

    public void loadAppInfoList(final List<AppData> appInfoList) {
        Log.i(TAG, "loadAppInfoList");
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                AppAdapter.this.appInfoList = appInfoList;
                notifyDataSetChanged();
            }
        });
    }
    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView imgApp;
        TextView txtApp;
        View layoutApp;
        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutApp = itemView.findViewById(R.id.layout_app);
            imgApp = itemView.findViewById(R.id.img_app);
            txtApp = itemView.findViewById(R.id.txt_app);
        }
    }

    static class SeparatorHolder extends RecyclerView.ViewHolder {
        TextView txtInfo;
        SeparatorHolder(@NonNull View itemView) {
            super(itemView);
            txtInfo = itemView.findViewById(R.id.txt_info);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == CONFIG.APP_DATA_TYPE_APP) { // for call layout
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            return new AppViewHolder(view);

        } else { // for email layout
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_separator, parent, false);
            return new SeparatorHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AppData data = getItem(position);
        if (getItemViewType(position) == CONFIG.APP_DATA_TYPE_APP) {

            ((AppViewHolder)holder).txtApp.setText(data.idx + " : " + data.label);
            ((AppViewHolder)holder).imgApp.setImageDrawable(data.icon);
            ((AppViewHolder)holder).layoutApp.setTag(R.id.layout_app, data);
            ((AppViewHolder)holder).layoutApp.setOnClickListener(onClickListener);
        } else {
            ((SeparatorHolder)holder).txtInfo.setText(data.label);
        }
    }

    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            AppData appData = (AppData)v.getTag(R.id.layout_app);
            if (itemClickListener != null) {
                itemClickListener.onItemClick(appData);
            }
        }
    };

    public AppData getItem(int pos) {
        return appInfoList.get(pos);
    }

    @Override
    public int getItemCount() {
        return appInfoList.size();
    }
}
