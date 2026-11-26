// Line drawing of a reading-room desk: a green banker's lamp, a stack of
// books and an open book in the lamp's light. Hand-drawn in SVG, no photos.
export default function LampArt({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 360 280" className={className} role="img" aria-label="A banker's lamp lighting an open book on a library desk">
      <defs>
        <linearGradient id="lamp-light" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#e3b04b" stopOpacity="0.45" />
          <stop offset="1" stopColor="#e3b04b" stopOpacity="0" />
        </linearGradient>
      </defs>
      <g fill="none" stroke="#e3b04b" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
        {/* desk */}
        <path d="M14 238 H346" />
        <path d="M30 246 H330" opacity="0.4" />
        {/* light falling on the book */}
        <path d="M168 112 L120 222 H300 L232 112 Z" fill="url(#lamp-light)" stroke="none" />
        {/* lamp: base, stem, arm, shade */}
        <ellipse cx="236" cy="230" rx="44" ry="8" />
        <path d="M236 222 V150" />
        <path d="M236 150 Q236 128 214 122" />
        <path d="M150 112 Q150 84 200 80 Q250 84 250 112 Z" fill="#2f7258" />
        <path d="M150 112 H250" />
        <path d="M166 92 Q200 86 234 92" opacity="0.5" />
        <circle cx="200" cy="74" r="4" />
        {/* open book */}
        <path d="M150 222 Q180 206 214 216 Q248 206 278 222" />
        <path d="M214 216 V230" />
        <path d="M150 222 V232 Q180 218 214 230 Q248 218 278 232 V222" />
        <path d="M164 214 Q186 205 204 210 M226 210 Q244 205 266 214" opacity="0.55" />
        {/* stacked books */}
        <rect x="40" y="206" width="92" height="16" rx="2" />
        <rect x="48" y="190" width="80" height="16" rx="2" fill="#5c1020" />
        <rect x="36" y="174" width="86" height="16" rx="2" />
        <rect x="52" y="158" width="66" height="16" rx="2" fill="#2f7258" />
        <path d="M56 214 H112 M62 198 H104 M50 182 H98 M64 166 H100" opacity="0.6" />
        {/* the reader's tea */}
        <path d="M306 214 H330 V228 Q330 236 318 236 Q306 236 306 228 Z" />
        <path d="M330 218 Q340 218 340 224 Q340 230 330 230" />
        <path d="M313 206 Q309 199 314 192 M322 206 Q318 199 323 192" opacity="0.6" />
      </g>
    </svg>
  )
}
