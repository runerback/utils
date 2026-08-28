from pathlib import Path


def test_web_dist_index_exists():
    dist = Path(__file__).parents[2] / "web" / "dist" / "index.html"
    assert dist.exists()
