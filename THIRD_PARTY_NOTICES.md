# Third-party components

NookPoint's own code is MIT licensed (see `LICENSE`). The app bundles the following data, each under its own license:

| Component | Files | License |
|---|---|---|
| Lora | `assets/fonts/Lora-*.ttf` | SIL Open Font License 1.1 (`assets/fonts/LICENSE-Lora-OFL.txt`) |
| IBM Plex Serif | `assets/fonts/IBMPlexSerif-*.ttf` | SIL Open Font License 1.1 (`assets/fonts/LICENSE-IBMPlex-OFL.txt`) |
| DejaVu Serif / Sans | `assets/fonts/DejaVu*.ttf` | Free license of the DejaVu fonts (`assets/fonts/LICENSE-DejaVu.txt`) |
| Hyphenation patterns | `assets/hyph/*.hyb` | Compact binary form (as used by Android/Chromium) of the TeX *hyph-utf8* patterns, https://github.com/hyphenation/tex-hyphen. Every language has its own license chosen by its authors (MIT, LGPL, LPPL...); see that repository and `assets/hyph/LICENSE-hyphenation.txt`. |

The fonts are included unmodified. NookPoint is inspired by the CrossPoint Reader firmware for the Xteink X4; no code from that project is used.
