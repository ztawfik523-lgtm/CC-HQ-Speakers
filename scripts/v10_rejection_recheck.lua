-- Standalone rerun for the three automated checks that were interrupted by the old missing shorten() helper.
-- Usage: v10_rejection_recheck <mp3> <wav>

local args = {...}
assert(args[1] and args[2] and not args[3], "usage: v10_rejection_recheck <mp3> <wav>")

local MP3_PATH = args[1]
local WAV_PATH = args[2]
local LOG = "/v10-rejection-recheck.log"
if fs.exists(LOG) then fs.delete(LOG) end

local speaker = assert(peripheral.find("speaker"), "attach a ComputerCraft speaker")

local function log(kind, message)
  local line = ("%-8s %s"):format(kind, tostring(message))
  print(line)
  local h = fs.open(LOG, "a")
  if h then h.writeLine(line) h.close() end
end

local function shorten(value, limit)
  local text = tostring(value or "")
  local max = math.max(0, math.floor(tonumber(limit) or #text))
  if #text <= max then return text end
  if max <= 3 then return text:sub(1, max) end
  return text:sub(1, max - 3) .. "..."
end

local function readBinary(path)
  local h = assert(fs.open(path, "rb"), "cannot open " .. path)
  local data = h.readAll()
  h.close()
  assert(#data > 0, path .. " is empty")
  return data
end

local function expectError(label, fn)
  local ok, err = pcall(fn)
  assert(not ok, label .. " was unexpectedly accepted")
  log("REJECT", label .. " -> " .. shorten(err, 120))
end

local function waitPlaying(timeout)
  local deadline = os.epoch("utc") + timeout * 1000
  while os.epoch("utc") < deadline do
    local status = speaker.audioStatus()
    if status.state == "error" then error("finite playback error: " .. tostring(status.error), 0) end
    if status.state == "playing" then return end
    sleep(0.05)
  end
  error("timed out waiting for finite playback", 0)
end

local function safeStop()
  pcall(function() speaker.audioStopAll() end)
  pcall(function() speaker.audioStop() end)
  pcall(function() speaker.speakStop() end)
end

local mp3 = readBinary(MP3_PATH)
local wav = readBinary(WAV_PATH)

local ok, err = pcall(function()
  log("BEGIN", "A8 malformed finite-media rejection")
  expectError("empty MP3", function() speaker.speakMp3("", 0.0) end)
  expectError("garbage MP3", function() speaker.speakMp3("this is not an mp3", 0.0) end)
  expectError("truncated MP3", function() speaker.speakMp3(mp3:sub(1, math.min(24, #mp3)), 0.0) end)
  expectError("WAV passed to speakMp3", function() speaker.speakMp3(wav, 0.0) end)
  expectError("empty WAV", function() speaker.speakWav("", 0.0) end)
  expectError("garbage WAV", function() speaker.speakWav("RIFFbad data", 0.0) end)
  expectError("truncated WAV", function() speaker.speakWav(wav:sub(1, math.min(24, #wav)), 0.0) end)
  expectError("MP3 passed to speakWav", function() speaker.speakWav(mp3, 0.0) end)
  log("PASS", "A8 malformed finite-media rejection")

  log("BEGIN", "A9 argument and RAW bounds")
  expectError("empty RAW", function() speaker.speakPCM({}, 0.0) end)
  expectError("RAW +32768", function() speaker.speakPCM({32768}, 0.0) end)
  expectError("RAW -32769", function() speaker.speakPCM({-32769}, 0.0) end)
  expectError("RAW non-number", function() speaker.speakPCM({"bad"}, 0.0) end)
  expectError("RAW infinite sample", function() speaker.speakPCM({math.huge}, 0.0) end)
  expectError("non-finite default volume", function() speaker.speakVolume(math.huge) end)
  expectError("speaker index above snapshot", function() speaker.audioStatusAt(speaker.getSpeakerCount() + 1) end)

  local oversized = {}
  for i = 1, speaker.speakMaxSamples() + 1 do oversized[i] = 0 end
  expectError("RAW maxSamples+1", function() speaker.speakPCM(oversized, 0.0) end)
  oversized = nil

  assert(speaker.speakMp3(mp3, 0.0), "MP3 rejected for finite-argument checks")
  waitPlaying(15)
  expectError("non-finite seek", function() speaker.audioSeek(math.huge) end)
  expectError("non-finite finite volume", function() speaker.audioSetVolume(math.huge) end)
  safeStop()
  log("PASS", "A9 argument and RAW bounds")

  log("BEGIN", "A18 stream security and metadata surface")
  assert(not speaker.isStreaming(), "stream unexpectedly active before radio test")
  assert(speaker.getStreamUrl() == nil, "stream URL should be nil while inactive")
  expectError("file:// stream URL", function() speaker.speakStream("file:///tmp/nope.mp3", 0.0) end)
  expectError("loopback stream URL", function() speaker.speakStream("http://127.0.0.1:8000/nope.mp3", 0.0) end)
  local meta = speaker.getStreamMeta()
  assert(type(meta) == "table", "stream metadata not a table")
  assert(type(speaker.getStreamTitle()) == "string", "stream title getter changed")
  assert(type(speaker.getStreamArtist()) == "string", "stream artist getter changed")
  assert(type(speaker.getStreamSong()) == "string", "stream song getter changed")
  assert(type(speaker.getStreamStation()) == "string", "stream station getter changed")
  assert(type(speaker.getStreamGenre()) == "string", "stream genre getter changed")
  assert(type(speaker.getStreamMetaSerial()) == "number", "stream metadata serial changed")
  log("PASS", "A18 stream security and metadata surface")

  log("PASS", "standalone A8/A9/A18 recheck complete")
end)

safeStop()
if not ok then
  log("FAIL", err)
  error(err, 0)
end
