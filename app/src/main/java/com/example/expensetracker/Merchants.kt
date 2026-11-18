package com.example.expensetracker

/** Payee names are only the nicknames you set yourself in Settings. Nothing is guessed. */
object Merchants {
    fun resolve(raw: String, custom: Map<String, String>): String = custom[raw.lowercase()] ?: raw
}

/** 176 paise-total -> "₹176", 6499 -> "₹64.99" */
fun formatRupees(paise: Long): String =
    if (paise % 100 == 0L) "₹${paise / 100}" else "₹%.2f".format(paise / 100.0)

/** "-₹176" for money out, "+₹176" for money in. */
fun signedRupees(paise: Long, debit: Boolean): String = (if (debit) "-" else "+") + formatRupees(paise)
