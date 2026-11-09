package com.yaadein.app

import android.app.Application

/** Plain Application for Robolectric tests so Hilt and the Keystore are never touched on the JVM. */
class TestApplication : Application()
