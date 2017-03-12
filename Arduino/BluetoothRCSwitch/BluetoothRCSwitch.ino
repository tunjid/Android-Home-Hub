// A sketch to sniff and control 433/315Mhz devices

// Based onBluegiga BGLib Arduino interface library slave device stub sketch
// 2014-02-12 by Jeff Rowberg <jeff@rowberg.net>

// 2014-03-12 modified by Adetunji Dahunsi <tunjid.com>
// Updates should (hopefully) always be available at https://github.com/tunjid

#include "Math.h"
#include "BGLib.h" // BGLib C library for BGAPI communication.
#include "RCSwitch.h"
#include "FlexiTimer2.h" // Timer interrupt to limit sniff lengths

// uncomment the following line for debug serial output
#define DEBUG

// ================================================================
// Constants
// ================================================================

// RC Switch state machine definitions

#define STATE_SNIFFING              0
#define STATE_SENDING               1

// BLE state machine definitions

#define BLE_STATE_STANDBY           0
#define BLE_STATE_SCANNING          1
#define BLE_STATE_ADVERTISING       2
#define BLE_STATE_CONNECTING        3
#define BLE_STATE_CONNECTED_MASTER  4
#define BLE_STATE_CONNECTED_SLAVE   5
#define BLE_STATE_TIMED_OUT         6

// ================================================================
// Dynamic variables
// ================================================================

volatile uint8_t state; // State of RC Switch.

boolean TIMED_OUT_FLAG = false; // Used to identify if the module timed out.

// ================================================================
// BLE STATE TRACKING 
// ================================================================

// BLE state/link status tracker

uint8_t ble_state = BLE_STATE_STANDBY;
uint8_t ble_encrypted = 0;  // 0 = not encrypted, otherwise = encrypted
uint8_t ble_bonding = 0xFF; // 0xFF = no bonding, otherwise = bonding handle

// ================================================================
// HARDWARE CONNECTIONS AND GATT STRUCTURE SETUP
// ================================================================

#define LED_PIN         13  // Arduino Uno LED pin
#define BLE_RESET_PIN   6   // BLE reset pin (active-low)

#define GATT_HANDLE_C_STATE_TOGGLE   17  // 0x11, supports "read", "notify" and "indicate" operations
#define GATT_HANDLE_C_SNIFFER   21  // 0x15, supports "read", "notify" and "indicate" operations
#define GATT_HANDLE_C_TRANSMITTER   25  // 0x19, supports "read", "notify" and "indicate" operations

// create BGLib object:

//  - use nothing for passthrough comms (0 = null pointer)
//  - enable packet mode on API protocol since flow control is unavailable

BGLib ble112((HardwareSerial * ) & Serial1, 0, 1);

#define BGAPI_GET_RESPONSE(v, dType) dType *v = (dType *)ble112.getLastRXPayload()

// The interrupt pin for the RcSwitch. See http://arduino.cc/en/Reference/AttachInterrupt
#define INTERRUPT_RX digitalPinToInterrupt(3)
#define PIN_TX 5

uint8_t stateCallback[1];
RCSwitch receiveSwitch = RCSwitch();

// ================================================================
// ARDUINO APPLICATION SETUP AND LOOP FUNCTIONS
// ================================================================

// initialization sequence

void setup() {

    // ================================================================
    // For BLE, Serial Communication and Timer interrupts
    // ================================================================

    // initialize status LED
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    // initialize BLE reset pin (active-low)
    pinMode(BLE_RESET_PIN, OUTPUT);
    digitalWrite(BLE_RESET_PIN, HIGH);

    // set up internal status handlers (these are technically optional)
    ble112.onBusy = onBusy;
    ble112.onIdle = onIdle;
    ble112.onTimeout = onTimeout;

    // set up BGLib event handolers
    ble112.ble_evt_system_boot = my_ble_evt_system_boot;
    ble112.ble_evt_connection_status = my_ble_evt_connection_status;
    ble112.ble_evt_connection_disconnected = my_ble_evt_connection_disconnect;
    ble112.ble_evt_attributes_value = my_ble_evt_attributes_value;
    ble112.ble_evt_attclient_indicated = my_ble_evt_attclient_indicated;
    ble112.ble_evt_attributes_status = my_ble_evt_attributes_status;
    ble112.ble_rsp_attributes_write = my_ble_rsp_attributes_write;

    // open Arduino USB serial
    // use 38400 since it works at 8MHz as well as 16MHz

    Serial.begin(38400);
    while (!Serial);

    // open BLE Hardware serial port
    Serial1.begin(38400);

    my_ble_evt_system_boot(NULL);

    // reset module
    digitalWrite(BLE_RESET_PIN, LOW);
    delay(5); // wait 5ms
    digitalWrite(BLE_RESET_PIN, HIGH);

    Serial.print("Starting up");
    Serial.println();

    state = STATE_SENDING;
}

