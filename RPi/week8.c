#include "bt.h"
#include "mqtt.h"
#include "stm.h"
#include <pthread.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

/**
 * Android client's bluetooth socket (RPi will be listening)
 */
int bluetooth_client_sock = -1;

/**
 * MQTT client used for communication with the laptop over WiFi
 */
MQTTAsync client;

/**
 * File descriptor for the serial port connection to the STM32
 */
int stm32_fd = -1;

/**
 * Flag to indicate if the last stop has been reached
 */
bool last_stop = false;

/**
 * Flag to indicate if the program should terminate
 */
bool time_to_die = false;

/**
 * Flag to indicate if the program should handle the finish
 */
bool handle_finish = false;

/**
 * Used to keep track of the current obstacle being processed
 * Needed to send the correct obstacle ID to the Android client
 */
int current_obstacle;

/**
 * Data structure to store the path data from the algorithm
 */
struct PathData {
  char *path;
  int size;
  int i;
};

/**
 * Path data to be used by the STM32
 */
struct PathData path_data;

/**
 * Converts an algo command to a single character and sends it to the STM32
 *
 * @param command The command to convert
 * @param val The value to convert
 *
 */
void write_stm(char command, char val) {
  if (stm32_fd != -1) {
    char x = command;
    if (command == 1 || command == 4) {
      x += val;
    } else {
      x -= 2;
    }
    stm_log("Written %d", x);
    write(stm32_fd, &x, sizeof(x));
  } else
    stm_err("Cannot send message.");
}

/**
 * Sends a message to the Android client over bluetooth
 *
 * @param command The command to send
 * @param arg1 The first argument to send
 * @param arg2 The second argument to send
 * @param arg3 The third argument to send
 */
void write_bt(char command, char arg1, char arg2, char arg3) {
  if (bluetooth_client_sock != -1) {
    char write_buf[4] = {command, arg1, arg2, arg3};
    send(bluetooth_client_sock, write_buf, sizeof(write_buf), 0);
  } else
    bt_err("No client connected. Cannot send message.");
}

/**
 * Writes a message to the MQTT broker
 *
 * @param topic The topic to publish to
 * @param payload The payload to send
 * @param payload_size The size of the payload
 * @param wait Whether to wait for the message to be sent
 */
void write_mqtt(const char *topic, char *payload, int payload_size, bool wait) {
  MQTTAsync_responseOptions opts = MQTTAsync_responseOptions_initializer;
  MQTTAsync_message pubmsg = MQTTAsync_message_initializer;
  int rc;

  opts.onFailure = onSendFailure;
  opts.context = client;
  pubmsg.payload = payload;
  pubmsg.payloadlen = payload_size;
  pubmsg.qos = QOS;
  pubmsg.retained = 0;
  if ((rc = MQTTAsync_sendMessage(client, topic, &pubmsg, &opts)) !=
      MQTTASYNC_SUCCESS)
    mqtt_err("Failed to start sendMessage, return code %d", rc);
  if (wait)
    MQTTAsync_waitForCompletion(client, opts.token, 10000);
}

/**
 * Writes a message to the MQTT broker to close the connection
 *
 * @param topic The topic (camera client/image rec client) to publish to.
 */
void write_mqtt_close(const char *topic) {
  char close_msg[5] = {'c', 'l', 'o', 's', 'e'};
  write_mqtt(topic, &close_msg[0], sizeof(close_msg), true);
}

/**
 * Tells the camera client to take a picture and informs the Android client
 */
void take_picture() {
  char one = '1';
  write_mqtt(TOPIC_PUBLISH_CAMERA, &one, 1, false);
  mqtt_cam("Taking Picture");
  write_bt(STATUS_UPDATE, OPENING_CAMERA, 0, 0);
}

/**
 * Moves the STM32 according to the path data
 */
void move_stm() {
  // this will be called after the last obstacle's image recognition, so we're
  // handling that case
  if (path_data.i >= path_data.size) {
    handle_finish = true;
    return;
  }

  // delay to prevent the STM32 from receiving commands too quickly
  if (path_data.i != 0)
    sleep(1);

  // break down into command and value
  char command = path_data.path[path_data.i];
  char val = path_data.path[path_data.i + 1];

  if (command == IMAGE_STOP) {
    // we have reached the obstacle. update & take picture
    current_obstacle = val;
    take_picture();
  } else {
    // normal movement command. inform STM and log it.
    write_stm(command, val);
    stm_log("Executing %s %d", int2command(command), val);
  }

  // move to the next command & value
  path_data.i += 2;

  // to indicate that we have stopped at the last obstacle
  if (path_data.i >= path_data.size)
    last_stop = true;
}

