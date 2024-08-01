package mdp.grp3.arcm.fragment;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import mdp.grp3.arcm.util.BluetoothConnection;
import mdp.grp3.arcm.component.FullscreenMaterialDialog;
import mdp.grp3.arcm.util.NavHelper;
import mdp.grp3.arcm.R;
import mdp.grp3.arcm.databinding.FragmentConnectionBinding;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

/**
 * The fragment responsible for connecting to the Bluetooth device.
 */
public class ConnectionFragment extends Fragment {

    private FragmentConnectionBinding binding;
    private ArrayList<String> deviceNames;
    private ArrayList<String> deviceMACs;
    ArrayAdapter<String> listAdapter;
    private final BluetoothConnection bluetoothConnection = BluetoothConnection.getInstance();

    /**
     * Broadcast receiver for Bluetooth device discovery.
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null)
                    return;
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress();
                // add device if it is not already in the list
                if (!deviceMACs.contains(deviceHardwareAddress) && deviceName != null) {
                    deviceNames.add(deviceName);
                    deviceMACs.add(deviceHardwareAddress);
                    listAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    /**
     * Handler for Bluetooth connection status.
     */
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == BluetoothConnection.ConnectionStatus.CONNECTED) {
                // connected, navigate to main fragment
                NavHelper.safeNavigate(ConnectionFragment.this, R.id.action_ConnectionFragment_to_MainFragment);
            } else if (msg.what == BluetoothConnection.ConnectionStatus.FAILED) {
                // failed to connect, allow user to retry
                Snackbar.make(binding.getRoot(), "❌ " + msg.obj, Snackbar.LENGTH_LONG).show();
                binding.scan.setText(R.string.scan);
                binding.scan.setEnabled(true);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Starts scanning for Bluetooth devices.
     */
    @RequiresPermission(allOf = { "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN" })
    private void startScan() {
        deviceNames = new ArrayList<>();
        deviceMACs = new ArrayList<>();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // add all paired devices by default
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            deviceNames.add(device.getName());
            deviceMACs.add(device.getAddress());
        }
        listAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, deviceNames);
        if (!bluetoothAdapter.isDiscovering())
            bluetoothAdapter.startDiscovery();
        // whenever the list adapter changes it will be added to the dialog
        new FullscreenMaterialDialog(new MaterialAlertDialogBuilder(requireContext()).setTitle("Connect")
                .setAdapter(listAdapter, (dialog, which) -> {
                    bluetoothAdapter.cancelDiscovery();
                    bluetoothConnection.tryConnection(deviceMACs.get(which), handler);
                    binding.scan.setText(String.format("Connecting to %s…", deviceNames.get(which)));
                    binding.scan.setEnabled(false);
                })).show();
    }

    @SuppressLint("MissingPermission")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        binding.scan.setOnClickListener(view1 -> startScan());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}