// ================================================================
// MAIN APPLICATION LOOP 
// ================================================================

void loop() {

    // keep polling for new data from BLE
    ble112.checkActivity();

    switch (state) {
        case STATE_SNIFFING:
            if (receiveSwitch.available()) { 
                // Stop timer interrupts
                FlexiTimer2::stop();
                
                unsigned long value = receiveSwitch.getReceivedValue();

                if (value == 0) {
                    Serial.println("Unknown encoding");
                }
                else {
                    Serial.print("Received ");
                    Serial.print(value);
                    Serial.print(" / ");
                    Serial.print(receiveSwitch.getReceivedBitlength());
                    Serial.print("bit ");
                    Serial.print("Protocol: ");
                    Serial.println(receiveSwitch.getReceivedProtocol());
                    Serial.print("Delay (Pulse Length): ");
                    Serial.println(receiveSwitch.getReceivedDelay());

                    // Break long value to binary byte array representation
                    uint8_t bytes[6];

                    bytes[0] = (value >> 24) & 0xff;
                    bytes[1] = (value >> 16) & 0xff;
                    bytes[2] = (value >> 8) & 0xff;
                    bytes[3] = value & 0xff;
                    bytes[4] = receiveSwitch.getReceivedDelay();
                    bytes[5] = receiveSwitch.getReceivedBitlength();
                    
                    // Write value to characteristic on ble112. Causes indication to be sent.
                    ble112.ble_cmd_attributes_write(GATT_HANDLE_C_SNIFFER, 0, 6, bytes);
                    
                    // Revert state to sending
                    state = STATE_SENDING;
                }
                receiveSwitch.resetAvailable();
            }
            break;
    }

    // blink Arduino LED based on state:
    //  - solid = STANDBY
    //  - 1 pulse per second = ADVERTISING
    //  - 2 pulses per second = CONNECTED_SLAVE
    //  - 3 pulses per second = CONNECTED_SLAVE with encryption

    uint16_t slice = millis() % 1000;

    if (ble_state == BLE_STATE_STANDBY) {
        digitalWrite(LED_PIN, HIGH);
    }
    if (ble_state == BLE_STATE_ADVERTISING) {
        digitalWrite(LED_PIN, slice < 100);
    }
    if (ble_state == BLE_STATE_TIMED_OUT) {
        digitalWrite(LED_PIN, LOW);
    }
    if (ble_state == BLE_STATE_CONNECTED_SLAVE) {
        if (!ble_encrypted) {
            digitalWrite(LED_PIN, slice < 100 || (slice > 200 && slice < 300));
        }
        else {
            digitalWrite(LED_PIN, slice < 100 || (slice > 200 && slice < 300) || (slice > 400 && slice < 500));
        }
    }
}

// ================================================================
// INTERNAL BGLIB CLASS CALLBACK FUNCTIONS
// ================================================================

// called when the module begins sending a command
void onBusy() {
    // turn LED on when we're busy
    //digitalWrite(LED_PIN, HIGH);
}

// called when the module receives a complete response or "system_boot" event
void onIdle() {
    // turn LED off when we're no longer busy
    //    digitalWrite(LED_PIN, LOW);
}

// called when the parser does not read the expected response in the specified time limit
void onTimeout() {
    ble_state == BLE_STATE_TIMED_OUT;
    TIMED_OUT_FLAG = true;
    // reset module (might be a bit drastic for a timeout condition though)
    Serial.println(F("Timed out."));
    resetBLE();
}

// ================================================================
// APPLICATION EVENT HANDLER FUNCTIONS
// ================================================================

