const phrases = [
  "track your finances",
  "manage smart tasks",
  "never miss a reminder",
  "split expenses fairly",
  "organize your notes",
  "see your daily reports"
];

const typeSpeed = 80;    // ms per character typed
const deleteSpeed = 40;  // ms per character deleted
const pauseAfterType = 2000;   // ms pause after finishing a phrase
const pauseAfterDelete = 500;  // ms pause before starting next phrase

let phraseIndex = 0;
let charIndex = 0;
let isDeleting = false;

function typeWriter() {
  const target = document.getElementById('typewriter-text');
  if (!target) return;

  const currentPhrase = phrases[phraseIndex];

  if (isDeleting) {
    target.textContent = currentPhrase.substring(0, charIndex - 1);
    charIndex--;
  } else {
    target.textContent = currentPhrase.substring(0, charIndex + 1);
    charIndex++;
  }

  let delay = isDeleting ? deleteSpeed : typeSpeed;

  if (!isDeleting && charIndex === currentPhrase.length) {
    delay = pauseAfterType;
    isDeleting = true;
  } else if (isDeleting && charIndex === 0) {
    isDeleting = false;
    phraseIndex = (phraseIndex + 1) % phrases.length;
    delay = pauseAfterDelete;
  }

  setTimeout(typeWriter, delay);
}

document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('typewriter-text')) {
    setTimeout(typeWriter, 500);
  }
});
