package mdp.grp3.arcm.component;

import android.content.DialogInterface;
import android.content.res.Configuration;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import mdp.grp3.arcm.util.ThemeManager;

/**
 * A custom dialog that provides a fullscreen dialog display from a
 * MaterialAlertDialogBuilder.
 */
public class FullscreenMaterialDialog {
    private final AlertDialog alertDialog;

    /**
     * Sets the text color of the specified button based on the current UI mode.
     *
     * @param button The button to set the text color for.
     */
    private void setButtonTextColor(Button button) {
        if (button != null) {
            if ((alertDialog.getContext().getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                button.setTextColor(ThemeManager.getColor(alertDialog.getContext(),
                        com.google.android.material.R.attr.colorAccent));
            } else {
                button.setTextColor(ThemeManager.getColor(alertDialog.getContext(),
                        com.google.android.material.R.attr.colorPrimaryDark));
            }
        }
    }

    /**
     * Constructor for FullscreenMaterialDialog.
     * 
     * @param materialAlertDialogBuilder The MaterialAlertDialogBuilder to create
     *                                   the dialog from.
     */
    public FullscreenMaterialDialog(MaterialAlertDialogBuilder materialAlertDialogBuilder) {
        this.alertDialog = materialAlertDialogBuilder.create();
    }

    /**
     * Shows the dialog.
     */
    public void show() {
        Objects.requireNonNull(alertDialog.getWindow()).setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        alertDialog.show();
        setButtonTextColor(alertDialog.getButton(DialogInterface.BUTTON_POSITIVE));
        setButtonTextColor(alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE));
        setButtonTextColor(alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL));
        alertDialog.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        alertDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    /**
     * Dismisses the dialog.
     */
    public void cancel() {
        alertDialog.cancel();
    }
}
