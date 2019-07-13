#include "Math.h"

#include "RCSwitch.h"

#include "FlexiTimer2.h" // Timer interrupt to limit sniff lengths

// ================================================================
// Constants
// ================================================================

// RC Switch state machine definitions

uint8_t STATE_SNIFFING = 82;
uint8_t STATE_SENDING = 84;
uint8_t INVALID = 69;

volatile uint8_t state; // State of RC Switch.
uint8_t last_transmission = STATE_SENDING;

// The interrupt pin for the RcSwitch. See http://arduino.cc/en/Reference/AttachInterrupt

#define LED_PIN 13  // Arduino Uno LED pin
#define INTERRUPT_RX digitalPinToInterrupt(3)
#define PIN_TX 5

uint8_t sniffedData[10];

RCSwitch transceiver = RCSwitch();

void setup() {

  // initialize status LED
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  // open Arduino USB serial
  // use 38400 since it works at 8MHz as well as 16MHz

  Serial.begin(115200);
  while (!Serial);

  Serial.println("Start");

  state = STATE_SENDING;
}

// ================================================================
// MAIN APPLICATION LOOP
// ================================================================

void loop() {

  if (state == STATE_SNIFFING) {

    if (transceiver.available()) {
      // Stop timer interrupts
      FlexiTimer2::stop();

      unsigned long value = transceiver.getReceivedValue();

      if (value == 0) {
        push(INVALID);
      }
      else {
        unsigned int pulseLength = transceiver.getReceivedDelay();
        uint8_t bitLength = transceiver.getReceivedBitlength();
        uint8_t protocol = transceiver.getReceivedProtocol();

        // Break long value to binary byte array representation

        sniffedData[0] = (value >> 24) & 0xff;
        sniffedData[1] = (value >> 16) & 0xff;
        sniffedData[2] = (value >> 8) & 0xff;
        sniffedData[3] = value & 0xff;
        sniffedData[4] = (pulseLength >> 24) & 0xff;
        sniffedData[5] = (pulseLength >> 16) & 0xff;
        sniffedData[6] = (pulseLength >> 8) & 0xff;
        sniffedData[7] = pulseLength & 0xff;
        sniffedData[8] = bitLength;
        sniffedData[9] = protocol;

        // Write to serial
        Serial.write(sniffedData, 10);
      }

      transceiver.resetAvailable();

      // Revert state to sending
      state = STATE_SENDING;
      transceiver.enableTransmit(PIN_TX);
    }
  }
  else if (Serial.available()) {
    delay(15); // Delay because Arduino serial buffer will return 1 if queried too quickly.

    uint8_t toRead = Serial.available();
    uint8_t written[toRead];

    Serial.readBytes((uint8_t *)written, toRead);

    if (toRead == 1) {
      uint8_t input = written[0];

      if (input == STATE_SNIFFING) startSniffing();
      else if (input == STATE_SENDING) enableTransmission();
      else push(INVALID);
    }
    else if (toRead == 10) {
      controlSwitch(written);
    }
    else {
      push(INVALID);
    }
  }

  uint16_t slice = millis() % 1000;

  if (last_transmission == STATE_SENDING) {
    digitalWrite(LED_PIN, slice < 100);
  }
  if (last_transmission == STATE_SNIFFING) {
    digitalWrite(LED_PIN, slice < 100 || (slice > 200 && slice < 300));
  }
  if (last_transmission == INVALID) {
    digitalWrite(LED_PIN, slice < 100 || (slice > 200 && slice < 300) || (slice > 400 && slice < 500));
  }
}

void startSniffing() {
  state = STATE_SNIFFING;
  transceiver.enableReceive(INTERRUPT_RX);
  push(state);

  // Only sniff for 5 seconds or till a packet is received.
  FlexiTimer2::set(5000, interruptSniff);
  FlexiTimer2::start();
}

void enableTransmission() {
  state = STATE_SENDING;
  transceiver.enableTransmit(PIN_TX);
  push(state);
}

void interruptSniff() {
  FlexiTimer2::stop();
  enableTransmission();
}

void push(uint8_t toPush) {
  Serial.write(toPush);
  Serial.flush();
  last_transmission = toPush;
}

void controlSwitch(uint8_t * b) {
  unsigned long code = ((uint32_t) b[0] << 24) | ((uint32_t) b[1] << 16) | ((uint32_t) b[2] << 8) | ((uint32_t) b[3]);
  unsigned int pulseLength = ((uint32_t) b[4] << 24) | ((uint32_t) b[5] << 16) | ((uint32_t) b[6] << 8) | ((uint32_t) b[7]);

  uint8_t bitLength = b[8];
  uint8_t protocol = b[9];

  enableTransmission();

  transceiver.setProtocol(protocol);
  transceiver.setPulseLength(pulseLength);
  transceiver.send(code, bitLength);
}