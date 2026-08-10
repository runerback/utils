package com.runerback.openposeeditor.skeleton

import android.graphics.Color
import org.joml.Vector3f

class ProceduralSkeleton : Skeleton {
    override val keypoints = mutableListOf<Keypoint>()
    override val bones = mutableListOf<Bone>()

    private var nextId = 0

    init {
        buildBody()
        buildLeftHand()
        buildRightHand()
        buildLeftFoot()
        buildRightFoot()
        buildFace()
    }

    private fun buildBody() {
        val group = KeypointGroup.BODY
        val c = PoseColors.body

        val nose = add("nose", group, c.nose, Vector3f(0f, 1.75f, 0f))
        val neck = add("neck", group, c.neck, Vector3f(0f, -0.12f, 0f), nose)
        val leftShoulder = add("left_shoulder", group, c.shoulder, Vector3f(-0.22f, -0.08f, 0f), neck)
        val rightShoulder = add("right_shoulder", group, c.shoulder, Vector3f(0.22f, -0.08f, 0f), neck)
        val leftElbow = add("left_elbow", group, c.elbow, Vector3f(-0.28f, -0.32f, 0f), leftShoulder)
        val rightElbow = add("right_elbow", group, c.elbow, Vector3f(0.28f, -0.32f, 0f), rightShoulder)
        val leftWrist = add("left_wrist", group, c.wrist, Vector3f(-0.30f, -0.58f, 0f), leftElbow)
        val rightWrist = add("right_wrist", group, c.wrist, Vector3f(0.30f, -0.58f, 0f), rightElbow)

        val spine = add("spine", group, c.spine, Vector3f(0f, -0.18f, 0f), neck)
        val leftHip = add("left_hip", group, c.hip, Vector3f(-0.10f, -0.22f, 0f), spine)
        val rightHip = add("right_hip", group, c.hip, Vector3f(0.10f, -0.22f, 0f), spine)
        val leftKnee = add("left_knee", group, c.knee, Vector3f(-0.12f, -0.55f, 0f), leftHip)
        val rightKnee = add("right_knee", group, c.knee, Vector3f(0.12f, -0.55f, 0f), rightHip)
        val leftAnkle = add("left_ankle", group, c.ankle, Vector3f(-0.12f, -0.90f, 0f), leftKnee)
        val rightAnkle = add("right_ankle", group, c.ankle, Vector3f(0.12f, -0.90f, 0f), rightKnee)

        val faceGroup = KeypointGroup.FACE
        val faceColor = PoseColors.face
        val leftEye = add("left_eye", faceGroup, faceColor.eye, Vector3f(-0.06f, 0.07f, 0.04f), nose)
        val rightEye = add("right_eye", faceGroup, faceColor.eye, Vector3f(0.06f, 0.07f, 0.04f), nose)
        val leftEar = add("left_ear", faceGroup, faceColor.ear, Vector3f(-0.06f, -0.02f, -0.04f), leftEye)
        val rightEar = add("right_ear", faceGroup, faceColor.ear, Vector3f(0.06f, -0.02f, -0.04f), rightEye)

        connect(nose, neck)
        connect(neck, leftShoulder)
        connect(neck, rightShoulder)
        connect(leftShoulder, leftElbow)
        connect(rightShoulder, rightElbow)
        connect(leftElbow, leftWrist)
        connect(rightElbow, rightWrist)
        connect(neck, spine)
        connect(spine, leftHip)
        connect(spine, rightHip)
        connect(leftHip, leftKnee)
        connect(rightHip, rightKnee)
        connect(leftKnee, leftAnkle)
        connect(rightKnee, rightAnkle)
    }

    private fun buildLeftHand() {
        val wrist = keypointByName("left_wrist") ?: return
        val group = KeypointGroup.LEFT_HAND
        val c = PoseColors.leftHand
        buildHand(wrist.id, group, c, -1f)
    }

    private fun buildRightHand() {
        val wrist = keypointByName("right_wrist") ?: return
        val group = KeypointGroup.RIGHT_HAND
        val c = PoseColors.rightHand
        buildHand(wrist.id, group, c, 1f)
    }

    private fun buildHand(wristId: Int, group: KeypointGroup, c: HandColors, side: Float) {
        val wrist = keypointById(wristId) ?: return
        val palm = add("${group.name.lowercase()}_palm", group, c.palm, Vector3f(side * 0.04f, -0.02f, 0.02f), wrist)

        for (fingerIndex in 0 until 5) {
            val fingerName = when (fingerIndex) {
                0 -> "thumb"
                1 -> "index"
                2 -> "middle"
                3 -> "ring"
                else -> "pinky"
            }
            val baseX = side * (0.02f + fingerIndex * 0.015f)
            val baseY = -0.02f + (if (fingerIndex == 0) 0.02f else 0f)
            val mcp = add("${group.name.lowercase()}_${fingerName}_mcp", group, c.mcp, Vector3f(baseX, baseY, 0.01f), palm)
            val pip = add("${group.name.lowercase()}_${fingerName}_pip", group, c.pip, Vector3f(side * 0.01f, -0.02f, 0f), mcp)
            val dip = add("${group.name.lowercase()}_${fingerName}_dip", group, c.dip, Vector3f(side * 0.01f, -0.02f, 0f), pip)
            val tip = add("${group.name.lowercase()}_${fingerName}_tip", group, c.tip, Vector3f(side * 0.01f, -0.02f, 0f), dip)
            connect(mcp, pip)
            connect(pip, dip)
            connect(dip, tip)
        }
        connect(wrist, palm)
    }

