#ifndef BT_CONSTANTS_H
#define BT_CONSTANTS_H

#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/rfcomm.h>

// logging functions
#define bt_log(format, ...) printf("\e[1;49;93m[BLUETOOTH]:\e[0m" format "\n", ##__VA_ARGS__)
#define bt_err(format, ...) printf("\e[1;49;91m[BLUETOOTH-ERR]:\e[0m" format "\n", ##__VA_ARGS__)

/**
 * Hardcoded address of the bluetooth client
 * Prevents anyone else from connecting to the RPI
 */
#define BLUETOOTH_CLIENT_ADDR "90:EE:C7:E7:D3:C2"

/**
 * Enum to indicate the direction of the obstacle
 */
enum Direction {
    NONE = 255,
    FORWARD = 0,
    BACKWARD = 1,
    RIGHT = 2,
    LEFT = 3
};

/**
 * Converts a direction to a string for logging
 * 
 * @param direction The direction to convert
 * @return The string representation of the direction
 */
const char* direction2str(enum Direction direction){
    switch (direction)
    {
        case FORWARD:
            return "Up";
        
        case BACKWARD:
            return "Down";
        
        case RIGHT:
            return "Right";
        
        case LEFT:
            return "Left";
        
        default:
            return "None";
    }
}

/**
 * Enum for the messages sent from the RPI to the ARCM
 */
enum RPI_TO_ARCM {
    TARGET_DISCOVERED = 1,
    FINISHED_WEEK9 = 2,
    STATUS_UPDATE = 3,
    FINISHED_WEEK8 = 4
};

/**
 * Enum for the type of status message
 */
enum StatusMessages {
    STARTING_WEEK8 = 1,
    STARTING_WEEK9 = 2,
    OPENING_CAMERA = 3,
    IDENTIFYING_IMAGE = 4,
    GOING_BACK = 5,
    NEXT_OBSTACLE = 6,
    RECEIVED_TARGET = 7
};

#endif