void my_ble_evt_system_boot(const ble_msg_system_boot_evt_t *msg) {
#ifdef DEBUG
    Serial.print("###\tsystem_boot: { ");
    Serial.print("major: ");
    Serial.print(msg->major, HEX);
    Serial.print(", minor: ");
    Serial.print(msg->minor, HEX);
    Serial.print(", patch: ");
    Serial.print(msg->patch, HEX);
    Serial.print(", build: ");
    Serial.print(msg->build, HEX);
    Serial.print(", ll_version: ");
    Serial.print(msg->ll_version, HEX);
    Serial.print(", protocol_version: ");
    Serial.print(msg->protocol_version, HEX);
    Serial.print(", hw: ");
    Serial.print(msg->hw, HEX);
    Serial.println(" }");
#endif

    // system boot means module is in standby state
    //ble_state = BLE_STATE_STANDBY;
    // ^^^ skip above since we're going right back into advertising below

    TIMED_OUT_FLAG = false; // reset time out boolean

    // set advertisement interval to 200-300ms, use all advertisement channels
    // (note min/max parameters are in units of 625 uSec)
    ble112.ble_cmd_gap_set_adv_parameters(320, 480, 7);
    while (ble112.checkActivity(1000));

    // USE THE FOLLOWING TO LET THE BLE STACK HANDLE YOUR ADVERTISEMENT PACKETS
    // ========================================================================
    // start advertising general discoverable / undirected connectable
    //ble112.ble_cmd_gap_set_mode(BGLIB_GAP_GENERAL_DISCOVERABLE, BGLIB_GAP_UNDIRECTED_CONNECTABLE);
    //while (ble112.checkActivity(1000));

    // USE THE FOLLOWING TO HANDLE YOUR OWN CUSTOM ADVERTISEMENT PACKETS
    // =================================================================

    // build custom advertisement data
    // default BLE stack value: 0201061107e4ba94c3c9b7cdb09b487a438ae55a19
    uint8 adv_data[] = {
            0x02, // field length
            BGLIB_GAP_AD_TYPE_FLAGS, // field type (0x01)
            0x06, // data (0x02 | 0x04 = 0x06, general discoverable + BLE only, no BR+EDR)
            0x11, // field length
            BGLIB_GAP_AD_TYPE_SERVICES_128BIT_ALL, // field type (0x07)
            0xe4, 0xba, 0x94, 0xc3, 0xc9, 0xb7, 0xcd, 0xb0, 0x9b, 0x48, 0x7a, 0x43, 0x8a, 0xe5, 0x5a, 0x19
    };

    // set custom advertisement data
    ble112.ble_cmd_gap_set_adv_data(0, 0x15, adv_data);
    while (ble112.checkActivity(1000));

    // build custom scan response data (i.e. the Device Name value)
    // default BLE stack value: 140942474c69622055314131502033382e344e4657
    uint8 sr_data[] = {
            0xD, // field length
            BGLIB_GAP_AD_TYPE_LOCALNAME_COMPLETE, // field type
            'B', 'L', 'E', ' ', 'R', 'C', ' ', 'S', 'w', 'i', 't', 'c', 'h'
    };

    // get BLE MAC address
    ble112.ble_cmd_system_address_get();
    while (ble112.checkActivity(1000));
    BGAPI_GET_RESPONSE(r0, ble_msg_system_address_get_rsp_t);

    // assign last three bytes of MAC address to ad packet friendly name (instead of 00:00:00 above)
    sr_data[13] = (r0->address.addr[2] / 0x10) + 48 + ((r0->address.addr[2] / 0x10) / 10 * 7); // MAC byte 4 10's digit
    sr_data[14] = (r0->address.addr[2] & 0xF) + 48 + ((r0->address.addr[2] & 0xF) / 10 * 7); // MAC byte 4 1's digit
    sr_data[16] = (r0->address.addr[1] / 0x10) + 48 + ((r0->address.addr[1] / 0x10) / 10 * 7); // MAC byte 5 10's digit
    sr_data[17] = (r0->address.addr[1] & 0xF) + 48 + ((r0->address.addr[1] & 0xF) / 10 * 7); // MAC byte 5 1's digit
    sr_data[19] = (r0->address.addr[0] / 0x10) + 48 + ((r0->address.addr[0] / 0x10) / 10 * 7); // MAC byte 6 10's digit
    sr_data[20] = (r0->address.addr[0] & 0xF) + 48 + ((r0->address.addr[0] & 0xF) / 10 * 7); // MAC byte 6 1's digit

    // set custom scan response data (i.e. the Device Name value)
    ble112.ble_cmd_gap_set_adv_data(1, 0x15, sr_data);
    while (ble112.checkActivity(1000));

    // put module into discoverable/connectable mode (with user-defined advertisement data)
    ble112.ble_cmd_gap_set_mode(BGLIB_GAP_USER_DATA, BGLIB_GAP_UNDIRECTED_CONNECTABLE);
    while (ble112.checkActivity(1000));

    // set state to ADVERTISING depending on timed out condition
    if (TIMED_OUT_FLAG == false) {
        ble_state = BLE_STATE_ADVERTISING;
    }
    else {
        ble_state = BLE_STATE_TIMED_OUT;
    }
}

