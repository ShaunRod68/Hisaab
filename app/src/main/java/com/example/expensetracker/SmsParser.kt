package com.example.expensetracker

import kotlin.math.roundToLong

data class Parsed(
    val direction: String, // "DEBIT" or "CREDIT"
    val amountPaise: Long,
    val payee: String,
    val upiRef: String
)

object SmsParser {

    // Matches JD-KOTAKB-S, JM-BOBSMS-T, AX-BOBTXN, BP-HDFCBN-S, AX-ICICIT-S, etc. Prefix and suffix vary, header stays fixed.
    private val SENDER = Regex(
        """^(?:[A-Z]{2}-)?(KOTAKB?|BOBSMS|BOBTXN|BOB|HDFCBK|HDFCBN|HDFCBANK|HDFCCC|HDFCAL|HDFC|ICICIB|ICICIT|ICICIS|ICICIC|ICICI)(?:-([A-Z0-9]+))?$""",
        RegexOption.IGNORE_CASE
    )

    private val AMOUNT = Regex("""(?:Rs\.?|INR|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val DEBIT = Regex("""\b(sent|debited|paid|dr\.?\s+from|withdrawn|spent|used\s+at|used\s+on)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("""\b(received|credited|cr\.?\s+to|deposited)\b""", RegexOption.IGNORE_CASE)
    private val OTP = Regex("""\bOTP\b""", RegexOption.IGNORE_CASE)
    private val REF = Regex(
        """(?:UPI\s*Ref(?:erence)?(?:\s*(?:No|Number|ID))?\.?|Ref(?:erence)?(?:\s*(?:No|Number))?\.?|RRN|UPI)\s*[:\-/]?\s*(\d{9,14})""",
        RegexOption.IGNORE_CASE
    )
    private val REF_FALLBACK = Regex("""\b(\d{12})\b""")

    fun isBankSender(address: String?): Boolean {
        val a = address?.trim() ?: return false
        val m = SENDER.matchEntire(a) ?: return false
        // "-P" suffix means promotional, never a transaction
        return m.groupValues[2].uppercase() != "P"
    }

    /** True for messages worth keeping in the "could not read" list. OTP messages are never useful. */
    fun looksLikeMoney(body: String) = AMOUNT.containsMatchIn(body) && !OTP.containsMatchIn(body)

    /** Turn line breaks and extra whitespace into a single space so multi-line SMS parse cleanly. */
    private fun normalize(s: String) = s
        .replace(Regex("-\\r?\\n"), "") // join hyphenated line breaks
        .replace(Regex("\\s+"), " ")

    fun parse(raw: String): Parsed? {
        val body = normalize(raw)
        if (OTP.containsMatchIn(body)) return null

        val amountText = AMOUNT.find(body)?.groupValues?.get(1) ?: return null
        val amount = amountText.replace(",", "").toDoubleOrNull() ?: return null
        val paise = (amount * 100).roundToLong()
        if (paise <= 0) return null

        // Whichever keyword appears first decides direction (BOB & ICICI messages contain both "debited" and "credited").
        val d = DEBIT.find(body)?.range?.first ?: Int.MAX_VALUE
        val c = CREDIT.find(body)?.range?.first ?: Int.MAX_VALUE
        if (d == Int.MAX_VALUE && c == Int.MAX_VALUE) return null
        val debit = d < c

        // BOB bank-transfer credits: "Credited to A/c ...1581 from:ONLINE_663326988"
        val online = Regex("""\bfrom\s*:\s*ONLINE_(\d+)""", RegexOption.IGNORE_CASE).find(body)
        if (online != null) {
            return Parsed(if (debit) "DEBIT" else "CREDIT", paise, "Online transfer", online.groupValues[1])
        }

        val ref = REF.find(body)?.groupValues?.get(1)
            ?: REF_FALLBACK.find(body)?.groupValues?.get(1)
            ?: ""

        return Parsed(if (debit) "DEBIT" else "CREDIT", paise, payee(body, debit), ref)
    }

    private fun payee(body: String, debit: Boolean): String {
        // 1) ICICI debit UPI format: "...debited for Rs 270.00 on 03-Nov-26; HOT SPICY credited. UPI:..."
        if (debit) {
            Regex("""(?:;\s*|^)([A-Za-z0-9 .&'\-]{2,40}?)\s+credited\b""", RegexOption.IGNORE_CASE)
                .find(body)?.let {
                    val candidate = cleanPayee(it.groupValues[1])
                    if (candidate.isNotEmpty() && !candidate.equals("your account", ignoreCase = true)) {
                        return candidate
                    }
                }
        }

        // 2) UPI id, e.g. "To blinkit.rzp@hdfcbank", "by info*someone@icici", "by account linked to VPA someone@okhdfc"
        Regex(
            """\b(?:to|towards|from|by)\s+(?:account\s+linked\s+to\s+)?(?:VPA\s+|info\*)?([A-Za-z0-9._\-]+@[A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE
        ).find(body)?.let { return it.groupValues[1] }

        // 3) UPI slash format: e.g. "towards UPI/432321456789/SWIGGY"
        Regex("""\bUPI/(?:\d+/)?([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)(?:/\d+)?(?=\s|[.,;(]|$)""", RegexOption.IGNORE_CASE)
            .find(body)?.let {
                val candidate = cleanPayee(it.groupValues[1])
                if (candidate.isNotEmpty()) return candidate
            }

        // 4) Plain name with To / Towards / From / By, e.g. "To INDAL LALCHANDRA CHAUHAN On 18/11/26"
        val keyPattern = if (debit) """(?:to|towards|by\s+info\*)""" else """(?:from|by\s+(?:account\s+linked\s+to\s+)?)"""
        val lookahead = """(?=\s+(?:on|via|using|UPI|Ref|Avl|Avbl|Bal|Not|Call|SMS|from|credited|debited)\b|[.,;(]|$)"""
        Regex(
            """\b$keyPattern\s+(?:VPA\s+|info\*)?([A-Za-z][A-Za-z0-9 .&'\-]{2,40}?)$lookahead""",
            RegexOption.IGNORE_CASE
        ).find(body)?.let {
            val candidate = cleanPayee(it.groupValues[1])
            if (candidate.isNotEmpty()) return candidate
        }

        // 5) Card spend, e.g. "spent via Kotak Debit Card XX7430 at WWW AMAZON IN on 02/11/2026"
        //    or "... on 18-Nov-26 at RELIANCE. Avl Lmt:..."
        if (debit) {
            Regex("""\bat\s+([A-Za-z0-9 .&'\-]{2,40}?)$lookahead""", RegexOption.IGNORE_CASE)
                .find(body)?.let {
                    val candidate = cleanPayee(it.groupValues[1])
                    if (candidate.isNotEmpty()) return candidate
                }
        }

        return "Unknown"
    }

    private fun cleanPayee(raw: String): String {
        return raw.trim()
            .removePrefix("info*")
            .removePrefix("INFO*")
            .removePrefix("Info*")
            .trim()
    }
}