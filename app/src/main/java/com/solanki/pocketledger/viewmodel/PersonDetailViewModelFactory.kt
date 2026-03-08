package com.solanki.pocketledger.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PersonDetailViewModelFactory(
    private val application: Application,
    private val personName: String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PersonDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PersonDetailViewModel(application, personName) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
