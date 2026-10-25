# Miaomo photo studio reference

Source: https://github.com/3dhaoxinren/miaomo

Pinned commit: `499268b34637a5c9e5199ef06e2eda4f9789870a` (2026-10-22).

The exact upstream `LICENSE`, `NOTICE`, and `THIRD-PARTY-LICENSES.txt` are retained in this directory. The same bytes are packaged in APK assets under `licenses/miaomo-*.txt`. Upstream's project license is PolyForm Noncommercial 1.0.0. InkSight-derived processing carries the included datascale-ai MIT notice; Pillow-compatible enhancement/resampling carries the included Pillow 10.1.0 HPND notice. The combined upstream dependency license file is retained intact for attribution; this does not mean every listed dependency is embedded by 随心印.

The port changes the package to `org.nfcresearch.qpolllab`, connects the local editor and image pipeline to 随心印's existing prepared-frame and A0/A1 packing interfaces, and uses 随心印 branding. Each adapted source file records its upstream source and changes. No upstream branding bitmap, example photograph, screenshot, NFC backend, update checker, or vendor library is copied into the application.

## Integration scope

| Reference component | Integration | Boundary |
| --- | --- | --- |
| `AppUi`, `UiIcon`, photo page structure | Native photo/device/app navigation and local editing presentation | 随心印 branding and existing device controls |
| `PhotoEdit`, `EditHistory` | Copyable scene, text/frame state, bounded undo/redo and local restoration | No bitmap duplication in undo snapshots |
| `PhotoEditor`, `PhotoDecorations` | Crop/rotate/mirror/zoom, text layers and frames | Render a local scene; do not send NFC commands |
| `InkSightFitter`, `InkSightQuantizer`, image composition from `ImageCodec` | Local fitting, Pillow-compatible enhancement, four-color quantization | Canonical 552×768 codes are packed by our existing A0/A1 implementation |
| `PicturePanel` interactions | Original/four-color comparison, preview saving and explicit send controls | Transfer snapshot remains fixed until the session finishes |
| Standard NFC, ST54L, Q-Poll backend and protocol classes | Excluded | Existing 随心印 connection, identity check, NFC protocol and cleanup remain authoritative |
| Upstream app updates, branding and sample images | Excluded | No upstream update endpoint or brand assets |

Calendar/weather and the existing card editor remain separate local features. Community gallery work is deferred for this integration. The upstream transport's compatibility claims and hardware results do not establish compatibility for this port.

## Validation boundaries

The imported synthetic fixture hashes and reference expectations document algorithm compatibility; they are not photographs or screen measurements. Java scene/algorithm tests can verify saved state, fitting and four-color packing. Android instrumentation can verify UI geometry, local editing and frozen send snapshots using a fake Host. Neither host tests nor compiling instrumentation demonstrates actual phone NFC or physical display quality; those require device execution.
