package com.totomarujapan.crowavatar

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CrowRigTest {
    private val layers = AvatarAssets.load(RuntimeEnvironment.getApplication())
    private val renderer = CrowRenderer(layers)
    private fun render(pose: FacePose = FacePose(), seconds: Double = 0.0): Bitmap =
        Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888).also {
            val c=Canvas(it); c.drawColor(Color.rgb(7,9,13)); renderer.draw(c,pose,seconds)
        }
    private fun same(a: Bitmap,b: Bitmap,r: Rect) {
        for(y in r.top until r.bottom) for(x in r.left until r.right)
            assertEquals("pixel $x,$y",a.getPixel(x,y),b.getPixel(x,y))
    }
    private fun bright(b: Bitmap,r: Rect): Int {
        var count=0
        for(y in r.top until r.bottom) for(x in r.left until r.right) {
            val c=b.getPixel(x,y)
            if(Color.red(c)>145 && Color.green(c)>95) count++
        }
        return count
    }
    @Test fun eyesCloseIndependentlyAndLeaveNoWhiteOrIris() {
        val base=render()
        val left=render(FacePose(blinkLeft=1f))
        val right=render(FacePose(blinkRight=1f))
        val both=render(FacePose(blinkLeft=1f,blinkRight=1f))
        val l=Rect(277,176,339,221); val r=Rect(164,176,222,221)
        assertTrue(bright(base,l)>50); assertTrue(bright(base,r)>50)
        same(base,left,r); same(base,right,l)
        assertEquals("left iris/white leak",0,bright(left,l))
        assertEquals("right iris/white leak",0,bright(right,r))
        assertEquals(0,bright(both,l)); assertEquals(0,bright(both,r))
    }
    @Test fun mouthDoesNotChangeUpperBeakEyesHairOrShoulders() {
        val base=render(); val open=render(FacePose(jaw=1f))
        same(base,open,Rect(10,0,470,235)) // all hair and eyes
        same(base,open,Rect(224,244,276,271)) // fixed upper beak interior
        same(base,open,Rect(0,350,480,480)) // torso
        var changed=0
        for(y in 288..344) for(x in 230..274) if(base.getPixel(x,y)!=open.getPixel(x,y)) changed++
        assertTrue("lower beak must move",changed>50)
        assertTrue("interior is opaque behind upper/lower",Color.alpha(open.getPixel(251,300))==255)
    }
    @Test fun bodyNeverFollowsHeadAndHingeKeepsMouthCentered() {
        val base=render()
        listOf(FacePose(yaw=1f),FacePose(yaw=-1f),FacePose(pitch=1f),FacePose(pitch=-1f),
            FacePose(roll=1f),FacePose(roll=-1f)).forEach {
            val b=render(it)
            same(base,b,Rect(0,350,130,480)); same(base,b,Rect(370,350,480,480))
        }
        for(i in 0..10) {
            val point=floatArrayOf(251f,315f)
            renderer.beakTransform(i/10f).mapPoints(point)
            assertEquals(251f,point[0],0.01f)
            if(i>0) assertTrue("lower tip opens down",point[1]>315f)
        }
    }
    @Test fun nativeRenderGalleryAndThirtySecondRigExercise() {
        val directory=File("build/reports/rig-preview").apply { mkdirs() }
        val poses=linkedMapOf("neutral" to FacePose(),"left-eye" to FacePose(blinkLeft=1f),
            "right-eye" to FacePose(blinkRight=1f),"both-eyes" to FacePose(blinkLeft=1f,blinkRight=1f),
            "half-blink" to FacePose(blinkLeft=0.4f,blinkRight=0.4f),
            "half-mouth" to FacePose(jaw=0.4f),"open-mouth" to FacePose(jaw=1f),
            "yaw-left" to FacePose(yaw=1f),"yaw-right" to FacePose(yaw=-1f),
            "pitch-up" to FacePose(pitch=1f),"pitch-down" to FacePose(pitch=-1f),
            "roll-left" to FacePose(roll=1f),"roll-right" to FacePose(roll=-1f),
            "combined" to FacePose(yaw=1f,pitch=1f,roll=1f,jaw=1f,blinkLeft=1f))
        poses.forEach { (name,pose) -> render(pose).useBitmap { bitmap ->
            File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        } }
        // Rendering endurance, not a substitute for the Galaxy camera/thermal test.
        for(frame in 0 until 720) {
            val t=frame/24.0
            render(FacePose(sin(t).toFloat(),cos(t).toFloat(),sin(t*0.3).toFloat(),
                (sin(t)+1).toFloat()/2f,if(frame%60<5) 1f else 0f,if(frame%71<5) 1f else 0f),t).recycle()
        }
        File(directory,"layer-head.png").outputStream().use { layers.head.compress(Bitmap.CompressFormat.PNG,100,it) }
        File(directory,"layer-body.png").outputStream().use { layers.body.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun Bitmap.useBitmap(block:(Bitmap)->Unit) { try { block(this) } finally { recycle() } }
    @Test fun columnMajorRotationAndBlendMapping() {
        val identity=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)
        assertArrayEquals(floatArrayOf(0f,0f,0f),FaceRotation.radians(identity),0.001f)
        val y=0.3f; val m=identity.copyOf()
        m[0]=cos(y);m[2]=-sin(y);m[8]=sin(y);m[10]=cos(y)
        assertEquals(y,FaceRotation.radians(m)[0],0.001f)
        assertEquals(1f,RigMotion.closure(0.72f),0.001f)
        assertEquals(0f,RigMotion.closure(0.08f),0.001f)
    }
}
