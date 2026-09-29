#!/usr/bin/env python3
"""Adversarial QA gate (L5) vs the LIVE ktayl-claims dev service. Stdlib only (urllib)."""
import json, urllib.request, urllib.error, time

BASE = "http://ktayl-claims.claims.svc/api/claims"
POL = "POL-PROP-0001"          # perils: FIRE, STORM, THEFT, WATER_DAMAGE
POL2 = "POL-PROP-0002"         # perils: FIRE, WATER_DAMAGE
results = []  # (name, ok, detail, severity_if_fail)

def call(method, path, body=None, headers=None, raw=None, ctype="application/json"):
    url = BASE + path
    data = None
    if raw is not None:
        data = raw.encode()
    elif body is not None:
        data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", ctype)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        r = urllib.request.urlopen(req, timeout=15)
        return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return -1, str(e)

def check(name, cond, detail, sev="BLOCKER"):
    results.append((name, bool(cond), detail, sev))
    print(("PASS " if cond else f"FAIL[{sev}] ") + name + " :: " + detail)

def fnol(peril, policy=POL, key=None, name="QA Co", body_override=None, raw=None, ctype="application/json"):
    hdr = {}
    if key is not None:
        hdr["Idempotency-Key"] = key
    b = body_override if body_override is not None else {
        "policyNumber": policy, "lossDate": "2026-09-20", "peril": peril,
        "claimantName": name, "description": "qa"}
    return call("POST", "", body=None if raw is not None else b, headers=hdr, raw=raw, ctype=ctype)

def reserve(clm, amount, auth="SENIOR", user="qa"):
    return call("POST", f"/{clm}/reserve", body={"amount": amount},
                headers={"X-Claims-Authority": auth, "X-Claims-User": user})

def settle(clm, amount, auth="SENIOR", user="qa"):
    return call("POST", f"/{clm}/settle", body={"amount": amount},
                headers={"X-Claims-Authority": auth, "X-Claims-User": user})

def num(): return str(int(time.time()*1000))

print("=== 1. HAPPY PATHS ===")
s, b = fnol("FIRE", key="qa-happy-"+num())
happy_clm = None
try: happy_clm = json.loads(b).get("claimNumber")
except: pass
check("FNOL covered peril → 201 NOTIFIED", s==201 and happy_clm and json.loads(b)["status"]=="NOTIFIED", f"status={s} clm={happy_clm}")

print("=== 2. INPUT VALIDATION (→ 4xx, never 5xx) ===")
s,b = fnol("FIRE", key=None)
check("missing Idempotency-Key → 4xx", 400<=s<500, f"status={s}")
s,b = fnol("FIRE", key="qa-"+num(), body_override={"lossDate":"2026-09-20","peril":"FIRE","claimantName":"x"})
check("missing policyNumber → 4xx", 400<=s<500, f"status={s}")
s,b = fnol("FIRE", key="qa-"+num(), body_override={"policyNumber":POL,"lossDate":"not-a-date","peril":"FIRE","claimantName":"x"})
check("bad lossDate → 4xx not 5xx", 400<=s<500, f"status={s}")
s,b = fnol("FIRE", key="qa-"+num(), raw="{not valid json")
check("malformed JSON → 4xx not 5xx", 400<=s<500, f"status={s}")
s,b = fnol("FIRE", key="qa-"+num(), raw="policyNumber=POL", ctype="text/plain")
check("wrong content-type → 4xx not 5xx", 400<=s<500, f"status={s}")
s,b = fnol("FIRE", key="qa-"+num(), name="Ünîcodé Sàrl 名前")
check("unicode claimant handled (201/4xx not 5xx)", s<500, f"status={s}")
s,b = fnol("FIRE", key="qa-"+num(), name="X"*5000)
check("oversized claimant → not 5xx", s<500, f"status={s}")

print("=== 3. NOT-FOUND ===")
s,b = call("GET","/CLM-2026-999999")
check("GET unknown claim → 404", s==404, f"status={s}")
s,b = reserve("CLM-2026-999999", 1000)
check("reserve unknown claim → 404", s==404, f"status={s}")

print("=== 7/2. COVERAGE / APPETITE ===")
s,b = fnol("THEFT", policy=POL2, key="qa-"+num())  # POL2 doesn't cover THEFT
check("uncovered peril → 422", s==422, f"status={s}")
s,b = fnol("FIRE", policy="POL-DOES-NOT-EXIST", key="qa-"+num())
check("unknown policy → 4xx (not 5xx/201)", 400<=s<500, f"status={s}")

