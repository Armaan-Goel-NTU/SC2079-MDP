# ARCM
Android Remote Control Module

This version was used for the Image Recognition Task and therefore does not contain code for passing the checklist. The UI is designed specifically for the Galaxy Tab A7 Lite that was provided to each group for this project. As such, no efforts were made to scale the UI properly for different devices and `RelativeLayout` is used in certain places. 

The `ConnectionFragment` class contains code for scanning and pairing with new Bluetooth devices, but wasn't used for the actual task in favour of selecting from a list of paired devices in the `MainFragment`. I have still included it, but it needs to be tweaked and included in the navigation flow to make it work with the rest of the app. 