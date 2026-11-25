package com.example.fishingnote.data

import com.nulabinc.zxcvbn.Zxcvbn
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** パスワードが違う、または暗号化したファイルが書き換えられている */
class WrongPasswordException : BackupFormatException("パスワードが違うか、ファイルが壊れています")

/**
 * バックアップをパスワードで暗号化する。
 *
 * 鍵は端末の中（Android の鍵保管庫）には置かず、利用者が決めたパスワードから作る。
 * バックアップは機種変更や紛失のときに別の端末へ戻すための物で、端末の中の鍵では端末と一緒に失われるため。
 *
 * - 鍵の作り方：PBKDF2（HMAC-SHA256）。塩は毎回ランダムな16バイト。繰り返し回数はファイルに書き、読むときは決めた範囲だけ受け付ける
 * - 暗号：AES-256-GCM。改ざんされた物やパスワードが違う物は、復号の時点で検出される
 * - 見出し（形式・版・繰り返し回数）も認証の対象に含め、書き換えると復号できないようにする
 *
 * パスワードは半角の英数字と記号だけ（passwordProblem）。PBKDF2 が文字をバイトにする方法の違いに左右されず、どの端末でも同じ鍵になる。
 *
 * ファイルは次の6行のテキスト（JSON ではないので、暗号化していないバックアップと取り違えない）。
 *   1行目 形式名 / 2行目 版 / 3行目 繰り返し回数 / 4行目 塩 / 5行目 初期化ベクトル / 6行目 暗号文（いずれも Base64）
 */
object BackupCrypto {
    const val FORMAT = "fishing-note-backup-encrypted"
    private const val VERSION = 1

    /** 繰り返し回数。総当たりを遅くするため大きくする（端末での所要時間はテストで確かめる） */
    const val ITERATIONS = 600_000
    private val ITERATION_RANGE = 100_000..2_000_000

    /** パスワードの最短の長さ（人が考えた物は見た目の長さほど強くないため、少し長めにする） */
    const val PASSWORD_MIN = 12

    /** よく使われる・推測されやすいパスワード（小文字にして比べる） */
    private val COMMON_PASSWORDS = setOf(
        "password", "password1", "password12", "password123", "passw0rd", "qwertyuiop", "qwerty1234", "1qaz2wsx3edc",
        "abcdefghij", "abcd123456", "iloveyou12", "letmein123", "welcome123", "admin12345", "1234567890", "0987654321",
        "tsurinote", "fishing123", "fishingnote",
    )

    /**
     * 数字や記号を足しても推測されやすい言葉（isCommonWordVariant で比べる）。
     * 「Password-2026!」「P@ssw0rd-26」「Tsuri_Note_26」「QwertyQwerty1」のように、よくある言葉に数字や記号を足した・
     * 文字を置き換えた・くり返しただけの物を断る
     */
    private val COMMON_WORDS = setOf(
        "password", "passwd", "pass", "qwerty", "qwertyuiop", "asdfgh", "asdfghjkl", "zxcvbn", "iloveyou", "welcome",
        "admin", "administrator", "letmein", "login", "test", "guest", "user", "secret", "dragon", "monkey", "sunshine",
        "baseball", "football", "abc", "abcd", "abcdef", "tsuri", "tsurinote", "tsuridougu", "fishing", "fishingnote",
        "fish", "sakana", "backup", "android", "google", "pixel",
        "spring", "summer", "autumn", "winter", "hello", "love", "sakura", "tokyo", "japan", "nihon",
    )

    /**
     * パスワードとして使えない理由を返す（使えるなら null）。
     * 文字は半角の英数字と記号だけにする。全角・半角カナ・濁点の分かれた形などは、端末の入力の設定で見た目が同じでも
     * 別の文字になり、機種変更の後に「パスワードが違う」となって戻せなくなるため。
     */
    fun passwordProblem(password: CharSequence): String? = ruleProblem(password) ?: strengthProblem(password)

    /**
     * 規則だけで見た、使えない理由（強さの見積もりはしない）。
     * アプリが作ったパスワード（約93ビットのランダムな物）と、暗号化の直前の確認に使う。
     * 見積もりは、利用者が自分で決めたパスワードを受け付けるかの判定（画面）で使う
     */
    fun ruleProblem(password: CharSequence): String? = when {
        password.length < PASSWORD_MIN -> "パスワードは${PASSWORD_MIN}文字以上にしてください"
        password.any { it.code !in 0x21..0x7E } -> "パスワードには半角の英数字と記号だけを使ってください（全角や空白は使えません）"
        // 文字列の写しを作らずに比べる（パスワードの写しは使い終わっても消せないため）
        COMMON_PASSWORDS.any { it.contentEquals(password, ignoreCase = true) } ||
            (password.any { it.isLetter() } && COMMON_WORDS.any { isCommonWordVariant(password, it) }) -> "よく使われるパスワードです。推測されにくいものにしてください"
        distinctCount(password) < 5 -> "使っている文字の種類が少なすぎます。いろいろな文字を混ぜてください"
        password.all { it == password[0] } -> "同じ文字だけのパスワードは使えません"
        password.all { it.isDigit() } -> "数字だけのパスワードは推測されやすいため使えません。英字や記号も混ぜてください"
        password.zipWithNext().all { (a, b) -> b.code == a.code + 1 } ||
            password.zipWithNext().all { (a, b) -> b.code == a.code - 1 } -> "並んだ文字だけのパスワードは推測されやすいため使えません"
        isShortRepeat(password) -> "同じ並びをくり返しただけのパスワードは推測されやすいため使えません"
        else -> null
    }

