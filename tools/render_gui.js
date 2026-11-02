const { chromium } = require('playwright');
const out = process.argv[2];
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const shot = async (url, file, w, h) => {
    const p = await b.newPage({ viewport: { width: w, height: h } });
    p.on('pageerror', e => console.log('pageerror', e.message));
    await p.goto('http://localhost:8765/' + url);
    await p.waitForFunction('window.READY === true', null, { timeout: 60000 });
    await p.waitForTimeout(300);
    await (await p.$('canvas')).screenshot({ path: file });
    await p.close(); console.log('ok', file);
  };
  await shot('scene.html?w=1200&h=780&built=0.45', out + '/hologram_building.png', 1200, 780);
  await shot('scene.html?w=1200&h=780&built=0', out + '/hologram_placing.png', 1200, 780);
  require('fs').copyFileSync(out + '/hologram_placing.png', 'bg.png');
  for (const pg of ['blueprints', 'materials', 'overview_ready', 'overview_locked', 'hud'])
    await shot('mockup.html?page=' + pg, out + '/gui_' + pg + '.png', 1200, 780);
  await b.close();
})();
