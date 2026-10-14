#!/usr/bin/env python3
"""Phase 6.3 smoke verification of the optimized Gemma3 default."""

import json, statistics, sys, time, urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATASET = ROOT / "src/test/resources/benchmark/questions/questions.json"
OUT_DIR = ROOT / "benchmark-results"
URL = "http://localhost:11434/api/chat"
PROMPT_BUILDER = ROOT / "src/main/java/com/example/ticketing/ai/PromptBuilder.java"

MODEL = "gemma3:4b"
TEMP = 0.2
NUM_PREDICT = 512
WARMUP = 1
MEASURED = 2


def get_system_prompt():
    text = PROMPT_BUILDER.read_text(encoding="utf-8")
    s = text.find("# ROLE\nYou are the local AI assistant")
    if s < 0:
        raise RuntimeError("Canonical prompt not found")
    e = text.find("// ===", s)
    if e < 0:
        e = text.find("    }", s)
    return text[s:e].strip()


def build_user(q):
    out = ["=== RETRIEVED HELPDESK DATA ===\n"]
    sources = q.get("sources", [])
    if not sources:
        out.append("(no relevant authorized tickets were found)\n")
    else:
        for i, s in enumerate(sources, 1):
            out.append("[Ticket " + str(i) + "]\n")
            out.append("Ticket: " + s.get("sourceRef", "") + "\n")
            out.append("Title: " + s.get("title", "") + "\n")
            cat = s.get("category", "")
            if s.get("subcategory"):
                cat = cat + " / " + s["subcategory"]
            out.append("Category: " + cat + "\n")
            out.append("Priority: " + s.get("priority", "") + "\n")
            out.append("Status: " + s.get("status", "") + "\n")
            if s.get("resolvedAt"):
                out.append("Resolved At: " + s["resolvedAt"] + "\n")
            out.append("Description:\n" + s.get("description", "") + "\n\n")
            out.append("Resolution:\n" + s.get("content", "") + "\n")
            out.append("--- end Ticket " + str(i) + " ---\n")
    out.append("=== END RETRIEVED HELPDESK DATA ===\n\n")
    out.append("[USER QUESTION]\n" + q["question"] + "\n")
    return "".join(out)


def call(sys, usr):
    body = json.dumps({
        "model": MODEL,
        "messages": [
            {"role": "system", "content": sys},
            {"role": "user", "content": usr}
        ],
        "stream": False,
        "options": {"temperature": TEMP, "num_predict": NUM_PREDICT}
    }).encode("utf-8")
    req = urllib.request.Request(URL, data=body, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read().decode("utf-8"))


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    print("[smoke] Loading dataset")
    ds = json.loads(DATASET.read_text(encoding="utf-8"))
    qs = ds["questions"]
    print("[smoke] Loaded", len(qs), "questions")

    sys_p = get_system_prompt()
    print("[smoke] System prompt length:", len(sys_p))

    results = []
    for q in qs:
        print()
        print("[smoke] ====== " + q["id"] + " - " + q["category"] + " ======")
        u = build_user(q)

        for _ in range(WARMUP):
            call(sys_p, u)

        for i in range(1, MEASURED + 1):
            t0 = time.perf_counter()
            r = call(sys_p, u)
            ms = int((time.perf_counter() - t0) * 1000)
            content = (r.get("message") or {}).get("content", "")
            empty = not content.strip()
            print("[smoke]   run", i, ":", ms, "ms, eval_tokens=", r.get("eval_count"),
                  ", doneReason=", r.get("done_reason"), ", empty=", empty)
            print("[smoke]   answer:", content[:200])
            results.append({
                "qid": q["id"],
                "category": q["category"],
                "runIndex": i,
                "totalMs": ms,
                "evalCount": r.get("eval_count"),
                "doneReason": r.get("done_reason"),
                "empty": empty,
                "answerPreview": content[:200]
            })

    summary = {
        "schemaVersion": "phase6.3-smoke-v1",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "model": MODEL,
        "temperature": TEMP,
        "numPredict": NUM_PREDICT,
        "questionCount": len(qs),
        "runType": "MEASURED",
        "runCount": len(results),
        "medianTotalMs": round(statistics.median(x["totalMs"] for x in results), 1),
        "emptyCount": sum(1 for x in results if x["empty"]),
        "stopCount": sum(1 for x in results if x["doneReason"] == "stop"),
        "lengthCount": sum(1 for x in results if x["doneReason"] == "length"),
        "perQuestion": results
    }
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H-%M-%S")
    out = OUT_DIR / ("gemma3-smoke-" + ts + ".json")
    out.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    print()
    print("[smoke] Wrote:", out)
    print("[smoke] Median totalMs:", summary["medianTotalMs"])
    print("[smoke] Empty:", summary["emptyCount"], "/", summary["runCount"])
    print("[smoke] doneReason=stop:", summary["stopCount"], "/", summary["runCount"])
    print("[smoke] doneReason=length:", summary["lengthCount"], "/", summary["runCount"])
    return 0

if __name__ == "__main__":
    sys.exit(main())