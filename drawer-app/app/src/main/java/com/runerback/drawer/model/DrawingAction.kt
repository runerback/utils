package com.runerback.drawer.model

sealed class DrawingAction {
    data class AddElement(
        val element: DrawingElement,
        val layerId: String
    ) : DrawingAction()

    data class CreateLayer(
        val layer: Layer
    ) : DrawingAction()

    data class DeleteLayer(
        val layer: Layer,
        val elements: Map<String, DrawingElement>
    ) : DrawingAction()

    data class MergeLayers(
        val removedLayerIds: List<String>,
        val targetLayerId: String,
        val previousElementIdsByLayer: Map<String, List<String>>,
        val previousSelectedLayerId: String
    ) : DrawingAction()

    data class MoveLayer(
        val layerId: String,
        val fromIndex: Int,
        val toIndex: Int
    ) : DrawingAction()

    data class Clear(
        val previousLayers: List<Layer>,
        val previousElements: Map<String, DrawingElement>,
        val previousSelectedLayerId: String
    ) : DrawingAction()
}
