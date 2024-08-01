package mdp.grp3.arcm.component;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.view.View;

/**
 * A custom DragShadowBuilder that allows the shadow to be rotated.
 */
public class RotatedDragShadowBuilder extends View.DragShadowBuilder {
    private final View view; // The view to create the shadow for
    private boolean danger; // Whether the object is being dragged over a danger zone

    /**
     * Constructor for RotatedDragShadowBuilder.
     * 
     * @param view The view to create the shadow for.
     */
    public RotatedDragShadowBuilder(View view) {
        super(view);
        this.view = view;
        danger = false;
    }

    /**
     * Sets danger value.
     * 
     * @param danger Whether the object is being dragged over a danger zone.
     */
    public void setDanger(boolean danger) {
        this.danger = danger;
    }

    /**
     * Draws the shadow.
     * 
     * @param canvas - The canvas to draw the shadow on.
     */
    @Override
    public void onDrawShadow(Canvas canvas) {
        canvas.save();
        canvas.rotate(view.getRotation(), getView().getWidth() / 2f, getView().getHeight() / 2f);
        if (!danger) {
            // if safe then draw the view
            getView().draw(canvas);
        } else {
            // draw a dark shadow to indicate danger
            Paint paint = new Paint();
            paint.setAlpha(255);
            ColorFilter colorFilter = new LightingColorFilter(Color.BLACK, Color.BLACK);
            paint.setColorFilter(colorFilter);

            Bitmap bitmap = Bitmap.createBitmap(getView().getWidth(), getView().getHeight(), Bitmap.Config.ARGB_8888);
            Canvas shadowCanvas = new Canvas(bitmap);
            getView().draw(shadowCanvas);

            canvas.drawBitmap(bitmap, 0, 0, paint);
        }
        canvas.restore();
    }
}
