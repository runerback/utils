package com.runerback.openposeeditor.export

import com.runerback.openposeeditor.render.Camera
import com.runerback.openposeeditor.skeleton.KeypointGroup
import com.runerback.openposeeditor.skeleton.Skeleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OpenPoseExporter : KeypointExporter {

    private val json = Json { prettyPrint = true }

    override fun export(skeleton: Skeleton, camera: Camera, width: Int, height: Int): String {
        val positions = skeleton.computeWorldPositions()
        val keypointMap = skeleton.keypoints.associateBy { it.name }

        val body = bodyKeypointNames.map { name ->
            keypointToTriplet(keypointMap[name], positions, width, height, camera)
        }.flatten()

        val leftHand = leftHandKeypointNames.map { name ->
            keypointToTriplet(keypointMap[name], positions, width, height, camera)
        }.flatten()

        val rightHand = rightHandKeypointNames.map { name ->
            keypointToTriplet(keypointMap[name], positions, width, height, camera)
        }.flatten()

        val face = faceKeypointNames.map { name ->
            keypointToTriplet(keypointMap[name], positions, width, height, camera)
        }.flatten()

        val data = OpenPoseJson(
            people = listOf(
                Person(
                    poseKeypoints2d = body,
                    faceKeypoints2d = face,
                    handLeftKeypoints2d = leftHand,
                    handRightKeypoints2d = rightHand,
                )
            )
        )
        return json.encodeToString(data)
    }

    fun applyOffset(jsonString: String, offsetX: Float, offsetY: Float): String {
        if (offsetX == 0f && offsetY == 0f) return jsonString
        val data = json.decodeFromString<OpenPoseJson>(jsonString)
        val shifted = data.copy(
            people = data.people.map { person ->
                person.copy(
                    poseKeypoints2d = shiftTriplets(person.poseKeypoints2d, offsetX, offsetY),
                    faceKeypoints2d = shiftTriplets(person.faceKeypoints2d, offsetX, offsetY),
                    handLeftKeypoints2d = shiftTriplets(person.handLeftKeypoints2d, offsetX, offsetY),
                    handRightKeypoints2d = shiftTriplets(person.handRightKeypoints2d, offsetX, offsetY),
                )
            }
        )
        return json.encodeToString(shifted)
    }

    private fun shiftTriplets(triplets: List<Float>, dx: Float, dy: Float): List<Float> {
        val result = mutableListOf<Float>()
        for (i in triplets.indices.step(3)) {
            result.add(triplets.getOrElse(i) { 0f } + dx)
            result.add(triplets.getOrElse(i + 1) { 0f } + dy)
            result.add(triplets.getOrElse(i + 2) { 0f })
        }
        return result
    }

    private fun keypointToTriplet(
        keypoint: com.runerback.openposeeditor.skeleton.Keypoint?,
        positions: Map<Int, org.joml.Vector3f>,
        width: Int,
        height: Int,
        camera: Camera,
    ): List<Float> {
        if (keypoint == null || !keypoint.enabled) return listOf(0f, 0f, 0f)
        val pos = positions[keypoint.id] ?: return listOf(0f, 0f, 0f)
        val (x, y) = camera.project(pos, width, height)
        return listOf(x, y, 1f)
    }

    companion object {
        val bodyKeypointNames = listOf(
            "nose",
            "left_eye", "right_eye", "left_ear", "right_ear",
            "left_shoulder", "right_shoulder",
            "left_elbow", "right_elbow",
            "left_wrist", "right_wrist",
            "left_hip", "right_hip",
            "left_knee", "right_knee",
            "left_ankle", "right_ankle",
        )

        val leftHandKeypointNames = listOf(
            "left_hand_palm",
            "left_hand_thumb_mcp", "left_hand_thumb_pip", "left_hand_thumb_dip", "left_hand_thumb_tip",
            "left_hand_index_mcp", "left_hand_index_pip", "left_hand_index_dip", "left_hand_index_tip",
            "left_hand_middle_mcp", "left_hand_middle_pip", "left_hand_middle_dip", "left_hand_middle_tip",
            "left_hand_ring_mcp", "left_hand_ring_pip", "left_hand_ring_dip", "left_hand_ring_tip",
            "left_hand_pinky_mcp", "left_hand_pinky_pip", "left_hand_pinky_dip", "left_hand_pinky_tip",
        )

        val rightHandKeypointNames = listOf(
            "right_hand_palm",
            "right_hand_thumb_mcp", "right_hand_thumb_pip", "right_hand_thumb_dip", "right_hand_thumb_tip",
            "right_hand_index_mcp", "right_hand_index_pip", "right_hand_index_dip", "right_hand_index_tip",
            "right_hand_middle_mcp", "right_hand_middle_pip", "right_hand_middle_dip", "right_hand_middle_tip",
            "right_hand_ring_mcp", "right_hand_ring_pip", "right_hand_ring_dip", "right_hand_ring_tip",
            "right_hand_pinky_mcp", "right_hand_pinky_pip", "right_hand_pinky_dip", "right_hand_pinky_tip",
        )

        val faceKeypointNames = listOf(
            "face_root", "face_left_eye", "face_right_eye", "face_mouth", "face_chin", "face_left_brow", "face_right_brow",
        )
    }
}

@Serializable
private data class OpenPoseJson(
    val version: Float = 1.3f,
    val people: List<Person>,
)

@Serializable
private data class Person(
    val poseKeypoints2d: List<Float>,
    val faceKeypoints2d: List<Float>,
    val handLeftKeypoints2d: List<Float>,
    val handRightKeypoints2d: List<Float>,
)
