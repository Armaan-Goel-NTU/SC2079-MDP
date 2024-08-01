package mdp.grp3.arcm.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Objects;

import mdp.grp3.arcm.constant.Direction;
import mdp.grp3.arcm.R;
import mdp.grp3.arcm.util.ThemeManager;

/**
 * A custom TextView that represents an obstacle on the grid.
 */
@SuppressLint("ViewConstructor")
public class ObstacleView extends androidx.appcompat.widget.AppCompatTextView {
    public static final int obstacleSize = 49; // Size of the obstacle
    public static final int numObstacles = 8; // Maximum number of obstacles

    private static float cardX, cardY; // Position of the outer card

    /**
     * Notes down the position of the card.
     * 
     * @param x The x-coordinate of the card.
     * @param y The y-coordinate of the card.
     */
    public static void setCardPos(float x, float y) {
        cardX = x;
        cardY = y;
    }

    private boolean onGrid, discovered;

    private char direction;
    private final int num;

    private FullscreenMaterialDialog selectorDialog;
    private final int[] directions = {
            R.id.direction_up,
            R.id.direction_down,
            R.id.direction_right,
            R.id.direction_left
    };
    private Pair<Integer, Integer> pos;

    /**
     * Updates the background of the obstacle based on its direction.
     */
    public void updateBackground() {
        // base layer
        Drawable bottom = ContextCompat.getDrawable(getContext(), R.drawable.square);

        // top layer to indicate direction. color changes based on theme
        Drawable top = ContextCompat.getDrawable(getContext(), R.color.black);
        Objects.requireNonNull(top)
                .setTint(ThemeManager.getColor(getContext(), com.google.android.material.R.attr.colorSecondary));

        LayerDrawable layer = new LayerDrawable(new Drawable[] { bottom, top });

        int topHeight = (int) (5.0f / 34.0f * getHeight());
        int bottomHeight = getHeight() - topHeight;

        // gravity for the text
        int gravity = Gravity.CENTER;

        // sets the insets of the layers based on the direction
        switch (direction) {
            case Direction.NONE:
                setBackground(bottom);
                return;
            case Direction.FORWARD:
                layer.setLayerInset(0, 0, topHeight, 0, 0);
                layer.setLayerInset(1, 0, 0, 0, bottomHeight);
                gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                break;
            case Direction.BACKWARD:
                layer.setLayerInset(0, 0, 0, 0, topHeight);
                layer.setLayerInset(1, 0, bottomHeight, 0, 0);
                gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                break;
            case Direction.RIGHT:
                layer.setLayerInset(0, 0, 0, topHeight, 0);
                layer.setLayerInset(1, bottomHeight, 0, 0, 0);
                gravity = Gravity.START | Gravity.CENTER_VERTICAL;
                break;
            case Direction.LEFT:
                layer.setLayerInset(0, topHeight, 0, 0, 0);
                layer.setLayerInset(1, 0, 0, bottomHeight, 0);
                gravity = Gravity.END | Gravity.CENTER_VERTICAL;
                break;
        }

        // always show text in the center if discovered
        if (discovered)
            setGravity(gravity);

        setBackground(layer);
    }

    /**
     * Constructor for ObstacleView.
     * 
     * @param context The context of the view.
     * @param num     The number of the obstacle.
     */
    public ObstacleView(Context context, int num) {
        super(context);
        this.num = num;
        this.setText(String.valueOf(num));
        setTextSize(12);
        setTextColor(ContextCompat.getColor(getContext(), R.color.white));
        setGravity(Gravity.CENTER);
        resetPosition();
        setOnClickListener(v -> {
            if (!discovered) {
                // if this obstacle hasn't been discovered, allow user to choose direction
                final View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.direction_selector, null,
                        false);
                for (int i = 0; i < 4; i++) {
                    int finalI = i;
                    TextView selector = dialogView.findViewById(directions[i]);
                    selector.setText(String.valueOf(num));
                    selector.setOnClickListener(v1 -> {
                        direction = (char) finalI;
                        updateBackground();
                        selectorDialog.cancel();
                    });
                }
                selectorDialog = new FullscreenMaterialDialog(
                        new MaterialAlertDialogBuilder(getContext()).setTitle("Choose Direction").setView(dialogView));
                selectorDialog.show();
            }
        });
    }

    /**
     * Resets the position of the obstacle. (Snaps it to the card with varying
     * elevation)
     */
    public void resetPosition() {
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(obstacleSize, obstacleSize);
        setLayoutParams(params);
        setX(cardX - 5f);
        setY(cardY - 6f);
        setElevation(15 + numObstacles - num);
        onGrid = false;
        discovered = false;
        direction = Direction.NONE;
        updateBackground();
    }

    /**
     * 
     * @return The position of the obstacle on the grid.
     */
    public Pair<Integer, Integer> getGridPos() {
        return onGrid ? pos : null;
    }

    /**
     * Sets the position of the obstacle on the grid.
     * 
     * @param pos The position of the obstacle on the grid.
     */
    public void setOnGrid(Pair<Integer, Integer> pos) {
        this.onGrid = true;
        this.pos = pos;
    }

    /**
     * Sets the obstacle as discovered.
     * 
     * @param targetId The target ID of the discovered image.
     */
    public void setDiscovered(int targetId) {
        if (onGrid && !discovered) {
            discovered = true;
            setTextSize(18);
            setTypeface(getResources().getFont(R.font.comic_sans));
            setText(String.valueOf(targetId));
            updateBackground();
        }
    }

    /**
     * 
     * @return The direction of the obstacle.
     */
    public char getDirection() {
        return direction;
    }
}