    private fun strengthProblem(password: CharSequence): String? = when (isGuessable(password)) {
        true -> "推測されやすいパスワードです。「パスワードを作ってもらう」を使うか、もっと長く、人が思いつきにくいものにしてください"
        false -> null
        null -> "パスワードの強さを確かめられませんでした。「パスワードを作ってもらう」を使ってください"
    }

    /**
     * 強さの推定で求める点数（0〜4）。4 は「推測に 10^10 回以上かかる」見込み。
     * バックアップは手に入れた人が端末の外で時間をかけて試せるため、一番上を求める
     */
    private const val REQUIRED_SCORE = 4

    /** 強さの推定（辞書の読み込みに時間がかかるため、初めて使うときに作る） */
    private val estimator by lazy { Zxcvbn() }

    /** このアプリで推測に使われやすい言葉（推定の辞書に足す） */
    private val APP_WORDS by lazy { COMMON_WORDS.toList() + listOf("tsuridougunote", "tsurinoto", "mebaru", "aji", "kurodai", "seabass", "suzuki") }

    /** 強さの推定の準備を先に済ませる（パスワードの入力の最初の1文字で待たせないため。画面の外のスレッドから呼ぶ） */
    fun warmUp() {
        isGuessable("warm-up")
    }

    /**
     * 辞書の言葉・名前・日付・キーボードの並び・言葉のつなぎ合わせなどから推測に要る回数を見積もり、足りなければ true。
     * 規則で断れない物（一覧に無い言葉、名前と生年月日の組み合わせなど）を断るため。
     * 見積もれなかったとき（辞書を読めない等）は null を返し、呼び出し側でパスワードを断る（確かめずに通さない）。
     * 推定の部品を複数のスレッドから同時に使わないよう、1つずつ順に行う
     */
    private fun isGuessable(p: CharSequence): Boolean? {
        // 推定には使い捨ての写しを渡す。wipe() は渡した入れ物そのものを0で埋めるため、
        // 呼び出し元の配列（このあと鍵を作るのに使う物）を渡すと、パスワードが消えて別の鍵になってしまう
        val copy = CharArray(p.length) { p[it] }
        var strength: com.nulabinc.zxcvbn.Strength? = null
        try {
            synchronized(this) {
                strength = estimator.measure(java.nio.CharBuffer.wrap(copy), APP_WORDS)
            }
            return strength!!.score < REQUIRED_SCORE
        } catch (e: Exception) {
            return null
        } catch (e: LinkageError) {
            return null
        } finally {
            strength?.wipe() // 推定の中に残る写しを消す
            copy.fill('\u0000')
        }
    }

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    /**
     * パスワードが、よくある言葉（word）に数字や記号を足しただけ、または言葉をくり返しただけの物か。
     * 英字はすべて言葉の文字に当たらなければならない。数字・記号は読み飛ばしてよいが、
     * 文字の代わりによく使われる物（0→o、@→a、$→s など）は、その文字として数えてもよい（P@ssw0rd も断る）。
     * 写しを作らないよう、文字を1つずつ読んで「言葉の何文字目まで当たったか」の集合だけを持つ（大文字と小文字は区別しない）
     */
    private fun isCommonWordVariant(p: CharSequence, word: String): Boolean {
        val w = word.length
        // at[j]: ここまでで言葉の j 文字目まで当たっている（j == w は言葉を1回以上言い切った所）
        var at = BooleanArray(w + 1).also { it[0] = true }
        for (c in p) {
            val next = BooleanArray(w + 1)
            for (j in 0..w) {
                if (!at[j]) continue
                val k = if (j == w) 0 else j // 言い切った後は、次のくり返しの頭として扱う
                if (c.isLetter()) {
                    if (c.lowercaseChar() == word[k]) next[k + 1] = true
                } else {
                    next[j] = true // 足しただけの数字・記号として読み飛ばす
                    if (standsFor(c, word[k])) next[k + 1] = true
                }
            }
            at = next
        }
        return at[w]
    }

    /** 文字の代わりによく使われる数字・記号か */
    private fun standsFor(c: Char, letter: Char): Boolean = when (c) {
        '0' -> letter == 'o'
        '@', '4' -> letter == 'a'
        '$', '5' -> letter == 's'
        '3' -> letter == 'e'
        '7' -> letter == 't'
        '1', '!', '|' -> letter == 'i' || letter == 'l'
        else -> false
    }