    private fun buildLeftFoot() {
        val ankle = keypointByName("left_ankle") ?: return
        val group = KeypointGroup.LEFT_FOOT
        val c = PoseColors.leftFoot
        val bigToe = add("left_big_toe", group, c.bigToe, Vector3f(0f, -0.06f, 0.08f), ankle)
        val smallToe = add("left_small_toe", group, c.smallToe, Vector3f(-0.04f, -0.06f, 0.06f), ankle)
        val heel = add("left_heel", group, c.heel, Vector3f(0f, -0.04f, -0.06f), ankle)
        connect(ankle, bigToe)
        connect(ankle, smallToe)
        connect(ankle, heel)
    }

    private fun buildRightFoot() {
        val ankle = keypointByName("right_ankle") ?: return
        val group = KeypointGroup.RIGHT_FOOT
        val c = PoseColors.rightFoot
        val bigToe = add("right_big_toe", group, c.bigToe, Vector3f(0f, -0.06f, 0.08f), ankle)
        val smallToe = add("right_small_toe", group, c.smallToe, Vector3f(0.04f, -0.06f, 0.06f), ankle)
        val heel = add("right_heel", group, c.heel, Vector3f(0f, -0.04f, -0.06f), ankle)
        connect(ankle, bigToe)
        connect(ankle, smallToe)
        connect(ankle, heel)
    }

    private fun buildFace() {
        val nose = keypointByName("nose") ?: return
        val group = KeypointGroup.FACE
        val c = PoseColors.face

        val faceRoot = add("face_root", group, c.face, Vector3f(0f, 0.02f, 0.06f), nose)
        val leftEyeCenter = add("face_left_eye", group, c.eye, Vector3f(-0.04f, 0.02f, 0.02f), faceRoot)
        val rightEyeCenter = add("face_right_eye", group, c.eye, Vector3f(0.04f, 0.02f, 0.02f), faceRoot)
        val mouthCenter = add("face_mouth", group, c.mouth, Vector3f(0f, -0.05f, 0.02f), faceRoot)
        val chin = add("face_chin", group, c.chin, Vector3f(0f, -0.10f, 0f), faceRoot)
        val leftBrow = add("face_left_brow", group, c.brow, Vector3f(-0.05f, 0.04f, 0.01f), faceRoot)
        val rightBrow = add("face_right_brow", group, c.brow, Vector3f(0.05f, 0.04f, 0.01f), faceRoot)

        connect(faceRoot, leftEyeCenter)
        connect(faceRoot, rightEyeCenter)
        connect(faceRoot, mouthCenter)
        connect(faceRoot, chin)
        connect(faceRoot, leftBrow)
        connect(faceRoot, rightBrow)
    }

    private fun add(
        name: String,
        group: KeypointGroup,
        color: Int,
        localPos: Vector3f,
        parent: Keypoint? = null,
    ): Keypoint {
        val kp = Keypoint(
            id = nextId++,
            name = name,
            group = group,
            parentId = parent?.id,
            color = color,
            restLocalPosition = localPos,
        )
        keypoints.add(kp)
        return kp
    }

    private fun connect(from: Keypoint, to: Keypoint, color: Int = to.color) {
        bones.add(Bone(from.id, to.id, color))
    }

    private fun keypointByName(name: String): Keypoint? = keypoints.find { it.name == name }
}

private object PoseColors {
    val body = BodyColors(
        nose = Color.rgb(255, 0, 85),
        neck = Color.rgb(85, 0, 255),
        shoulder = Color.rgb(255, 170, 0),
        elbow = Color.rgb(170, 255, 0),
        wrist = Color.rgb(0, 85, 255),
        spine = Color.rgb(170, 0, 255),
        hip = Color.rgb(255, 0, 170),
        knee = Color.rgb(0, 255, 85),
        ankle = Color.rgb(85, 255, 0),
    )
    val leftHand = HandColors(
        palm = Color.rgb(0, 128, 255),
        mcp = Color.rgb(0, 170, 255),
        pip = Color.rgb(0, 200, 255),
        dip = Color.rgb(0, 230, 255),
        tip = Color.rgb(0, 255, 255),
    )
    val rightHand = HandColors(
        palm = Color.rgb(255, 128, 0),
        mcp = Color.rgb(255, 170, 0),
        pip = Color.rgb(255, 200, 0),
        dip = Color.rgb(255, 230, 0),
        tip = Color.rgb(255, 255, 0),
    )
    val leftFoot = FootColors(
        bigToe = Color.rgb(0, 255, 128),
        smallToe = Color.rgb(0, 255, 170),
        heel = Color.rgb(0, 255, 85),
    )
    val rightFoot = FootColors(
        bigToe = Color.rgb(255, 0, 128),
        smallToe = Color.rgb(255, 0, 170),
        heel = Color.rgb(255, 0, 85),
    )
    val face = FaceColors(
        face = Color.rgb(255, 192, 203),
        eye = Color.rgb(0, 255, 170),
        ear = Color.rgb(0, 170, 255),
        mouth = Color.rgb(255, 20, 147),
        chin = Color.rgb(219, 112, 147),
        brow = Color.rgb(199, 21, 133),
    )
}

private data class BodyColors(
    val nose: Int, val neck: Int,
    val shoulder: Int, val elbow: Int, val wrist: Int, val spine: Int,
    val hip: Int, val knee: Int, val ankle: Int,
)
private data class HandColors(val palm: Int, val mcp: Int, val pip: Int, val dip: Int, val tip: Int)
private data class FootColors(val bigToe: Int, val smallToe: Int, val heel: Int)
private data class FaceColors(val face: Int, val eye: Int, val ear: Int, val mouth: Int, val chin: Int, val brow: Int)
