package com.violinjourney.app.core.recording.video

import kotlinx.coroutines.flow.MutableStateFlow

/** The rule the thumbnails were made by, in memory: the current one by default — nothing to make anew. */
class FakeVideoThumbRuleStore(rule: Int = VideoThumbs.RULE) : VideoThumbRuleStore {
    override val rule = MutableStateFlow(rule)

    override suspend fun markRule(rule: Int) {
        this.rule.value = rule
    }
}
