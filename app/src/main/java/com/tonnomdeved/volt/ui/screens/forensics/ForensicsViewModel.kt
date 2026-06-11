package com.tonnomdeved.volt.ui.screens.forensics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonnomdeved.volt.VoltApplication
import com.tonnomdeved.volt.data.forensics.NightReport
import com.tonnomdeved.volt.data.hibernation.HibernationLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ForensicsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as VoltApplication).container
    private val analyzer = container.forensicsAnalyzer
    private val controller = container.hibernationController

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _report = MutableStateFlow<NightReport?>(null)
    val report: StateFlow<NightReport?> = _report.asStateFlow()

    private val _windowHours = MutableStateFlow(8)
    val windowHours: StateFlow<Int> = _windowHours.asStateFlow()

    fun setWindowHours(hours: Int) {
        _windowHours.value = hours.coerceIn(1, 24)
    }

    fun scan() {
        viewModelScope.launch {
            _loading.value = true
            val report = analyzer.analyze(windowMs = TimeUnit.HOURS.toMillis(_windowHours.value.toLong()))
            _report.value = report
            _loading.value = false
        }
    }

    /** Hiberne directement une app coupable depuis le rapport (niveau SOFT). */
    fun hibernate(packageName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = controller.hibernate(packageName, HibernationLevel.SOFT)
            onResult(res is com.tonnomdeved.volt.data.hibernation.HibernationResult.Success ||
                     res is com.tonnomdeved.volt.data.hibernation.HibernationResult.Unchanged)
        }
    }
}
