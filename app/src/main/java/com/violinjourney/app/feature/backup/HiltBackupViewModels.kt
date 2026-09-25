package com.violinjourney.app.feature.backup

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.backup.BackupConfig
import com.violinjourney.app.core.backup.BackupManager
import com.violinjourney.app.core.backup.BackupPrefs
import com.violinjourney.app.core.backup.BackupStore
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.time.WallClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HiltBackupViewModel @Inject constructor(
    manager: BackupManager,
    store: BackupStore,
    config: BackupConfig,
    watch: RecordingWatch,
    importer: VideoTakeImporter,
) : BackupViewModel(manager, store, config, watch, importer)

@HiltViewModel
class HiltRestoreViewModel @Inject constructor(
    manager: BackupManager,
    store: BackupStore,
    watch: RecordingWatch,
    importer: VideoTakeImporter,
    savedState: SavedStateHandle,
) : RestoreViewModel(manager, store, watch, importer, savedState)

@HiltViewModel
class HiltDataBlockViewModel @Inject constructor(
    manager: BackupManager,
    prefs: BackupPrefs,
    sessions: SessionRepository,
    store: BackupStore,
    config: BackupConfig,
    clock: WallClock,
) : DataBlockViewModel(manager, prefs, sessions, store, config, clock)
