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
            vm.reset()
        }
    }

    @Test
    fun `counts down while running`() = timerTest { vm ->
        vm.start()
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(vm.state.value.isRunning)
        assertEquals("24:00", vm.state.value.formatted)
    }

    @Test
    fun `pause freezes remaining time and start resumes`() = timerTest { vm ->
        vm.start()
        advanceTimeBy(10_000)
        vm.pause()
        val paused = vm.state.value.remainingMillis

        advanceTimeBy(120_000)
        assertEquals(paused, vm.state.value.remainingMillis)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isIdle)

        vm.start()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(paused - 5_000, vm.state.value.remainingMillis)
    }

    @Test
    fun `focus is followed by a 5 minute break and then focus again`() = timerTest { vm ->
        vm.start()
        advanceTimeBy(25 * 60_000L)
        runCurrent()

        assertEquals(FocusPhase.BREAK, vm.state.value.phase)
        assertEquals(1, vm.state.value.completedFocusSessions)
        assertTrue(vm.state.value.isRunning)

        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(FocusPhase.FOCUS, vm.state.value.phase)
        assertEquals("25:00", vm.state.value.formatted)
    }

    @Test
    fun `reset returns to idle 25 minute focus`() = timerTest { vm ->
        vm.start()
        advanceTimeBy(90_000)
        vm.reset()

        val state = vm.state.value
        assertTrue(state.isIdle)
        assertEquals(FocusPhase.FOCUS, state.phase)
        assertEquals("25:00", state.formatted)
    }
}
