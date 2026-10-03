# Заметки по коду · Видео с нотами

> Поведение — `docs/spec/overlay.md` (3.37, 5.30). Читай перед работой над этой фичей; дополняй здесь же.

Этапы 123–126 не начаты (03.10.2026): написаны спека (0.89, черновик), план — `docs/plan-overlay.md`, макет в точных размерах — `docs/design/project/overlay/project/overlay.html`. Идея и выбор владельца (B «лента нот» + D «итог») — `docs/ideas/video-overlay/`.

- **Зависимость.** Android перекодирует картинку Media3 Transformer 1.9.0 (`media3-transformer`, `media3-effect`) — согласовано владельцем 03.10.2026. Версия та же, что у `media3-common` и `media3-muxer`, которые уже приносит CameraX 1.6.2 (там же Guava 33.3.1): поднимать вместе с CameraX, сверяя `:app:dependencies`. iOS — только системные AVFoundation и Skia, которая уже есть в Compose Multiplatform.
- **Media3 1.9.0, проверено по исходникам** (`media3-effect-1.9.0-sources.jar`): `BitmapOverlay.getTextureId` перезаливает текстуру, когда у `Bitmap` меняется ссылка или `generationId`, — рисовать можно в одну `Bitmap`, стирая её каждый кадр. `Composition.Builder.setEffects` и `setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)` есть; портретное видео Transformer по умолчанию кодирует повёрнутым с меткой поворота (`setPortraitEncodingEnabled(false)`), `VideoMuxer.splice` её переносит.

## Что не проверено

Всё — фича не начата.
