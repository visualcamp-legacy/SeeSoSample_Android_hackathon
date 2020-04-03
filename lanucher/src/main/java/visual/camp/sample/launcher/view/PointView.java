package visual.camp.sample.launcher.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class PointView extends View {
    public PointView(Context context) {
        super(context);
        init();
    }

    public PointView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PointView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public PointView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    private Paint paint;
    private void init() {
        paint = new Paint();
        paint.setColor(Color.rgb(0x00, 0x00, 0xff));
        paint.setStrokeWidth(2f);
    }

    private float offsetX, offsetY;
    private PointF position = new PointF();
    public void setOffset(int x, int y) {
        offsetX = x;
        offsetY = y;
    }
    public void setPosition(float x, float y) {
        position.x = x - offsetX;
        position.y = y - offsetY;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(position.x, position.y, 10, paint);
        canvas.drawLine(0, position.y, getWidth(), position.y, paint);
        canvas.drawLine(position.x, 0, position.x, getHeight(), paint);
    }
}
