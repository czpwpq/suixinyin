# References and notices

## Public release attribution index

The first public release is an integration based directly on [ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app), pinned baseline `f16adae5aae1da0db387f83abe4331ca6019a22d`. The baseline has no separate top-level LICENSE; its README and third-party notices expressly describe the PolyForm Noncommercial protocol references and MIT Shizuku dependency. Those original notices are retained. This project does not relicense any upstream portion lacking a separately stated grant or describe the entire integration as MIT.

The reader, photo studio, runtime libraries, indirect interaction references and content services are listed with their precise scope in [CREDITS.md](CREDITS.md) and [docs/OPEN_SOURCE_SOURCES.md](docs/OPEN_SOURCE_SOURCES.md). These include wiwiwei/ink-nfc-lab as an indirect source through miaomo, atunverse/moink as interaction inspiration without source/assets redistribution, and miaomo's recorded PhotoEditor/uCrop/Android-Image-Cropper interaction references without embedding those SDKs. Their attribution is not a claim of runtime dependency or a new permission grant.

This index supplements the original notices below; it does not remove, replace or extend their license terms. Online content services are credited separately from code dependencies. Their project licenses do not automatically license third-party news, quotations, character images or other returned content.

The app-local wireless ADB client uses [MuntashirAkon/libadb-android](https://github.com/MuntashirAkon/libadb-android) 3.1.1, commit `c849886ebc6d48e7b46d967e78a6bb65c90c3b74`, with the Apache-2.0 license option. Local adaptations bound TCP/TLS/stream timeouts, restrict connections to loopback and fix stream OPEN/EOF races. Original headers, notices, exact dependency versions/hashes and modifications are documented in `third-party/activation/PROVENANCE.md`.

TLS uses Conscrypt Android 2.5.3 (Apache-2.0), RSA/X509 helpers use Bouncy Castle 1.81 (upstream license), and pairing uses the official SPAKE2 Android 2.2.1 JNI library (LGPL-3.0), with its exact same-version Java JNI facade compiled from source. Complete JNI/C corresponding source and LGPL library replacement/rebuilding instructions are retained in `third-party/activation/`; no alternative pure-Java SPAKE2 mathematics is shipped. AndroidX annotation 1.3.0 is also included under Apache-2.0. Combined notices are packaged in `assets/licenses/activation-LICENSES.txt` and displayed by the app license dialog.

The internal bootstrap/Provider mechanism references the MIT-licensed Shizuku ServiceStarter/UserService design. This is an app-specific privilege service for the photo and reading binders, not a bundled Shizuku manager or an authorization provider for unrelated apps. The original Shizuku MIT license remains packaged.

The native TXT reader, bookshelf, pagination helpers and LZO/preload reading transport are adapted from [pjk2336/huawei-eink-reader](https://github.com/pjk2336/huawei-eink-reader), pinned commit `a4b72b79acec37543339b9810bcb34105b061d65` (0.3.9). Original PolyForm Noncommercial 1.0.0 license, NOTICE, credits and third-party notices are retained in `third-party/inkreader/`; APK license assets use the `inkreader-` prefix. This integration replaces the launcher/branding, embeds the bookshelf into the main Reading tab, uses this app's package and full UID identity protections, and shares an NFC session lock with the existing photo sender. See `docs/READER_PROVENANCE.md` for scope and test boundaries.

InkReader: Copyright (c) 2026 pjk2336 and contributors. Required Notice: Copyright (c) 2026 kur0neko. The upstream real LZO1X implementation references encounter/lzokay-rs and jackoalan/lzokay, Copyright 2021 Luke Street, MIT License; full text is packaged in `assets/licenses/inkreader-lzokay-LICENSE.txt`. Independent host decoding uses lzokay 2.1.0 solely as a development test dependency, not part of the app.

The factory image scan layout and D3/D4 protocol implementation were adapted with reference to:

- kur0neko, https://gitee.com/kur0neko/huawei-epd-pn532, commit `5d3acf730a988815b637262fd4862c1794ab079e`.
- Required Notice: Copyright (c) 2026 kur0neko
- PolyForm Noncommercial License 1.0.0; exact license and notice are retained in `third-party/kur0neko/` and packaged in APK assets. The app's license dialog displays the full license.

Protocol research also used solar2ain/ink-nfc-fmsc-by-pn532, commit `0f88cb315eab60e538c102e5883c5f957d9c1be0` (GPL-3.0-or-later), including the published D8/APDU definitions and LZO1X literal-stream format. No Python modules or native binaries from that project are embedded. The local reference checkout retains its license. The Android literal encoder implements the literal length prefix and LZO end marker directly; its output is independently tested with lzokay.

The v2.10-test1 panel selector and Java layout implementation also use the published Huawei A0 even/odd controller-row mapping and A1 linear layout as protocol references from that checkout (`images.py`, `catalog.py`, `_assets/drivers.json`). Full-frame scan-01 output was compared byte-for-byte against its `pack_indices` function. Its DB/DA driver recipes are not embedded or written by this app; the board's stored driver is retained.

The Android port adds Root/Binder Q-Poll discovery, ISO-DEP transport, target/profile gates, immutable image snapshots, Android image selection/preview, cancellation, and stricter refresh-state reporting. It does not ship PN532 serial transport, replacement display drivers, or vendor test images. The built-in test pattern is drawn locally.

lzokay is used only by the host-side test script and is not part of the APK.


Shizuku API 13.1.5 (api, provider, aidl, shared), copyright Rikka and contributors, MIT License. Source: https://github.com/RikkaApps/Shizuku-API . Full license: assets/licenses/Shizuku-LICENSE.txt.

The photo studio presentation, scene model, text/frame editing and local InkSight processing are adapted from [3dhaoxinren/miaomo](https://github.com/3dhaoxinren/miaomo), pinned commit `499268b34637a5c9e5199ef06e2eda4f9789870a`. Upstream uses PolyForm Noncommercial 1.0.0. Exact upstream license, notice and combined dependency license texts are retained in `third-party/miaomo/` and APK assets `licenses/miaomo-*.txt`; integration scope and modifications are described in `third-party/miaomo/PROVENANCE.md`. The port uses 随心印 branding and retains this app's existing connection and NFC implementation; it does not embed upstream branding, screenshots, transport backend or update checker.

InkSight palette, photo enhancement and Atkinson processing reference datascale-ai/inksight, MIT License, Copyright (c) 2026 datascale-ai. Pillow-compatible enhancement and Lanczos algorithms reference Pillow 10.1.0, HPND License, with the copyright and permission notices included in `third-party/miaomo/THIRD-PARTY-LICENSES.txt` and `assets/licenses/miaomo-THIRD-PARTY-LICENSES.txt`. No Python or Pillow runtime is embedded in the APK.
