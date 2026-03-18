package com.example.mysmscode

import com.example.mysmscode.domain.FailedRetryFilterOption
import com.example.mysmscode.domain.HistoryFilterOption
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkbenchTextResourceMappingTest {

    @Test
    fun historyFilterLabelRes_mapsAllFiltersToStringResources() {
        assertEquals(R.string.history_filter_all, historyFilterLabelRes(HistoryFilterOption.ALL))
        assertEquals(R.string.history_filter_success, historyFilterLabelRes(HistoryFilterOption.SUCCESS))
        assertEquals(R.string.history_filter_failed, historyFilterLabelRes(HistoryFilterOption.FAILED))
        assertEquals(R.string.history_filter_not_matched, historyFilterLabelRes(HistoryFilterOption.NOT_MATCHED))
        assertEquals(
            R.string.history_filter_configuration_failed,
            historyFilterLabelRes(HistoryFilterOption.CONFIGURATION_FAILED),
        )
    }

    @Test
    fun failedRetryFilterLabelRes_mapsAllFiltersToStringResources() {
        assertEquals(R.string.failed_filter_all, failedRetryFilterLabelRes(FailedRetryFilterOption.ALL))
        assertEquals(R.string.failed_filter_scheduled, failedRetryFilterLabelRes(FailedRetryFilterOption.SCHEDULED))
        assertEquals(R.string.failed_filter_exhausted, failedRetryFilterLabelRes(FailedRetryFilterOption.EXHAUSTED))
        assertEquals(
            R.string.failed_filter_non_recoverable,
            failedRetryFilterLabelRes(FailedRetryFilterOption.NON_RECOVERABLE),
        )
    }

    @Test
    fun smsStatusLabelRes_mapsKnownStatusesToStringResources() {
        assertEquals(R.string.record_status_not_matched, smsStatusLabelRes("NOT_MATCHED"))
        assertEquals(R.string.record_status_pending_forward, smsStatusLabelRes("PENDING_FORWARD"))
        assertEquals(R.string.record_status_configuration_failed, smsStatusLabelRes("CONFIGURATION_FAILED"))
        assertEquals(R.string.record_status_success, smsStatusLabelRes("SUCCESS"))
        assertEquals(R.string.record_status_failed, smsStatusLabelRes("FAILED"))
    }

    @Test
    fun smsSourceLabelRes_mapsKnownSourcesToStringResources() {
        assertEquals(R.string.record_source_real_sms, smsSourceLabelRes("REAL_SMS"))
        assertEquals(R.string.record_source_simulation, smsSourceLabelRes("SIMULATION"))
    }
}
