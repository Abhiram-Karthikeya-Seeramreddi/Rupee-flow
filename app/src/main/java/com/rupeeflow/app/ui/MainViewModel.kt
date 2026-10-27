package com.rupeeflow.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rupeeflow.app.container
import com.rupeeflow.app.data.FinanceRepository
import com.rupeeflow.app.data.InsightsBuilder
import com.rupeeflow.app.data.InsightsData
import com.rupeeflow.app.data.Settings
import com.rupeeflow.app.data.Snapshot
import com.rupeeflow.app.data.SnapshotBuilder
import com.rupeeflow.app.data.TransactionEntity
import com.rupeeflow.app.data.ValidationException
import com.rupeeflow.app.notify.Scheduling
import com.rupeeflow.core.DateRange
import com.rupeeflow.core.PeriodKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val c = app.container
    val repo: FinanceRepository = c.repo

    /** Emits the date and re-emits after midnight so "today" stays correct while the app is open. */
    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged()

    val settings: StateFlow<Settings?> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The single source for every screen: built from one consistent read of the ledger. */
    val snapshot: StateFlow<Snapshot?> = combine(repo.observeRaw(), c.settings.flow, today) { r, s, d ->
        SnapshotBuilder.build(d, s, r.accounts, r.categories, r.transactions, r.budgets, r.subscriptions, r.payments,
            r.goals, r.rules, r.templates, r.dayChecks, r.challenges)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val insightsPeriod = MutableStateFlow(PeriodKind.SALARY_CYCLE)
    val insightsCustom = MutableStateFlow<DateRange?>(null)

    val insights: StateFlow<InsightsData?> = combine(snapshot, insightsPeriod, insightsCustom) { s, k, cr ->
        s?.let { InsightsBuilder.build(it, k, cr) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    fun toast(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _messages.tryEmit(UiMessage(text, actionLabel, action))
    }

    /** Runs a write; validation errors become friendly messages instead of crashes. */
    fun write(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let { toast(it) }
            } catch (e: ValidationException) {
                toast(e.message ?: "Please check the details.")
            } catch (e: Exception) {
                toast("Couldn't save: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun updateSettings(reschedule: Boolean = false, f: (Settings) -> Settings) {
        viewModelScope.launch {
            c.settings.update(f)
            if (reschedule) Scheduling.scheduleAll(getApplication(), c.settings.current())
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            val d = repo.deleteTransaction(id) ?: return@launch
            toast("Deleted", "Undo") { viewModelScope.launch { repo.undoDelete(d) } }
        }
    }

    fun saveTransaction(t: TransactionEntity, onDone: () -> Unit = {}) {
        write {
            repo.saveTransaction(t)
            onDone()
        }
    }

    suspend fun duplicateOf(t: TransactionEntity): TransactionEntity? = repo.findLikelyDuplicate(t)
}
