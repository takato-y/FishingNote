# -*- coding: utf-8 -*-
"""設計書どうしの ID のつながりを検査する。

使い方:
    python3 scripts/check_docs_trace.py          # リポジトリの docs/ を検査
    python3 scripts/check_docs_trace.py DIR      # 別のフォルダを検査

検査内容:
    1. 詳細設計書（03_詳細設計.md）に出てくる ERR-xxx が、すべて詳細設計書の「エラー一覧」の表で定義されていること
    2. 詳細設計書の ERR-xxx が、障害対応手順書（06_障害対応手順書.md）の「エラー別の対応手順」の章の
       見出し（### `ERR-xxx` 名称）と「エラーID対応表」の章の表の両方にあること
    3. 障害対応手順書に、詳細設計書に無い ERR-xxx が出てこないこと
    4. 障害対応手順書の対応表の節へのリンク（#err-xxx-…）が、実在する見出しを指していること
    5. 基本設計書（02_基本設計.md）の機能一覧の FN-xxx が、すべて詳細設計書の見出し（### FN-xxx 名称）にあること
    6. 詳細設計書に、基本設計書の機能一覧に無い FN-xxx が出てこないこと

章は番号ではなく見出しの名前で探す（章を足しても検査がずれないように）。
終了コード: 0 = 合格、1 = 不一致あり、2 = 対象の文書や章が見つからない
標準ライブラリだけで実行できる。
"""
import re
import sys
from pathlib import Path

ERR_RE = re.compile(r"(?<![A-Za-z0-9_-])ERR-\d{3}(?![A-Za-z0-9_-])")
FN_RE = re.compile(r"(?<![A-Za-z0-9_-])FN-\d{3}(?![A-Za-z0-9_-])")
ERR_HEADING_RE = re.compile(r"^###\s+`?(ERR-\d{3})`?\s")
FN_HEADING_RE = re.compile(r"^###\s+(FN-\d{3})\s")
CHAPTER_RE = re.compile(r"^##\s+(?:\d+\.\s*)?(.+?)\s*$")
LINK_RE = re.compile(r"\]\(#([^)]+)\)")

DETAIL = "03_詳細設計.md"
RUNBOOK = "06_障害対応手順書.md"
BASIC = "02_基本設計.md"


class DocError(Exception):
    """検査の対象が見つからない"""


def read(path):
    if not path.is_file():
        raise DocError(f"文書が見つかりません: {path}")
    return path.read_text(encoding="utf-8-sig")


def chapter(text, title, path):
    """見出し（## N. title）の章の行を返す。次の「## 」の見出しまで。"""
    lines, inside, found = [], False, False
    for line in text.splitlines():
        m = CHAPTER_RE.match(line) if line.startswith("## ") else None
        if m:
            inside = m.group(1) == title
            found = found or inside
            continue
        if inside:
            lines.append(line)
    if not found:
        raise DocError(f"章「{title}」が見つかりません: {path.name}")
    return lines


def table_first_cells(lines, pattern):
    """表の行の1列目が pattern に一致する物の集合"""
    ids = set()
    for line in lines:
        if not line.lstrip().startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if cells and pattern.fullmatch(cells[0]):
            ids.add(cells[0])
    return ids


def slug(heading):
    """GitHub の見出しのアンカーと同じ規則で作る（小文字化、記号の除去、空白をハイフンに）"""
    text = heading.strip().lstrip("#").strip().replace("`", "").lower()
    text = re.sub(r"[^\w\- ]", "", text)
    return text.replace(" ", "-")


def check(docs_dir):
    """(不一致の説明のリスト, 集計) を返す。リストが空なら合格。"""
    docs_dir = Path(docs_dir)
    detail_path, runbook_path, basic_path = docs_dir / DETAIL, docs_dir / RUNBOOK, docs_dir / BASIC
    detail, runbook, basic = read(detail_path), read(runbook_path), read(basic_path)
    problems = []

    # 1. 詳細設計書のエラー一覧
    defined = table_first_cells(chapter(detail, "エラー一覧", detail_path), ERR_RE)
    if not defined:
        raise DocError("詳細設計書のエラー一覧に ERR-xxx が1件もありません")
    detail_errs = set(ERR_RE.findall(detail))
    for err in sorted(detail_errs - defined):
        problems.append(f"{err}: 詳細設計書に出てくるが、エラー一覧の表で定義されていない")

    # 2・3. 障害対応手順書との対応
    sections = chapter(runbook, "エラー別の対応手順", runbook_path)
    headings = {m.group(1) for line in sections if (m := ERR_HEADING_RE.match(line))}
    table_lines = chapter(runbook, "エラーID対応表", runbook_path)
    table_ids = table_first_cells(table_lines, ERR_RE)
    runbook_errs = set(ERR_RE.findall(runbook))
    for err in sorted(defined - headings):
        problems.append(f"{err}: 詳細設計書にあるが、障害対応手順書の「エラー別の対応手順」に見出しが無い")
    for err in sorted(defined - table_ids):
        problems.append(f"{err}: 詳細設計書にあるが、障害対応手順書の「エラーID対応表」に無い")
    for err in sorted(runbook_errs - defined):
        problems.append(f"{err}: 障害対応手順書にあるが、詳細設計書のエラー一覧に無い")

    # 4. 対応表のリンク先
    anchors = {slug(line) for line in runbook.splitlines() if line.startswith("#")}
    for line in table_lines:
        for target in LINK_RE.findall(line):
            if target not in anchors:
                problems.append(f"#{target}: 障害対応手順書の対応表のリンク先の見出しが無い")

    # 5・6. 基本設計書の機能一覧と詳細設計書
    basic_fns = table_first_cells(chapter(basic, "機能一覧", basic_path), FN_RE)
    if not basic_fns:
        raise DocError("基本設計書の機能一覧に FN-xxx が1件もありません")
    detail_fn_headings = {m.group(1) for line in detail.splitlines() if (m := FN_HEADING_RE.match(line))}
    for fn in sorted(basic_fns - detail_fn_headings):
        problems.append(f"{fn}: 基本設計書の機能一覧にあるが、詳細設計書に見出しが無い")
    for fn in sorted(set(FN_RE.findall(detail)) - basic_fns):
        problems.append(f"{fn}: 詳細設計書にあるが、基本設計書の機能一覧に無い")

    summary = {"err": len(defined), "fn": len(basic_fns)}
    return problems, summary


def main(argv):
    # Windows のコンソールでも日本語を出せるようにする
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    docs_dir = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parent.parent / "docs"
    try:
        problems, summary = check(docs_dir)
    except DocError as exc:
        print(f"ERROR {exc}")
        return 2
    for p in problems:
        print(f"FAIL {p}")
    status = "PASS" if not problems else "FAIL"
    print(f"{status} エラーID {summary['err']} 件・機能ID {summary['fn']} 件の対応（不一致 {len(problems)} 件）")
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
