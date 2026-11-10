# AutoCrop Desktop

A Windows desktop photo utility for smart crop, quick image enhancement and export. Images are processed locally using Sharp; the app does not upload photos to a cloud service.

## Features
- Smart crop positioning using Sharp's attention strategy.
- Presets: original, square, portrait 4:5, story 9:16, landscape 16:9 and photo 3:2.
- Basic enhancement: auto-normalization, modest brightness/saturation adjustment and sharpening.
- Optional 2× upscale using Lanczos resampling.
- Batch processing and save-one/save-all export.
- JPG, PNG, WEBP, TIFF and AVIF input support (output follows the source format where supported).

> Note: this first version uses conventional image processing, not a generative AI or neural super-resolution model. 2× enlargement increases pixel dimensions but cannot reconstruct details that were never present.

## Requirements
- Windows 10/11
- Node.js 20 or newer

## Run from source
```bash
npm install
npm start
```

## Build Windows installers
```bash
npm run dist
```
Artifacts are written to `release/` (NSIS installer and portable build).

## Privacy
Image processing runs on the local machine. No photo upload endpoint or account is required. The UI currently loads Google Fonts from the internet; use system fonts or bundle fonts locally for a fully offline build.