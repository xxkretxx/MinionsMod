// Renders model previews and exports GLB files with headless Chromium.
const { chromium } = require('playwright');
const fs = require('fs');
const out = process.argv[2] || 'renders';
fs.mkdirSync(out, { recursive: true });
(async () => {
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const shots = [
    ['minions_lineup.png', 'mode=hero&w=1600&h=900'],
    ['minion_copper.png', 'mode=single&tier=copper&w=900&h=1000&ry=0.5'],
    ['minion_gold.png', 'mode=single&tier=gold&w=900&h=1000&ry=-0.5'],
    ['minion_diamond.png', 'mode=single&tier=diamond&w=900&h=1000&ry=0.4'],
    ['minion_copper_back.png', 'mode=single&tier=copper&w=900&h=1000&ry=2.6&pose=t'],
  ];
  for (const [name, q] of shots) {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
    page.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
    page.on('pageerror', e => console.log('pageerror:', e.message));
    await page.goto('http://localhost:8765/preview.html?' + q);
    await page.waitForFunction('window.READY === true', null, { timeout: 60000 });
    await page.waitForTimeout(500);
    await page.evaluate('window.renderOnce()');
    const canvas = await page.$('canvas');
    await canvas.screenshot({ path: `${out}/${name}` });
    if (name === 'minions_lineup.png') {
      for (const t of ['copper', 'gold', 'diamond']) {
        const b64 = await page.evaluate(`window.exportGLB('${t}')`);
        fs.writeFileSync(`${out}/minion_${t}.glb`, Buffer.from(b64, 'base64'));
      }
    }
    await page.close();
    console.log('rendered', name);
  }
  await browser.close();
})();
