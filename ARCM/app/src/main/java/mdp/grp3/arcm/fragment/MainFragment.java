package mdp.grp3.arcm.fragment;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.codertainment.dpadview.DPadView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import mdp.grp3.arcm.constant.Task;
import mdp.grp3.arcm.constant.Direction;
import mdp.grp3.arcm.constant.RPI_TO_ARCM;
import mdp.grp3.arcm.util.BluetoothConnection;
import mdp.grp3.arcm.component.FullscreenMaterialDialog;
import mdp.grp3.arcm.util.NavHelper;
import mdp.grp3.arcm.component.ObstacleView;
import mdp.grp3.arcm.R;
import mdp.grp3.arcm.component.RotatedDragShadowBuilder;
import mdp.grp3.arcm.databinding.FragmentMainBinding;
import mdp.grp3.arcm.util.ThemeManager;

/**
 * The main fragment that allows the user to interact with the robot car and
 * obstacles.
 */
public class MainFragment extends Fragment {

    // these are internally used for drag and drop
    enum Cell {
        EMPTY,
        OBSTACLE,
        CAR
    }

    private static final String obstacleLabel = "obstacle";
    private static final String carLabel = "robotCar";
    private static final int cellSize = 34;

    private FragmentMainBinding binding;
    private BluetoothConnection bluetoothConnection;
    private FullscreenMaterialDialog reconnectionDialog;
    private boolean obstacleDrag;
    private Rect gridRect;
    private Cell[][] grid;
    private Pair<Integer, Integer> robotPos;
    private SpannableStringBuilder textBuilder;
    private RotatedDragShadowBuilder shadowBuilder;

    private TextView timerTextView;
    private Button timerButton;
    private long startTime = 0;
    private boolean timerRunning = false;
    private Handler timeHandler;

    private int axisRow = -1;
    private int axisColumn = -1;

    /**
     * Updates the timer for a task.
     */
    private void updateTimer() {
        long timeInMilliseconds = System.currentTimeMillis() - startTime;
        int seconds = (int) (timeInMilliseconds / 1000);
        int minutes = seconds / 60;
        int milliseconds = (int) (timeInMilliseconds % 1000);
        seconds = seconds % 60;
        timerTextView.setText(String.format(Locale.ENGLISH, "%02d:%02d:%03d", minutes, seconds, milliseconds));
    }

    /**
     * Resets the timer for a task.
     */
    private void stopTimer() {
        if (startTime == 0)
            return; // start time = 0 means the timer didn't start, so no need to stop
        timerRunning = false;
        timeHandler.removeCallbacks(stopWatch);
        timerButton.setText(R.string.reset_timer);
        // blinking animation
        Animation anim = new AlphaAnimation(1.0f, 0.0f);
        anim.setDuration(700);
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        timerTextView.startAnimation(anim);
    }

    /**
     * The stopwatch that updates the timer every millisecond.
     */
    private final Runnable stopWatch = new Runnable() {
        @Override
        public void run() {
            updateTimer();
            timeHandler.postDelayed(this, 1);
        }
    };

