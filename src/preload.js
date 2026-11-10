const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('autocrop', {
  selectImages: () => ipcRenderer.invoke('select-images'),
  previewImage: (filePath) => ipcRenderer.invoke('preview-image', filePath),
  processImage: (options) => ipcRenderer.invoke('process-image', options),
  saveImage: (payload) => ipcRenderer.invoke('save-image', payload),
  saveBatch: (items) => ipcRenderer.invoke('save-batch', items)
});