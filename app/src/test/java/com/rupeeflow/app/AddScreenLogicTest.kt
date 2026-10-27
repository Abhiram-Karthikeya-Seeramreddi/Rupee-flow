package com.rupeeflow.app

import com.rupeeflow.app.data.AccountEntity
import com.rupeeflow.app.data.CategoryEntity
import com.rupeeflow.app.data.Settings
import com.rupeeflow.app.data.SnapshotBuilder
import com.rupeeflow.app.data.TransactionEntity
import com.rupeeflow.app.ui.components.MoneyFmt
import com.rupeeflow.app.ui.screens.cleanAmount
import com.rupeeflow.app.ui.screens.confirmationMessage
import com.rupeeflow.core.rupees
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddScreenLogicTest {

    @Test fun amountFieldAcceptsOnlyValidMoney() {
        assertEquals("250", cleanAmount("250"))
        assertEquals("1250.5", cleanAmount("1,250.5"))
        assertEquals("0.5", cleanAmount(".5"))
        assertEquals("", cleanAmount(""))
        assertNull(cleanAmount("12.345"), "max two decimals")
        assertNull(cleanAmount("1.2.3"))
        assertNull(cleanAmount("abc"))
        assertNull(cleanAmount("-5"))
    }

    @Test fun confirmationSaysWhatHappened() {
        val today = LocalDate.of(2026, 10, 27)
        val bank = AccountEntity("bank", "HDFC", "BANK", 0, true, "🏦")
        val sav = AccountEntity("sav", "Savings", "SAVINGS", 0, false, "🐷")
        val cats = listOf(CategoryEntity("food", "Groceries", "🛒", 0, "EXPENSE"))
        val snap = SnapshotBuilder.build(today, Settings(onboarded = true), listOf(bank, sav), cats, emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val fmt = MoneyFmt()
        fun tx(type: String, cat: String? = null, to: String? = null, day: LocalDate = today) =
            TransactionEntity("x", type, rupees(250), "bank", to, cat, day.toEpochDay(), 600)

        assertEquals("Expense added · ₹250 · Groceries", confirmationMessage(tx("EXPENSE", "food"), false, snap, fmt))
        assertEquals("Expense updated · ₹250 · Groceries", confirmationMessage(tx("EXPENSE", "food"), true, snap, fmt))
        assertEquals("Income added · ₹250", confirmationMessage(tx("INCOME"), false, snap, fmt))
        assertEquals("Saved ₹250 → Savings", confirmationMessage(tx("SAVINGS", to = "sav"), false, snap, fmt))
        assertEquals("Transfer done · ₹250 → Savings", confirmationMessage(tx("TRANSFER", to = "sav"), false, snap, fmt))
        assertEquals("Expense added · ₹250 · Groceries (scheduled)", confirmationMessage(tx("EXPENSE", "food", day = today.plusDays(2)), false, snap, fmt))
    }
}
