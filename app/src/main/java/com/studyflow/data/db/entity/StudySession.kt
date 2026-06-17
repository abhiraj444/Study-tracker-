package com.studyflow.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "subject")
    val subject: String,
    @ColumnInfo(name = "chapter")
    val chapter: String?,
    @ColumnInfo(name = "mode")
    val mode: String?,
    @ColumnInfo(name = "start_time")
    val startTime: Long,
    @ColumnInfo(name = "end_time")
    val endTime: Long?,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int?,
    @ColumnInfo(name = "break_duration_seconds")
    val breakDurationSeconds: Int = 0,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "mood_rating")
    val moodRating: Int?,
    @ColumnInfo(name = "focus_score")
    val focusScore: Int?,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
