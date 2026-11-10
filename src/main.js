const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs/promises');
const sharp = require('sharp');

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1320,
    height: 900,
    minWidth: 980,
    minHeight: 700,
    backgroundColor: '#10141c',
    title: 'AutoCrop',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  mainWindow.loadFile(path.join(__dirname, 'index.html'));
}

app.whenReady().then(() => {
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

ipcMain.handle('select-images', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Choose photos',
    properties: ['openFile', 'multiSelections'],
    filters: [{ name: 'Images', extensions: ['jpg', 'jpeg', 'png', 'webp', 'tif', 'tiff', 'avif'] }]
  });
  if (result.canceled) return [];
  return Promise.all(result.filePaths.map(async (filePath) => {
    const meta = await sharp(filePath).metadata();
    return {
      id: filePath,
      name: path.basename(filePath),
      path: filePath,
      width: meta.width || 0,
      height: meta.height || 0,
      format: meta.format || 'image'
    };
  }));
});

ipcMain.handle('preview-image', async (_event, filePath) => {
  const buffer = await sharp(filePath).rotate().resize({ width: 1100, height: 760, fit: 'inside', withoutEnlargement: true }).jpeg({ quality: 82 }).toBuffer();
  return 'data:image/jpeg;base64,' + buffer.toString('base64');
});

ipcMain.handle('process-image', async (_event, options) => {
  const { filePath, aspect, upscale, quality } = options;
  const input = sharp(filePath, { failOn: 'none' }).rotate();
  const meta = await input.metadata();
  if (!meta.width || !meta.height) throw new Error('Could not read image dimensions.');

  const ratios = {
    original: null,
    square: 1,
    portrait: 4 / 5,
    story: 9 / 16,
    landscape: 16 / 9,
    photo: 3 / 2
  };
  const ratio = ratios[aspect] ?? null;
  let pipeline = sharp(filePath, { failOn: 'none' }).rotate();

  if (ratio) {
    const width = meta.width;
    const height = meta.height;
    let cropWidth = width;
    let cropHeight = height;
    if (width / height > ratio) cropWidth = Math.round(height * ratio);
    else cropHeight = Math.round(width / ratio);
    pipeline = pipeline.resize(cropWidth, cropHeight, {
      fit: 'cover',
      position: sharp.strategy.attention
    });
  }

  const scale = upscale ? 2 : 1;
  let cropWidth = meta.width;
  let cropHeight = meta.height;
  if (ratio) {
    if (meta.width / meta.height > ratio) cropWidth = Math.round(meta.height * ratio);
    else cropHeight = Math.round(meta.width / ratio);
  }
  const targetWidth = Math.min(Math.round(cropWidth * scale), 12000);
  const targetHeight = Math.min(Math.round(cropHeight * scale), 12000);

  pipeline = pipeline
    .resize(targetWidth, targetHeight, {
      fit: 'cover',
      position: sharp.strategy.attention,
      kernel: sharp.kernel.lanczos3
    })
    .normalize()
    .modulate({ brightness: 1.03, saturation: 1.06 })
    .sharpen({ sigma: 0.8, m1: 0.5, m2: 1.2 });

  const outputFormat = ['jpeg', 'png', 'webp'].includes(meta.format) ? meta.format : 'jpeg';
  if (outputFormat === 'jpeg') pipeline = pipeline.jpeg({ quality: quality || 92, mozjpeg: true });
  else if (outputFormat === 'webp') pipeline = pipeline.webp({ quality: quality || 92 });
  else pipeline = pipeline.png({ compressionLevel: 8 });

  const outputBuffer = await pipeline.toBuffer();
  const outputMeta = await sharp(outputBuffer).metadata();
  return {
    dataUrl: 'data:image/' + (outputFormat === 'jpeg' ? 'jpeg' : outputFormat) + ';base64,' + outputBuffer.toString('base64'),
    buffer: outputBuffer.toString('base64'),
    width: outputMeta.width,
    height: outputMeta.height,
    format: outputFormat,
    originalWidth: meta.width,
    originalHeight: meta.height
  };
});

ipcMain.handle('save-image', async (_event, payload) => {
  const suggested = payload.name.replace(/\.[^.]+$/, '') + '-autocrop.' + payload.format;
  const result = await dialog.showSaveDialog(mainWindow, {
    title: 'Save enhanced photo',
    defaultPath: suggested,
    filters: [{ name: payload.format.toUpperCase(), extensions: [payload.format] }]
  });
  if (result.canceled || !result.filePath) return { canceled: true };
  await fs.writeFile(result.filePath, Buffer.from(payload.buffer, 'base64'));
  return { canceled: false, filePath: result.filePath };
});

ipcMain.handle('save-batch', async (_event, items) => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Choose output folder',
    properties: ['openDirectory', 'createDirectory']
  });
  if (result.canceled || !result.filePaths[0]) return { canceled: true };
  const folder = result.filePaths[0];
  for (const item of items) {
    const ext = item.format === 'jpeg' ? 'jpg' : item.format;
    const base = path.basename(item.name, path.extname(item.name));
    await fs.writeFile(path.join(folder, base + '-autocrop.' + ext), Buffer.from(item.buffer, 'base64'));
  }
  return { canceled: false, folder, count: items.length };
});