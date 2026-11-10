package com.yaadein.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods the first launch needs (startup, the welcome screen) so Android compiles them ahead
 * of time. CI uploads the generated `*-baseline-prof.txt`; copy it to app/src/main/baseline-prof.txt (see
 * docs/PERFORMANCE.md) to replace the hand-written profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // First run shows the welcome screen (one scrollable page): touch it, the code every new user runs.
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        repeat(2) { device.findObject(By.scrollable(true))?.fling(Direction.RIGHT); device.waitForIdle() }
        device.pressBack()
    }
}
