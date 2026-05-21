package com.anantva.tether.ocr

object ReceiptMerchantExtractor {

  private val BOILERPLATE = Regex(
      """(google pay|phonepe|paytm|cred|upi|transaction|ref\.?|hdfc|sbi|axis|bank|completed|successful|debited|payment)""",
      RegexOption.IGNORE_CASE
  )

  fun afterKeyword(lines: List<OcrLine>, keyword: Regex): String? {
      for (i in lines.indices) {
          val line = lines[i].text
          if (!keyword.containsMatchIn(line)) continue

          val match = keyword.find(line) ?: continue
          val sameLine = line.substring(match.range.last + 1).trim().removePrefix(":").trim()
          if (isValidMerchant(sameLine)) return cleanMerchant(sameLine)

          if (i + 1 < lines.size) {
              val next = lines[i + 1].text.trim()
              if (isValidMerchant(next)) return cleanMerchant(next)
          }
      }
      return null
  }

  fun lineAboveCredFooter(lines: List<OcrLine>): String? {
      val credIdx = lines.indexOfFirst {
          val lower = it.text.lowercase()
          lower.contains("paid via cred") || lower.contains("paid securely by cred")
      }
      if (credIdx <= 0) {
          return afterKeyword(lines, Regex("""\bpaid\b""", RegexOption.IGNORE_CASE))
      }
      for (i in credIdx - 1 downTo 0) {
          val candidate = lines[i].text.trim()
          if (isValidMerchant(candidate) && !candidate.contains("₹")) {
              return cleanMerchant(candidate)
          }
      }
      return null
  }

  private fun isValidMerchant(text: String): Boolean {
      if (text.length < 2) return false
      if (text.contains("₹") || text.contains("@")) return false
      if (BOILERPLATE.containsMatchIn(text)) return false
      if (text.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) return false
      if (Regex("""^\d{4}$""").matches(text)) return false
      return true
  }

  private fun cleanMerchant(text: String): String {
      var extracted = text
      val phoneIdx = extracted.indexOfFirst { it == '+' || (it.isDigit() && extracted.count { c -> c.isDigit() } >= 8) }
      if (phoneIdx > 0) extracted = extracted.substring(0, phoneIdx)
      val upiIdx = extracted.indexOf('@')
      if (upiIdx > 0) extracted = extracted.substring(0, upiIdx)
      return extracted.trim().trim('-', ':', '.', ',')
  }
}