/**
 * Thread function to handle the bluetooth connection
 */
void *bt_handler() {
  struct sockaddr_rc loc_addr = {0},
                     client_addr = {0}; // local bluetooth adapter's info
  int server_sock, bytes_read;

  bt_log("Start RFCOMM server…");
  server_sock = socket(AF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM);

  loc_addr.rc_family = AF_BLUETOOTH; // Addressing family, always AF_BLUETOOTH
  bacpy(&loc_addr.rc_bdaddr,
        BDADDR_ANY);       // Bluetooth address of local bluetooth adapter
  loc_addr.rc_channel = 1; // port number of local bluetooth adapter

  bt_log("Binding…");
  if (bind(server_sock, (struct sockaddr *)&loc_addr, sizeof(loc_addr)) < 0) {
    bt_err("failed to bind");
    exit(1);
  }

  bool read_from_socket = true;
  char read_buf[30] = {0};

  while (true) {
    // if the client is not connected, wait for a connection
    if (bluetooth_client_sock == -1) {
      bt_log("Listening…");

      listen(server_sock, 1);
      unsigned int opt = sizeof(client_addr);
      bluetooth_client_sock =
          accept(server_sock, (struct sockaddr *)&client_addr,
                 &opt); // return new socket for connection with a client

      char mac[32] = {0};
      ba2str(&client_addr.rc_bdaddr, mac);

      if (strcmp(mac, BLUETOOTH_CLIENT_ADDR) == 0) {
        bt_log("connected from %s", mac);
      } else {
        // just in case someone else tries to connect
        bt_err("received connection from unauthorized device %s", mac);
        close(bluetooth_client_sock);
        bluetooth_client_sock = -1;
        continue;
      }
    }

    // read from the client
    memset(read_buf, 0, sizeof(read_buf));
    bytes_read = recv(bluetooth_client_sock, read_buf, sizeof(read_buf), 0);

    // the android client sends all the obstacle data at once, which we pass on
    // to the algorithm the android client never sends any data after this
    if (bytes_read > 0) {
      bt_log("received arena buffer of length %d", bytes_read);
      bt_log("START WEEK8 IMAGE RECOGNITION TASK");
      write_mqtt(TOPIC_PUBLISH_ALGO, read_buf, bytes_read, false);
      write_bt(STATUS_UPDATE, STARTING_WEEK8, 0, 0);
    } else if (bytes_read == -1) {
      // we still need to stay connected to android, so close the connection and
      // wait for a new one android will always attempt to reconnect
      close(bluetooth_client_sock);
      bluetooth_client_sock = -1;

      // break out of the loop if we need to terminate
      if (time_to_die)
        break;
    }
  }

  bt_log("Closing Server");
  close(server_sock);

  // just handle STM32 disconnection here
  if (stm32_fd != -1) {
    stm_log("Closing Connection");
    close(stm32_fd);
  }
  return NULL;
}

/**
 * Callback function for when the MQTT client connects successfully to the
 broker

 * @param context The context passed in the connect options
 * @param response The response data (unused)
 */
void onConnect(void *context, MQTTAsync_successData *response) {
  MQTTAsync_responseOptions opts = MQTTAsync_responseOptions_initializer;
  int rc;

  opts.onSuccess = onSubscribe;
  opts.onFailure = onSubscribeFailure;
  opts.context = client;

  // subscribe to the topics
  mqtt_rpi("Subscribing to %s", TOPIC_SUBSCRIBE_ALGO);
  if ((rc = MQTTAsync_subscribe(client, TOPIC_SUBSCRIBE_ALGO, QOS, &opts)) !=
      MQTTASYNC_SUCCESS)
    mqtt_err("Failed to start subscribe for %s, return code %d",
             TOPIC_SUBSCRIBE_ALGO, rc);

  mqtt_rpi("Subscribing to %s", TOPIC_SUBSCRIBE_IMGREC);
  if ((rc = MQTTAsync_subscribe(client, TOPIC_SUBSCRIBE_IMGREC, QOS, &opts)) !=
      MQTTASYNC_SUCCESS)
    mqtt_err("Failed to start subscribe for %s, return code %d",
             TOPIC_SUBSCRIBE_IMGREC, rc);

  mqtt_rpi("Subscribing to %s", TOPIC_SUBSCRIBE_CAMERA);
  if ((rc = MQTTAsync_subscribe(client, TOPIC_SUBSCRIBE_CAMERA, QOS, &opts)) !=
      MQTTASYNC_SUCCESS)
    mqtt_err("Failed to start subscribe for %s, return code %d",
             TOPIC_SUBSCRIBE_CAMERA, rc);
}

