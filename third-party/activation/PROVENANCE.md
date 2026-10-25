# Local wireless activation dependencies

This app embeds a localhost ADB client and a private authenticated backend for the user's own device. It does not bundle the standalone Shizuku manager/server. The existing Shizuku mode remains a separate fallback.

## LibADB Android

- Source: https://github.com/MuntashirAkon/libadb-android
- Version 3.1.1; pinned source commit `c849886ebc6d48e7b46d967e78a6bb65c90c3b74`.
- Copyright 2021 Muntashir Al-Islam; dual Apache-2.0 or GPL-3.0-or-later; this integration chooses Apache-2.0. Files with additional BSD-3-Clause/MIT notices retain their original headers (Cameron Gutman, Sam Palmer, Google and other named contributors).
- Library Java source is under `src/io/github/muntashirakon/adb/`.
- Integration patches: bounded TCP/TLS/pair/open timeouts and cancellable sockets; null-safe pairing cleanup; avoid logging exchanged peer identities; discard transient key bytes; per-identity SSLContext instead of a stale global context; bounded connection-thread shutdown; validate negotiated maximum payload; predicate/deadline wait for OPEN acknowledgement; drain buffered data correctly when remote CLSE arrives; unsigned single-byte InputStream reads. Pre-Android-9 charset fallback removed for this min-26 app.
- No upstream sample app or unrestricted shell UI is included.

## SPAKE2 Android JNI

- Source: https://github.com/MuntashirAkon/spake2-java/tree/2.2.1
- Tag 2.2.1 source commit `7615ddd680b990e14513ebb66eac4cb0dbf82464`.
- C submodule: https://github.com/MuntashirAkon/spake2-c/tree/0d15933e5ba3e662cb01245a7ac0dc9fca3eac31
- Copyright 2021 Muntashir Al-Islam, LGPL-3.0. Full LGPL and incorporated GPL text accompany the source. The SPAKE2 Android/JNI/C source (including C submodule and make/CMake files) is preserved in `spake2-source/`.
- The distributed `libs/spake2-android-2.2.1.aar` is the unmodified JitPack artifact. SHA-256: `8798fb6c04b5d53a6307ed9481e9afe563227abd4e8b9e917c8514fa850071bb`.
- Only its JNI libraries are packaged from the AAR. The official matching two Java JNI facades are compiled from source under `src/io/github/muntashirakon/crypto/spake2/` using Java 8, avoiding an old D8 incompatibility with the published facade bytecode. The cryptographic native implementation is unmodified. No pure-Java mathematical implementation is shipped.
- The native library remains separately replaceable in `lib/<ABI>/libspake2.so`. Library users may modify/rebuild/relink this LGPL portion and reverse engineer the combined work to debug such modifications as permitted by LGPL-3.0. No app restriction is intended to remove those rights.
- To replace it, build the included Android JNI/C source with Android NDK/CMake for the same ABI, keep exported JNI symbols, replace the corresponding native entry in a rebuilt AAR/APK, rebuild this complete source project, and sign the resulting APK with your own key. Keep native 16 KiB LOAD alignment. The app contains no signing-key or anti-tamper lock preventing a locally modified/re-signed build. A differently signed build cannot overwrite the developer-signed install; install it separately or first export/clear that install as appropriate.

## Conscrypt Android

- https://github.com/google/conscrypt/tree/2.5.3 ; fixed commit `fd209de6167201e4f3bca5b99c6db9f08d1843c6`.
- Apache-2.0, Copyright 2016 The Android Open Source Project. Apache Harmony/Netty notices and their licenses are retained.
- Maven artifact `org.conscrypt:conscrypt-android:2.5.3`, SHA-256: `551ae4e301c571760d1791e647db6ed1dcb10d34dcae7aa12b67f220f2ce98d1`.
- Its own properties identify BoringSSL commit `49f0329110a1d93a5febc2bceceedc655d995420`; exact BoringSSL LICENSE retained. Native library unmodified.
- JNI libraries for armeabi-v7a, arm64-v8a, x86 and x86_64 are packaged. The arm64-v8a and x86_64 ELF LOAD alignments of both Conscrypt and SPAKE2 were checked as 0x4000; this is separate from APK ZIP alignment.

## Other Java dependencies

- Bouncy Castle `org.bouncycastle:bcprov-jdk15to18:1.81`, MIT-style Bouncy Castle license. SHA-256 `0ef9c4d9536719aaa1c92a7c694d87981fa9d4d64dd9479cc322a08aebbc651e`. Used for HKDF/AES-GCM and self-signed client certificate generation; no global provider is installed.
- AndroidX `androidx.annotation:annotation:1.3.0`, Apache-2.0, Android Open Source Project. SHA-256 `97dc45afefe3a1e421da42b8b6e9f90491477c45fc6178203e3a5e8a05ee8553`.
- Ed25519-Java's upstream license is retained for provenance of SPAKE2 algorithms; the rejected experimental pure-Java module is not included in the APK.

## Validation boundary

`tests/activation/run_adb_crypto_tests.py` executes actual RSA/X509/wire-format checks and the actual ADB stream state machine against a loopback fake daemon. It checks immediate OKAY responses, output draining after CLSE and cancellation of a pending OPEN. This is not a real Android wireless-debugging pairing or a privileged Binder/NFC test.

`tests/android/AdbPairingNativeChecks.java` runs the shipped JNI SPAKE2 plus HKDF/AEAD, wrong-code and tampered-data cases only on an Android runtime. Building this test is not evidence of execution. The host has no connected phone/emulator, so actual TLS pairing, backend handoff, vendor NFC, and UI require target-device acceptance.
