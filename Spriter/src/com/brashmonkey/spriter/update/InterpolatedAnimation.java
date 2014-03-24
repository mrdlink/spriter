package com.brashmonkey.spriter.update;

import java.util.ArrayDeque;

import com.brashmonkey.spriter.SpriterException;
import com.brashmonkey.spriter.interpolation.SpriterCurve;
import com.brashmonkey.spriter.update.Mainline.Key.BoneRef;
import com.brashmonkey.spriter.update.Mainline.Key.ObjectRef;
import com.brashmonkey.spriter.update.Timeline.Key.Bone;
import com.brashmonkey.spriter.update.Timeline.Key.Object;

public class InterpolatedAnimation extends Animation{
	
	public float weight = .5f, spriteThreshold = .5f;
	public final SpriterCurve curve;
	public final Entity entity;
	private Animation anim1, anim2;
	public Animation baseAnimation;
	public BoneRef base = null;

	public InterpolatedAnimation(Entity entity) {
		super(-1, "interpolatedAnimation", 0, true);
		this.entity = entity;
		this.curve = new SpriterCurve();
		this.setUpTimelines();
	}
	
	public Mainline.Key getCurrentKey(){
		return this.currentKey;
	}

	ArrayDeque<Timeline> tempTimelines = new ArrayDeque<Timeline>();
	@Override
	public void update(int time, Bone root){
		super.currentKey = onFirstMainLine() ? anim1.currentKey: anim2.currentKey;
    	for(Timeline.Key timelineKey: this.mappedTweenedKeys)
			timelineKey.active = false;
    	if(base != null){
        	//Animation currentAnim = onFirstMainLine() ? anim1: anim2;
        	Animation baseAnim = baseAnimation == null ? (onFirstMainLine() ? anim1:anim2) : baseAnimation;
	    	for(BoneRef ref: baseAnim.currentKey.boneRefs){
	    		Timeline.Key key, mappedKey;
    			key = baseAnim.tweenedKeys[ref.timeline];
    			mappedKey = baseAnim.mappedTweenedKeys[ref.timeline];
	    		this.tweenedKeys[ref.timeline].active = key.active;
	    		this.tweenedKeys[ref.timeline].object().set(key.object());
	    		this.mappedTweenedKeys[ref.timeline].active = mappedKey.active;
				this.unmapTimelineObject(ref.timeline, false,(ref.parent != null) ?
						this.mappedTweenedKeys[ref.parent.timeline].object(): root);
	    	}
	    	for(ObjectRef ref: currentKey.objectRefs){//TODO: Sprite mapping works not properly...
	        	Timeline timeline = baseAnim.getSimilarTimeline(ref, tempTimelines);
	        	if(timeline != null){
	        		tempTimelines.addLast(timeline);
	        		Timeline.Key key = baseAnim.tweenedKeys[timeline.id];
	        		Timeline.Key mappedKey = baseAnim.mappedTweenedKeys[timeline.id];
	        		Object obj = (Object) key.object();
	        		
		    		this.tweenedKeys[ref.timeline].active = key.active;
		    		((Object)this.tweenedKeys[ref.timeline].object()).set(obj);
		    		this.mappedTweenedKeys[ref.timeline].active = mappedKey.active;
					this.unmapTimelineObject(ref.timeline, true,(ref.parent != null) ?
							this.mappedTweenedKeys[ref.parent.timeline].object(): root);
	        	}
	    	}
	    	tempTimelines.clear();
    	}
    		
    	this.tweenRefs(base, root);
    }
	
	private void tweenRefs(BoneRef base, Bone root){
    	int startIndex = base == null ? -1 : base.id-1;
    	int length = super.currentKey.boneRefs.size();
		for(int i = startIndex+1; i < length; i++){
			BoneRef ref = currentKey.boneRefs.get(i);
			if(ref.parent != base) continue;
			this.update(ref, root, 0);
			this.tweenRefs(ref, root);
		}
		for(ObjectRef ref: super.currentKey.objectRefs){
			if(ref.parent == base)
				this.update(ref, root, 0);
		}
	}
	
	@Override
	protected void update(BoneRef ref, Bone root, int time){
    	boolean isObject = ref instanceof ObjectRef;
		//Tween bone/object
    	Bone bone1 = null, bone2 = null, tweenTarget = null;
    	Timeline t1 = onFirstMainLine() ? anim1.getTimeline(ref.timeline) : anim1.getSimilarTimeline(anim2.getTimeline(ref.timeline));
    	Timeline t2 = onFirstMainLine() ? anim2.getSimilarTimeline(t1) : anim2.getTimeline(ref.timeline);
    	Timeline targetTimeline = super.getTimeline(onFirstMainLine() ? t1.id:t2.id);
    	if(t1 != null) bone1 = anim1.tweenedKeys[t1.id].object();
    	if(t2 != null) bone2 = anim2.tweenedKeys[t2.id].object();
    	if(targetTimeline != null) tweenTarget = this.tweenedKeys[targetTimeline.id].object();
    	if(isObject){
    		if(!onFirstMainLine()) bone1 = bone2;
    		else bone2 = bone1;
    	}
		if(bone2 != null && tweenTarget != null && bone1 != null){
			if(isObject) this.tweenObject((Object)bone1, (Object)bone2, (Object)tweenTarget, this.weight, this.curve);
			else this.tweenBone(bone1, bone2, tweenTarget, this.weight, this.curve);
			this.mappedTweenedKeys[targetTimeline.id].active = true;
		}
		//Transform the bone relative to the parent bone or the root
		if(this.mappedTweenedKeys[ref.timeline].active){
			this.unmapTimelineObject(targetTimeline.id, isObject,(ref.parent != null) ?
					this.mappedTweenedKeys[ref.parent.timeline].object(): root);
		}
    }
	protected void tweenObject(Object object1, Object object2, Object target, float t, SpriterCurve curve){
		this.tweenBone(object1, object2, target, t, curve);
		target.alpha = curve.tweenAngle(object1.alpha, object2.alpha, t);
		target.ref.set(onFirstMainLine() ? object1.ref: object2.ref);
	}
	
	public boolean onFirstMainLine(){
		return this.weight < this.spriteThreshold;
	}
	
	private void setUpTimelines(){
		Animation maxAnim = entity.getMaxAnimationTimelines();
		int max = maxAnim.timelines();
		for(int i = 0; i < max; i++){
			Timeline t = new Timeline(i, maxAnim.getTimeline(i).name, maxAnim.getTimeline(i).objectInfo);
			addTimeline(t);
		}
		prepare();
	}
	
	public void setAnimations(Animation animation1, Animation animation2){
		boolean areInterpolated = animation1 instanceof InterpolatedAnimation || animation2 instanceof InterpolatedAnimation;
		if(animation1 == anim1 && animation2 == anim2) return;
		if((!this.entity.containsAnimation(animation1) || !this.entity.containsAnimation(animation2)) && !areInterpolated)
			throw new SpriterException("Both animations have to be part of the same entity!");
		this.anim1 = animation1;
		this.anim2 = animation2;
	}
	
	public Animation getFirstAnimation(){
		return this.anim1;
	}
	
	public Animation getSecondAnimation(){
		return this.anim2;
	}

}
