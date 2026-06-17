package com.studyflow.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.studyflow.data.db.dao.StudySessionDao
import com.studyflow.data.db.entity.StudySession

@Database(entities = [StudySession::class], version = 1, exportSchema = false)
abstract class StudyFlowDatabase : RoomDatabase() {
    abstract fun studySessionDao(): StudySessionDao
}
