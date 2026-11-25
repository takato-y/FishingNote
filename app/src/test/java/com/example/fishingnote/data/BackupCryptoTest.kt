package com.example.fishingnote.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    // テストを速くするため、繰り返し回数は受け付ける範囲の最小にする（本番は BackupCrypto.ITERATIONS）
    private val fast = 100_000
    private val plain = """{"format":"fishing-note-backup","version":1,"spots":[{"name":"見本海岸"}]}""".toByteArray()

    private fun expectFailure(block: () -> Unit, wrongPassword: Boolean = false) {
        try {
            block(); fail("失敗しなければいけない")
        } catch (e: WrongPasswordException) {
            if (!wrongPassword) fail("形式の誤りとして止まるはず: $e")
        } catch (e: BackupFormatException) {
            if (wrongPassword) fail("パスワード違いとして止まるはず: $e")
        }
    }

    @Test fun round_trip_with_same_password() {
        val text = BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), fast)
        assertTrue(BackupCrypto.isEncrypted(text))
        assertArrayEquals(plain, BackupCrypto.decrypt(text, "Mebaru!Night-07".toCharArray()))
    }

    @Test fun encrypted_file_does_not_contain_the_data() {
        val text = BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), fast)
        assertFalse(text.contains("見本海岸"))
        assertFalse(text.contains("spots"))
        // 同じ中身・同じパスワードでも、毎回違う暗号文になる（塩と初期化ベクトルが毎回変わる）
        assertNotEquals(text, BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), fast))
    }

    @Test fun wrong_password_is_rejected() {
        val text = BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), fast)
        expectFailure({ BackupCrypto.decrypt(text, "Mebaru!Night-08".toCharArray()) }, wrongPassword = true)
    }

    @Test fun tampered_file_is_rejected() {
        val lines = BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), fast).lines().toMutableList()
        // 暗号文の1文字を変える
        val c = lines[5].toCharArray(); c[10] = if (c[10] == 'A') 'B' else 'A'; lines[5] = String(c)
        expectFailure({ BackupCrypto.decrypt(lines.joinToString("\n"), "Mebaru!Night-07".toCharArray()) }, wrongPassword = true)
    }

    @Test fun header_is_bound_to_the_data() {
        // 繰り返し回数の行を書き換えると、正しいパスワードでも復号できない
        val lines = BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), fast).lines().toMutableList()
        lines[2] = "100001"
        expectFailure({ BackupCrypto.decrypt(lines.joinToString("\n"), "Mebaru!Night-07".toCharArray()) }, wrongPassword = true)
    }

    @Test fun broken_or_extreme_files_are_rejected_as_format_errors() {
        val good = BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), fast).lines()
        fun with(i: Int, v: String) = good.toMutableList().also { it[i] = v }.joinToString("\n")
        for (bad in listOf(
            "not encrypted",
            with(1, "2"),                 // 知らない版
            with(2, "999999999"),         // 極端な繰り返し回数（読み込みが終わらなくなる）
            with(2, "10"),                // 小さすぎる繰り返し回数
            with(3, "***"),               // Base64 でない
            with(4, "AAAA"),              // 初期化ベクトルの長さが違う
        )) expectFailure({ BackupCrypto.decrypt(bad, "Mebaru!Night-07".toCharArray()) })
    }

    @Test fun short_password_is_not_allowed() {
        try {
            BackupCrypto.encrypt(plain, "short".toCharArray(), fast); fail("短いパスワードは使えない")
        } catch (e: IllegalArgumentException) {
            // 想定どおり
        }
    }

    @Test fun file_with_bom_or_windows_line_endings_can_be_read() {
        // 外のエディタで保存し直すと、先頭に BOM が付いたり、行末が CRLF になったりする
        val text = BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), fast)
        val edited = "\uFEFF" + text.replace("\n", "\r\n")
        assertTrue(BackupCrypto.isEncrypted(edited))
        assertArrayEquals(plain, BackupCrypto.decrypt(edited, "Mebaru!Night-07".toCharArray()))
    }

    @Test fun plain_json_is_not_mistaken_for_encrypted() {
        assertFalse(BackupCrypto.isEncrypted(String(plain)))
    }

    @Test fun matches_vector_made_by_another_implementation() {
        // Python（hashlib.pbkdf2_hmac と cryptography の AESGCM）で作った見本と、同じ暗号文になり、同じ中身に戻る。
        // 端末上のテストでも同じ見本を確かめ、パソコンと端末・別の実装の間で同じ鍵になることを保証する
        assertEquals(KnownAnswer.TEXT, BackupCrypto.encryptWith(KnownAnswer.PLAIN, KnownAnswer.PASSWORD.toCharArray(), 100_000, KnownAnswer.SALT, KnownAnswer.IV))
        assertArrayEquals(KnownAnswer.PLAIN, BackupCrypto.decrypt(KnownAnswer.TEXT, KnownAnswer.PASSWORD.toCharArray()))
    }

    @Test fun weak_or_unstable_passwords_are_refused() {
        for (bad in listOf(
            "short", "Kurodai!26x", "password123", "1234567890", "aaaaaaaaaa", "abcdefghij", "ｔｓｕｒｉｎｏｔｅ１２", "tsuri note 26",
            "釣りノート2026pass", "jihgfedcba", "abcabcabca", "Ab1Ab1Ab1Ab1",
            "abcdefghijkl", "123456789012", "aaaaaaaaaaaa", "abcabcabcabc",
            // よくある言葉に数字や記号を足しただけ
            "Password-2026!", "Tsuri_Note_2026", "fishing!2026!!", "QWERTY_123456",
            // 文字を数字や記号に置き換えた・言葉をくり返しただけ
            "Summer2026!!", "1qaz2wsx3edc",
            "P@ssw0rd-2026!", "Passw0rd2026!", "Pa55word-2026", "Tsur1Note2026!", "F1shing-2026!!", "Qwerty-Qwerty-1", "PasswordPassword1",
            // 文字の種類が少ない
            "aabbaabbaabb", "1212!!1212!!",
        )) assertTrue(bad, BackupCrypto.passwordProblem(bad) != null)
        // 規則では断れないが、推測されやすい物（強さの推定で断る）
        for (bad in listOf("Tanaka19850315", "Tsuri2026Fishing", "Kurodai!Iso-26", "Goo9le2026!!x"))
            assertTrue(bad, BackupCrypto.passwordProblem(bad) != null)
        for (ok in listOf("Ki7#vQ2!mW9zR", "Hz4-Tq8@Lp3x-Nb", "7#3%5&7*9(0)Qw"))
            assertEquals(ok, null, BackupCrypto.passwordProblem(ok))
    }

    @Test fun checking_a_password_does_not_change_it() {
        // 判定の途中で呼び出し元の配列を消すと、このあと作る鍵が別の物になり、二度と戻せないバックアップになる
        val p = "Ki7#vQ2!mW9zR".toCharArray()
        BackupCrypto.passwordProblem(java.nio.CharBuffer.wrap(p))
        assertArrayEquals("Ki7#vQ2!mW9zR".toCharArray(), p)
    }

    @Test fun backup_saved_under_older_rules_can_still_be_opened() {
        // 前の版（10文字以上）で保存したバックアップは、今の規則では作れないパスワードでも開ける（読み込みでは規則を確かめない）
        val old = "Kurodai!26x".toCharArray()
        assertTrue(BackupCrypto.passwordProblem(String(old)) != null)
        val salt = ByteArray(16) { (it * 7).toByte() }
        val iv = ByteArray(12) { (it * 3).toByte() }
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(javax.crypto.spec.PBEKeySpec(old, salt, fast, 256)).encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        cipher.updateAAD("${BackupCrypto.FORMAT}\n1\n$fast".toByteArray())
        val b64 = java.util.Base64.getEncoder()
        val text = listOf(BackupCrypto.FORMAT, "1", "$fast", b64.encodeToString(salt), b64.encodeToString(iv), b64.encodeToString(cipher.doFinal(plain)))
            .joinToString("\n", postfix = "\n")
        assertArrayEquals(plain, BackupCrypto.decrypt(text, old))
    }

    @Test fun generated_passwords_are_valid_and_different() {
        val seen = HashSet<String>()
        repeat(200) {
            val p = BackupCrypto.generatePassword()
            val s = String(p)
            assertEquals(19, p.size)
            assertTrue(s, Regex("[A-HJ-NP-Za-km-z2-9]{4}(-[A-HJ-NP-Za-km-z2-9]{4}){3}").matches(s))
            assertEquals(s, null, BackupCrypto.passwordProblem(s))
            assertTrue("同じ物が2回作られた", seen.add(s))
            // 作った物でそのまま暗号化・復号できる
            if (it < 3) assertArrayEquals(plain, BackupCrypto.decrypt(BackupCrypto.encrypt(plain, p, fast), p))
        }
    }

    @Test fun out_of_range_iterations_cannot_be_written() {
        try {
            BackupCrypto.encrypt(plain, "Mebaru!Night-07".toCharArray(), 10); fail("読み戻せない回数では書かない")
        } catch (e: IllegalArgumentException) {
            // 想定どおり
        }
    }
}

/** 別の実装（Python）で作った見本。端末上のテストからも使う */
internal object KnownAnswer {
    const val PASSWORD = "Kat-Test-Pass-2026"
    val SALT = ByteArray(16) { it.toByte() }
    val IV = ByteArray(12) { it.toByte() }
    val PLAIN = "{\"kat\":\"釣りノート\"}".toByteArray(Charsets.UTF_8)
    const val TEXT = "fishing-note-backup-encrypted\n1\n100000\nAAECAwQFBgcICQoLDA0ODw==\nAAECAwQFBgcICQoL\nVEic0vZ6XcOKE/OAPk0WdU3pwSTjn9jV3/3VfEDq6u8ekLszyVknMgQ=\n"
}
