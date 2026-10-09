package com.totomarujapan.crowavatar

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class EyeInputTest {
    private fun eye(height: Float) = MutableList(478) { EyePoint(0f,0f,0f) }.apply {
        val ids=EyeInputGeometry.LEFT
        this[ids[0]]=EyePoint(-0.5f,0f,0f); this[ids[1]]=EyePoint(0.5f,0f,0f)
        for (i in 2 until ids.size step 2) {
            val x=(i-4)*0.10f
            this[ids[i]]=EyePoint(x,height/2,0f); this[ids[i+1]]=EyePoint(x,-height/2,0f)
        }
    }
    private fun rotate(v: EyePoint,yaw: Float,pitch: Float,roll: Float): EyePoint {
        val x1=v.x*cos(yaw)+v.z*sin(yaw); val z1=-v.x*sin(yaw)+v.z*cos(yaw)
        val y2=v.y*cos(pitch)-z1*sin(pitch); val z2=v.y*sin(pitch)+z1*cos(pitch)
        return EyePoint(x1*cos(roll)-y2*sin(roll),x1*sin(roll)+y2*cos(roll),z2)
    }
    @Test fun apertureIsInvariantAcrossYawPitchRollTranslationAndScale() {
        for (yaw in listOf(-0.8f,0f,0.8f)) for(pitch in listOf(-0.6f,0f,0.6f))
            for(roll in listOf(-0.5f,0f,0.5f)) {
                val posed=eye(0.28f).map { rotate(it,yaw,pitch,roll).let { p ->
                    EyePoint(p.x*0.15f+0.5f,p.y*0.15f+0.4f,p.z*0.15f-0.2f)
                } }
                assertEquals(0.28f,requireNotNull(EyeInputGeometry.aperture(posed,EyeInputGeometry.LEFT)),0.0001f)
            }
    }
    @Test fun openEyesRejectFalseBlinkScoresWhenHeadMoves() {
        val filter=EyeBlinkInput(); filter.reset(0.28f)
        // Open-eye landmark estimate drifts 0-15%, while ML blink varies widely.
        for(raw in listOf(0.15f,0.4f,0.6f,0.85f)) for(ratio in listOf(0.28f,0.26f,0.24f))
            assertEquals(0f,filter.update(raw,ratio,allowCalibration=false),0.0001f)
    }
    @Test fun winksRemainIndependentAndHeldClosedDoesNotRecalibrate() {
        val left=EyeBlinkInput(); val right=EyeBlinkInput()
        left.reset(0.28f);right.reset(0.24f)
        repeat(750) {
            assertTrue(RigMotion.closure(left.update(0.65f,0.04f))>0.999f)
            assertEquals(0f,right.update(0.5f,0.24f),0.0001f)
        }
        assertEquals(0f,left.update(0.6f,0.28f),0.0001f)
        assertTrue(RigMotion.closure(right.update(0.9f,0.035f))>0.999f)
    }
    @Test fun partialBlinkIsContinuousAndDoesNotMergeTheTwoEyes() {
        val filter=EyeBlinkInput();filter.reset(0.28f)
        val open=filter.update(0.1f,0.28f)
        val half=filter.update(0.5f,0.18f)
        val shut=filter.update(0.8f,0.035f)
        assertEquals(0f,open,0.0001f);assertTrue(half>open && half<shut)
    }
    @Test fun calibrationDoesNotLearnOutliersOrHeadPoseAsOpenEye() {
        val filter=EyeBlinkInput();filter.reset(0.28f)
        repeat(100) { filter.update(0.1f,0.4f) } // outlier must not inflate reference
        assertEquals(0f,filter.update(0.5f,0.28f),0.0001f)
        repeat(100) { filter.update(0.1f,0.19f,allowCalibration=false) }
        assertTrue(filter.update(0.8f,0.06f)>0.72f)
        filter.reset(0.04f) // cannot calibrate a closed wink as open
        assertTrue(filter.update(0.8f,0.04f)>0.72f)
    }
    @Test fun degenerateLandmarksAndExtremePoseDoNotLeaveHalfClosedEyes() {
        val filter=EyeBlinkInput();filter.reset(0.28f)
        assertEquals(0f,filter.update(0.7f,null),0.0001f)
        assertEquals(0f,filter.update(0.7f,0.04f,poseReliable=false),0.0001f)
        assertNull(EyeInputGeometry.aperture(emptyList(),EyeInputGeometry.LEFT))
        assertNull(EyeInputGeometry.aperture(MutableList(478) { EyePoint(0f,0f,0f) },EyeInputGeometry.LEFT))
    }
}
