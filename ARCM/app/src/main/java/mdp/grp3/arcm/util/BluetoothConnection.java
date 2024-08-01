package mdp.grp3.arcm.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * A class that manages the Bluetooth connection.
 */
public class BluetoothConnection {

    /**
     * A class that contains the constants for the connection status
     */
    public static final class ConnectionStatus {
        public static final int CONNECTED = 1;
        public static final int FAILED = 2;
        public static final int DISCONNECTED = 3;
        public static final int RECONNECTED = 4;
    }

    /**
     * A class that contains the constants for the IO status
     */
    public static final class IOStatus {
        public static final int WRITE_FAILED = 5;
        public static final int READ_BYTES = 6;
    }

    private static volatile BluetoothConnection INSTANCE = null;
    private final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Standard SerialPortService
                                                                                         // ID
    protected BluetoothSocket socket;
    protected boolean isRunning = false;
    private BluetoothDevice device; // target device
    private InputStream inputStream;
    private OutputStream outputStream;
    private BTCommThread btCommThread;

    private BluetoothConnection() {
    }

    /**
     * Singleton pattern
     * 
     * @return the instance of the BluetoothConnection
     */
    public static BluetoothConnection getInstance() {
        if (INSTANCE == null) {
            synchronized (BluetoothConnection.class) {
                if (INSTANCE == null)
                    INSTANCE = new BluetoothConnection();
            }
        }
        return INSTANCE;
    }

    /**
     * Connect to the target device
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private void connect() throws IOException {
        socket = device.createRfcommSocketToServiceRecord(myUUID);
        socket.connect();
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    /**
     * Try to connect to a target device
     * 
     * @param btAddress the address of the target device
     * @param handler   the handler to send messages to the UI thread
     */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    public void tryConnection(String btAddress, Handler handler) {
        new Thread(() -> {
            device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(btAddress);
            socket = null;
            try {
                connect();
                handler.sendEmptyMessage(ConnectionStatus.CONNECTED);
            } catch (IOException e) {
                handler.sendMessage(Message.obtain(handler, ConnectionStatus.FAILED, e.getMessage()));
                if (socket != null)
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
            }
        }).start(); // async connection
    }

    /**
     * Setup the communication thread
     * 
     * @param handler the handler to send messages to the UI thread
     */
    public void setupCommunication(Handler handler) {
        if (isRunning) {
            btCommThread.setHandler(handler);
        } else {
            isRunning = true;
            btCommThread = new BTCommThread(handler);
            Executors.newSingleThreadExecutor().submit(btCommThread);
        }
    }

    /**
     * Write data to the output stream
     * 
     * @param buff the data to be written
     */
    public void write(byte[] buff) {
        if (btCommThread != null)
            btCommThread.write(buff);
    }

    /**
     * Close the communication
     */
    public void closeCommunication() {
        isRunning = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * @return the target device
     */
    public BluetoothDevice getDevice() {
        return device;
    }

    /**
     * Internal thread for handling the communication
     */
    private class BTCommThread implements Runnable {

        private Handler handler; // The Handler for sending data to the UI thread
        private boolean connected; // Whether the device is connected

        /**
         * Update the handler
         * 
         * @param handler - The Handler for sending data to the UI thread
         */
        public void setHandler(Handler handler) {
            this.handler = handler;
        }

        /**
         * Constructor for BTCommThread
         * 
         * @param handler - The Handler for sending data to the UI thread
         */
        public BTCommThread(Handler handler) {
            this.connected = true;
            setHandler(handler);
        }

        /**
         * The main loop for the communication
         */
        @Override
        @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
        public void run() {
            while (isRunning) {
                // keep trying to reconnect if not connected
                // should initially be connected when the thread is started
                if (!connected) {
                    try {
                        connect();
                        connected = true;
                        handler.sendEmptyMessage(ConnectionStatus.RECONNECTED);
                    } catch (IOException ignored) {
                        continue;
                    }
                }
                // keep reading data
                try {
                    byte[] mmBuffer = new byte[4];
                    int bytesRead = inputStream.read(mmBuffer);
                    if (bytesRead > 0) {
                        Integer receivedData = ByteBuffer.wrap(mmBuffer).getInt();
                        handler.sendMessage(handler.obtainMessage(IOStatus.READ_BYTES, receivedData));
                    }
                } catch (IOException e) {
                    // attempt reconnection if we should be connected
                    if (isRunning && connected) {
                        try {
                            socket.close();
                        } catch (IOException ignored) {
                        }
                        handler.sendEmptyMessage(ConnectionStatus.DISCONNECTED);
                        connected = false;
                    }
                }
            }
        }

        /**
         * Write data to the output stream
         * 
         * @param data - The data to be written
         */
        public void write(byte[] data) {
            synchronized (this) {
                try {
                    outputStream.write(data);
                    outputStream.flush();
                } catch (IOException e) {
                    handler.sendMessage(handler.obtainMessage(IOStatus.WRITE_FAILED, e.getMessage()));
                }
            }
        }

    }
}
