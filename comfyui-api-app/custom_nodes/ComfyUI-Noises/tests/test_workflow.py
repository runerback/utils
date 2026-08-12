"""Validate the example workflow JSON."""

import json
from pathlib import Path

import sys
import types
import importlib.util

pkg_dir = Path(__file__).parent.parent
comfy_root = pkg_dir.parent.parent
sys.path.insert(0, str(comfy_root))
sys.path.insert(0, str(pkg_dir.parent))
sys.path.insert(0, str(pkg_dir))

import noise.sources as sources
import noise.effects as effects

pkg = types.ModuleType("ComfyUI_Noises")
pkg.__path__ = [str(pkg_dir)]
sys.modules["ComfyUI_Noises"] = pkg

noise_pkg = types.ModuleType("ComfyUI_Noises.noise")
noise_pkg.sources = sources
noise_pkg.effects = effects
sys.modules["ComfyUI_Noises.noise"] = noise_pkg
sys.modules["ComfyUI_Noises.noise.sources"] = sources
sys.modules["ComfyUI_Noises.noise.effects"] = effects

spec = importlib.util.spec_from_file_location("ComfyUI_Noises.nodes", pkg_dir / "nodes.py")
nodes_mod = importlib.util.module_from_spec(spec)
sys.modules["ComfyUI_Noises.nodes"] = nodes_mod
spec.loader.exec_module(nodes_mod)


def _known_node_ids():
    extension = nodes_mod.ComfyUINoisesExtension()
    import asyncio

    async def _load():
        return await extension.get_node_list()

    node_list = asyncio.run(_load())
    return {node_cls.define_schema().node_id for node_cls in node_list}


KNOWN_NODE_IDS = _known_node_ids()


def test_workflow_structure():
    workflow_path = Path(__file__).parent / "brown_noise_10s.json"
    assert workflow_path.exists(), f"workflow file not found: {workflow_path}"

    with workflow_path.open("r", encoding="utf-8") as f:
        workflow = json.load(f)

    assert isinstance(workflow, dict)
    assert len(workflow) > 0

    for node_id, node in workflow.items():
        assert "class_type" in node, f"node {node_id} missing class_type"
        assert "inputs" in node, f"node {node_id} missing inputs"
        assert isinstance(node["inputs"], dict)


def test_workflow_node_types():
    workflow_path = Path(__file__).parent / "brown_noise_10s.json"
    with workflow_path.open("r", encoding="utf-8") as f:
        workflow = json.load(f)

    node_classes = []
    for name in dir(nodes_mod):
        obj = getattr(nodes_mod, name, None)
        if (
            isinstance(obj, type)
            and issubclass(obj, nodes_mod.io.ComfyNode)
            and obj is not nodes_mod.io.ComfyNode
        ):
            node_classes.append(obj)
    node_schemas = {cls.define_schema().node_id: cls.define_schema() for cls in node_classes}

    for node_id, node in workflow.items():
        class_type = node["class_type"]
        if class_type in KNOWN_NODE_IDS:
            assert class_type in node_schemas, f"schema not found for {class_type}"


def test_workflow_references_valid():
    workflow_path = Path(__file__).parent / "brown_noise_10s.json"
    with workflow_path.open("r", encoding="utf-8") as f:
        workflow = json.load(f)

    for node_id, node in workflow.items():
        for input_name, value in node["inputs"].items():
            if isinstance(value, list) and len(value) == 2 and isinstance(value[0], str):
                ref_id, ref_index = value
                assert ref_id in workflow, f"node {node_id} references unknown node {ref_id}"
                assert ref_index == 0, f"node {node_id} references non-zero output index"


def test_workflow_duration():
    workflow_path = Path(__file__).parent / "brown_noise_10s.json"
    with workflow_path.open("r", encoding="utf-8") as f:
        workflow = json.load(f)

    source = next(
        node for node in workflow.values() if node["class_type"] == "NoisesNoiseSource"
    )
    assert source["inputs"]["length_seconds"] == 10.0


if __name__ == "__main__":
    test_workflow_structure()
    print("test_workflow_structure passed")
    test_workflow_node_types()
    print("test_workflow_node_types passed")
    test_workflow_references_valid()
    print("test_workflow_references_valid passed")
    test_workflow_duration()
    print("test_workflow_duration passed")
    print("All workflow tests passed.")
