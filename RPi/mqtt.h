#ifndef MQTT_CONSTANTS_H
#define MQTT_CONSTANTS_H

#include <MQTTAsync.h>

// logging functions
#define mqtt_rpi(format, ...) printf("\e[1;49;95m[MQTT]:\e[0m" format "\n", ##__VA_ARGS__)
#define mqtt_cam(format, ...) printf("\e[1;49;92m[MQTT-CAMERA]:\e[0m" format "\n", ##__VA_ARGS__)
#define mqtt_img(format, ...) printf("\e[1;49;94m[MQTT-IMGREC]:\e[0m" format "\n", ##__VA_ARGS__)
#define mqtt_algo(format, ...) printf("\e[1;49;34m[MQTT-ALGO]:\e[0m" format "\n", ##__VA_ARGS__)
#define mqtt_err(format, ...) printf("\e[1;49;91m[MQTT-ERR]:\e[0m" format "\n", ##__VA_ARGS__)

// MQTT parameters
#define ALIVE_INTERVAL          10
#define ADDRESS                 "localhost:1883"
#define CLIENTID                "RPi_Client"
#define QOS                     1
#define CLIENT_USERNAME         "rpimdp3"
#define CLIENT_PASSWORD         "MDPGr@up3!"

// MQTT topics (for pub/sub)
#define TOPIC_SUBSCRIBE_ALGO    "path_data"
#define TOPIC_PUBLISH_ALGO      "run_algo"
#define TOPIC_SUBSCRIBE_IMGREC  "detected_target"
#define TOPIC_PUBLISH_IMGREC    "run_recognition"
#define TOPIC_SUBSCRIBE_CAMERA  "picture_taken"
#define TOPIC_PUBLISH_CAMERA    "take_picture"

// Most algorithm commands are not referred to individually in the RPi Code
// Only IMAGE_STOP is used to inititate the image recognition process
#define IMAGE_STOP 7

/**
 * Converts an algo command to a string for loggin
 * 
 * @param command The command to convert
 * @return The string representation of the command
 */
const char* int2command(int command){
    switch(command){
        case 1:
            return "FC";
        case 2:
            return "FR";
        case 3:
            return "FL";
        case 4:
            return "BC";
        case 5:
            return "BR";
        case 6:
            return "BL";
        case 7:
            return "ST";
    }
}

// MQTT callbacks
void onSendFailure(void* context, MQTTAsync_failureData* response) { mqtt_err("Message send failed token %d error code %d", response->token, response->code); }
void onConnectFailure(void* context, MQTTAsync_failureData* response) { mqtt_err("Connect failed, rc %d", response->code); }
void onSubscribe(void* context, MQTTAsync_successData* response) { mqtt_rpi("Subscribe succeeded"); }
void onSubscribeFailure(void* context, MQTTAsync_failureData* response) { mqtt_err("Subscribe failed, rc %d", response->code); }

#endif