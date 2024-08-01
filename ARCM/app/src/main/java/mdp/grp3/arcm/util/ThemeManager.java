package mdp.grp3.arcm.util;

import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.content.pm.PackageManager.GET_ACTIVITIES;
import static android.content.pm.PackageManager.GET_META_DATA;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.util.TypedValue;

import java.util.HashMap;
import java.util.Map;

import mdp.grp3.arcm.R;

/**
 * A class that manages the themes of the application.
 */
public final class ThemeManager {
    private static final Map<String, Integer> themeMap = new HashMap<String, Integer>() {
        {
            put("Purple", R.style.PurpleTheme);
            put("Blue", R.style.BlueTheme);
            put("Red", R.style.RedTheme);
            put("Pink", R.style.PinkTheme);
            put("Orange", R.style.OrangeTheme);
            put("Grey", R.style.GreyTheme);
        }
    }; // Maps the theme name to the theme defined in the styles.xml

    /**
     * 
     * @return The themes available in the application.
     */
    public static String[] getThemes() {
        return themeMap.keySet().toArray(new String[0]);
    }

    /**
     * 
     * @param name - The name of the theme.
     * @return The theme associated with the specified name.
     */
    public static int getTheme(String name) {
        return themeMap.getOrDefault(name, R.style.PurpleTheme);
    }

    /**
     * 
     * @param context - The context of the application.
     * @param attr    - The attribute value
     * @return The color associated with the attribute value.
     */
    public static int getColor(Context context, int attr) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.data;
    }

    /**
     * Sets the initial theme of the application.
     * 
     * @param activity - The application activity.
     */
    public static void setInitialTheme(Activity activity) {
        PackageManager packageManager = activity.getPackageManager();
        // Sets the theme on first launch
        if (packageManager.getComponentEnabledSetting(new ComponentName(activity,
                activity.getPackageName() + ".InitialAlias")) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(
                    new ComponentName(activity, activity.getPackageName() + ".Purple"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
            packageManager.setComponentEnabledSetting(
                    new ComponentName(activity, activity.getPackageName() + ".InitialAlias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP);
        }
        try {
            // find the theme that is enabled and apply it
            for (ActivityInfo activityInfo : packageManager.getPackageInfo(activity.getPackageName(),
                    GET_ACTIVITIES | GET_META_DATA).activities) {
                if (activityInfo.targetActivity != null) {
                    activity.setTheme(ThemeManager.getTheme(activityInfo.name.split("\\.")[3]));
                    break;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the new theme of the application.
     * 
     * @param activity  - The application activity.
     * @param selection - The selected theme.
     */
    public static void setNewTheme(Activity activity, int selection) {
        String[] themeArray = getThemes();
        int themeValue = ThemeManager.getTheme(themeArray[selection]);
        activity.setTheme(themeValue);

        // Turns off all themes except the selected theme
        PackageManager packageManager = activity.getPackageManager();
        for (int i = 0; i < themeArray.length; i++) {
            int state = (i == selection) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            packageManager.setComponentEnabledSetting(
                    new ComponentName(activity, activity.getPackageName() + "." + themeArray[i]),
                    state,
                    PackageManager.DONT_KILL_APP);
        }
    }

}