print("=== 5. IDEMPOTENCY ===")
k = "qa-idem-"+num()
s1,b1 = fnol("FIRE", key=k); s2,b2 = fnol("FIRE", key=k)
c1 = json.loads(b1).get("claimNumber") if s1==201 else None
c2 = json.loads(b2).get("claimNumber") if s2 in (200,201) else None
check("same Idempotency-Key → same claim (no dup)", c1 and c1==c2, f"c1={c1} c2={c2} s2={s2}")

print("=== 4. STATE & ORDERING GUARDS ===")
s,b = fnol("FIRE", key="qa-state-"+num()); clm = json.loads(b).get("claimNumber")
s,b = settle(clm, 100)  # settle before reserve
check("settle before reserve → 409", s==409, f"status={s}")
# proper lifecycle
s,b = reserve(clm, 3000, auth="SENIOR")
check("reserve NOTIFIED→RESERVED → 200", s==200 and json.loads(b)["status"]=="RESERVED", f"status={s}")
s,b = settle(clm, 2000, auth="SENIOR")
check("settle RESERVED→SETTLED → 200", s==200 and json.loads(b)["status"]=="SETTLED", f"status={s}")
s,b = reserve(clm, 1000, auth="SENIOR")  # on terminal SETTLED
check("reserve on SETTLED (terminal) → 409", s==409, f"status={s}")

print("=== 8. AUTHZ / AUTHORITY MATRIX ===")
s,b = fnol("FIRE", key="qa-auth-"+num()); aclm = json.loads(b).get("claimNumber")
s,b = reserve(aclm, 60000, auth="ADJUSTER")   # > 50000 adjuster max
check("ADJUSTER reserve >50000 → 403", s==403, f"status={s}")
s,b = call("POST", f"/{aclm}/reserve", body={"amount":1000})  # no authority header
check("missing X-Claims-Authority → 4xx", 400<=s<500, f"status={s}")
s,b = call("POST", f"/{aclm}/reserve", body={"amount":1000}, headers={"X-Claims-Authority":"BOGUS"})
check("invalid authority value → 4xx not 5xx", 400<=s<500, f"status={s}")
# boundary: exactly at adjuster limit ok, just over rejected
s,b = reserve(aclm, 50000, auth="ADJUSTER")
check("ADJUSTER reserve ==50000 (boundary) → 200", s==200, f"status={s}")
s,b = fnol("FIRE", key="qa-auth2-"+num()); a2 = json.loads(b).get("claimNumber")
s,b = reserve(a2, 50001, auth="ADJUSTER")
check("ADJUSTER reserve 50001 (over) → 403", s==403, f"status={s}")

print("=== 9. ROBUSTNESS / MONEY ===")
s,b = fnol("FIRE", key="qa-money-"+num()); mclm = json.loads(b).get("claimNumber")
s,b = reserve(mclm, -100, auth="SENIOR")
check("negative reserve amount → 4xx not 5xx", 400<=s<500, f"status={s}")
s,b = reserve(mclm, "not-a-number", auth="SENIOR")
check("non-numeric amount → 4xx not 5xx", 400<=s<500, f"status={s}")
s,b = call("POST", f"/{mclm}/reserve", raw="{bad", headers={"X-Claims-Authority":"SENIOR"})
check("malformed reserve JSON → 4xx not 5xx", 400<=s<500, f"status={s}")

print("=== 11. EVENTUAL CONSISTENCY (read-your-writes on command; GET eventual) ===")
s,b = fnol("FIRE", key="qa-ec-"+num()); ec = json.loads(b).get("claimNumber")
s,b = reserve(ec, 5000, auth="SENIOR")
cmd_status = json.loads(b).get("status") if s==200 else None
check("command response is authoritative (RESERVED immediately)", cmd_status=="RESERVED", f"cmd status={cmd_status}")
# GET may lag; poll up to 10s
got=None
for _ in range(5):
    s,b = call("GET", f"/{ec}")
    if s==200: got=json.loads(b).get("status")
    if got=="RESERVED": break
    time.sleep(2)
check("read-model reflects RESERVED within 10s", got=="RESERVED", f"read-model status={got}")

# SUMMARY
print("\n==================== QA VERDICT ====================")
blockers=[r for r in results if not r[1] and r[3]=="BLOCKER"]
majors=[r for r in results if not r[1] and r[3]=="MAJOR"]
passed=[r for r in results if r[1]]
print(f"PASS {len(passed)}/{len(results)} | BLOCKERS {len(blockers)} | MAJORS {len(majors)}")
for r in blockers+majors:
    print(f"  {r[3]}: {r[0]} :: {r[2]}")
print("PROMOTION:", "BLOCKED" if blockers else ("CONCERNS" if majors else "CLEAN"))
