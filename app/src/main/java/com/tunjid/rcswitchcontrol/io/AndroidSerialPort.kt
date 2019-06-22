package com.tunjid.rcswitchcontrol.io

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.tunjid.rcswitchcontrol.App
import com.zsmartsystems.zigbee.serial.ZigBeeSerialPort
import com.zsmartsystems.zigbee.transport.ZigBeePort
import jssc.SerialPortException
import org.slf4j.LoggerFactory


class AndroidSerialPort(
        private val driver: UsbSerialDriver,
        private val baudRate: Int
) : ZigBeePort, SerialInputOutputManager.Listener {


    private var end = 0
    private var start = 0
    private val maxLength = 512

    private val buffer = IntArray(512)

    private var serialPort: UsbSerialPort? = null
    private var serialThread: HandlerThread? = null
    private var serialHandler: Handler? = null

    private val lock = Object()
    private val bufferSynchronisationObject = Any()

    private lateinit var serialInputOutputManager: SerialInputOutputManager

    override fun open(): Boolean = open(baudRate)

    override fun open(baudRate: Int): Boolean {
        return try {
            openSerialPort(driver, baudRate, null)
            true
        } catch (e: Exception) {
            logger.warn("Unable to open serial port: " + e.message)
            false
        }
    }

    override fun open(baudRate: Int, flowControl: ZigBeePort.FlowControl): Boolean {
        return try {
            openSerialPort(driver, baudRate, flowControl)
            true
        } catch (e: Exception) {
            logger.warn("Unable to open serial port: " + e.message)
            false
        }
    }

    /**
     * Opens serial port.
     *
     * @param driver the USB serial driver
     * @param baudRate the baud rate
     * @param flowControl the flow control option
     */
    private fun openSerialPort(driver: UsbSerialDriver, baudRate: Int, flowControl: ZigBeePort.FlowControl?) {
        if (serialPort != null) throw RuntimeException("Serial port already open.")

        logger.debug("Opening port {} at {} baud with {}.", driver.ports[0].portNumber, baudRate, flowControl)

        val manager = App.instance.getSystemService(Context.USB_SERVICE) as UsbManager


        val connection = manager.openDevice(driver.device)
                ?: // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
                return

        serialPort = driver.ports[0]
        val port = serialPort!!

        try {
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            serialInputOutputManager = SerialInputOutputManager(port, this)

            serialThread = HandlerThread("serialThread").apply { start() }
            serialHandler = serialThread?.let { Handler(it.looper) }

            serialHandler?.post(serialInputOutputManager)

        } catch (e: SerialPortException) {
            logger.error("Error opening serial port.", e)
            throw RuntimeException("Failed to open serial port: $driver", e)
        }

    }

    override fun close() = try {
        serialPort?.apply {
            synchronized(lock) {
                close()
                serialInputOutputManager.stop()

                serialPort = null
                lock.notify()
            }

            logger.info("Serial port '$driver' closed.")
        }
        serialThread?.quitSafely()
        serialThread = null
        serialHandler = null
    } catch (e: Exception) {
        logger.warn("Error closing serial port: '$driver'", e)
    }

    override fun write(value: Int) {
        serialPort?.apply {
            try {
                this.write(byteArrayOf(value.toByte()), 9999999)
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

    override fun onRunError(e: java.lang.Exception?) = e?.printStackTrace() ?: Unit

    override fun onNewData(data: ByteArray?) {
        try {
            data ?: return

            synchronized(bufferSynchronisationObject) {
                val intBuffer = data.map { if (it < 0) 256 + it else it.toInt() }

                for (recv in intBuffer) {
                    buffer[end++] = recv
                    if (end >= maxLength) end = 0
                }
            }

            synchronized(lock) {
                lock.notify()
            }

        } catch (e: SerialPortException) {
            logger.error("Error while handling serial event.", e)
        }
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
