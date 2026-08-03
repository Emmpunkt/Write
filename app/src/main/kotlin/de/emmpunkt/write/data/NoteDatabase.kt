package de.emmpunkt.write.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, TemplateEntity::class], version = 2, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun notes(): RoomNoteDao

    abstract fun templates(): RoomTemplateDao

    companion object {
        @Volatile
        private var instanz: NoteDatabase? = null

        fun dao(context: Context): NoteDao = datenbank(context).notes()

        fun templateDao(context: Context): TemplateDao = datenbank(context).templates()

        /**
         * Die Anweisung stammt WOERTLICH aus dem von Room erzeugten `NoteDatabase_Impl.kt`.
         *
         * Room prueft beim Start Spalte fuer Spalte gegen seine eigene Erwartung; eine selbst
         * formulierte Anweisung weicht fast immer in einem Detail ab (NOT NULL, Reihenfolge,
         * Typname) und laesst die App dann beim Oeffnen abstuerzen.
         */
        private const val CREATE_TEMPLATES =
            "CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `name` TEXT NOT NULL, `text` TEXT NOT NULL, `werte` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `fontId` TEXT NOT NULL, `sizeMm` REAL NOT NULL, " +
                "`align` TEXT NOT NULL, `lineSpacing` REAL NOT NULL, `letterSpacing` REAL NOT " +
                "NULL, `wordSpacing` REAL NOT NULL, `slantDeg` REAL NOT NULL, `paperWidthMm` " +
                "REAL NOT NULL, `paperHeightMm` REAL NOT NULL, `marginMm` REAL NOT NULL)"

        /**
         * Version 1 -> 2: die Vorlagentabelle kommt dazu, die Notizen bleiben unberuehrt.
         *
         * `fallbackToDestructiveMigration` waere hier die falsche Abkuerzung - sie loeschte
         * alle Notizen des Nutzers, nur damit die App startet.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_TEMPLATES)
            }
        }

        /**
         * Eine Datenbank fuer die ganze App.
         *
         * Room haelt Verbindungen und einen Zwischenspeicher; zwei Instanzen auf derselben
         * Datei wuerden sich gegenseitig veraltete Staende zeigen.
         */
        private fun datenbank(context: Context): NoteDatabase = instanz ?: synchronized(this) {
            instanz ?: Room.databaseBuilder(
                context.applicationContext,
                NoteDatabase::class.java,
                "write_notes.db",
            ).addMigrations(MIGRATION_1_2).build().also { instanz = it }
        }
    }
}
