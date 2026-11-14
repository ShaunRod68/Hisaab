package com.example.expensetracker

import kotlin.math.roundToLong

data class Parsed(
    val direction: String, // "DEBIT" or "CREDIT"
    val amountPaise: Long,
    val payee: String,
    val upiRef: String
)

object SmsParser {

    // Matches JD-KOTAKB-S, JM-BOBSMS-T, AX-KOTAKB, etc. Prefix and suffix vary, header stays fixed.
    private val SENDER = Regex("^[A-Z]{2}-(KOTAKB|BOBSMS)(-[A-Z])?$", RegexOption.IGNORE_CASE)

    private val AMOUNT = Regex("""(?:Rs\.?|INR|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val DEBIT = Regex("""\b(sent|debited|paid|dr\.?\s+from|withdrawn|spent)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("""\b(received|credited|cr\.?\s+to|deposited)\b""", RegexOption.IGNORE_CASE)
    private val REF = Regex(
        """(?:UPI\s*Ref(?:erence)?(?:\s*(?:No|Number|ID))?\.?|Ref(?:erence)?(?:\s*(?:No|Number))?\.?|RRN|UPI)\s*[:\-]?\s*(\d{9,14})""",
        RegexOption.IGNORE_CASE
    )
    private val REF_FALLBACK = Regex("""\b(\d{12})\b""")

    fun isBankSender(address: String?): Boolean {
        val a = address?.trim() ?: return false
        val m = SENDER.matchEntire(a) ?: return false
        // "-P" suffix means promotional, never a transaction
        return m.groupValues[2].uppercase() != "-P"
    }

    fun looksLikeMoney(body: String) = AMOUNT.containsMatchIn(body)

    fun parse(body: String): Parsed? {
        val amountText = AMOUNT.find(body)?.groupValues?.get(1) ?: return null
        val amount = amountText.replace(",", "").toDoubleOrNull() ?: return null
        val paise = (amount * 100).roundToLong()
        if (paise <= 0) return null

        // Whichever keyword appears first decides direction (BOB messages contain both "Dr. from" and "Cr. to").
        val d = DEBIT.find(body)?.range?.first ?: Int.MAX_VALUE
        val c = CREDIT.find(body)?.range?.first ?: Int.MAX_VALUE
        if (d == Int.MAX_VALUE && c == Int.MAX_VALUE) return null
        val debit = d < c

        val ref = REF.find(body)?.groupValues?.get(1)
            ?: REF_FALLBACK.find(body)?.groupValues?.get(1)
            ?: ""

        return Parsed(if (debit) "DEBIT" else "CREDIT", paise, payee(body, debit), ref)
    }

    private fun payee(body: String, debit: Boolean): String {
        val key = if (debit) "to" else "from"
        // 1) UPI id, e.g. "to blinkit@hdfcbank"
        Regex("""\b$key\s+(?:VPA\s+)?([A-Za-z0-9._\-]+@[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { return it.groupValues[1] }
        // 2) plain name, e.g. "to RAMESH CHAI on 14-11-26"
        Regex(
            """\b$key\s+([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)(?=\s+(?:on|via|using|UPI|Ref|Avl|Not|from)\b|[.,;(]|$)""",
            RegexOption.IGNORE_CASE
        ).find(body)?.let { return it.groupValues[1].trim() }
        return "Unknown"
    }
}