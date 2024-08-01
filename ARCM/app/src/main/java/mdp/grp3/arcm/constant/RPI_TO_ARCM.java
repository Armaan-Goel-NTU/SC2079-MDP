package mdp.grp3.arcm.constant;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that contains the constants for the messages sent from the RPi to the
 * ARCM
 */
public final class RPI_TO_ARCM {
    public final static char TARGET_DISCOVERED = 1;
    public final static char STATUS_UPDATE = 3;

    public static final class StatusMessages {
        public static final char STARTING_WEEK8 = 1;
        public static final char STARTING_WEEK9 = 2;
        public static final char OPENING_CAMERA = 3;
        public static final char IDENTIFYING_IMAGE = 4;
        public static final char GOING_BACK = 5;
        public static final char NEXT_OBSTACLE = 6;
        public static final char RECEIVED_TARGET = 7;
        public static final char FINISHED_WEEK8 = 8;
        public static final char FINISHED_WEEK9 = 9;
        public static final Map<Character, String> messageMap = new HashMap<Character, String>() {
            {
                put(STARTING_WEEK8, "Starting Image Recognition Task [Week 8]");
                put(STARTING_WEEK9, "Starting Faster Car Task [Week 9]");
                put(OPENING_CAMERA, "Opening Camera to Take Picture");
                put(IDENTIFYING_IMAGE, "Sending Image for Recognition");
                put(GOING_BACK, "Going Back to Carpark");
                put(NEXT_OBSTACLE, "Going to the next Obstacle");
                put(RECEIVED_TARGET, "Detected Target ID: ");
                put(FINISHED_WEEK8, "FINISHED WEEK 8 TASK!");
                put(FINISHED_WEEK9, "FINISHED WEEK 9 TASK!");
            }
        };
    }
}
