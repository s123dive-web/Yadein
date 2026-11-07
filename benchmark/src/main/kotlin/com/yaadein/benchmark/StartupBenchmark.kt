package com.yaadein.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start with the baseline profile applied (CompilationMode.Partial) versus without (None). The CI workflow reads
 * the JSON results and fails if the profiled cold start median goes over the budget in docs/PERFORMANCE.md.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldStartWithProfile() = start(CompilationMode.Partial())
    @Test fun coldStartNoCompilation() = start(CompilationMode.None())

    private fun start(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
    }
}

const val TARGET = "com.yaadein.app.debug"
