package com.rupeeflow.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rupeeflow.app.data.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration safety net. When the schema moves to version 2, add:
 *   helper.createDatabase(DB, 1).close()
 *   helper.runMigrationsAndValidate(DB, 2, true, *AppDatabase.MIGRATIONS)
 * The exported JSON schemas live in app/schemas.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun currentSchemaOpensAndValidates() {
        helper.createDatabase("migration-test", AppDatabase.VERSION).use { db ->
            db.query("SELECT COUNT(*) FROM transactions").use { c -> c.moveToFirst(); assert(c.getInt(0) == 0) }
        }
    }
}

/** First-run smoke test: the onboarding opens and navigates. */
@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test fun onboardingStartsAndMovesToSalaryDay() {
        rule.waitUntil(10_000) { runCatching { rule.onNodeWithText("Get started").assertExists() }.isSuccess }
        rule.onNodeWithText("Get started").performClick()
        rule.onNodeWithText("When does your salary arrive?").assertExists()
    }
}
