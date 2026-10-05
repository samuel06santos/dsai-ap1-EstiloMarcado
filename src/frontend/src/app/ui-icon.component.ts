import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type IconName = 'home' | 'calendar' | 'building' | 'users' | 'scissors' |
  'user' | 'menu' | 'close' | 'chevron' | 'logout' | 'arrow' | 'sparkles' |
  'phone' | 'mail' | 'check' | 'clock' | 'google';

@Component({
  selector: 'app-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ui-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      @switch (name) {
        @case ('home') { <path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1zM9 21v-7h6v7"/> }
        @case ('calendar') { <rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 10h18M8 15h3"/> }
        @case ('building') { <path d="M4 21V6l8-3 8 3v15M3 21h18M8 10h1M15 10h1M8 14h1M15 14h1M10 21v-4h4v4"/> }
        @case ('users') { <circle cx="9" cy="8" r="3"/><path d="M3 20v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6M18 15a5 5 0 0 1 3 5"/> }
        @case ('scissors') { <circle cx="6" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M8.5 8.5 21 21M8.5 15.5 21 3"/> }
        @case ('user') { <circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/> }
        @case ('menu') { <path d="M4 7h16M4 12h16M4 17h16"/> }
        @case ('close') { <path d="M5 5 19 19M19 5 5 19"/> }
        @case ('chevron') { <path d="m6 9 6 6 6-6"/> }
        @case ('logout') { <path d="M10 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5M15 7l5 5-5 5M8 12h12"/> }
        @case ('arrow') { <path d="M4 12h16M14 6l6 6-6 6"/> }
        @case ('sparkles') { <path d="m12 3 1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 17l.6 1.4L21 19l-1.4.6L19 21l-.6-1.4L17 19l1.4-.6z"/> }
        @case ('phone') { <path d="M6 3h3l1.2 4-2 2a15 15 0 0 0 6.8 6.8l2-2 4 1.2v3a2 2 0 0 1-2 2A17 17 0 0 1 4 5a2 2 0 0 1 2-2z"/> }
        @case ('mail') { <rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/> }
        @case ('check') { <path d="m4 12 5 5L20 6"/> }
        @case ('clock') { <circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/> }
        @case ('google') { <path d="M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z"/> }
      }
    </svg>
  `
})
export class UiIconComponent {
  @Input({ required: true }) name!: IconName;
}
