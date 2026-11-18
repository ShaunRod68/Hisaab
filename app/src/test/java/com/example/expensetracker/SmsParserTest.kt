package com.example.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun testBankSenderHdfcAndIcici() {
        assertTrue(SmsParser.isBankSender("BP-HDFCBN-S"))
        assertTrue(SmsParser.isBankSender("AX-ICICIT-S"))
        assertTrue(SmsParser.isBankSender("AD-HDFCBK"))
        assertTrue(SmsParser.isBankSender("VM-ICICIB"))
        assertTrue(SmsParser.isBankSender("HDFCBK"))
        assertTrue(SmsParser.isBankSender("ICICIB"))
        assertTrue(SmsParser.isBankSender("JD-KOTAKB-S"))
        assertTrue(SmsParser.isBankSender("JM-BOBSMS-T"))
        assertTrue(SmsParser.isBankSender("AX-BOBTXN"))

        assertFalse(SmsParser.isBankSender("BP-HDFCBN-P"))
        assertFalse(SmsParser.isBankSender("AX-ICICIT-P"))
        assertFalse(SmsParser.isBankSender("DZ-SWIGGY"))
    }

    @Test
    fun testHdfcMessage1_FromScreenshot() {
        val sms = """
            Sent Rs.130.00
            From HDFC Bank A/C *0061
            To INDAL LALCHANDRA CHAUHAN
            On 18/11/26
            Ref 668807793536
            Not You?
            Call 18002586161/SMS BLOCK UPI to 7308080808
        """.trimIndent()

        val parsed = SmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("DEBIT", parsed!!.direction)
        assertEquals(13000L, parsed.amountPaise)
        assertEquals("INDAL LALCHANDRA CHAUHAN", parsed.payee)
        assertEquals("668807793536", parsed.upiRef)
    }

    @Test
    fun testHdfcMessage2_FromScreenshot() {
        val sms = """
            Sent Rs.23.00
            From HDFC Bank A/C *0061
            To SMART ZONE MEDICAL
            On 08/10/26
            Ref 130905903750
            Not You?
            Call 18002586161/SMS BLOCK
            UPI to 7308080808
        """.trimIndent()

        val parsed = SmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("DEBIT", parsed!!.direction)
        assertEquals(2300L, parsed.amountPaise)
        assertEquals("SMART ZONE MEDICAL", parsed.payee)
        assertEquals("130905903750", parsed.upiRef)
    }

    @Test
    fun testHdfcMessage3_MultilineName_FromScreenshot() {
        val sms = """
            Sent Rs.700.00
            From HDFC Bank A/C *0061
            To SHEKH MOSIN SHEKH
            MOHAMMA
            On 18/11/26
            Ref 133045114234
            Not You?
            Call 18002586161/SMS BLOCK
            UPI to 7308080808
        """.trimIndent()

        val parsed = SmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("DEBIT", parsed!!.direction)
        assertEquals(70000L, parsed.amountPaise)
        assertEquals("SHEKH MOSIN SHEKH MOHAMMA", parsed.payee)
        assertEquals("133045114234", parsed.upiRef)
    }

    @Test
    fun testIciciMessage1_FromScreenshot() {
        val sms = """
            ICICI Bank Acct XX695 debited
            for Rs 270.00 on 03-Nov-26;
            HOT SPICY credited.
            UPI:667357762774. Call 18002662
            for dispute. SMS BLOCK 695 to
            9215676766.
        """.trimIndent()

        val parsed = SmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("DEBIT", parsed!!.direction)
        assertEquals(27000L, parsed.amountPaise)
        assertEquals("HOT SPICY", parsed.payee)
        assertEquals("667357762774", parsed.upiRef)
    }

    @Test
    fun testIciciMessage2_FromScreenshot() {
        val sms = """
            ICICI Bank Acct XX695 debited
            for Rs 300.00 on 03-Nov-26;
            DURGA ENTERPRIS credited.
            UPI:630779237289. Call
            18002662 for dispute. SMS
            BLOCK 695 to 9215676766.
        """.trimIndent()

        val parsed = SmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("DEBIT", parsed!!.direction)
        assertEquals(30000L, parsed.amountPaise)
        assertEquals("DURGA ENTERPRIS", parsed.payee)
        assertEquals("630779237289", parsed.upiRef)
    }

    @Test
    fun testIciciMessage3_NilkanthSuper() {
        val sms = """
            ICICI Bank Acct XX695 debited
            for Rs 20.00 on 17-Nov-26;
            NILKANTH SUPER credited.
            UPI:667284146368. Call 18002662
            for dispute. SMS BLOCK 695 to
            9215676766.
        """.trimIndent()

        val parsed = SmsParser.parse(sms)
        assertNotNull(parsed)
        assertEquals("DEBIT", parsed!!.direction)
        assertEquals(2000L, parsed.amountPaise)
        assertEquals("NILKANTH SUPER", parsed.payee)
        assertEquals("667284146368", parsed.upiRef)
    }

    @Test
    fun testIciciVpaDebitAndCredit() {
        val debitVpa = "Dear Customer, your A/C XXXXXX1234 is debited for Rs 250.00 on 18-Nov-26 by info*payee@icici. Ref no 123456789012."
        val pDebit = SmsParser.parse(debitVpa)
        assertNotNull(pDebit)
        assertEquals("DEBIT", pDebit!!.direction)
        assertEquals(25000L, pDebit.amountPaise)
        assertEquals("payee@icici", pDebit.payee)
        assertEquals("123456789012", pDebit.upiRef)

        val creditVpa = "Dear Customer, your A/C XXXXXX1234 is credited with Rs 500.00 on 18-Nov-26 by someone@okhdfc. UPI:432321456789."
        val pCredit = SmsParser.parse(creditVpa)
        assertNotNull(pCredit)
        assertEquals("CREDIT", pCredit!!.direction)
        assertEquals(50000L, pCredit.amountPaise)
        assertEquals("someone@okhdfc", pCredit.payee)
        assertEquals("432321456789", pCredit.upiRef)
    }

    @Test
    fun testHdfcCreditAndTowardsUpi() {
        val towards = "Dear Customer, INR 500.00 has been debited from your A/C **1234 on 18-11-26 towards UPI/432321456789/SWIGGY."
        val pTowards = SmsParser.parse(towards)
        assertNotNull(pTowards)
        assertEquals("DEBIT", pTowards!!.direction)
        assertEquals(50000L, pTowards.amountPaise)
        assertEquals("SWIGGY", pTowards.payee)
        assertEquals("432321456789", pTowards.upiRef)

        val hdfcCredit = "INR 1,500.00 credited to HDFC Bank A/C **1234 on 18-11-26 by account linked to VPA someone@okhdfcbank (UPI Ref: 432321456789)."
        val pHdfcCredit = SmsParser.parse(hdfcCredit)
        assertNotNull(pHdfcCredit)
        assertEquals("CREDIT", pHdfcCredit!!.direction)
        assertEquals(150000L, pHdfcCredit.amountPaise)
        assertEquals("someone@okhdfcbank", pHdfcCredit.payee)
        assertEquals("432321456789", pHdfcCredit.upiRef)
    }

    @Test
    fun testExistingKotakAndBobFormats() {
        val kotakCard = "Rs 450.00 spent via Kotak Debit Card XX7430 at WWW AMAZON IN on 02/11/2026"
        val pCard = SmsParser.parse(kotakCard)
        assertNotNull(pCard)
        assertEquals("DEBIT", pCard!!.direction)
        assertEquals(45000L, pCard.amountPaise)
        assertEquals("WWW AMAZON IN", pCard.payee)

        val bobOnline = "Credited to A/c ...1581 from:ONLINE_663326988 for Rs. 1000.00"
        val pBob = SmsParser.parse(bobOnline)
        assertNotNull(pBob)
        assertEquals("CREDIT", pBob!!.direction)
        assertEquals(100000L, pBob.amountPaise)
        assertEquals("Online transfer", pBob.payee)
        assertEquals("663326988", pBob.upiRef)
    }
}
