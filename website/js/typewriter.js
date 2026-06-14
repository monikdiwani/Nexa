// Typewriter effect
const Typewriter = (el, phrases, opts={})=>{
  const {typeSpeed=80, deleteSpeed=40, pauseAfterType=2000, pauseAfterDelete=500} = opts;
  let i=0, j=0, current=''; let deleting=false;
  function tick(){
    const phrase=phrases[i];
    if (!deleting){
      current = phrase.slice(0, j+1);
      el.textContent = current;
      j++;
      if (j===phrase.length){ deleting=true; setTimeout(tick,pauseAfterType); return }
      setTimeout(tick,typeSpeed);
    } else {
      current = phrase.slice(0, j-1);
      el.textContent = current;
      j--;
      if (j===0){ deleting=false; i=(i+1)%phrases.length; setTimeout(tick,pauseAfterDelete); return }
      setTimeout(tick,deleteSpeed);
    }
  }
  tick();
};

// Auto-init helper
document.addEventListener('DOMContentLoaded',()=>{
  const el=document.getElementById('typewriter-text');
  if (!el) return;
  const phrases = [
    "track your finances",
    "manage smart tasks",
    "never miss a reminder",
    "split expenses fairly",
    "organize your notes",
    "see your daily reports"
  ];
  Typewriter(el, phrases, {typeSpeed:80, deleteSpeed:40, pauseAfterType:1800});
});