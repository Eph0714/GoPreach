package com.emfitsolutions.gopreach.ui.screens.biblestudy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emfitsolutions.gopreach.data.model.BibleStudyRecord
import com.emfitsolutions.gopreach.data.repository.BibleStudyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Spec §6.4 — publisher-managed Bible Study Record CRUD. */
@HiltViewModel
class BibleStudyViewModel @Inject constructor(
    private val bibleStudyRepository: BibleStudyRepository,
) : ViewModel() {

    fun recordsFor(publisherPersonId: String): Flow<List<BibleStudyRecord>> =
        bibleStudyRepository.observeForPublisher(publisherPersonId)

    fun save(record: BibleStudyRecord) {
        viewModelScope.launch { bibleStudyRepository.save(record) }
    }

    fun delete(recordId: String) {
        viewModelScope.launch { bibleStudyRepository.delete(recordId) }
    }
}
