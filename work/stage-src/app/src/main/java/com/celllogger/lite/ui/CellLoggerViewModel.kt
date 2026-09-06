package com.celllogger.lite.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.celllogger.lite.data.local.CellSampleEntity
import com.celllogger.lite.data.local.NetworkTypeStat
import com.celllogger.lite.data.local.RsrpSummary
import com.celllogger.lite.data.local.SessionSummary
import com.celllogger.lite.data.model.CellReading
import com.celllogger.lite.data.model.CollectionConfig
import com.celllogger.lite.data.model.ExperimentId
import com.celllogger.lite.data.source.DataSourceAvailability
import com.celllogger.lite.di.ServiceLocator
import com.celllogger.lite.export.CsvExporter
import com.celllogger.lite.service.CellLoggerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CellLoggerViewModel(application: Application) : AndroidViewModel(application) {

    private val services = ServiceLocator.instance

    val running: StateFlow<Boolean> = services.collectionManager.running
    val session: StateFlow<CollectionConfig?> = services.collectionManager.session
    val managerError: StateFlow<String?> = services.collectionManager.error
    val currentReading: StateFlow<CellReading?> = services.collectionManager.currentReading
    val telephonyAvailability: StateFlow<DataSourceAvailability> =
        services.telephonyDataSource.availability

    val recentSamples: StateFlow<List<CellSampleEntity>> = services.repository.recentSamples
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessionSummaries: StateFlow<List<SessionSummary>> = services.repository.sessionSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val networkTypeStats: StateFlow<List<NetworkTypeStat>> =
        services.repository.networkTypeStats
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rsrpSummary: StateFlow<RsrpSummary?> = services.repository.rsrpSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _intervalMs = MutableStateFlow(2_000)
    val intervalMs: StateFlow<Int> = _intervalMs.asStateFlow()

    private val _experiment = MutableStateFlow(ExperimentId.STATIC)
    val experiment: StateFlow<ExperimentId> = _experiment.asStateFlow()

    private val _manualBusy = MutableStateFlow(false)
    val manualBusy: StateFlow<Boolean> = _manualBusy.asStateFlow()

    fun setIntervalMs(value: Int) {
        _intervalMs.value = value.coerceIn(1_000, 5_000)
    }

    fun setExperiment(value: ExperimentId) {
        _experiment.value = value
    }

    fun startCollection(note: String = "") {
        val config = CollectionConfig.create(
            experimentId = _experiment.value,
            sampleIntervalMs = _intervalMs.value,
            subscriptionId = null,
            note = note
        )
        CellLoggerService.start(getApplication(), config)
    }

    fun stopCollection() {
        CellLoggerService.stop(getApplication())
    }

    fun requestManualSample() {
        if (!running.value || _manualBusy.value) return
        _manualBusy.value = true
        services.collectionManager.requestManualSample()
        viewModelScope.launch {
            // Keep the button disabled while a request callback is expected.
            kotlinx.coroutines.delay(3_000)
            _manualBusy.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch { services.repository.clearAll() }
    }

    fun exportCsv(uri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch {
            val samples = services.repository.getAllSamples()
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                CsvExporter.write(samples, it)
            }
            onComplete()
        }
    }
}
