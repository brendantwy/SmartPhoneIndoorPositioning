package com.example.tp

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.*
import com.example.tp.repository.UserPreferencesRepository
import kotlinx.coroutines.*
import java.lang.IllegalArgumentException

class BleScannerViewModel(
    private val userPref: UserPreferencesRepository,
    private val appContext: Context
) : ViewModel(){

    //
    private var _switchStatus = MutableLiveData<Boolean>()
    var switchStatus: LiveData<Boolean> = _switchStatus
    lateinit var text: MutableLiveData<String>

    companion object {
        private val TAG = BleScannerViewModel::class.simpleName
        private lateinit var BUTTON_TEXT:String
    }

    fun changeSwitchState(state: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (state)
            _switchStatus.postValue(true)
            //call service here
        else {
            _switchStatus.postValue(false)
        }
    }

    fun editText(value: String) = viewModelScope.launch(Dispatchers.IO) {
        text.postValue(value)
    }
}
class BleScannerViewModelFactory(
    private val userPref: UserPreferencesRepository,
    private val appContext: Context)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BleScannerViewModel::class.java)) {
            return BleScannerViewModel(userPref, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}






