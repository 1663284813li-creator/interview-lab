const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
let browser;
(async()=>{
  browser=await chromium.launch({headless:true,channel:'msedge'});
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  await page.goto(process.env.APP_URL||'http://127.0.0.1:8080');
  await page.waitForFunction(()=>document.getElementById('connection').textContent==='服务已连接');
  assert.equal(await page.locator('#notice').isVisible(),false);
  assert.equal(await page.locator('.skill-btn').count(),12);
  for(const id of ['resume','knowledge','history','interview']) {
    await page.locator('[data-view='+id+']').click();
    assert.equal(await page.locator('#'+id).isVisible(),true);
  }
  await page.locator('[data-view=resume]').click();
  await page.evaluate(()=>window.taskListProbe=document.querySelector('#resume-tasks').firstChild);
  await page.waitForResponse(r=>new URL(r.url()).pathname==='/api/jobs'&&r.request().method()==='GET');
  assert(await page.evaluate(()=>window.taskListProbe===document.querySelector('#resume-tasks').firstChild));
  await page.locator('[data-view=interview]').click();
  await page.screenshot({path:'tmp/ui-live-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  await page.screenshot({path:'tmp/ui-live-mobile.png',fullPage:true});
  await browser.close();console.log('PASS 真实服务的浏览器连接、认证、页面切换与手机布局');
})().catch(async e=>{console.error(e.message);if(browser)await browser.close();process.exitCode=1;});
