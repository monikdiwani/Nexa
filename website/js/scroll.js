document.addEventListener('DOMContentLoaded', () => {
  const observerOptions = {
    root: null,
    rootMargin: '0px',
    threshold: 0.15 // Trigger when 15% visible
  };

  const observer = new IntersectionObserver((entries, observer) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        const el = entry.target;
        const delay = el.getAttribute('data-delay') || 0;
        
        // Apply delay if specified
        if (delay) {
          el.style.transitionDelay = `${delay}s`;
        }
        
        el.classList.add('is-visible');
        
        // Stop observing once animated
        observer.unobserve(el);
      }
    });
  }, observerOptions);

  const animatedElements = document.querySelectorAll('[data-animate]');
  animatedElements.forEach(el => observer.observe(el));
});
