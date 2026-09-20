-- Current protocol-v10 MP3/ICY radio acceptance.
-- Usage: v10_radio_acceptance [direct-mp3-radio-url]
--
-- Automated checks cover the exposed surface and stream lifecycle. Listen during
-- the grouped phase to confirm spatial sources start together; exact audibility is
-- intentionally a runtime/manual assertion.

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

print("Direct MP3/ICY radio: " .. url)
speaker.speakStop()
assert(speaker.speakStream(url, 0.35), "speakStream rejected the URL")
sleep(4)
assert(speaker.isStreaming(), "direct radio did not remain active")
print("Metadata:", textutils.serialize(speaker.getStreamMeta(), {compact = true}))
speaker.speakStop()
sleep(0.1)
assert(not speaker.isStreaming(), "direct radio did not stop")

local count = speaker.getSpeakerCount()
print("Attached speaker snapshot: " .. count)
if count >= 2 then
  print("Starting strict grouped radio. All audible members should begin together after buffering.")
  assert(speaker.speakStreamAll(url, 0.35), "speakStreamAll rejected the URL")
  sleep(5)
  assert(speaker.isStreaming(), "grouped radio did not remain active on the calling endpoint")
  print("Listen now: the participating speakers should be synchronized.")
  print("A speaker which missed this start must stay out until this command is rerun.")
  sleep(8)
  speaker.audioStopAll()
  sleep(0.1)
  assert(not speaker.isStreaming(), "audioStopAll did not stop grouped radio")
else
  print("Grouped radio skipped: attach at least two speakers to this computer.")
end

print("protocol-v10 radio acceptance passed")
