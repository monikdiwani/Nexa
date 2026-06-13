/**
 * Nexa Reminder Alarm System
 * Uses Web Audio API to generate alarm tones based on priority
 */

let audioCtx: AudioContext | null = null;

function getAudioContext(): AudioContext {
  if (!audioCtx || audioCtx.state === "closed") {
    audioCtx = new AudioContext();
  }
  return audioCtx;
}

/** Single beep: freq Hz, duration seconds, volume 0–1 */
function beep(ctx: AudioContext, freq: number, start: number, duration: number, volume = 0.4) {
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.connect(gain);
  gain.connect(ctx.destination);

  osc.type = "sine";
  osc.frequency.setValueAtTime(freq, ctx.currentTime + start);

  gain.gain.setValueAtTime(0, ctx.currentTime + start);
  gain.gain.linearRampToValueAtTime(volume, ctx.currentTime + start + 0.02);
  gain.gain.setValueAtTime(volume, ctx.currentTime + start + duration - 0.05);
  gain.gain.linearRampToValueAtTime(0, ctx.currentTime + start + duration);

  osc.start(ctx.currentTime + start);
  osc.stop(ctx.currentTime + start + duration);
}

/**
 * HIGH priority — urgent escalating alarm (3 pulse bursts)
 * Sounds like a classic alarm clock
 */
export async function playHighAlarm() {
  const ctx = getAudioContext();
  if (ctx.state === "suspended") await ctx.resume();

  // 3 rapid double-beep bursts (escalating pitch)
  const pattern = [880, 1046, 1174];
  pattern.forEach((freq, i) => {
    beep(ctx, freq, i * 0.45, 0.18, 0.5);
    beep(ctx, freq * 1.12, i * 0.45 + 0.2, 0.18, 0.5);
  });
}

/**
 * MEDIUM priority — single soft ping
 */
export async function playMediumAlarm() {
  const ctx = getAudioContext();
  if (ctx.state === "suspended") await ctx.resume();
  beep(ctx, 660, 0, 0.35, 0.3);
  beep(ctx, 880, 0.4, 0.25, 0.2);
}

/**
 * LOW priority — gentle single chime
 */
export async function playLowAlarm() {
  const ctx = getAudioContext();
  if (ctx.state === "suspended") await ctx.resume();
  beep(ctx, 523, 0, 0.4, 0.2);
}

export function playAlarm(priority: string) {
  try {
    if (priority === "HIGH") return playHighAlarm();
    if (priority === "MEDIUM") return playMediumAlarm();
    return playLowAlarm();
  } catch {
    // Web Audio not supported — silent fail
    return Promise.resolve();
  }
}
