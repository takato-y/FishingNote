"""テスト仕様書（docs/04_テスト仕様書.md）のテストケース一覧を、テストコードから作り直す。

app/src/test（単体テスト）と app/src/androidTest（端末上のテスト）の Kotlin のファイルから、
`@Test` の付いた関数を順に拾い、クラス名・関数名・実行の条件（`@Ignore`・`assumeTrue` など）を一覧にする。
「確認すること」は、下の TESTS に書いた説明を使う。説明が無いテストは、関数のすぐ上の KDoc（/** */）か
行コメント（//）を使う。どちらも無ければ止める（説明の無いテストを一覧に載せないため）。

テストを足したり名前を変えたりしたら、TESTS を直してからこのスクリプトを実行する。
一覧は <!-- tc-list:begin --> と <!-- tc-list:end --> の間だけを書き換える。

使い方（リポジトリの直下で）:
    python3 scripts/update_test_spec.py           # 書き換える
    python3 scripts/update_test_spec.py --check   # 書き換えずに、一覧が最新かだけを確かめる（最新でなければ終了コード 1）
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "04_テスト仕様書.md"
# 並び順：単体テスト → 端末上のテスト。それぞれの中はファイルのパスの順、ファイルの中は書かれた順。
SOURCES = [
    ("単体", ROOT / "app" / "src" / "test"),
    ("端末", ROOT / "app" / "src" / "androidTest"),
]

# "クラス.関数" → (対象の機能, 確認すること)。確認することが None なら、関数の上のコメントを使う。
TESTS = {
    # --- 単体テスト：バックアップの暗号化 ---
    "BackupCryptoTest.round_trip_with_same_password": ("FN-020、FN-021", "同じパスワードで暗号化と復号ができる"),
    "BackupCryptoTest.encrypted_file_does_not_contain_the_data": ("FN-020", "暗号化したファイルに元の中身が現れず、同じ中身・同じパスワードでも毎回違う暗号文になる"),
    "BackupCryptoTest.wrong_password_is_rejected": ("FN-021", "違うパスワードは「パスワード違い」として止まる"),
    "BackupCryptoTest.tampered_file_is_rejected": ("FN-021", "暗号文を1文字書き換えたファイルは復号できない"),
    "BackupCryptoTest.header_is_bound_to_the_data": ("FN-021", "繰り返し回数の行を書き換えると、正しいパスワードでも復号できない"),
    "BackupCryptoTest.broken_or_extreme_files_are_rejected_as_format_errors": ("FN-021", "暗号化の形式でない・知らない版・極端な繰り返し回数・Base64 でない値・初期化ベクトルの長さ違いを、形式の誤りとして止める"),
    "BackupCryptoTest.short_password_is_not_allowed": ("FN-019、FN-020", "短いパスワードでは暗号化しない"),
    "BackupCryptoTest.file_with_bom_or_windows_line_endings_can_be_read": ("FN-021", "先頭に BOM が付いた・行末が CRLF になったファイルも読める"),
    "BackupCryptoTest.plain_json_is_not_mistaken_for_encrypted": ("FN-021", "暗号化していない JSON を、暗号化したファイルと取り違えない"),
    "BackupCryptoTest.matches_vector_made_by_another_implementation": ("FN-020、FN-021", "別の実装（Python）で作った見本と同じ暗号文になり、同じ中身に戻る"),
    "BackupCryptoTest.weak_or_unstable_passwords_are_refused": ("FN-019", "よくある言葉とその変形・くり返し・文字の種類が少ない物・全角を含む物・強さの見積もりで推測されやすい物を断り、推測されにくい物は通す"),
    "BackupCryptoTest.checking_a_password_does_not_change_it": ("FN-019", "パスワードを判定しても、呼び出し元の文字の配列を変えない"),
    "BackupCryptoTest.backup_saved_under_older_rules_can_still_be_opened": ("FN-021", "前の版の規則（10文字以上）で保存したバックアップを、今の規則に合わないパスワードでも開ける"),
    "BackupCryptoTest.generated_passwords_are_valid_and_different": ("FN-019", "作るパスワードは19文字の決まった形で規則を満たし、200回作って重複せず、そのまま暗号化と復号に使える"),
    "BackupCryptoTest.out_of_range_iterations_cannot_be_written": ("FN-020", "読み戻せない繰り返し回数では書き出さない"),
    # --- 単体テスト：判定の関数 ---
    "RulesTest.line_status_is_null_for_category_without_line": ("FN-004", "リール・ライン以外の道具には巻き替えの状態を出さない"),
    "RulesTest.line_status_is_null_without_change_date": ("FN-004", "巻き替え日が無ければ状態を出さない"),
    "RulesTest.line_is_due_only_after_reminder_days": ("FN-004", "決めた日数を超えたときだけ巻き替え時期にする（30日の設定で30日目は時期でなく、31日目は時期）"),
    "RulesTest.line_is_never_due_without_reminder": ("FN-004", "知らせる日数を決めていなければ、何日たっても時期にしない"),
    "RulesTest.future_change_date_counts_as_zero_days": ("FN-004", "未来の巻き替え日は0日として扱う"),
    "RulesTest.low_stock_when_quantity_at_or_below_min": ("FN-004", "数がしきい値以下なら残り少ないとする"),
    "RulesTest.no_low_stock_without_min_or_when_wanted": ("FN-004", "しきい値の無い道具と欲しい物は、残り少ないにしない"),
    "RulesTest.search_matches_all_words_across_fields_ignoring_width_and_case": ("FN-002", "空白で区切った全部の語を名前・メーカー・仕様から探し、全角と半角・大文字と小文字を区別しない。空白だけなら全件"),
    "RulesTest.search_treats_hiragana_and_halfwidth_kana_as_katakana": ("FN-002", "ひらがなと半角カナをカタカナとして探す"),
    "RulesTest.shared_text_from_maps_gives_name_and_map_url": ("FN-018", "地図アプリの共有の文から、名前と地図のリンクを取り出す"),
    "RulesTest.shared_single_line_keeps_name_before_url": ("FN-018", "1行の共有の文では、リンクの前を名前にする"),
    "RulesTest.non_map_urls_are_not_treated_as_map": ("FN-017、FN-018", "地図でない URL を地図のリンクとして扱わない"),
    "RulesTest.map_url_check_rejects_lookalike_hosts_and_tricks": ("FN-017", "似せたホスト・http・ユーザー情報・ポートの指定・「..」やエンコードした区切りを含むパスを断り、決まった形のリンクは通す"),
    "RulesTest.shared_text_uses_only_the_map_url_among_several": ("FN-018", "複数の URL があれば、地図のリンクだけを使う"),
    "RulesTest.split_spec_separates_trailing_parentheses": ("FN-009", "チェックリストの項目の末尾の括弧を、仕様として分けて表示する"),
    "RulesTest.monthly_fish_counts_only_the_year": ("FN-014", "月ごとの釣れた数は、その年の分だけを数える"),
    "RulesTest.quick_species_fills_with_common_fish_without_duplicates": ("FN-011", "魚のボタンは前に記録した魚の多い順に並べ、足りなければよく釣れる魚で重複なく6つまで埋める"),
    "RulesTest.checklist_shows_spec_of_gear": ("FN-008", "チェックリストの道具は「名前（仕様）」の形にする"),
    "RulesTest.filters_select_expected_gear": ("FN-002", "「残り少ない」「欲しい物」「巻き替え」の絞り込みと、絞り込みごとの件数"),
    "RulesTest.checklist_puts_set_gear_first_by_category_then_basic_without_duplicates": ("FN-008", "セットの道具を種類の順に先に、いつもの持ち物を後に並べ、同じ名前・空の名前・欲しい物は入れない"),
    "RulesTest.summary_counts_only_the_year_and_ranks_species": ("FN-014", "今年の分だけを集計し、魚を多い順に並べ、セットごとに数える（前後の空白の違いは同じ魚）"),
    "RulesTest.summary_reports_biggest_fish_of_the_year": ("FN-014", "今年いちばん大きかった魚を出す。無ければ出さない"),
    "RulesTest.summary_keeps_sets_with_same_name_apart": ("FN-014", "同じ名前の別のセットを、まとめずに別々に集計する"),
    "RulesTest.summary_for_deleted_set_falls_back_to_no_set": ("FN-014", "消したセットの釣行は「セットなし」に数える"),
    "RulesTest.catch_line_merges_same_species": ("FN-014", "1回の釣行の要約で同じ魚をまとめ、最大の大きさを添える。釣果が無ければ「釣果なし」"),
    "RulesTest.suggestions_rank_by_frequency_and_drop_blank": ("FN-013", "場所・魚の入力の候補を多い順に並べ、空の値を除く"),
    "RulesTest.map_query_labels_lat_lng_and_falls_back_to_name": ("FN-017", "緯度経度には名前のラベルを付け、値が空なら名前で探す"),
    "RulesTest.nearbyGeoUri_passesRoundedCoordsAndEncodedQuery": ("FN-016", "現在地を小数4桁にし、検索語をエンコードして地図アプリに渡す（負の座標・空白を含む語を含む）"),
    "RulesTest.nearbyGeoUri_ignoresDeviceDecimalComma": ("FN-016", "小数点がカンマになる言語の設定でも、座標を点で書く"),
    "RulesTest.shared_place_name_drops_invisible_characters": ("FN-018","共有の文の名前から、向きを変える制御文字とゼロ幅の文字を取り除く"),
    "RulesTest.brand_suggestions_match_partial_input_past_first": ("FN-005", "メーカー・商品名の候補：途中まで・ひらがなでも当たり、前に登録した値を先に、言葉で始まる物を先に並べ、空・同じ値は出さず、6件までにする"),
    "RulesTest.gear_search_query_joins_brand_and_name_without_repeats": ("FN-006", "「ネットで調べる」の検索語は、メーカー・商品名と名前をつなぎ、一方が他方を含めば重ねない"),
    # --- 単体テスト：編集画面の変更判定・プライバシーポリシー ---
    "EditKeyTest.separator_in_input_does_not_make_different_inputs_equal": ("FN-028", "区切りの文字を入れても、別の入力と同じ値にならない（変更したまま戻るときの判定）"),
    "EditKeyTest.same_input_gives_same_key": ("FN-028", "同じ入力からは同じ値になる"),
    "PrivacyPolicyTest.parses_headings_bullets_and_tables": ("FN-026", "原稿の題名・見出し・本文・箇条書き・表を、アプリで表示する形に分ける"),
    "PrivacyPolicyTest.uneven_table_row_is_not_cut": ("FN-026", None),
    "PrivacyPolicyTest.problems_are_found": ("FN-026", None),
    "PrivacyPolicyTest.real_policy_uses_only_supported_markdown": ("FN-026", None),
    # --- 端末上のテスト：データベースとバックアップ ---
    "RepositoryTest.update_keeps_id_for_set_and_trip": ("FN-007、FN-013", "セットと釣行を保存し直しても ID が変わらず、中身（入れる道具・釣果）が置き換わる"),
    "RepositoryTest.deleting_gear_removes_it_from_sets_and_deleting_set_keeps_trip": ("FN-001、FN-007", "道具を消すとセットからも外れ、セットを消しても釣行は残る（セットは空になる）"),
    "RepositoryTest.prepare_checklist_uses_set_and_basic_items": ("FN-008、FN-009", "セットといつもの持ち物からチェックリストを作り、チェック・項目の追加・全部外すができる"),
    "RepositoryTest.quick_catch_adds_to_todays_trip_and_undo_removes_only_what_it_created": ("FN-011、FN-012", "ワンタップ記録は今日の記録に1匹ずつ足し、取り消しは1匹分ずつ戻す。ワンタップで作った記録だけを消し、場所入りの既存の記録は残す"),
    "RepositoryTest.undo_keeps_empty_trip_that_user_made_beforehand": ("FN-012", "利用者が先に作った空の今日の記録は、取り消しても残す"),
    "RepositoryTest.quick_catch_uses_last_used_set_and_undo_still_removes_its_trip": ("FN-011、FN-012", "ワンタップで作る記録に前回使ったセットを入れ、取り消すとその記録を消す"),
    "RepositoryTest.undo_after_backup_restore_does_not_delete_restored_trip": ("FN-012、FN-022", "バックアップを読み込んだ後は、読み込む前の「元に戻す」で記録を変えない"),
    "RepositoryTest.deleted_checklist_item_can_be_restored_in_place": ("FN-009", "チェックリストから消した項目を、元の位置に戻せる"),
    "RepositoryTest.catch_count_stays_within_backup_range": ("FN-011", "魚の数を 0〜99999 に収め、両端の値でも書き出した物を読み戻せる"),
    "RepositoryTest.quantity_never_goes_below_zero": ("FN-003", "道具の数を 0〜9999 に収め、上限の値でも書き出した物を読み戻せる"),
    "RepositoryTest.backup_round_trip_restores_everything": ("FN-020、FN-022", "書き出した JSON から全データを元どおりに戻せる"),
    "RepositoryTest.encrypted_backup_round_trip_on_device": ("FN-020、FN-021、FN-022", "本番と同じ繰り返し回数で暗号化したバックアップを端末で戻せ、違うパスワードでは開けない（所要時間をログに出す）"),
    "RepositoryTest.encryption_matches_vector_made_by_another_implementation_on_device": ("FN-020、FN-021", "端末の暗号の部品でも、別の実装（Python）で作った見本と同じ結果になる"),
    "RepositoryTest.broken_backup_is_rejected_before_touching_db": ("FN-021、FN-022", "壊れた・表が欠けた・範囲外の値（負の数・丸めると範囲内に見える数・小数・文字の数・極端な日付・長すぎる文字）・深い入れ子のバックアップを止め、データを変えない"),
    "PasswordCheckTimingTest.check_finishes_quickly_on_device": ("FN-019", "パスワードの判定（強さの見積もりを含む）が、端末で1つ1秒未満で終わる"),
    # --- 端末上のテスト：現在地と地図アプリ ---
    "LocationTest.returnsCurrentLocationNearTheFix": ("FN-015", "テスト用の位置の提供元から届いた新しい位置を、現在地として返す（おおよその位置のため 0.1 度の幅で確かめる）"),
    "LocationTest.returnsNullWhenNoLocationArrivesInTime": ("FN-015", "時間内に位置が届かなければ、待ち続けずに「取れない」を返す"),
    "SignatureTest.genuine_google_maps_is_trusted": ("FN-016", "端末に入っている本物の Google マップを、署名の証明書で信用する"),
    "SignatureTest.other_signature_or_missing_app_is_not_trusted": ("FN-016", "署名の違うアプリと、入っていないアプリは信用しない"),
    # --- 見本データの投入（テストではなく、手動で実行する道具） ---
    "SampleDataSeeder.seed": ("—", "画面の確認用に、架空の見本データを入れる（通常のテストではスキップ）"),
}

TEST_ANNOTATION = re.compile(r"@Test\b")
FUN_AFTER_TEST = re.compile(r"(?:\s*@\w+(?:\([^)]*\))?)*\s*fun\s+`?([\w ]+?)`?\s*\(")
CLASS_DECL = re.compile(r"^(?:@[^\n]*\n|\s)*?(?:(?:internal|public|private|open|abstract)\s+)*class\s+(\w+)", re.M)
ASSUME = re.compile(r"\bassume(?:True|False|That|NotNull)\s*\(")


def strip_code(text: str) -> str:
    """文字列とコメントを同じ長さの空白に置き換える（括弧の対応を数えるため）。改行は残す。"""
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
        elif text[i] == '"':
            j = i + 1
            while j < n and text[j] != '"' and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            j = min(j + 1, n)
        elif text[i] == "'" and i + 2 < n and (text[i + 2] == "'" or text[i + 1] == "\\"):
            j = text.find("'", i + 2) + 1 or n
        elif text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
        else:
            out.append(text[i])
            i += 1
            continue
        out.append("".join(c if c == "\n" else " " for c in text[i:j]))
        i = j
    return "".join(out)


def block_end(code: str, start: int) -> int:
    """start 以降の最初の { に対応する } の位置を返す。"""
    open_at = code.find("{", start)
    if open_at < 0:
        return len(code)
    depth = 0
    for k in range(open_at, len(code)):
        if code[k] == "{":
            depth += 1
        elif code[k] == "}":
            depth -= 1
            if depth == 0:
                return k
    return len(code)


def comment_above(text: str, pos: int) -> str | None:
    """pos（@Test の位置）のすぐ上にある KDoc か、続いた行コメントを返す。空行を挟んだ物は使わない。"""
    lines = text[:pos].split("\n")[:-1]
    picked: list[str] = []
    while lines and re.match(r"\s*@\w+", lines[-1]):  # @Test より上の別の注釈は飛ばす
        lines.pop()
    if lines and lines[-1].strip().endswith("*/"):
        block = []
        while lines:
            line = lines.pop()
            block.insert(0, line)
            if line.strip().startswith("/*"):
                break
        body = " ".join(re.sub(r"^\s*(/\*\*?|\*/|\*)\s?", "", l).replace("*/", "").strip() for l in block)
        return body.strip() or None
    while lines and lines[-1].strip().startswith("//"):
        picked.insert(0, lines.pop().strip()[2:].strip())
    picked = [p for p in picked if not re.fullmatch(r"-+.*-+", p)]  # 「--- 区切り ---」は説明にしない
    return " ".join(picked) or None


def condition(code: str, text: str, start: int, end: int) -> str:
    found = []
    for m in ASSUME.finditer(code, start, end):
        close = m.end()
        depth = 1
        while close < len(code) and depth:
            depth += {"(": 1, ")": -1}.get(code[close], 0)
            close += 1
        found.append(re.sub(r"\s+", " ", text[m.start():close]))
    return "、".join(f"`{f}`" for f in found)


def annotations_before(code: str, pos: int) -> str:
    """pos を含む行と、そのすぐ上に続く注釈（@ で始まる行）を返す。"""
    lines = code[:pos].split("\n")
    picked = [lines.pop()]
    while lines and lines[-1].strip().startswith("@"):
        picked.insert(0, lines.pop())
    return "\n".join(picked)


def collect() -> list[tuple[str, str, str, str, str | None]]:
    """(種類, クラス, 関数, 実行の条件, 上のコメント) を書かれた順に返す。"""
    found = []
    for kind, base in SOURCES:
        for path in sorted(base.rglob("*.kt"), key=lambda p: p.relative_to(base).as_posix()):
            text = path.read_text(encoding="utf-8")
            code = strip_code(text)
            classes = [(m.start(1), m.group(1), block_end(code, m.end())) for m in CLASS_DECL.finditer(code)]
            for m in TEST_ANNOTATION.finditer(code):
                f = FUN_AFTER_TEST.match(code, m.end())
                if not f:
                    continue
                name = f.group(1).strip()
                owner = [c for c in classes if c[0] < m.start() <= c[2]]
                if not owner:
                    raise SystemExit(f"クラスの外のテストは扱えません: {path}:{name}")
                cls_pos, cls, cls_end = owner[-1]
                notes = []
                # クラスか関数に付いた @Ignore
                if "@Ignore" in annotations_before(code, cls_pos) or "@Ignore" in annotations_before(code, m.start()) + code[m.start():f.end()]:
                    notes.append("無効（`@Ignore`）")
                # @Before の中の assume はクラスの全テストに効く
                for b in re.finditer(r"@Before\b", code[cls_pos:cls_end]):
                    bstart = cls_pos + b.end()
                    c = condition(code, text, bstart, block_end(code, bstart))
                    if c:
                        notes.append(c)
                end = block_end(code, f.end())
                c = condition(code, text, f.end(), end)
                if c:
                    notes.append(c)
                found.append((kind, cls, name, "、".join(notes), comment_above(text, m.start())))
    return found


def build_table() -> tuple[str, int, int]:
    found = collect()
    keys = [f"{cls}.{name}" for _, cls, name, _, _ in found]
    missing = [k for k in keys if k not in TESTS]
    stale = sorted(set(TESTS) - set(keys))
    if missing or stale:
        msg = []
        if missing:
            msg.append("TESTS に無いテスト: " + ", ".join(missing))
        if stale:
            msg.append("テストコードに無い TESTS の項目: " + ", ".join(stale))
        raise SystemExit("\n".join(msg) + "\n（scripts/update_test_spec.py の TESTS を直してください）")
    if len(set(keys)) != len(keys):
        raise SystemExit("同じクラス名・関数名のテストが2つあります。")

    sections = {"単体": [], "端末": []}
    for no, (kind, cls, name, cond, comment) in enumerate(found, start=1):
        function, desc = TESTS[f"{cls}.{name}"]
        desc = desc or comment
        if not desc:
            raise SystemExit(f"{cls}.{name} の説明がありません（TESTS に書くか、関数の上にコメントを書いてください）")
        function = function or "—"
        sections[kind].append(f"| TC-{no:03d} | {function} | {desc.replace('|', '／')} | `{cls}.{name}` | {cond or '—'} |")

    header = "| ID | 対象 | 確認すること | テスト（クラス.関数） | 実行の条件 |\n|---|---|---|---|---|\n"
    unit, device = sections["単体"], sections["端末"]
    text = (
        f"#### 単体テスト（`app/src/test`・パソコンの JVM で実行）\n\n{header}" + "\n".join(unit)
        + f"\n\n#### 端末上のテスト（`app/src/androidTest`・エミュレータか実機で実行）\n\n{header}" + "\n".join(device)
        + f"\n\n単体テスト {len(unit)} 件、端末上のテスト {len(device)} 件（見本データの投入 `SampleDataSeeder.seed` を含む）。"
    )
    return text, len(unit), len(device)


def main() -> int:
    table, unit, device = build_table()
    spec = SPEC.read_text(encoding="utf-8")
    # 目印の間が空でも、既に一覧があっても一致するようにする。
    pattern = re.compile(r"(<!-- tc-list:begin -->\n)(?:.*?\n)?(<!-- tc-list:end -->)", re.S)
    if not pattern.search(spec):
        raise SystemExit("テスト仕様書に tc-list の目印がありません。")
    updated = pattern.sub(lambda m: m.group(1) + table + "\n" + m.group(2), spec)

    if "--check" in sys.argv[1:]:
        if updated != spec:
            print("テスト仕様書の一覧が最新ではありません。python3 scripts/update_test_spec.py を実行してください。")
            return 1
        print(f"テスト仕様書の一覧は最新です（単体 {unit} 件、端末 {device} 件）。")
        return 0

    SPEC.write_text(updated, encoding="utf-8", newline="\n")
    print(f"テスト仕様書の一覧を更新しました（単体 {unit} 件、端末 {device} 件）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
