// While the camera moves, bloom + streak rebuild on one rendered frame in three, and
// the first frame after it settles always rebuilds. Each rebuild renders the scene
// twice more; measured on the car with the camera orbiting, 28.1 fps against 34.2.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const source=readFileSync(new URL('../index.html',import.meta.url),'utf8');
function check(html){
 const ctx=vm.createContext({DCLogic:class{},setTimeout:()=>0,clearTimeout:()=>{}});
 vm.runInContext(html.match(/<script type="text\/x-dc" data-dc-script>([\s\S]*?)<\/script>/)[1]+';globalThis.App=Component;globalThis.EVERY=POSTFX_MOTION_EVERY;',ctx);
 assert.equal(ctx.EVERY,3,'one rebuild in three moving frames');
 const app=Object.create(ctx.App.prototype);
 const requests=[];app.requestRender=(n,keep)=>{requests.push([n,keep])};
 // One loop frame: the camera re-dirties post-FX while it moves; a rebuild clears it.
 const frame=(busy)=>{if(busy)app._postFxDirty=true;const r=app._postFxRebuildNow(busy);if(r)app._postFxDirty=false;return r};
 app._postFxDirty=true;
 assert.equal(frame(false),true,'camera still: a dirty overlay rebuilds on the next frame');
 assert.equal(frame(false),false,'camera still and clean: cached overlay');
 const moving=Array.from({length:9},()=>frame(true));
 assert.deepEqual(moving,[false,false,true,false,false,true,false,false,true],'moving: frames 3, 6 and 9 rebuild');
 assert.equal(requests.length,6,'every skipped frame queues another frame');
 assert.ok(requests.every(([n,keep])=>n===1&&keep===true),'the queued frame keeps the overlay as it is');
 frame(true);frame(true);
 assert.equal(app._postFxDirty,true,'a skipped frame leaves post-FX dirty');
 assert.equal(frame(false),true,'the first frame after the camera settles rebuilds');
 assert.equal(frame(false),false,'and only that one');
 requests.length=0;app._postFxDirty=false;
 assert.equal(app._postFxRebuildNow(true),false,'moving but clean: nothing to rebuild');
 assert.equal(requests.length,0,'and no frame is queued for it');
 // The loop consults the throttle, with the loop's own cameraBusy, before the bloom pass.
 assert.match(html,/if \(\(anyLight \|\| xrayBloom\) && this\.bloomComposer\) \{\s*if \(this\._postFxRebuildNow\(cameraBusy\)\) \{\s*syncCam\(this\.bloomCam\);/,'render loop rebuilds through _postFxRebuildNow');
}
check(source);
const body=(s)=>source.replace(/  _postFxRebuildNow\(cameraBusy\) \{[\s\S]*?\n  \}\n/,`  _postFxRebuildNow(cameraBusy) {\n${s}\n  }\n`);
assert.throws(()=>check(body('    return !!this._postFxDirty;')),/frames 3, 6 and 9/,'negative control: no throttle');
assert.throws(()=>check(body(`    if (!cameraBusy) { this._postFxMotionFrames = 0; return !!this._postFxDirty; }
    if (!this._postFxDirty) return false;
    this._postFxMotionFrames = (this._postFxMotionFrames || 0) + 1;
    return this._postFxMotionFrames % POSTFX_MOTION_EVERY === 0;`)),/queues another frame/,'negative control: no queued frame for the settle');
assert.throws(()=>check(body(`    if (!cameraBusy) { this._postFxMotionFrames = 0; return !!this._postFxDirty; }
    if (!this._postFxDirty) return false;
    this._postFxMotionFrames = (this._postFxMotionFrames || 0) + 1;
    if (this._postFxMotionFrames % POSTFX_MOTION_EVERY === 0) return true;
    this._postFxDirty = false; if (this.requestRender) this.requestRender(1, true); return false;`)),/leaves post-FX dirty/,'negative control: a skip that drops the dirty flag');
assert.throws(()=>check(source.replace('if (this._postFxRebuildNow(cameraBusy)) {','if (this._postFxDirty) {')),/rebuilds through _postFxRebuildNow/,'negative control: loop bypasses the throttle');
assert.throws(()=>check(source.replace('this.requestRender(1, true);\n    return false;','this.requestRender(1);\n    return false;')),/keeps the overlay/,'negative control: the queued frame invalidates the overlay');
console.log('PASS post-FX motion throttle: 1 in 3 while moving, exact on settle, still camera unchanged + negative controls');
