package de.emmpunkt.write.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, TemplateEntity::class], version = 4, exportSchema = false)
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
         *
         * ACHTUNG: Das ist die Fassung von VERSION 2 und bleibt es. Sie fuehrt auf Stand 2,
         * die Spalten von Stand 3 haengt MIGRATION_2_3 an. Wird sie nachgezogen, bekommen
         * Geraete, die ueber 1 -> 2 -> 3 wandern, die Spalten zweimal - und die Migration
         * bricht ab.
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
         * Version 2 -> 3: Die Vorlage bekommt ihre Position dazu.
         *
         * Der Versatz war vorher global. Vorhandene Vorlagen starten deshalb bei 0/0 - das
         * entspricht dem bisherigen Verhalten, solange der globale Versatz 0 war, und ist
         * sichtbar korrigierbar, falls nicht. Ein Rateversuch waere hier schlechter als ein
         * Wert, den der Nutzer sieht und anpassen kann.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `templates` ADD COLUMN `paperOffsetXMm` REAL NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `templates` ADD COLUMN `paperOffsetYMm` REAL NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Die Vorlagentabelle auf Stand 4, wieder woertlich aus dem erzeugten
         * `NoteDatabase_Impl.kt`. Der Platzhalter `templates` wird beim Umbau ersetzt.
         */
        private const val CREATE_TEMPLATES_V4 =
            "CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT " +
                "NOT NULL, `name` TEXT NOT NULL, `text` TEXT NOT NULL, `werte` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `fontId` TEXT NOT NULL, `sizeMm` REAL NOT NULL, " +
                "`align` TEXT NOT NULL, `lineSpacing` REAL NOT NULL, `letterSpacing` REAL NOT " +
                "NULL, `wordSpacing` REAL NOT NULL, `slantDeg` REAL NOT NULL, `rahmenXMm` REAL " +
                "NOT NULL, `rahmenYMm` REAL NOT NULL, `rahmenBreiteMm` REAL NOT NULL, " +
                "`rahmenHoeheMm` REAL NOT NULL)"

        /**
         * Version 3 -> 4: Blatt und Textrahmen werden getrennt.
         *
         * Bis Stand 3 war "das Blatt der Vorlage" in Wahrheit der Textkasten - Breite, Hoehe,
         * Rand und Lage auf dem Tisch in einem. Ein grosses Blatt mit einem kleinen Text
         * darauf liess sich damit gar nicht beschreiben. Ab Stand 4 haelt die Vorlage nur
         * noch den Rahmen; das Blatt steht global unter Optionen.
         *
         * Umgerechnet wird so, dass sich am Ergebnis auf dem Papier nichts aendert: Der alte
         * Rand wandert in die Lage (er war der Abstand vom Kastenrand zum Text), und aus
         * Kasten minus zweimal Rand wird die neue Rahmengroesse.
         *
         * Der alte Versatz zaehlte ab der Tischecke, der neue ab der Blattecke. Solange das
         * Blatt bei 0/0 liegt - die Vorgabe -, ist das dieselbe Stelle. Liegt es woanders,
         * verschiebt sich der Rahmen um diesen Betrag; der Wert steht sichtbar im Feld und
         * laesst sich in einem Zug richtigstellen. Raten waere hier schlechter, denn die
         * Migration sieht die globalen Einstellungen nicht - die liegen im DataStore.
         *
         * SQLite kann keine Spalten entfernen, deshalb der Umweg ueber eine neue Tabelle.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_TEMPLATES_V4.replace("`templates`", "`templates_neu`"))
                db.execSQL(
                    "INSERT INTO `templates_neu` (`id`, `name`, `text`, `werte`, `updatedAt`, " +
                        "`fontId`, `sizeMm`, `align`, `lineSpacing`, `letterSpacing`, " +
                        "`wordSpacing`, `slantDeg`, `rahmenXMm`, `rahmenYMm`, `rahmenBreiteMm`, " +
                        "`rahmenHoeheMm`) SELECT `id`, `name`, `text`, `werte`, `updatedAt`, " +
                        "`fontId`, `sizeMm`, `align`, `lineSpacing`, `letterSpacing`, " +
                        "`wordSpacing`, `slantDeg`, " +
                        "`paperOffsetXMm` + `marginMm`, `paperOffsetYMm` + `marginMm`, " +
                        // Der untere Anschlag verhindert einen Rahmen der Breite 0: `Frame`
                        // wirft dann im Konstruktor, und die Vorlage waere nicht mehr zu
                        // oeffnen. Betrifft nur Vorlagen, deren Rand groesser war als der
                        // halbe Kasten - die konnten ohnehin nichts schreiben.
                        "MAX(`paperWidthMm` - 2 * `marginMm`, 1.0), " +
                        "MAX(`paperHeightMm` - 2 * `marginMm`, 1.0) FROM `templates`",
                )
                db.execSQL("DROP TABLE `templates`")
                db.execSQL("ALTER TABLE `templates_neu` RENAME TO `templates`")
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instanz = it }
        }
    }
}
