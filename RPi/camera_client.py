import paho.mqtt.client as mqtt
import cv2

# MQTT parameters
USERNAME = "rpimdp3"
PASSWORD = "MDPGr@up3!"
IP = "192.168.3.3"
PORT = 1883

# MQTT topics
TOPIC_SUBSCRIBE = "take_picture"
TOPIC_PUBLISH_RPI = "picture_taken"
TOPIC_PUBLISH_IMAGEREC = "run_recognition"

# CV2 parameters
WIDTH = 640
HEIGHT = 480
SOURCE_INDEX = 0
API_PREFERENCE = cv2.CAP_V4L2

client = mqtt.Client("Camera_Client")
camera = cv2.VideoCapture(SOURCE_INDEX, API_PREFERENCE)
should_exit = False


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        # Connection successful
        client.subscribe(TOPIC_SUBSCRIBE)
        print(f"Subscribed to {TOPIC_SUBSCRIBE}")


def on_message(client, userdata, message):
    # Callback function that is called when a message is received on a subscribed topic
    global should_exit
    print(f"Received data on topic '{message.topic}'")
    if message.topic == TOPIC_SUBSCRIBE:
        # Check if the message is to close the camera
        if len(message.payload) == 5 and message.payload.decode() == "close":
            should_exit = True
            return

        # Open camera and take a picture
        try:
            if not camera.isOpened():
                camera.open(SOURCE_INDEX, API_PREFERENCE)

            camera.set(cv2.CAP_PROP_FRAME_WIDTH, WIDTH)
            camera.set(cv2.CAP_PROP_FRAME_HEIGHT, HEIGHT)
            ret, frame = camera.read()

            if not ret:
                # shouldn't happen
                print("Error: Could not capture a frame.")
            else:
                print("sending the image")

                # Inform the image recgonition client that an image is ready
                client.publish(TOPIC_PUBLISH_IMAGEREC, frame.tobytes())

                # Inform the RPi client that the image has been taken
                client.publish(TOPIC_PUBLISH_RPI, True)

        except Exception as e:
            print(e)


# Set up the client and connect to the broker
client.username_pw_set(USERNAME, PASSWORD)
client.on_message = on_message
client.on_connect = on_connect

client.connect(IP, PORT)
client.loop_start()

# Was most likely wrapped in a try-except block to catch KeyboardInterrupt (Ctrl+C)
try:
    while not should_exit:
        pass
except:
    pass

# Clean up
print("releasing camera and disconnecting")
camera.release()  # important to release the camera
client.disconnect()
