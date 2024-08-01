package mdp.grp3.arcm.fragment;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import mdp.grp3.arcm.util.NavHelper;
import mdp.grp3.arcm.R;
import mdp.grp3.arcm.databinding.FragmentBluetoothBinding;

/**
 * The fragment responsible for enabling Bluetooth.
 */
public class BluetoothFragment extends Fragment {
    private FragmentBluetoothBinding binding;

    // Broadcast receiver for Bluetooth state changes
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON)
                    navigateNext();
                if (state == BluetoothAdapter.STATE_TURNING_ON)
                    binding.turnOn.setText(R.string.bluetooth_turning_on);
            }
        }
    };

    /**
     * Enables bluetooth it if it is not enabled.
     * 
     * @return current state of bluetooth
     */
    private boolean enableBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ActivityCompat.startActivityForResult(requireActivity(), intent, 1, null);
        }
        return mBluetoothAdapter.isEnabled();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (enableBluetooth())
            navigateNext();
        binding = FragmentBluetoothBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Navigates to the next fragment.
     */
    private void navigateNext() {
        NavHelper.safeNavigate(BluetoothFragment.this, R.id.action_BluetoothFragment_to_ConnectionFragment);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        ContextCompat.registerReceiver(requireContext(), mReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        binding.turnOn.setOnClickListener(view1 -> enableBluetooth());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}