-- Frozen protocol-v10 MP3/ICY radio acceptance.
-- Usage: v10_radio_acceptance [direct-mp3-radio-url]
-- isStreaming() is server-owned request state, not proof of client connectivity.

local args = {...}
local url = args[1] or "https://ice5.somafm.com/groovesalad-128-mp3"

local speaker = peripheral.find("speaker")
assert(speaker, "attach a ComputerCraft speaker")
local name = assert(peripheral.getName(speaker), "could not resolve speaker name")

local methods = {}
for _, method in ipairs(peripheral.getMethods(name) or {}) do methods[method] = true end
for _, method in ipairs({"speakStream", "speakStreamAll", "speakStreamAt"}) do
  assert(methods[method], "missing radio method: " .. method)
end
for _, method in ipairs({
  "speakHLS", "speakHLSAll", "speakHLSAt",
  "speakTS", "speakTSAll", "speakTSAt",
}) do
  assert(not methods[method], "retired live method unexpectedly exposed: " .. method)
end

local formats = speaker.getStreamFormats()
assert(type(formats) == "table" and formats.mp3, "MP3/ICY radio format missing")
assert(formats.hls == nil and formats.ts == nil, "HLS/TS must not be advertised")

local function waitAt(index, wanted, timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local s = speaker.audioStatusAt(index)
    if s.state == wanted then return s end
    sleep(0.05)
  end
  error(("timed out waiting for speaker %d state %s"):format(index, wanted))
end

print("Direct MP3/ICY radio: " .. url)
speaker.audioStop()
assert(speaker.speakStream(url, 0.35), "speakStream rejected the URL")
sleep(4)
assert(speaker.isStreaming(), "server stream ownership did not remain active")
print("Metadata:", textutils.serialize(speaker.getStreamMeta(), {compact = true}))
speaker.audioStop()
sleep(0.1)
assert(not speaker.isStreaming(), "direct radio did not stop")

local count = speaker.getSpeakerCount()
print("Attached speaker snapshot: " .. count)

if count >= 1 then
  assert(speaker.speakStreamAt(1, url, 0.30), "speakStreamAt(1) rejected")
  sleep(4)
  assert(waitAt(1, "playing", 2).kind == "stream", "At stream status kind mismatch")
  speaker.audioStopAt(1)
  waitAt(1, "idle", 2)
end

if count >= 2 then
  print("Starting strict grouped radio; listen for synchronized release after prebuffer.")
  assert(speaker.speakStreamAll(url, 0.35), "speakStreamAll rejected the URL")
  sleep(5)
  waitAt(1, "playing", 2)
  waitAt(2, "playing", 2)

  speaker.audioStopAt(2)
  waitAt(2, "idle", 2)
  local survivor = speaker.audioStatusAt(1)
  assert(survivor.state == "playing", "audioStopAt stopped the surviving radio endpoint")
  sleep(2)

  print("Rerunning group: this creates a fresh strict membership snapshot.")
  assert(speaker.speakStreamAll(url, 0.35), "group rerun rejected")
  sleep(5)
  waitAt(1, "playing", 2)
  waitAt(2, "playing", 2)

  print("Manual strict-membership check: add/enter range of another speaker now.")
  print("It must stay silent until speakStreamAll is rerun.")
  print("Also listen for long-run drift while this group continues.")
  sleep(8)

  speaker.audioStopAll()
  waitAt(1, "idle", 2)
  waitAt(2, "idle", 2)
else
  print("Grouped radio skipped: attach at least two speakers.")
end

print("[PASS] frozen v10 radio server/API acceptance")
print("Manual audibility, strict late-membership and long-run sync still require listening.")
