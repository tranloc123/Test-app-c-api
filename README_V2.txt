SCBD TikTok Dev Shell V2 - FULL FRAME BRIDGE

Purpose:
- Removes the old 2048-byte WebSocket binary truncation.
- Passes complete ArrayBuffer frames from the document-start bootstrap to the hot-loaded probe.js.
- Keeps the existing local hot-reload server workflow at http://127.0.0.1:8788.
- Future TikTok decoder/filter changes still live in probe.js, so this APK should only need to be built once.

Use with:
  dev-probe-0.3.9-fullframe-multi-message

GitHub build:
- Put this package contents at repository root.
- Workflow: .github/workflows/build-apk.yml
- Run the workflow or push to main.
- Artifact name: SCBD-TikTok-Dev-Shell-V2-APK
- APK filename: SCBD_TikTok_Dev_Shell_V2.apk

After installing APK V2, replace the Termux SCBD_DEV/probe.js and probe_version.txt with V0.3.9.
