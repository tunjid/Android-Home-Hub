Android Home
=======

### About

This Project is an open source Android powered Home Automation Bus, heavily inspired by [OpenHAB](https://www.openhab.org/docs/).
It is nowhere near as fully featured as OpenHAB, nor does it aim to be; it's a hobby Android project.

There are 2 main components:

1. An Android DNS-based Service Discovery (DNS-SD) Near Service Discovery (NSD) Server.
2. An Android client that connects to the server.

NOTE: It is highly recommended to run the server on an Android Things device to avoid permission prompts for Bluetooth or USB, and the availability of USB ports.

#### Client

Each connected client opens a socket on the server, and writes and reads lines over the socket's input and output streams. These lines contain
serialized representations of the ```Payload``` class which hosts various properties to describe whatever communications have taken place.

#### Server

The Android Server interfaces with end devices to be controlled via the ```CommsProtocol``` abstract class. Implementations of this class
are responsible for liaising input commands from the client to the devices it connects to and reporting any results back to the client.

Client requests are responded to synchronously for immediate feedback. Should a request require asynchronous processing, a ```PrintWriter``` is provided
in the constructor of the class, which writes to the same socket output stream. Whatever is written out of these PrintWriter must also be a serialization of the
```Payload``` class, else the client will fail to parse and interpret it.

The Server supports multiple client connections, limited only by the size of the thread pool in ServerNsdService.

### Supported Protocols

#### KnockKnockProtocol

A demo protocol that tells knock knock jokes

#### ZigBeeProtocol
Controls ZigBee Devices using Texas Instrument's CC2531 dongle running the Zstack, it interfaces with the dongle over USB.
This protocol pretty much just proxies [Zsmartsystems](https://github.com/zsmartsystems/com.zsmartsystems.zigbee) console app.
Theoretically, any supported dongle there can be supported here provided a supported serial implementation is provided.
The current Serial implementation has no support for flow control, and so is limited to the TI CC2531.

#### RcProtocols

Sniffs and controls 433 MHz devices with an Arduino, essentially replacing the device's bundled remote.
A video overview can be see [here](https://youtu.be/FrNVvwTE1eg).

There are two implementations:

1. Wired, using a USB serial.
2. Wireless, using Bluetooth Low Energy and Silicon Lab's BLE112

Description of the wireless implementation follows:

![](http://i.imgur.com/MnurD22.png "Connections")

##### The Arduino GATT Server

The Arduino GATT server is comprised of:

1. An Arduino Mega 
2. The BLE112 Module from Silicon Labs
3. A Generic RF Receiver
4. A Generic RF Transmitter

An Arduino Mega, while not necessary is preferred because of the avaialabilty of more than 1 hardware serial.
An Uno is fine provided debug messages are fed to a software serial, but the BLE communication should always use a hardware serial
as it is way more Reliable.

##### The Android GATT Client

The Android GATT client provides a convenient UI for the server. After following the prompts to 
a connect to a Arduino server, the app will automatically attempt to connect to it whenever it is within range.

The app assumes the switches it controls has 2 states, ON and OFF. To Create a switch, the app prompts a user to sniff the ON code and OFF code
consecutively, after which a switch will be created with a UI to toggle between the ON and OFF states. Once a switch is created, it is persisted
in the app unless it is deleted with a swipe on the UI. Long pressing a switch brings up a dialog to rename the switch.

## License

MIT License

Copyright (c) 2019 Adetunji Dahunsi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.