/**
 * Callback function for when the MQTT client receives a message from the broker
 * for the subscribed topics
 *
 * @param context The context passed in the connect options
 * @param topicName The name of the topic the message was received on
 * @param topicLen The length of the topic name
 * @param message The message data
 * @returns 1
 * */
int msgarrvd(void *context, char *topicName, int topicLen,
             MQTTAsync_message *message) {
  mqtt_rpi("Recieved data of length %d on topic '%s'", message->payloadlen,
           topicName);
  if (strcmp(topicName, TOPIC_SUBSCRIBE_IMGREC) == 0) {
    char target_id = *(char *)(message->payload);
    write_bt(STATUS_UPDATE, RECEIVED_TARGET, target_id, 0);
    mqtt_img("Target ID: %d", (int)target_id);
    write_bt(TARGET_DISCOVERED, current_obstacle, target_id, 0);
    if (last_stop) {
      handle_finish = true;
      return 1;
    } else {
      move_stm();
      write_bt(STATUS_UPDATE, NEXT_OBSTACLE, 0, 0);
    }
  } else if (strcmp(topicName, TOPIC_SUBSCRIBE_ALGO) == 0) {
    // we have received the path data from the algorithm
    char *data = message->payload;
    char path_size = message->payloadlen;
    mqtt_algo("Recieved path from algo. %d commands", message->payloadlen / 2);

    // if the path is SOS, then the algorithm has failed
    if (path_size == 3 && strncmp(data, "SOS", 3) == 0) {
      mqtt_err("ALGO FAILED. GG");
    } else {
      // load path data and tell stm to start moving
      path_data.path = data;
      path_data.size = path_size;
      path_data.i = 0;
      move_stm();
      write_bt(STATUS_UPDATE, NEXT_OBSTACLE, 0, 0);
    }
  } else if (strcmp(topicName, TOPIC_SUBSCRIBE_CAMERA)) {
    // this is just a status update telling us that the image recognition is
    // starting
    write_bt(STATUS_UPDATE, IDENTIFYING_IMAGE, 0, 0);
  }
  MQTTAsync_freeMessage(&message);
  MQTTAsync_free(topicName);
  return 1;
}

/**
 * Initializes the MQTT client and connects to the broker
 */
void setup_mqtt() {
  int rc;
  if ((rc = MQTTAsync_create(&client, ADDRESS, CLIENTID,
                             MQTTCLIENT_PERSISTENCE_NONE, NULL)) !=
      MQTTASYNC_SUCCESS) {
    mqtt_err("Failed to create client, return code %d", rc);
    return;
  }

  if ((rc = MQTTAsync_setCallbacks(client, client, NULL, msgarrvd, NULL)) !=
      MQTTASYNC_SUCCESS) {
    mqtt_err("Failed to set callbacks, return code %d", rc);
    return;
  }

  MQTTAsync_connectOptions conn_opts = MQTTAsync_connectOptions_initializer;
  conn_opts.keepAliveInterval = ALIVE_INTERVAL;
  conn_opts.cleansession = 1;
  conn_opts.onSuccess = onConnect;
  conn_opts.onFailure = onConnectFailure;
  conn_opts.context = client;
  conn_opts.username = CLIENT_USERNAME;
  conn_opts.password = CLIENT_PASSWORD;

  mqtt_rpi("Connecting");
  if ((rc = MQTTAsync_connect(client, &conn_opts)) != MQTTASYNC_SUCCESS) {
    mqtt_err("Failed to start connect, return code %d", rc);
    return;
  }
}

/**
 * Thread function to handle the serial connection to the STM32
 * This thread never exits (being extra careful)
 */
