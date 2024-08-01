# SC2079-MDP (AY 23/24 S1)
My code for the Android Remote Control Module, Multi-threaded RPi client, and Pathfinding Algorithm. 
I also helped integrate the image recognition model built using [YOLOv5](https://github.com/ultralytics/yolov5) and the STM code. (omitted from this repo)

**Image Recognition Task** \
Recorded Time: 1m 27s for 7/7 Images. \
Actual Time: 1m 22s (did not realise it finished) \
Rank: 6/44

**Car Park Task** \
Disqualified \
The car managed to perform the loop but hit the car park barrier when stopping (instant disqualification). This was likely due to a misconfigured IR sensor. It was calibrated for a darker environment the night before the assessment, which was outside in bright daylight. 

## ARCM
This is the Android project for the Android Remote Control Module used to populate the obstacle information for the image recognition task and to receive status updates from the RPi. 

## RPi
This is the code for the multi-threaded client running on the RPi for the image recognition task. It enabled communication between the Android, Pathfinding Algorithm, STM, RPi Camera, and Image Recognition.

## Algorithm
This is the actual pathfinding algorithm used during the image recognition task. It tries its best to accommodate the unstable nature of the car (hardware issues). 

It was written in Python closer to the assessment date and not used for the simulator/checklist. There were plans to rewrite it in Rust to improve execution speed but there was no time left. The Python version isn't slow by any means either. (around 600ms on an M1 Macbook Air)
