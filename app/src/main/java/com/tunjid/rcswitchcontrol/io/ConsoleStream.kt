package com.tunjid.rcswitchcontrol.io

import java.io.PrintStream

class ConsoleStream(consumer: (String) -> Unit) : PrintStream(FlushNotifyingOutputStream(consumer), true)