    /**
     * The handler for the Bluetooth connection.
     */
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == BluetoothConnection.IOStatus.READ_BYTES) {
                // we have received a message from the RPi
                byte[] received = ByteBuffer.allocate(4).putInt((Integer) msg.obj).array();
                if (received[0] == RPI_TO_ARCM.TARGET_DISCOVERED) {
                    // important to show which obstacle has been discovered
                    int obstacle = received[1];
                    int targetId = received[2];
                    // the range is given in the task instructions
                    if (obstacle >= 0 && obstacle < ObstacleView.numObstacles && targetId >= 11 && targetId <= 40) {
                        ObstacleView obstacleView = (ObstacleView) binding.parentRelative.getChildAt(obstacle);
                        obstacleView.setDiscovered(targetId);
                    }
                } else if (received[0] == RPI_TO_ARCM.STATUS_UPDATE) {
                    // status update
                    String receivedMessage = RPI_TO_ARCM.StatusMessages.messageMap.get((char) received[1]);
                    if (received[1] == RPI_TO_ARCM.StatusMessages.RECEIVED_TARGET)
                        receivedMessage += (int) received[2];
                    if (received[1] == RPI_TO_ARCM.StatusMessages.FINISHED_WEEK8
                            || received[1] == RPI_TO_ARCM.StatusMessages.FINISHED_WEEK9) {
                        stopTimer();
                    }
                    boolean atEnd = !binding.messageScroll.canScrollVertically(1);
                    // add this to the message box
                    String messageBoxText = binding.messageBox.getText().toString();
                    int start = textBuilder.length();
                    if (!messageBoxText.isEmpty())
                        textBuilder.append("\n");
                    textBuilder.append(receivedMessage);
                    int end = textBuilder.length();
                    // grey out everything before this message
                    textBuilder.setSpan(
                            new ForegroundColorSpan(ThemeManager.getColor(requireContext(),
                                    com.google.android.material.R.attr.colorPrimary)),
                            start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    textBuilder.setSpan(
                            new ForegroundColorSpan(0x90000000 | (ThemeManager.getColor(requireContext(),
                                    com.google.android.material.R.attr.colorPrimary) - 0xFF000000)),
                            0, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    binding.messageBox.setText(textBuilder);
                    // this auto scrolls the message box if the scroll position was at the end of
                    // the box
                    binding.messageBox.post(() -> {
                        if (atEnd)
                            binding.messageScroll.fullScroll(NestedScrollView.FOCUS_DOWN);
                    });
                }
            } else if (msg.what == BluetoothConnection.IOStatus.WRITE_FAILED) {
                // just in case the write fails, never really happened
                Snackbar.make(binding.getRoot(), "❌ " + msg.obj, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(
                                ThemeManager.getColor(requireContext(), com.google.android.material.R.attr.colorAccent))
                        .setTextColor(ThemeManager.getColor(requireContext(),
                                com.google.android.material.R.attr.colorPrimaryDark))
                        .show();
            } else if (msg.what == BluetoothConnection.ConnectionStatus.DISCONNECTED) {
                // inform the user that the connection was lost
                binding.connectionIndicator.setColorFilter(Color.RED);
                final ProgressBar progressBar = new ProgressBar(requireContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                progressBar.setLayoutParams(lp);
                // prevents any interaction until reconnected
                reconnectionDialog = new FullscreenMaterialDialog(new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("❌ Disconnected")
                        .setMessage("Please Wait! Reconnecting…")
                        .setView(progressBar)
                        .setCancelable(false)
                        .setNeutralButton("Cancel Reconnection", (dialog, which) -> disconnect()));
                reconnectionDialog.show();
            } else if (msg.what == BluetoothConnection.ConnectionStatus.RECONNECTED) {
                // closes the reconnection dialog and informs the user
                reconnectionDialog.cancel();
                binding.connectionIndicator.setColorFilter(Color.GREEN);
                Snackbar.make(binding.getRoot(), "Back Online ✅", Snackbar.LENGTH_SHORT)
                        .setBackgroundTint(
                                ThemeManager.getColor(requireContext(), com.google.android.material.R.attr.colorAccent))
                        .setTextColor(ThemeManager.getColor(requireContext(),
                                com.google.android.material.R.attr.colorPrimaryDark))
                        .show();
            }
        }
    };

    /**
     * Disconnects the Bluetooth connection and navigates to the permission fragment
     * (the initial fragment).
     */
    private void disconnect() {
        bluetoothConnection.closeCommunication();
        NavHelper.safeNavigate(MainFragment.this, R.id.action_MainFragment_to_PermissionFragment);
    }

    /**
     * Adds the obstacles to the grid.
     */
    private void addObstacles() {
        ObstacleView.setCardPos(binding.obstaclesCard.getX(), binding.obstaclesCard.getY());
        binding.parentRelative.setElevation(15);
        for (int idx = 0; idx < ObstacleView.numObstacles; idx++) {
            ObstacleView v = new ObstacleView(getContext(), idx + 1);
            setDraggable(v, obstacleLabel);
            binding.parentRelative.addView(v, idx);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // initialize grid and other variables
        gridRect = new Rect();
        grid = new Cell[21][21];
        Arrays.stream(grid).forEach(a -> Arrays.fill(a, Cell.EMPTY));
        textBuilder = new SpannableStringBuilder();
        timeHandler = new Handler(Looper.getMainLooper());
        bluetoothConnection = BluetoothConnection.getInstance();
        binding = FragmentMainBinding.inflate(inflater, container, false);
        binding.obstaclesCard.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // add obstacles after the card has been laid out
                        addObstacles();
                        // tweak the position of the grid and obstacles card
                        binding.gridBackground.setX(binding.gridBackground.getX() - 4);
                        binding.grid.setX(binding.grid.getX() - 5);
                        // save the position of the main grid
                        binding.grid.getHitRect(gridRect);
                        // we only need to do this once
                        binding.obstaclesCard.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
        return binding.getRoot();
    }

    /**
     * Creates the grid layout for the obstacles.
     *
     * @param row the row of the cell
     * @param col the column of the cell
     * @return the layout parameters for the cell
     */
    private GridLayout.LayoutParams getLayoutFor(int row, int col) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = cellSize;
        params.height = cellSize;
        params.rowSpec = GridLayout.spec(row);
        params.columnSpec = GridLayout.spec(col);
        return params;
    }

    /**
     * Helps in writing the numbers for the axis cells.
     * 
     * @param num   the number of the axis cell
     * @param isRow whether the cell is a row cell
     * @return the label for the axis cells
     */
    private TextView getAxisLabel(int num, boolean isRow) {
        TextView v = new TextView(getContext());
        if (num == 0)
            return v;
        v.setText(String.valueOf(num - 1));
        v.setTypeface(Typeface.DEFAULT_BOLD);
        // change color depending on light/dark mode
        if ((requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            v.setTextColor(ThemeManager.getColor(requireContext(), com.google.android.material.R.attr.colorAccent));
        } else {
            v.setTextColor(
                    ThemeManager.getColor(requireContext(), com.google.android.material.R.attr.colorPrimaryDark));
        }
        // set gravity and text alignment
        if (isRow) {
            v.setGravity(Gravity.CENTER);
            v.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        } else {
            v.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }
        v.setTextSize(10);
        return v;
    }

    /**
     * Makes a view draggable.
     * 
     * @param v     the view to make draggable
     * @param label the label of the view
     */
    private void setDraggable(View v, String label) {
        v.setOnLongClickListener((v1 -> {
            ClipData data = ClipData.newPlainText(label, "");
            shadowBuilder = new RotatedDragShadowBuilder(v1);
            v.startDragAndDrop(data, shadowBuilder, v1, 0);
            v.setVisibility(View.INVISIBLE);
            return true;
        }));
    }

    /**
     * Changes the type of the cells occupied by the robot.
     * 
     * @param what The cell type to change to
     */
    private void changeRobotGrid(Cell what) {
        if (robotPos == null)
            return;
        int row = robotPos.first;
        int col = robotPos.second;
        grid[row][col] = what;
        grid[row][col + 1] = what;
        grid[row + 1][col] = what;
        grid[row + 1][col + 1] = what;
    }

    /**
     * Sets the rotation of the robot.
     * 
     * @param rotation The rotation to set (in degrees)
     */
    private void setRotation(float rotation) {
        binding.robotCar.setRotation(rotation % 360);
        updateStatus();
    }

    /**
     * Rotates the robot by a certain amount.
     * 
     * @param rotation The amount to rotate by (in degrees)
     */
    public void rotateBy(float rotation) {
        setRotation(binding.robotCar.getRotation() + rotation);
    }

    /**
     * Updates the position and direction of the robot.
     */
    private void updateStatus() {
        final String[] dirs = new String[] { "North", "East", "South", "West" };
        int dirIndex = (int) binding.robotCar.getRotation() / 90, x, y;
        String statusString = "";
        if (robotPos != null) {
            y = 18 - robotPos.first;
            x = robotPos.second - 1;
            statusString += "X: " + x + ", Y: " + y;
        } else
            statusString += "Ready";
        statusString += "\nFacing " + dirs[dirIndex];
        binding.status.setText(statusString);
    }

    /**
     * Changes the color of the grid cell to indicate the robot has visited it.
     * 
     * @param row The row of the cell
     * @param col The column of the cell
     */
    private void changeGridColor(int row, int col) {
        binding.grid.getChildAt((row * 21) + col)
                .setBackgroundColor(ThemeManager.getColor(requireContext(), androidx.appcompat.R.attr.colorAccent));
    }

    /**
     * Moves the robot to a new position.
     * 
     * @param rRow             The row to move to
     * @param rCol             The column to move to
     * @param allowOutOfBounds Whether to allow the robot to move out of bounds
     * @return if the move was successful
     */
    private boolean moveRobot(int rRow, int rCol, boolean allowOutOfBounds) {
        int row = Math.max(0, Math.min(rRow, 18));
        int col = Math.max(1, Math.min(rCol, 19));
        if ((row != rRow || col != rCol) && !allowOutOfBounds)
            return false;
        // if any of the cells are obstacles, don't move
        if (grid[row][col] == Cell.OBSTACLE || grid[row + 1][col] == Cell.OBSTACLE
                || grid[row][col + 1] == Cell.OBSTACLE || grid[row + 1][col + 1] == Cell.OBSTACLE)
            return false;
        // mark the current cells occupied as empty
        changeRobotGrid(Cell.EMPTY);
        robotPos = new Pair<>(row, col);
        // mark the new cells as occupied by the robot
        changeRobotGrid(Cell.CAR);
        // change car object position
        View cell = binding.grid.getChildAt((row * 21) + col);
        binding.robotCar.setX(binding.grid.getX() + cell.getX() + 2);
        binding.robotCar.setY(binding.grid.getY() + cell.getY() + 2);
        binding.robotCar.setElevation(15);
        // mark the new cells as visited
        changeGridColor(row, col);
        changeGridColor(row + 1, col);
        changeGridColor(row, col + 1);
        changeGridColor(row + 1, col + 1);
        updateStatus();
        return true;
    }

    /**
     * Resets the fragment by detaching and reattaching the fragment.
     */
    private void resetFragment() {
        getParentFragmentManager().beginTransaction().detach(MainFragment.this).commit();
        getParentFragmentManager().beginTransaction().attach(MainFragment.this).commit();
    }

    /**
     * Turns the robot in a certain direction.
     * 
     * @param direction The direction to turn the robot
     */
    private void turnRobot(DPadView.Direction direction) {
        int row = robotPos.first;
        int col = robotPos.second;

        // originally these changes were marked for each type of turn from each
        // direction
        // they were later reduced to a single change for each direction using
        // trigonometry
        double rad = Math.toRadians(binding.robotCar.getRotation());
        double dY = -Math.cos(rad), dX = Math.sin(rad);
        int addedRotation = 0;
        if (direction == DPadView.Direction.RIGHT) {
            dX += Math.cos(rad);
            dY += Math.sin(rad);
            addedRotation = 90;
        } else if (direction == DPadView.Direction.LEFT) {
            dX -= Math.cos(rad);
            dY -= Math.sin(rad);
            addedRotation = 270;
        } else if (direction == DPadView.Direction.DOWN) {
            dX = -Math.sin(rad);
            dY = Math.cos(rad);
        }
        col += (int) Math.round(dX);
        row += (int) Math.round(dY);
        if (moveRobot(row, col, false))
            if (addedRotation != 0)
                rotateBy(addedRotation);
    }

    /**
     * @return the number of obstacles with directions assigned
     */
    private int getDirectedObstacles() {
        int val = 0;
        for (int i = 0; i < ObstacleView.numObstacles; i++) {
            ObstacleView obstacleView = (ObstacleView) binding.parentRelative.getChildAt(i);
            if (obstacleView.getGridPos() != null && obstacleView.getDirection() != Direction.NONE)
                val++;
        }
        return val;
    }

    /**
     * Callback for the timer buttons.
     * 
     * @param timerWeek The week of the task
     */
    private void timerButtonCallback(int timerWeek) {
        timerButton = timerWeek == Task.WEEK8 ? binding.wk8button : binding.wk9button;
        timerTextView = timerWeek == Task.WEEK8 ? binding.wk8timer : binding.wk9timer;
        Button oppositeTimerButton = timerWeek == Task.WEEK8 ? binding.wk9button : binding.wk8button;
        if (startTime == 0) {
            // "This means the button was showing Start WeekX"
            // allow if week9 or if week8 conditions met
            if (timerWeek == Task.WEEK9 || (robotPos != null && getDirectedObstacles() >= 4)) {
                if (timerWeek == Task.WEEK8) {
                    // need to build the arena buffer for pathfinding algorithm
                    ArrayList<Byte> arenaBuffer = new ArrayList<>();

                    // add car position info
                    arenaBuffer.add((byte) (robotPos.second - 1));
                    arenaBuffer.add((byte) (18 - robotPos.first));
                    switch ((int) binding.robotCar.getRotation()) {
                        case 90:
                            arenaBuffer.add((byte) 2);
                            break;
                        case 180:
                            arenaBuffer.add((byte) 1);
                            break;
                        case 270:
                            arenaBuffer.add((byte) 3);
                            break;
                        default:
                            arenaBuffer.add((byte) 0);
                            break;
                    }

                    // add obstacle info
                    for (int i = 0; i < ObstacleView.numObstacles; i++) {
                        ObstacleView obstacleView = (ObstacleView) binding.parentRelative.getChildAt(i);
                        if (obstacleView.getGridPos() != null && obstacleView.getDirection() != Direction.NONE) {
                            Pair<Integer, Integer> pos = obstacleView.getGridPos();
                            arenaBuffer.add((byte) (pos.second - 1));
                            arenaBuffer.add((byte) (19 - pos.first));
                            arenaBuffer.add((byte) obstacleView.getDirection());
                        }
                    }

                    // convert to byte array and send
                    byte[] byteArray = new byte[arenaBuffer.size()];
                    int index = 0;
                    for (byte b : arenaBuffer) {
                        byteArray[index++] = b;
                    }
                    bluetoothConnection.write(byteArray);
                } else {
                    // just tell RPi to go
                    bluetoothConnection.write(new byte[] { 'G', 'O' });
                }
                // disable the other week's timer button
                oppositeTimerButton.setEnabled(false);
                oppositeTimerButton.setTextColor(ThemeManager.getColor(requireContext(),
                        com.google.android.material.R.attr.colorSecondaryVariant));

                // start the timer
                timerRunning = true;
                startTime = System.currentTimeMillis();
                timeHandler.post(stopWatch);
                timerButton.setText(String.format(Locale.ENGLISH, "Running Week%d", timerWeek));
            } else {
                // Week 8 conditions not met
                Snackbar.make(binding.getRoot(), "Car and at least 4 obstacles with direction must be placed!",
                        Snackbar.LENGTH_SHORT)
                        .setBackgroundTint(
                                ThemeManager.getColor(requireContext(), com.google.android.material.R.attr.colorAccent))
                        .setTextColor(ThemeManager.getColor(requireContext(),
                                com.google.android.material.R.attr.colorPrimaryDark))
                        .show();
            }
        } else if (timerRunning) {
            // this means the button was showing "Running WeekX"
            stopTimer();
        } else {
            // this means the button was showing "Reset Timer"
            timerTextView.clearAnimation();
            timerTextView.setText(R.string.default_timer);
            timerButton.setText(String.format(Locale.ENGLISH, "Start Week%d", timerWeek));
            oppositeTimerButton.setEnabled(true);
            oppositeTimerButton.setTextColor(
                    ThemeManager.getColor(requireContext(), com.google.android.material.R.attr.colorPrimaryDark));
            startTime = 0;
        }
    }

    /**
     * Changes the color for the axes cells when being dragged over.
     * 
     * @param row    The row of the cell
     * @param column The column of the cell
     */
    private void changeAxesColors(int row, int column) {
        if (axisRow != -1) {
            // reset the color if the current row is not -1
            binding.grid.getChildAt(axisRow * 21).setBackgroundColor(Color.TRANSPARENT);
            binding.grid.getChildAt(21 * 20 + axisColumn).setBackgroundColor(Color.TRANSPARENT);
        }
        axisRow = row;
        axisColumn = column;
        if (row != -1) {
            // if the new row is not -1, change the color to indicate this cell is being
            // dragged over
            int bgColor;
            if ((requireContext().getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                bgColor = ThemeManager.getColor(requireContext(), com.google.android.material.R.attr.colorPrimaryDark);
            } else {
                bgColor = ThemeManager.getColor(requireContext(), com.google.android.material.R.attr.colorAccent);
            }
            binding.grid.getChildAt(axisRow * 21).setBackgroundColor(bgColor);
            binding.grid.getChildAt(21 * 20 + axisColumn).setBackgroundColor(bgColor);
        }
    }

    /**
     * Callback for bluetooth connection status.
     */
    private final Handler handler2 = new Handler(Looper.getMainLooper()) {
        @RequiresPermission(allOf = { "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN" })
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == BluetoothConnection.ConnectionStatus.CONNECTED) {
                bluetoothConnection.setupCommunication(handler);
                Snackbar.make(binding.getRoot(), "Connected to RPi!", Snackbar.LENGTH_LONG).show();
                binding.connectedDevice.setText(bluetoothConnection.getDevice().getName());
                binding.connectionIndicator.setColorFilter(Color.GREEN);
                binding.connect.setText(R.string.connected);
            } else if (msg.what == BluetoothConnection.ConnectionStatus.FAILED) {
                Snackbar.make(binding.getRoot(), "❌ " + msg.obj, Snackbar.LENGTH_LONG).show();
            }
        }
    };

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.connectionIndicator.setColorFilter(Color.RED);
        binding.connect.setOnClickListener(v -> {
            // show list of paired devices to connect to
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            ArrayList<String> deviceNames = new ArrayList<>();
            ArrayList<String> deviceMACs = new ArrayList<>();
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                deviceNames.add(device.getName());
                deviceMACs.add(device.getAddress());
            }
            ArrayAdapter<String> listAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1,
                    deviceNames);
            new FullscreenMaterialDialog(new MaterialAlertDialogBuilder(requireContext()).setTitle("Connect")
                    .setAdapter(listAdapter, (dialog, which) -> {
                        bluetoothConnection.tryConnection(deviceMACs.get(which), handler2);
                        Snackbar.make(binding.getRoot(), "Connecting Now", Snackbar.LENGTH_LONG).show();
                    })).show();
        });
        binding.dpad.setOnDirectionClickListener(direction -> {
            if (direction != null && robotPos != null && direction != DPadView.Direction.CENTER) {
                turnRobot(direction);
            }
            return null;
        });
        binding.theme
                .setOnClickListener(v -> new FullscreenMaterialDialog(new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Choose Theme")
                        .setItems(ThemeManager.getThemes(), (dialog, which) -> {
                            ThemeManager.setNewTheme(requireActivity(), which);
                            resetFragment();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                        })
                        .setPositiveButton("Reset", (dialog, which) -> resetFragment())).show());
        binding.wk8button.setOnClickListener((v1) -> timerButtonCallback(Task.WEEK8));
        binding.wk9button.setOnClickListener((v1) -> timerButtonCallback(Task.WEEK9));
        binding.robotCar.setOnClickListener(v -> rotateBy(90));
        setDraggable(binding.robotCar, carLabel);

        // Drag handler
        binding.MainFragment.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // if an obstacle is being dragged from the grid
                    obstacleDrag = (gridRect.contains((int) event.getX(), (int) event.getY())
                            && event.getClipDescription().getLabel().equals(obstacleLabel));
                    break;
                case DragEvent.ACTION_DROP:
                    View draggedView = (View) event.getLocalState();

                    float eventX = event.getX() - draggedView.getWidth() / 2.0f;
                    float eventY = event.getY() - draggedView.getHeight() / 2.0f;

                    if (event.getClipDescription().getLabel().equals(obstacleLabel)) {
                        // reset the dragged position
                        changeAxesColors(-1, -1);
                        binding.coordPreview.setText("");
                    }

                    if (gridRect.contains((int) eventX, (int) eventY)) {
                        // being dropped into the grid
                        float xRelativeToGrid = eventX - binding.grid.getX();
                        float yRelativeToGrid = eventY - binding.grid.getY();

                        float cellWidth = binding.grid.getWidth() / 21.0f;
                        float cellHeight = binding.grid.getHeight() / 21.0f;

                        if (event.getClipDescription().getLabel().equals(obstacleLabel)) {
                            // its an obstacle that was dragged
                            int row = Math.max(0, Math.min((int) (yRelativeToGrid / cellHeight), 19));
                            int column = Math.max(1, Math.min((int) (xRelativeToGrid / cellWidth), 20));
                            if (grid[row][column] == Cell.EMPTY) {
                                // only allowed if new cell is empty
                                View cell = binding.grid.getChildAt((row * 21) + column);
                                ObstacleView obstacleView = (ObstacleView) draggedView;
                                obstacleView.setLayoutParams(new RelativeLayout.LayoutParams(cellSize, cellSize));
                                draggedView.setX(binding.grid.getX() + cell.getX() - 21);
                                draggedView.setY(binding.grid.getY() + cell.getY() - 21);
                                if (!obstacleDrag) {
                                    // if it was dragged from one cell to the other then update the background
                                    obstacleView.post(obstacleView::updateBackground);
                                }
                                Pair<Integer, Integer> currentPos = obstacleView.getGridPos();
                                if (currentPos != null) {
                                    grid[currentPos.first][currentPos.second] = Cell.EMPTY;
                                }
                                obstacleView.setOnGrid(new Pair<>(row, column));
                                grid[row][column] = Cell.OBSTACLE;
                            }
                        } else if (event.getClipDescription().getLabel().equals(carLabel)) {
                            // change car position
                            moveRobot((int) (yRelativeToGrid / cellHeight), (int) (xRelativeToGrid / cellWidth), true);
                        }
                    } else if (obstacleDrag && event.getClipDescription().getLabel().equals(obstacleLabel)) {
                        // the obstacle has been dragged out of the grid
                        ObstacleView obstacleView = (ObstacleView) draggedView;
                        Pair<Integer, Integer> currentPos = obstacleView.getGridPos();
                        grid[currentPos.first][currentPos.second] = Cell.EMPTY;
                        obstacleView.resetPosition();
                        binding.obstaclesCard.setBackgroundColor(ThemeManager.getColor(requireContext(),
                                com.google.android.material.R.attr.colorSecondary));
                        binding.trash.setAlpha(0.0f);
                    }
                    draggedView.setVisibility(View.VISIBLE);
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    if (event.getClipDescription().getLabel().equals(carLabel)) {
                        // we check if the car is in danger of hitting an obstacle
                        boolean danger = true;
                        if (gridRect.contains((int) event.getX(), (int) event.getY())) {
                            float xRelativeToGrid = event.getX() - binding.robotCar.getWidth() / 2.0f
                                    - binding.grid.getX();
                            float yRelativeToGrid = event.getY() - binding.robotCar.getHeight() / 2.0f
                                    - binding.grid.getY();

                            float cellWidth = binding.grid.getWidth() / 21.0f;
                            float cellHeight = binding.grid.getHeight() / 21.0f;

                            int rRow = (int) (yRelativeToGrid / cellHeight), rCol = (int) (xRelativeToGrid / cellWidth);
                            int row = Math.max(0, Math.min(rRow, 18)), col = Math.max(1, Math.min(rCol, 19));
                            if (row == rRow && col == rCol) {
                                if (grid[row][col] != Cell.OBSTACLE && grid[row + 1][col] != Cell.OBSTACLE
                                        && grid[row][col + 1] != Cell.OBSTACLE
                                        && grid[row + 1][col + 1] != Cell.OBSTACLE) {
                                    danger = false;
                                }
                            }
                        }
                        shadowBuilder.setDanger(danger);
                        binding.robotCar.updateDragShadow(shadowBuilder);
                    } else {
                        if (gridRect.contains((int) event.getX(), (int) event.getY())) {
                            // obstacle being dragged in the grid, find the cell being dragged over
                            View v1 = (View) event.getLocalState();
                            float xRelativeToGrid = event.getX() - v1.getWidth() / 2.0f - binding.grid.getX();
                            float yRelativeToGrid = event.getY() - v1.getHeight() / 2.0f - binding.grid.getY();

                            float cellWidth = binding.grid.getWidth() / 21.0f;
                            float cellHeight = binding.grid.getHeight() / 21.0f;

                            int row = Math.max(0, Math.min((int) (yRelativeToGrid / cellHeight), 19));
                            int column = Math.max(1, Math.min((int) (xRelativeToGrid / cellWidth), 20));

                            // update the drag indicators
                            binding.coordPreview
                                    .setText(String.format(Locale.ENGLISH, "(%d,%d)", column - 1, 20 - row - 1));
                            changeAxesColors(row, column);
                        } else {
                            // obstacle being dragged out of grid. reset drag position indicators.
                            changeAxesColors(-1, -1);
                            binding.coordPreview.setText("");
                        }
                    }
                    if (!obstacleDrag)
                        break;
                    // highlight the obstacles card and show the trash icon if the obstacle is being
                    // dragged out of the grid
                    if (!gridRect.contains((int) event.getX(), (int) event.getY())) {
                        binding.obstaclesCard.setBackgroundColor(ThemeManager.getColor(requireContext(),
                                com.google.android.material.R.attr.colorPrimary));
                        binding.trash.setAlpha(1.0f);
                    } else {
                        binding.obstaclesCard.setBackgroundColor(ThemeManager.getColor(requireContext(),
                                com.google.android.material.R.attr.colorSecondary));
                        binding.trash.setAlpha(0.0f);
                    }
                    break;
            }
            return true;
        });

        // initialize the grid
        for (int row = 0; row < 21; row++) {
            for (int col = 0; col < 21; col++) {
                View v;
                GridLayout.LayoutParams params = getLayoutFor(row, col);
                if (col == 0) {
                    v = getAxisLabel(20 - row, true);
                    params.setMargins(1, 0, 4, 0);
                } else if (row == 20) {
                    v = getAxisLabel(col, false);
                } else {
                    v = new View(getContext());
                    v.setBackgroundColor(Color.parseColor("#FFFFFF"));
                    params.setMargins(1, 1, 1, 1);
                }
                binding.grid.addView(v, (row * 21) + col, params);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}