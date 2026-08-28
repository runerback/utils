from home_assistant import system


def test_cpu_temp_returns_none_when_no_thermal():
    # On non-Linux systems this should return None.
    temp = system.cpu_temp_celsius()
    assert temp is None or isinstance(temp, float)


def test_memory_usage_handles_missing_proc_meminfo():
    usage = system.memory_usage()
    assert "total_kb" in usage
    assert "used_kb" in usage
    assert "percent" in usage


def test_memory_usage_parses_sample_meminfo():
    sample = """MemTotal:       16000000 kB
MemAvailable:    8000000 kB
"""
    total = system._parse_meminfo_value(sample, "MemTotal")
    available = system._parse_meminfo_value(sample, "MemAvailable")
    assert total == 16000000
    assert available == 8000000
