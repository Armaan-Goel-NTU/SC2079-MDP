#ifndef STM_CONSTANTS_H
#define STM_CONSTANTS_H

#include <string.h>
#include <fcntl.h>
#include <termios.h>

// logging functions
#define stm_log(format, ...) printf("\e[1;49;36m[STM]:\e[0m" format "\n", ##__VA_ARGS__)
#define stm_err(format, ...) printf("\e[1;49;91m[STM-ERR]:\e[0m" format "\n", ##__VA_ARGS__)

// The file descriptor for the serial port
#define SERIAL_PORT "/dev/ttyUSB0"

// The baud rate for the serial port
#define BAUD_RATE B115200 

#endif
