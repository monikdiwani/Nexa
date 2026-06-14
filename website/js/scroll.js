// Scroll animation system
(function(){
  const obs = new IntersectionObserver((entries,observer)=>{
    entries.forEach(entry=>{
      if (entry.isIntersecting){
        const el=entry.target;
        const delay = parseFloat(el.getAttribute('data-delay')||0)*1000;
        setTimeout(()=> el.classList.add('is-visible'), delay);
        observer.unobserve(el);
      }
    });
  },{threshold:0.15});
  document.addEventListener('DOMContentLoaded',()=>{
    document.querySelectorAll('[data-animate]').forEach(el=>obs.observe(el));
  });
})();