    /** 使っている文字の種類の数（ここに来る時点で半角だけなので、写しを作らず表で数える） */
    private fun distinctCount(p: CharSequence): Int {
        val seen = BooleanArray(128)
        var n = 0
        for (c in p) if (!seen[c.code]) { seen[c.code] = true; n++ }
        return n
    }

    /** 作るパスワードに使う文字（0とO、1とlとI のように見間違えやすい物は除く） */
    private const val GENERATE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    /**
     * 推測されにくいパスワードを作る（4文字ずつ4組をハイフンでつないだ19文字）。
     * 紙に控えて書き写しやすいよう、見間違えやすい文字は使わない。強さは約93ビット（57種類の文字を16個）
     */
    fun generatePassword(): CharArray {
        // 規則だけで確かめる（ランダムな物なので見積もりは要らず、見積もりが使えない端末でも作れるように）。
        // 規則に当たるのはごくまれなので、作り直しは回数を決めて止める（止まらなくなるのを防ぐ）
        repeat(GENERATE_TRIES) {
            val out = CharArray(19) { i -> if (i % 5 == 4) '-' else GENERATE_CHARS[random.nextInt(GENERATE_CHARS.length)] }
            if (ruleProblem(java.nio.CharBuffer.wrap(out)) == null) return out
            out.fill('\u0000')
        }
        throw IllegalStateException("パスワードを作れませんでした")
    }

    private const val GENERATE_TRIES = 100

    /** 「abcabcabca」のように、4文字以下の並びをくり返しただけか */
    private fun isShortRepeat(p: CharSequence): Boolean =
        (1..4).any { n -> p.length > n && p.indices.all { p[it] == p[it % n] } }

    /** 暗号化したバックアップかどうか（1行目で見分ける） */
    fun isEncrypted(text: String): Boolean {
        val t = stripLeading(text)
        return t.startsWith("$FORMAT\n") || t.startsWith("$FORMAT\r\n")
    }

    /** 先頭の BOM と空白を落とす。判定（isEncrypted）と読み込み（decrypt）で同じ前処理を使う */
    private fun stripLeading(text: String) = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): String =
        encryptWith(plain, password, iterations, ByteArray(SALT_BYTES).also(random::nextBytes), ByteArray(IV_BYTES).also(random::nextBytes))

    /** 塩と初期化ベクトルを外から渡す版。別の実装で作った見本と同じ結果になるかのテストにだけ使う */
    internal fun encryptWith(plain: ByteArray, password: CharArray, iterations: Int, salt: ByteArray, iv: ByteArray): String {
        // 文字列の写しを作らずに確かめる（文字列は使い終わっても消せないため）
        // 規則で確かめる（強さの見積もりは、自分で決めたパスワードを画面で受け付けるときに済ませている）
        ruleProblem(java.nio.CharBuffer.wrap(password))?.let { throw IllegalArgumentException(it) }
        // 範囲外の回数で書くと、読み込みの検査で止まり、読み戻せないファイルになるため
        require(iterations in ITERATION_RANGE) { "繰り返し回数が範囲外です" }
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header(iterations))
        val sealed = cipher.doFinal(plain)
        val b64 = Base64.getEncoder()
        return listOf(FORMAT, VERSION.toString(), iterations.toString(), b64.encodeToString(salt), b64.encodeToString(iv), b64.encodeToString(sealed))
            .joinToString("\n", postfix = "\n")
    }

    fun decrypt(text: String, password: CharArray): ByteArray {
        // 外のエディタで保存し直して先頭に BOM が付いた物も読めるようにする（isEncrypted と同じ前処理）
        val lines = stripLeading(text).lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size != 6 || lines[0] != FORMAT) throw BackupFormatException("暗号化したバックアップとして読めません")
        if (lines[1] != VERSION.toString()) throw BackupFormatException("対応していない版の暗号化バックアップです")
        // 繰り返し回数は範囲内の物だけ受け付ける（極端に大きい値で、読み込みが終わらなくなるのを防ぐ）
        val iterations = lines[2].toIntOrNull()?.takeIf { it in ITERATION_RANGE }
            ?: throw BackupFormatException("暗号化したバックアップの設定が範囲外です")
        val b64 = Base64.getDecoder()
        val (salt, iv, sealed) = try {
            Triple(b64.decode(lines[3]), b64.decode(lines[4]), b64.decode(lines[5]))
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("暗号化したバックアップとして読めません")
        }
        if (salt.size != SALT_BYTES || iv.size != IV_BYTES || sealed.size < TAG_BITS / 8) {
            throw BackupFormatException("暗号化したバックアップとして読めません")
        }
        // 中身が上限を超える物は、時間のかかる鍵の計算の前に止める
        if (sealed.size - TAG_BITS / 8 > Backup.MAX_BYTES) throw BackupFormatException("ファイルが大きすぎます")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header(iterations))
        return try {
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun header(iterations: Int) = "$FORMAT\n$VERSION\n$iterations".toByteArray(Charsets.UTF_8)

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            // SecretKeySpec は配列を写して持つので、元の配列は消してよい
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
