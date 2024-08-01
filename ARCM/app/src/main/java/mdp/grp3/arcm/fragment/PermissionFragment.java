package mdp.grp3.arcm.fragment;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import mdp.grp3.arcm.util.NavHelper;
import mdp.grp3.arcm.R;
import mdp.grp3.arcm.databinding.FragmentPermissionBinding;

/**
 * Fragment that requests for permissions.
 */
public class PermissionFragment extends Fragment {

    private FragmentPermissionBinding binding;

    // all the permissions required by the app
    private final String[] permissions = { "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN",
            "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION" };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // request for permissions
        ActivityCompat.requestPermissions(requireActivity(), permissions, 2);
        if (permissionsGranted())
            navigateNext();
        binding = FragmentPermissionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * 
     * @return whether the permissions are granted
     */
    private boolean permissionsGranted() {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Navigates to the next fragment.
     */
    private void navigateNext() {
        NavHelper.safeNavigate(PermissionFragment.this, R.id.action_PermissionFragment_to_BluetoothFragment);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // if permission has been previously denied, we can prompt the user to go to
        // settings
        view.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
            if (hasFocus && permissionsGranted())
                navigateNext();
        });
        binding.settingsButton.setOnClickListener(view1 -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}