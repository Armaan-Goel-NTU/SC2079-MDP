# RPi Client
The RPi manages communication with the following components:
- Android Client (ARCM) over Bluetooth
- STM over USB
- RPi Camera locally
- Algorithm Client over WiFi
- Image Recognition Client over WiFi 

Communication over WiFi and the RPi Camera Client (locally) is handled using [MQTT](https://mqtt.org/). The broker ran on the RPi.

## Installation
```bash
gcc "week8.c" -lpthread -lbluetooth -lpaho-mqtt3a -o "rpi_client"`
```
I've only tried compiling on `Ubuntu 22.04 LTS` with `build-essential` `libbluetooth-dev` from `apt` and `Eclipse Paho C Client Library` installed by following the instructions [here](https://github.com/eclipse/paho.mqtt.c).