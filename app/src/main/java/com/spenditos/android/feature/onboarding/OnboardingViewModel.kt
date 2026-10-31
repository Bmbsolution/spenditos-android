package com.spenditos.android.feature.onboarding

import com.spenditos.android.core.i18n.Strings
import com.spenditos.android.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spenditos.android.core.network.ApiError
import com.spenditos.android.data.onboarding.OnboardingActions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingErrorKind { COUNTRY, INPUT, COMPLETE }

data class OnboardingUiState(
    val page: Int = 0,
    val displayName: String = "",
    val country: String = defaultCountry(),
    val currency: String = OnboardingData.currencyForCountry(defaultCountry()),
    val language: String = com.spenditos.android.core.i18n.AppLanguage.resolved(),
    val isDetectingCountry: Boolean = false,
    val countryLoaded: Boolean = false,
    val countryEdited: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val errorKind: OnboardingErrorKind? = null,
)

sealed interface OnboardingEvent {
    data object Finished : OnboardingEvent
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboarding: OnboardingActions,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        OnboardingUiState(
            page = savedStateHandle[KEY_PAGE] ?: 0,
            displayName = savedStateHandle[KEY_NAME] ?: "",
            country = savedStateHandle[KEY_COUNTRY] ?: defaultCountry(),
            currency = savedStateHandle[KEY_CURRENCY] ?: OnboardingData.currencyForCountry(defaultCountry()),
            language = com.spenditos.android.core.i18n.AppLanguage.resolved(),
            countryLoaded = savedStateHandle[KEY_COUNTRY_LOADED] ?: false,
            countryEdited = savedStateHandle[KEY_COUNTRY_EDITED] ?: false,
        ).let { it.copy(page = it.page.coerceIn(0, LAST_PAGE)) },
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun initialize() {
        if (_state.value.isDetectingCountry || _state.value.countryLoaded) return
        update { it.copy(isDetectingCountry = true, error = null, errorKind = null) }
        viewModelScope.launch {
            try {
                val detected = onboarding.detectCountry()?.uppercase()?.takeIf { it.length == 2 }
                update { current ->
                    val next = if (detected != null && !current.countryEdited) {
                        current.copy(country = detected, currency = OnboardingData.currencyForCountry(detected))
                    } else {
                        current
                    }
                    next.copy(isDetectingCountry = false, countryLoaded = true, error = null, errorKind = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (e is ApiError.Network) {
                    Strings.get(R.string.onboarding_you_re_offline_check_your)
                } else {
                    Strings.get(R.string.onboarding_couldn_t_check_your_country)
                }
                update { it.copy(isDetectingCountry = false, error = message, errorKind = OnboardingErrorKind.COUNTRY) }
            }
        }
    }

    fun onNameChange(value: String) = update { it.copy(displayName = value.take(80), error = null, errorKind = null) }

    fun onCountryChange(value: String) {
        val country = value.filter(Char::isLetter).take(2).uppercase()
        update {
            it.copy(
                country = country,
                currency = OnboardingData.currencyForCountry(country),
                countryLoaded = true,
                countryEdited = true,
                error = null,
                errorKind = null,
            )
        }
        savedStateHandle[KEY_COUNTRY_EDITED] = true
    }

    fun onCurrencyChange(value: String) = update {
        it.copy(currency = value.filter(Char::isLetter).take(3).uppercase(), error = null, errorKind = null)
    }

    fun onContinue() {
        val current = _state.value
        when (current.page) {
            NAME_PAGE -> if (current.displayName.isBlank()) {
                update { it.copy(error = Strings.get(R.string.onboarding_enter_your_name_to_continue), errorKind = OnboardingErrorKind.INPUT) }
            } else {
                update { it.copy(page = PREFERENCES_PAGE, error = null, errorKind = null) }
                if (!_state.value.countryLoaded && !_state.value.isDetectingCountry) initialize()
            }
            PREFERENCES_PAGE -> if (current.country.length != 2 || current.currency.length != 3) {
                update { it.copy(error = Strings.get(R.string.onboarding_enter_a_two_letter_country), errorKind = OnboardingErrorKind.INPUT) }
            } else {
                update { it.copy(page = READY_PAGE, error = null, errorKind = null) }
            }
            WELCOME_PAGE -> update { it.copy(page = NAME_PAGE) }
        }
    }

    fun onBack() {
        update { current -> current.copy(page = (current.page - 1).coerceAtLeast(WELCOME_PAGE), error = null, errorKind = null) }
    }

    fun retry() {
        when (_state.value.errorKind) {
            OnboardingErrorKind.COUNTRY -> initialize()
            OnboardingErrorKind.COMPLETE -> finish()
            else -> Unit
        }
    }

    fun finish() {
        val current = _state.value
        if (current.page != READY_PAGE || current.isSaving) return
        update { it.copy(isSaving = true, error = null, errorKind = null) }
        viewModelScope.launch {
            try {
                onboarding.complete(
                    displayName = current.displayName.trim(),
                    currency = current.currency,
                    country = current.country,
                    language = current.language,
                    seedCategories = OnboardingData.categorySeeds.map { it.toRequest() },
                )
                update { it.copy(isSaving = false) }
                _events.send(OnboardingEvent.Finished)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = if (e is ApiError.Network) {
                    Strings.get(R.string.onboarding_you_re_offline_check_your_2)
                } else {
                    Strings.get(R.string.onboarding_couldn_t_finish_setup_please)
                }
                update { it.copy(isSaving = false, error = message, errorKind = OnboardingErrorKind.COMPLETE) }
            }
        }
    }

    private fun update(transform: (OnboardingUiState) -> OnboardingUiState) {
        _state.update(transform)
        val current = _state.value
        savedStateHandle[KEY_PAGE] = current.page
        savedStateHandle[KEY_NAME] = current.displayName
        savedStateHandle[KEY_COUNTRY] = current.country
        savedStateHandle[KEY_CURRENCY] = current.currency
        savedStateHandle[KEY_COUNTRY_LOADED] = current.countryLoaded
        savedStateHandle[KEY_COUNTRY_EDITED] = current.countryEdited
    }

    private companion object {
        const val WELCOME_PAGE = 0
        const val NAME_PAGE = 1
        const val PREFERENCES_PAGE = 2
        const val READY_PAGE = 3
        const val LAST_PAGE = READY_PAGE
        const val KEY_PAGE = "onboarding.page"
        const val KEY_NAME = "onboarding.name"
        const val KEY_COUNTRY = "onboarding.country"
        const val KEY_CURRENCY = "onboarding.currency"
        const val KEY_COUNTRY_LOADED = "onboarding.countryLoaded"
        const val KEY_COUNTRY_EDITED = "onboarding.countryEdited"
    }
}

private fun defaultCountry(): String = Locale.getDefault().country.uppercase().takeIf { it.length == 2 } ?: "US"
