package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import kotlinx.coroutines.flow.first

/**
 * The summary sheet of [running] at [lengthMs] with «Что играли» (spec 3.12, 3.28), read from the stores themselves:
 * a view model made just now for this very ask — «Закончить занятие» from Live, the prompt of a forgotten practice —
 * has not heard from them yet, and its mirrors would give an empty list. Only the blocks of this practice: blocks left
 * of another one are not its own.
 */
internal suspend fun summarySheetOf(
    running: RunningPractice,
    lengthMs: Long,
    config: PracticeConfig,
    blocks: BlockStore,
    repertoire: RepertoireRepository,
): PracticeSheet.Summary = PracticeReducer.summarySheet(
    startedAtEpochMs = running.startedAtEpochMs,
    actualMs = lengthMs,
    config = config,
    blocks = BlockRules.ofPractice(running, blocks.blocks.first()),
    titles = repertoire.pieces.first().associate { it.id to it.title },
)