void my_ble_evt_connection_status(const ble_msg_connection_status_evt_t *msg) {
#ifdef DEBUG
    Serial.print("###\tconnection_status: { ");
    Serial.print("connection: ");
    Serial.print(msg->connection, HEX);
    Serial.print(", flags: ");
    Serial.print(msg->flags, HEX);
    Serial.print(", address: ");
    // this is a "bd_addr" data type, which is a 6-byte uint8_t array
    for (uint8_t i = 0; i < 6; i++) {
        if (msg->address.addr[i] < 16) Serial.write('0');
        Serial.print(msg->address.addr[i], HEX);
    }
    Serial.print(", address_type: ");
    Serial.print(msg->address_type, HEX);
    Serial.print(", conn_interval: ");
    Serial.print(msg->conn_interval, HEX);
    Serial.print(", timeout: ");
    Serial.print(msg->timeout, HEX);
    Serial.print(", latency: ");
    Serial.print(msg->latency, HEX);
    Serial.print(", bonding: ");
    Serial.print(msg->bonding, HEX);
    Serial.println(" }");
#endif

    // "flags" bit description:
    //  - bit 0: connection_connected
    //           Indicates the connection exists to a remote device.
    //  - bit 1: connection_encrypted
    //           Indicates the connection is encrypted.
    //  - bit 2: connection_completed
    //           Indicates that a new connection has been created.
    //  - bit 3; connection_parameters_change
    //           Indicates that connection parameters have changed, and is set
    //           when parameters change due to a link layer operation.

    // check for new connection established
    if ((msg->flags & 0x05) == 0x05) {
        // track state change based on last known state, since we can connect two ways
        if (ble_state == BLE_STATE_ADVERTISING) {
            ble_state = BLE_STATE_CONNECTED_SLAVE;
        }
        else {
            ble_state = BLE_STATE_CONNECTED_MASTER;
        }
    }

    // update "encrypted" status
    ble_encrypted = msg->flags & 0x02;

    // update "bonded" status
    ble_bonding = msg->bonding;
}

void my_ble_evt_connection_disconnect(const struct ble_msg_connection_disconnected_evt_t *msg) {
#ifdef DEBUG
    Serial.print("###\tconnection_disconnect: { ");
    Serial.print("connection: ");
    Serial.print(msg->connection, HEX);
    Serial.print(", reason: ");
    Serial.print(msg->reason, HEX);
    Serial.println(" }");
#endif

    // set state to DISCONNECTED
    //ble_state = BLE_STATE_DISCONNECTED;
    // ^^^ skip above since we're going right back into advertising below

    // after disconnection, resume advertising as discoverable/connectable
    //ble112.ble_cmd_gap_set_mode(BGLIB_GAP_GENERAL_DISCOVERABLE, BGLIB_GAP_UNDIRECTED_CONNECTABLE);
    //while (ble112.checkActivity(1000));

    // after disconnection, resume advertising as discoverable/connectable (with user-defined advertisement data)
    ble112.ble_cmd_gap_set_mode(BGLIB_GAP_USER_DATA, BGLIB_GAP_UNDIRECTED_CONNECTABLE);
    while (ble112.checkActivity(1000));

    // set state to ADVERTISING
    ble_state = BLE_STATE_ADVERTISING;

    // clear "encrypted" and "bonding" info
    ble_encrypted = 0;
    ble_bonding = 0xFF;
}

