package visual.camp.sample.launcher.view;

import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


/**
 * 액션바, 스테이터스바, 네비게이션 바에 따라 바뀌는 뷰의 offset을 구하는데 사용되는 클래스
 */

///**
// * Class used for defining offset View of Action Bar, Status Bar, Navigation Bar
// */
public class ViewLayoutChecker {
    private static final String TAG = ViewLayoutChecker.class.getSimpleName();

    private View overlayView;
    private static int[] layoutLT = new int[]{0, 0};

    /**
     * ViewLayoutChecker에서 사용하는 리스너
     * <br/>설정한 뷰의 offset을 구하면 {@link visual.camp.vmex.view.ViewLayoutChecker.ViewLayoutListener#getOffset(int, int)}을 호출
     */

//    /**
//     * ViewlayoutChecker Listener
//     * <br/>Called getOffset(), when offset of set view is defined.
//     */
    @Keep
    public interface ViewLayoutListener {
        /**
         * 설정한 뷰의 오프셋을 구하면 호출
         * @param x left offset
         * @param y right offset
         */
//        /**
//         * Called when offset of set view is defined
//         * @param x left offset
//         * @param y right offset
//         */
        void getOffset(int x, int y);
    }
    private ViewLayoutListener viewLayoutListener;

    /**
     * 가장 최근에 구한 뷰의 left, top offset을 구함
     * <br/>시선 좌표의 결과를 offset만큼 감소시킨 값을 오버레이뷰에 그려야 시선좌표를 제대로 그릴수 있음
     * @return offset index : 0 = left, index : 1 = top
     */

//    /**
//     * Gets left, top offset
//     * @return offset index : 0 = left, index : 1 = top
//     */
    public static int[] getLayoutLeftTop() {
        return layoutLT;
    }

    private ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            overlayView.getLocationOnScreen(layoutLT);
            if (viewLayoutListener != null) {
                viewLayoutListener.getOffset(layoutLT[0], layoutLT[1]);
            }
//            Log.i(TAG, "overlayView getLocationInScreen : " + layoutLT[0] + ", " + layoutLT[1]);
        }
    };

    /**
     * 좌표를 오버레이할 뷰에서 offset을 찾도록 설정
     * @param overlayView 시선 좌표를 그릴 뷰
     */

//    /**
//     * Set to find offset in view to overlay coordinates
//     * @param overlayView
//     */
    public void setOverlayView(@NonNull View overlayView, @Nullable ViewLayoutListener viewLayoutListener) {
        releaseChecker();
        ViewTreeObserver viewTreeObserver = overlayView.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            overlayView.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
            this.overlayView = overlayView;
        }
        this.viewLayoutListener = viewLayoutListener;
    }

    /**
     * 가지고 있는 뷰와, 인터페이스 참조를 제거
     */

//    /**
//     * Release
//     */
    public void releaseChecker() {
        if (overlayView != null) {
            ViewTreeObserver viewTreeObserver = overlayView.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener);
            }
            overlayView = null;
        }
        viewLayoutListener = null;
        layoutLT = new int[]{0, 0};
    }
}
