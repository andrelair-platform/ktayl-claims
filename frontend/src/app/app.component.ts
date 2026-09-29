import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  template: `
    <header class="topbar">
      <a routerLink="/" class="brand"><span class="dot"></span> Claims Workbench</a>
      <nav>
        <a routerLink="/" class="link">Inbox</a>
        <a routerLink="/claims/new" class="btn">New FNOL</a>
      </nav>
    </header>
    <main class="page"><router-outlet /></main>
  `,
  styles: [`
    .topbar { display:flex; justify-content:space-between; align-items:center; padding:.75rem 1.5rem;
      border-bottom:1px solid #e2e8f0; background:#fff; }
    .brand { font-weight:600; color:#0f172a; text-decoration:none; display:flex; align-items:center; gap:.5rem; }
    .dot { width:.6rem; height:.6rem; border-radius:9999px; background:#0d9488; display:inline-block; }
    nav { display:flex; gap:1rem; align-items:center; font-size:.9rem; }
    .link { color:#475569; text-decoration:none; }
    .link:hover { color:#0f172a; }
    .btn { background:#0d9488; color:#fff; padding:.4rem .8rem; border-radius:.375rem; text-decoration:none; font-weight:500; }
    .btn:hover { background:#0f766e; }
    .page { max-width:72rem; margin:0 auto; padding:1.5rem; }
  `],
})
export class AppComponent {}
