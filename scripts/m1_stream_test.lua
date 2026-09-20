-- Current protocol-v10 direct MP3/ICY radio smoke.
-- For strict grouped-radio acceptance, use v10_radio_acceptance.lua.

local args = {...}
local url = args[1] or "https://ice5.somafm.com/groovesalad-128-mp3"
local speaker = peripheral.find("speaker")
assert(speaker, "attach an HQ speaker")

print("Direct MP3 stream:")
print(url)
speaker.speakStop()
assert(speaker.speakStream(url, 0.35), "speakStream rejected the URL")

local deadline = os.epoch("utc") + 20000
while os.epoch("utc") < deadline do
  if speaker.speakIsPlaying() then
    print("Stream started. Listen for 20 seconds; press any key to stop early.")
    local timer = os.startTimer(20)
    while true do
      local event, value = os.pullEvent()
      if event == "key" or (event == "timer" and value == timer) then break end
    end
    print("Metadata:", textutils.serialize(speaker.getStreamMeta(), {compact = true}))
    speaker.speakStop()
    print("Stream stopped.")
    return
  end
  sleep(0.1)
end

speaker.speakStop()
error("stream did not start within 20 seconds; use a direct MP3 endpoint, not .pls/.m3u")