void my_ble_evt_attributes_value(const struct ble_msg_attributes_value_evt_t *msg) {
#ifdef DEBUG
    Serial.print("###\tattributes_value: { ");
    Serial.print("connection: ");
    Serial.print(msg->connection, HEX);
    Serial.print(", reason: ");
    Serial.print(msg->reason, HEX);
    Serial.print(", handle: ");
    Serial.print(msg->handle, HEX);
    Serial.print(", offset: ");
    Serial.print(msg->offset, HEX);
    Serial.print(", value_len: ");
    Serial.print(msg->value.len, HEX);
    Serial.print(", value_data: ");
    // this is a "uint8array" data type, which is a length byte and a uint8_t* pointer
    for (uint8_t i = 0; i < msg->value.len; i++) {
        if (msg->value.data[i] < 16) Serial.write('0');
        Serial.print(msg->value.data[i], HEX);
    }
    Serial.println(" }");
#endif

    // check for data written to "GATT_HANDLE_C_STATE_TOGGLE" handle
    if (msg->handle == GATT_HANDLE_C_STATE_TOGGLE && msg->value.len > 0) {

        state = msg->value.data[0];

        // Send indication acknowledging the write
        stateCallback[0] = STATE_SNIFFING;
        ble112.ble_cmd_attributes_write(GATT_HANDLE_C_STATE_TOGGLE, 0, 1, stateCallback);

        Serial.print("Current state is: ");
        Serial.println(state);

        switch (state){
            case STATE_SNIFFING:
                receiveSwitch.enableReceive(INTERRUPT_RX);

                // Only sniff for 5 seconds or till a packet is received.
                FlexiTimer2::set(5000, interruptSniff);
                FlexiTimer2::start();
                break;
            case STATE_SENDING:
                receiveSwitch.enableTransmit(PIN_TX);
                break;
        }
    }
    else if (msg->handle == GATT_HANDLE_C_TRANSMITTER && msg->value.len > 0) {
      uint8_t* b = msg->value.data;

      unsigned long code = ((uint32_t)b[0] << 24) | ((uint32_t)b[1] << 16) | ((uint32_t)b[2] << 8) | ((uint32_t)b[3]);

      int pulseLength = msg->value.data[4];
      int bitLength  = msg->value.data[5];
      
      Serial.print("Code received: ");
      Serial.println(code);
      Serial.print("Pulse Length: ");
      Serial.println(pulseLength);
      Serial.print("Bit Length: ");
      Serial.println(bitLength);

      receiveSwitch.enableTransmit(PIN_TX);
      receiveSwitch.setProtocol(1);
      receiveSwitch.setPulseLength(pulseLength);
      receiveSwitch.send(code, bitLength);
    }
}

void my_ble_evt_attclient_indicated(const struct ble_msg_attclient_indicated_evt_t *msg) {
#ifdef DEBUG
    Serial.print("###\tattclient_indicate: { ");
    Serial.print("Indication received.");
    Serial.println(" }");
#endif
}

void my_ble_rsp_attributes_write(const struct ble_msg_attributes_write_rsp_t *msg) {
#ifdef DEBUG
    if (msg->result == 0) {
    }
    else {
        Serial.print("###\trsp_attributes_write: {");
        Serial.print("result: ");
        Serial.print(msg->result, DEC);
        Serial.println("}");
    }
#endif
}

void my_ble_evt_attributes_status(const struct ble_msg_attributes_status_evt_t *msg) {
#ifdef DEBUG
    Serial.print("###\tattributes_status: { ");
    Serial.print("nSubscription changed");
    Serial.print(", flags: ");
    Serial.print(msg->flags, HEX);
    Serial.print(", Handle: ");
    Serial.print(msg->handle, DEC);
    Serial.println(" }");
#endif
}

void interruptSniff() {
    FlexiTimer2::stop();
    Serial.println("Timer interrupt called");
    state = STATE_SENDING;
    receiveSwitch.enableTransmit(PIN_TX);

    stateCallback[0] = STATE_SENDING;
    ble112.ble_cmd_attributes_write(GATT_HANDLE_C_STATE_TOGGLE, 0, 1, stateCallback);
}

void resetBLE() {
    digitalWrite(BLE_RESET_PIN, LOW);
    delay(50); // wait 5ms
    digitalWrite(BLE_RESET_PIN, HIGH);
    Serial.println("Reset attempt.");
}







