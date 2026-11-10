const $ = (id) => document.getElementById(id);
let files = [];
let results = [];

async function addFiles() {
  try {
    const selected = await window.autocrop.selectImages();
    const known = new Set(files.map(f => f.path));
    files = files.concat(selected.filter(f => !known.has(f.path)));
    renderFiles();
  } catch (error) { setStatus(error.message, true); }
}
function renderFiles() {
  $('fileList').innerHTML = files.map(f => `<div class="file-chip">${escapeHtml(f.name)} <span>${f.width}×${f.height}</span></div>`).join('');
  $('processBtn').disabled = files.length === 0;
  setStatus(files.length ? files.length + ' photo(s) selected.' : 'Ready when you are.');
}
function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
function setStatus(message, error = false) {
  $('status').textContent = message;
  $('status').style.color = error ? '#ff9b91' : '#94a0ad';
}
$('addBtn').addEventListener('click', addFiles);
$('browseBtn').addEventListener('click', addFiles);
$('quality').addEventListener('input', e => $('qualityValue').textContent = e.target.value + '%');

$('dropzone').addEventListener('dragover', e => { e.preventDefault(); $('dropzone').classList.add('drag'); });
$('dropzone').addEventListener('dragleave', () => $('dropzone').classList.remove('drag'));
$('dropzone').addEventListener('drop', async e => {
  e.preventDefault(); $('dropzone').classList.remove('drag');
  // Native file paths are intentionally selected through the secure file picker.
  setStatus('Use “Add photos” to select files from your computer.');
});

$('processBtn').addEventListener('click', async () => {
  if (!files.length) return;
  const btn = $('processBtn');
  btn.disabled = true; results = [];
  $('results').classList.add('hidden');
  try {
    for (let i = 0; i < files.length; i++) {
      setStatus('Enhancing ' + (i + 1) + ' of ' + files.length + ': ' + files[i].name);
      const output = await window.autocrop.processImage({
        filePath: files[i].path,
        aspect: $('aspect').value,
        upscale: $('upscale').checked,
        quality: Number($('quality').value)
      });
      results.push({ ...output, name: files[i].name, format: output.format });
      renderResults();
    }
    setStatus('Done — ' + results.length + ' photo(s) enhanced.');
    $('results').classList.remove('hidden');
  } catch (error) {
    setStatus('Could not process image: ' + error.message, true);
  } finally { btn.disabled = false; }
});

function renderResults() {
  $('resultGrid').innerHTML = results.map((r, i) => `
    <article class="result-card">
      <img src="${r.dataUrl}" alt="Enhanced ${escapeHtml(r.name)}">
      <strong title="${escapeHtml(r.name)}">${escapeHtml(r.name)}</strong>
      <small>${r.originalWidth}×${r.originalHeight} → ${r.width}×${r.height}</small>
      <button data-save="${i}">Save photo ↓</button>
    </article>`).join('');
  document.querySelectorAll('[data-save]').forEach(button => button.addEventListener('click', async () => {
    const item = results[Number(button.dataset.save)];
    const saved = await window.autocrop.saveImage({ name: item.name, format: item.format, buffer: item.buffer });
    if (!saved.canceled) setStatus('Saved: ' + saved.filePath);
  }));
}
$('saveAllBtn').addEventListener('click', async () => {
  if (!results.length) return;
  const saved = await window.autocrop.saveBatch(results.map(r => ({ name: r.name, format: r.format, buffer: r.buffer })));
  if (!saved.canceled) setStatus('Saved ' + saved.count + ' photos to ' + saved.folder);
});