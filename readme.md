BLE RC Switch Control
=======

### About

This Project contains an Android app and an Arduino sketch to sniff 433/315Mhz device codes
from a remote, and reproduce the same codes, essentially replacing the remote.

A video overview can be see [here](https://youtu.be/FrNVvwTE1eg).

### The Arduino GATT Server

The Arduino GATT server is comprised of:

1. An Arduino Mega 
2. The BLE112 Module from Silicon Labs
3. A Generic RF Receiver
4. A Generic RF Transmitter

An Arduino Mega, while not necessary is preferred because of the avaialabilty of more than 1 hardware serial.
An Uno is fine provided debug messages are fed to a software serial, but the BLE communication should always use a hardware serial
as it is way more Reliable.

### The Android GATT Client

The Android GATT client provides a convenient UI for the server. After following the prompts to 
a connect to a Arduino server, the app will automatically attempt to connect to it whenever it is within range.

The app assumes the switches it controls has 2 states, ON and OFF. To Create a switch, the app prompts a user to sniff the ON code and OFF code
consecutively, after which a switch will be created with a UI to toggle between the ON and OFF states. Once a switch is created, it is persisted
in the app unless it is deleted with a swipe on the UI. Long pressing a switch brings up a dialog to rename the switch.

### Near Service Discovery (NSD)

Should you want multiple devices to be able to control your RC switches, it is possible to set up one device as an NSD server.
This device will hold the actual Bluetooth connection to the sniffer, while consecutive devices will connect to the server device
via NSD. The clients will then proxy the RC commands over to the server device which will then execute them.


![alt text](http://i.imgur.com/MnurD22.png "Connections")




