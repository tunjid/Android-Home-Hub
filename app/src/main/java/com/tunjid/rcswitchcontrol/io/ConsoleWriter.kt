package com.tunjid.rcswitchcontrol.io

import java.io.PrintWriter

class ConsoleWriter(consumer: (String) -> Unit) : PrintWriter(FlushNotifyingOutputStream(consumer), true)