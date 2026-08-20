const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert=require('node:assert/strict');
let browser;
(async()=>{
  browser=await chromium.launch({headless:true,channel:'msedge'});
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  await page.goto('http://127.0.0.1:4173');
  await page.waitForSelector('#notice:not(.hidden)');
  assert.equal(await page.locator('.skill-btn').count(),12);
  await page.locator('.skill-btn').filter({hasText:'AI Agent'}).click();
  assert.equal(await page.locator('#direction').inputValue(),'AI Agent');
  for(const [nav,id] of [['简历分析','resume'],['知识库问答','knowledge'],['训练记录','history']]){
    await page.getByRole('button',{name:nav,exact:false}).first().click();
    assert.equal(await page.locator('#'+id).isVisible(),true);
  }
  await page.locator('[data-view=interview]').click();
  await page.screenshot({path:'tmp/ui-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
  await page.screenshot({path:'tmp/ui-mobile.png',fullPage:true});
  await browser.close();console.log('UI: 12 skills, selection, four views and mobile overflow passed.');
})().catch(async e=>{console.error(e);if(browser)await browser.close();process.exitCode=1;});
