package com.spendobserver.data.local

import com.spendobserver.data.local.entity.RecordEntity
import com.spendobserver.data.local.entity.TagEntity
import com.spendobserver.data.local.entity.TrackerEntity
import com.spendobserver.data.local.relation.RecordWithParts
import com.spendobserver.domain.model.Part
import com.spendobserver.domain.model.Record
import com.spendobserver.domain.model.RecordDraft
import com.spendobserver.domain.model.Tag
import com.spendobserver.domain.model.Tracker

internal fun TrackerEntity.toDomain() = Tracker(id, title, createdAt, updatedAt, backupAt)

internal fun Tracker.toEntity() = TrackerEntity(id, title, createdAt, updatedAt, backupAt)

internal fun TagEntity.toDomain() = Tag(id, trackerId, name, isGlobal)

internal fun Tag.toEntity() = TagEntity(id, trackerId, name, isGlobal)

internal fun RecordDraft.toEntity() = RecordEntity(id, trackerId, date, type, description)

/** Parts in their entered order: Room doesn't order [androidx.room.Relation] lists. */
internal fun RecordWithParts.toDomain() = Record(
    id = record.id,
    trackerId = record.trackerId,
    date = record.date,
    type = record.type,
    description = record.description,
    tags = tags.map { it.toDomain() },
    parts = parts.sortedBy { it.part.position }.map { p -> Part(p.part.amountCents, p.tags.map { it.toDomain() }) },
)
