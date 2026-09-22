# PROJECT CHAIN - SCBD Viewer Battle Mode

## Các mốc đã xâu chuỗi vào V0.6.0

### Game automation
- V1.5: KO/outro/return Training/Full Character Select baseline.
- V1.6: một full auto cycle được xác nhận.
- V1.7: winner pick một cycle.
- V1.8B: pick window 15 giây + P2 Random30.
- V1.9: chỉ Top1 team thắng được pick.
- V20 FULL: đọc cả P1/P2 HP thật + victory state/timer.

### TikTok realtime
- Viewer Avatar Overlay V2.3: resolve TikTok LIVE link và realtime comments.
- V2.4 Gift Engine: TikTools gift events, repeatEnd, diamond/repeat score.
- Winner Live Bridge V6: team lock, gift scoreboard, Top1 winner quyền pick.

### Native APK
- V0.5.8F: visual stable + `/pick14 = KRATOS` + PickSuccess timing 4500ms.
- V0.5.9A: UDP Native Live Bridge 8796.
- V0.5.9B: exact Auto Character Resolver `/pick1..28`.
- V0.5.9C: hero portrait 2X.
- V0.5.9D/7B: full 28 smart profiles + single-file build fix.
- V0.5.9E/HOTFIX8: hard portrait clip, current installed baseline.

## V0.6.0 source-of-truth rules

- TikTok provider: TikTools WSS.
- Viewer identity: TikTools user id/secUid/uniqueId, display name prefers uniqueId.
- Team lock is session-wide.
- Score is session-wide.
- Winner is derived from real P1/P2 HP.
- Winner Top1 is snapshotted at KO.
- Pick authority is that snapshotted Top1 only.
- Pick mapping is current exact 1..28 resolver, not ranking order.
- P1 next source slot = resolver sourceSlot, or 30 on timeout.
- P2 next source slot = 30.
- Native APK is visual status layer; game selection is executed by PPSSPP debugger input.
