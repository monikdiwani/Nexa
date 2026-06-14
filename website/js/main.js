// Main site JS: particles, navbar, mobile menu, count-up
(function(){
  function initNavbar(){
    const nav=document.querySelector('.navbar');
    if(!nav) return;
    window.addEventListener('scroll',()=>{
      if(window.scrollY>50) nav.classList.add('scrolled'); else nav.classList.remove('scrolled');
    });
  }

  function initMobileMenu(){
    const btn=document.getElementById('hamburger');
    const overlay=document.getElementById('mobile-menu');
    if(!btn||!overlay) return;
    btn.addEventListener('click',()=>overlay.classList.toggle('open'));
    overlay.addEventListener('click',(e)=>{ if(e.target===overlay) overlay.classList.remove('open'); });
  }

  function initCountUp(){
    const nodes = document.querySelectorAll('[data-count]');
    const obs = new IntersectionObserver((entries,observer)=>{
      entries.forEach(entry=>{
        if(entry.isIntersecting){
          const el=entry.target; const to=parseInt(el.getAttribute('data-count')||'0',10);
          let start=0; const dur=1500; const startTs=performance.now();
          function frame(ts){
            const p=Math.min(1,(ts-startTs)/dur);
            const eased = 1 - Math.pow(1 - p, 3);
            el.textContent = Math.round(eased*to);
            if(p<1) requestAnimationFrame(frame);
          }
          requestAnimationFrame(frame);
          observer.unobserve(el);
        }
      });
    },{threshold:0.2});
    nodes.forEach(n=>obs.observe(n));
  }

  // Simple particles canvas
  function initParticles(){
    const canvas=document.getElementById('particles');
    if(!canvas) return;
    const ctx=canvas.getContext('2d');
    let w=canvas.width=canvas.offsetWidth; let h=canvas.height=canvas.offsetHeight;
    const N=80; const pts=[];
    for(let i=0;i<N;i++) pts.push({x:Math.random()*w,y:Math.random()*h, vx:(Math.random()-0.5)*0.3, vy:(Math.random()-0.5)*0.3});
    function resize(){ w=canvas.width=canvas.offsetWidth; h=canvas.height=canvas.offsetHeight; }
    window.addEventListener('resize',resize);
    function loop(){ ctx.clearRect(0,0,w,h);
      for(let i=0;i<N;i++){ const p=pts[i]; p.x+=p.vx; p.y+=p.vy; if(p.x<0||p.x>w) p.vx*=-1; if(p.y<0||p.y>h) p.vy*=-1; ctx.fillStyle='rgba(255,255,255,0.06)'; ctx.beginPath(); ctx.arc(p.x,p.y,1.6,0,Math.PI*2); ctx.fill(); }
      // connect
      for(let i=0;i<N;i++) for(let j=i+1;j<N;j++){ const a=pts[i], b=pts[j]; const dx=a.x-b.x, dy=a.y-b.y; const d=Math.hypot(dx,dy); if(d<150){ ctx.strokeStyle='rgba(255,255,255,'+(0.08*(1-d/150))+')'; ctx.lineWidth=1; ctx.beginPath(); ctx.moveTo(a.x,a.y); ctx.lineTo(b.x,b.y); ctx.stroke(); } }
      requestAnimationFrame(loop);
    }
    loop();
  }

  function initSmoothScroll(){ document.querySelectorAll('a[href^="#"]').forEach(a=>{ a.addEventListener('click',e=>{ e.preventDefault(); const tgt=document.querySelector(a.getAttribute('href')); if(tgt) tgt.scrollIntoView({behavior:'smooth',block:'start'}); }); }); }

  document.addEventListener('DOMContentLoaded',()=>{ initNavbar(); initMobileMenu(); initCountUp(); initParticles(); initSmoothScroll(); });
})();