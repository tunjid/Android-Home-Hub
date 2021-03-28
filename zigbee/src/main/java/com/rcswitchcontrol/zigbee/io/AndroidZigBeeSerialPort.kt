/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.rcswitchcontrol.zigbee.io

import android.hardware.usb.UsbDevice
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.SerialInfo
import com.tunjid.rcswitchcontrol.common.serial.FelHR85UsbSerialPort
import com.tunjid.rcswitchcontrol.common.serial.ProxyUsbSerialPort
import com.zsmartsystems.zigbee.serial.ZigBeeSerialPort
import com.zsmartsystems.zigbee.transport.ZigBeePort
import jssc.SerialPortException
import org.slf4j.LoggerFactory


class AndroidZigBeeSerialPort(
    private val serialInfo: SerialInfo,
    private val usbDevice: UsbDevice
) : ZigBeePort {

    private var end = 0
    private var start = 0
    private val maxLength = 512

    private val buffer = IntArray(512)

    private var serialPort: ProxyUsbSerialPort? = null

    private val lock = Object()
    private val bufferSynchronisationObject = Any()

    private val onDataRead = { data: ByteArray ->
        try {
            synchronized(bufferSynchronisationObject) {
                for (byte in data) {
                    val int = if (byte < 0) 256 + byte else byte.toInt()
                    buffer[end++] = int
                    if (end >= maxLength) end = 0
                    if (end == start) {
                        println("Serial buffer overrun.")
                        if (++start == maxLength) start = 0
                    }
                }
            }

            synchronized(lock) {
                lock.notify()
            }

        } catch (e: SerialPortException) {
            logger.error("Error while handling serial event.", e)
        }
    }

    override fun open(): Boolean = open(serialInfo.baudRate)

    override fun open(baudRate: Int): Boolean {
        return try {
            openSerialPort(usbDevice, baudRate, null)
            true
        } catch (e: Exception) {
            logger.warn("Unable to open serial port: " + e.message)
            false
        }
    }

    override fun open(baudRate: Int, flowControl: ZigBeePort.FlowControl): Boolean {
        return try {
            openSerialPort(usbDevice, baudRate, flowControl)
            true
        } catch (e: Exception) {
            logger.warn("Unable to open serial port: " + e.message)
            false
        }
    }

    private fun openSerialPort(device: UsbDevice, baudRate: Int, flowControl: ZigBeePort.FlowControl?) {
        if (serialPort != null) throw RuntimeException("Serial port already open.")

        println("Attempting to open. baudRate: $baudRate, flowControl: $flowControl")
        val port = FelHR85UsbSerialPort(ContextProvider.appContext)
        serialPort = port

        port.open(serialInfo, device, onDataRead)
    }

    override fun close() = try {
        serialPort?.let {
            synchronized(lock) {
                it.close()
                serialPort = null
                lock.notify()
            }
        } ?: Unit
    } catch (e: Exception) {
        logger.warn("Error closing serial port: '$serialInfo'", e)
    }

    override fun write(value: Int) {
        serialPort?.apply {
            try {
                this.write(byteArrayOf(value.toByte()))
            } catch (e: SerialPortException) {
                e.printStackTrace()
            }
        }

    }

    override fun read(): Int = read(9999999)

    override fun read(timeout: Int): Int {
        val endTime = System.currentTimeMillis() + timeout

        try {
            while (System.currentTimeMillis() < endTime) {
                synchronized(bufferSynchronisationObject) {
                    if (start != end) {
                        val value = buffer[start++]
                        if (start >= maxLength) start = 0

                        return value
                    }
                }

                synchronized(lock) {
                    if (serialPort == null) return -1
                    lock.wait(endTime - System.currentTimeMillis())
                }
            }
            return -1
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return -1
    }

    override fun purgeRxBuffer() = synchronized(bufferSynchronisationObject) {
        start = 0
        end = 0
    }

    companion object {
        /**
         * The logger.
         */
        private val logger = LoggerFactory.getLogger(ZigBeeSerialPort::class.java)
    }
}