void *stm_handler() {
  char read_buf[1] = {0};
  stm_log("Establishing Connection on Serial Port: %s Baud Rate: %d",
          SERIAL_PORT, BAUD_RATE);

  while (true) {
    if (stm32_fd == -1) {
      // try to connect to the STM32
      stm32_fd =
          open(SERIAL_PORT,
               O_RDWR | O_NOCTTY); // opens serial port with the configurations
      if (stm32_fd != -1) {
        // if connection has been made through the serial port
        struct termios serial_settings;
        tcgetattr(
            stm32_fd,
            &serial_settings); // retrieves current settings in fd and stores
                               // inside the serial_settings structure
        cfsetispeed(&serial_settings, BAUD_RATE); // sets input baud rate
        cfsetospeed(&serial_settings, BAUD_RATE); // sets output baud rate

        serial_settings.c_cflag |= (CLOCAL | CREAD); /* ignore modem controls */
        serial_settings.c_cflag &= ~CSIZE;
        serial_settings.c_cflag |= CS8;      /* 8-bit characters */
        serial_settings.c_cflag &= ~PARENB;  /* no parity bit */
        serial_settings.c_cflag &= ~CSTOPB;  /* only need 1 stop bit */
        serial_settings.c_cflag &= ~CRTSCTS; /* no hardware flowcontrol */

        /* setup for non-canonical mode */
        serial_settings.c_iflag &=
            ~(IGNBRK | BRKINT | PARMRK | ISTRIP | INLCR | IGNCR | ICRNL | IXON);
        serial_settings.c_lflag &= ~(ECHO | ECHONL | ICANON | ISIG | IEXTEN);
        serial_settings.c_oflag &= ~OPOST;

        /* fetch bytes as they become available */
        serial_settings.c_cc[VMIN] = 1;
        serial_settings.c_cc[VTIME] = 1;
        tcflush(stm32_fd, TCIFLUSH);
        tcsetattr(stm32_fd, TCSANOW, &serial_settings);
        stm_log("Established connection on Serial Port: %s Baud Rate: %d",
                SERIAL_PORT, BAUD_RATE);
      } else
        continue;
    }

    // read from the STM32
    int bytes_read = read(stm32_fd, read_buf, sizeof(read_buf));
    if (bytes_read > 0) {
      // we have received a stop signal from the STM32
      stm_log("Recieved STOP Signal");
      // move to the next command
      move_stm();
    } else if (bytes_read == -1) {
      // we have been disconnected from the STM32
      // it'll go back to the connection logic
      stm_err("Disconnected from STM. Reconnecting Now.");
      stm32_fd = -1;
    }
  }
  return NULL;
}

/**
 * Thread function to handle the finishing of the program
 * continuously checks if the program should terminate
 */
void *finish_handler() {
  while (!time_to_die) {
    if (handle_finish) {
      // inform the android client that we have finished
      write_bt(FINISHED_WEEK8, 0, 0, 0);

      // close mqtt connections
      write_mqtt_close(TOPIC_PUBLISH_CAMERA);
      write_mqtt_close(TOPIC_PUBLISH_IMGREC);
      MQTTAsync_disconnectOptions opts =
          MQTTAsync_disconnectOptions_initializer;
      opts.context = client;
      int rc;
      if ((rc = MQTTAsync_disconnect(client, &opts)) != MQTTASYNC_SUCCESS)
        mqtt_err("Failed to disconnect, return code %d", rc);
      mqtt_rpi("Closing... Will exit after android disconnects");
      sleep(3);
      MQTTAsync_destroy(&client);

      // this will break the loop
      time_to_die = true;
    }
  }
  return NULL;
}

/**
 * Main function to start the threads and setup the MQTT client
 * @param argc The number of arguments
 * @param argv The arguments
 */
int main(int argc, char **argv) {
  // start the threads
  pthread_t bluetooth_thread, stm_thread, finishing_thread;
  pthread_create(&bluetooth_thread, NULL, bt_handler, NULL);
  pthread_create(&stm_thread, NULL, stm_handler, NULL);
  pthread_create(&finishing_thread, NULL, finish_handler, NULL);

  // setup the MQTT client
  setup_mqtt();

  // wait for the threads to finish
  pthread_join(bluetooth_thread, NULL);
  pthread_join(finishing_thread, NULL);
  return 0;
}
