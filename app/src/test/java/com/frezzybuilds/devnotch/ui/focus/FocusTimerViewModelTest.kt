package com.frezzybuilds.devnotch.ui.focus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusTimerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Die Uhr des ViewModels ist die virtuelle Zeit des Test-Schedulers. Der Timer wird immer
     * gestoppt, sonst tickt er nach einem fehlgeschlagenen Assert endlos in virtueller Zeit.
     */
    private fun timerTest(block: suspend TestScope.(FocusTimerViewModel) -> Unit) = runTest(dispatcher) {
        val vm = FocusTimerViewModel(clock = { testScheduler.currentTime })
        try {
            block(vm)
        } finally {
            vm.resetTimer()
        }
    }

    @Test
    fun `defaults to 25 minutes, not running`() = timerTest { vm ->
        assertEquals(25 * 60L, vm.remainingTime.value)
        assertEquals(25 * 60L, vm.totalTime.value)
        assertFalse(vm.isRunning.value)
    }

    @Test
    fun `counts down while running`() = timerTest { vm ->
        vm.startTimer()
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(vm.isRunning.value)
        assertEquals(24 * 60L, vm.remainingTime.value)
        assertEquals("24:00", formatMmSs(vm.remainingTime.value))
    }

    @Test
    fun `pause freezes remaining time and start resumes`() = timerTest { vm ->
        vm.startTimer()
        advanceTimeBy(10_500)
        vm.pauseTimer()
        assertFalse(vm.isRunning.value)
        assertEquals(1490L, vm.remainingTime.value) // 1489,5 s aufgerundet

        advanceTimeBy(120_000)
        assertEquals(1490L, vm.remainingTime.value)

        // Die angebrochene halbe Sekunde geht beim Pausieren nicht verloren.
        vm.startTimer()
        advanceTimeBy(5_500)
        runCurrent()
        assertEquals(1484L, vm.remainingTime.value)
    }

    @Test
    fun `stops at zero and start restarts the preset`() = timerTest { vm ->
        vm.resetTimer(5)
        vm.startTimer()
        advanceTimeBy(5 * 60_000L)
        runCurrent()

        assertEquals(0L, vm.remainingTime.value)
        assertFalse(vm.isRunning.value)

        vm.startTimer()
        runCurrent()
        assertEquals(5 * 60L, vm.remainingTime.value)
        assertTrue(vm.isRunning.value)
    }

    @Test
    fun `resetTimer switches preset and stops`() = timerTest { vm ->
        vm.startTimer()
        advanceTimeBy(90_000)
        vm.resetTimer(5)

        assertFalse(vm.isRunning.value)
        assertEquals(5 * 60L, vm.remainingTime.value)
        assertEquals(5 * 60L, vm.totalTime.value)

        advanceTimeBy(60_000)
        assertEquals(5 * 60L, vm.remainingTime.value)
    }
}
