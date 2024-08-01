package mdp.grp3.arcm.util;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavAction;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

/**
 * A class that helps with navigating between fragments
 */
public final class NavHelper {

    /**
     * 
     * @param navController - the navigation controller
     * @param direction     - the id of the navigation action
     * @return - whether the navigation is safe
     */
    private static boolean isSafe(NavController navController, int direction) {
        NavDestination destination = navController.getCurrentDestination();
        if (destination != null) {
            NavAction action = destination.getAction(direction);
            return action != null;
        }
        return false;
    }

    /**
     * Safely navigate to a fragment
     * 
     * @param fragment  - the fragment to navigate from
     * @param direction - the id of the navigation action
     */
    public static void safeNavigate(Fragment fragment, int direction) {
        NavController navController = NavHostFragment.findNavController(fragment);
        if (isSafe(navController, direction)) {
            navController.navigate(direction);
        }
    }
}
