package com.example.violintuner.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.violintuner.core.domain.practice.BlockStore
import com.example.violintuner.core.domain.practice.PieceBlock
import com.example.violintuner.core.domain.practice.PracticeBlocks
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The blocks of the running practice (spec 3.28), beside the practice itself in the settings file:
 * like its timer, they have to outlive the process, and a row in the database would be a block of a
 * practice that may never be saved. One key, one string: a start or a stop is a single atomic edit.
 */
class DataStoreBlockStore @Inject constructor(private val store: DataStore<Preferences>) : BlockStore {
    override val blocks: Flow<PracticeBlocks?> = store.data
        .map { prefs -> prefs[BLOCKS]?.let(BlockCodec::decode) }
        .distinctUntilChanged()

    override suspend fun update(transform: (PracticeBlocks?) -> PracticeBlocks?) {
        store.edit { prefs ->
            val next = transform(prefs[BLOCKS]?.let(BlockCodec::decode))
            if (next == null) prefs.remove(BLOCKS) else prefs[BLOCKS] = BlockCodec.encode(next)
        }
    }

    override suspend fun clear() {
        store.edit { it.remove(BLOCKS) }
    }

    private companion object {
        val BLOCKS = stringPreferencesKey("practice_blocks")
    }
}

/**
 * `practiceStart|current|finished,finished…`, a block being `pieceId:start:goal:end` with the end
 * left empty while it is on the bookmark. Numbers only, so the separators never occur inside a
 * field; anything unreadable is dropped rather than failing — it is a practice in progress, not history.
 */
internal object BlockCodec {
    private const val PARTS = '|'
    private const val LIST = ','
    private const val FIELDS = ':'

    fun encode(blocks: PracticeBlocks): String =
        listOf(
            blocks.practiceStartedAtEpochMs.toString(),
            blocks.current?.let(::encodeBlock).orEmpty(),
            blocks.finished.joinToString(LIST.toString(), transform = ::encodeBlock),
        ).joinToString(PARTS.toString())

    fun decode(text: String): PracticeBlocks? {
        val parts = text.split(PARTS)
        if (parts.size != 3) return null
        val practice = parts[0].toLongOrNull() ?: return null
        return PracticeBlocks(
            practiceStartedAtEpochMs = practice,
            current = parts[1].takeIf { it.isNotEmpty() }?.let(::decodeBlock),
            finished = parts[2].split(LIST).filter { it.isNotEmpty() }.mapNotNull(::decodeBlock),
        )
    }

    private fun encodeBlock(block: PieceBlock): String =
        listOf(block.pieceId, block.startedAtEpochMs, block.goalMs, block.endedAtEpochMs ?: "").joinToString(FIELDS.toString())

    private fun decodeBlock(text: String): PieceBlock? {
        val fields = text.split(FIELDS)
        if (fields.size != 4) return null
        return PieceBlock(
            pieceId = fields[0].toLongOrNull() ?: return null,
            startedAtEpochMs = fields[1].toLongOrNull() ?: return null,
            goalMs = fields[2].toLongOrNull() ?: return null,
            endedAtEpochMs = if (fields[3].isEmpty()) null else fields[3].toLongOrNull() ?: return null,
        )
    }
}
