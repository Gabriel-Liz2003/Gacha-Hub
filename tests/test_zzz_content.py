import json, pathlib, unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class ZzzContentTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pack = json.loads((ROOT / "content/starter.json").read_text(encoding="utf-8"))
        cls.chars = [c for c in cls.pack["characters"] if c["game"] == "ZZZ"]
        cls.costs = [c for c in cls.pack["costs"] if c["characterId"].startswith("zzz:")]

    def test_live_cutoff_roster_and_stable_ids(self):
        self.assertEqual(59, len(self.chars))
        self.assertEqual(58, len([c for c in self.chars if c["providerId"]]))
        self.assertEqual("zzz:ellen", next(c["id"] for c in self.chars if c["providerId"] == "1191"))
        self.assertNotIn("Roxy", [c["name"] for c in self.chars])

    def test_exact_progression_tracks_have_unit_edges(self):
        covered = {c["characterId"] for c in self.costs}
        self.assertEqual(58, len(covered))
        for cid in covered:
            rows = [c for c in self.costs if c["characterId"] == cid]
            self.assertEqual(59, len([c for c in rows if c["track"] == "EXP de nível (sem Denny)"]))
            self.assertEqual(59 + 5 * 11 + 5, len(rows) - len([c for c in rows if c["track"] == "Core"]))
            for row in rows:
                self.assertEqual(row["from"] + 1, row["to"])
                self.assertTrue(all(isinstance(n, int) and n > 0 for n in row["costs"].values()))

    def test_core_partial_coverage_is_explicit(self):
        core = [c for c in self.costs if c["track"] == "Core"]
        self.assertEqual(26 * 6, len(core))
        self.assertIn("Core para 26", self.pack["coverage"])
        self.assertIn("custos e Provider ID ainda não verificados", self.pack["coverage"])

    def test_build_equipment_references_are_not_orphans(self):
        engines = {x["id"] for x in self.pack["wEngines"]}
        discs = {x["id"] for x in self.pack["driveDiscs"]}
        zzz = {x["id"] for x in self.chars}
        builds = [b for b in self.pack["builds"] if b["characterId"] in zzz]
        self.assertEqual(59, len(builds))
        for build in builds:
            self.assertTrue(set(build.get("wEngineIds", [])) <= engines)
            self.assertTrue(set(build.get("driveDiscIds", [])) <= discs)

    def test_snapshot_and_pack_limits(self):
        self.assertLess((ROOT / "content/starter.json").stat().st_size, 8 * 1024 * 1024)
        for key in ("characters", "materials", "costs", "builds", "teams", "wEngines", "driveDiscs"):
            for row in self.pack[key]:
                self.assertLess(len(json.dumps(row, ensure_ascii=False).encode("utf-8")), 256 * 1024)

    def test_sources_are_pinned_and_licensed_snapshot_present(self):
        snapshot = json.loads((ROOT / "content/sources/zzz-optimizer.json").read_text(encoding="utf-8"))
        self.assertEqual("180a0a1015cb725c7570cd828eb086749960df98", snapshot["sha"])
        self.assertTrue((ROOT / "content/sources/zzz-optimizer-LICENSE.txt").read_text(encoding="utf-8").startswith("MIT License"))
        for row in self.chars:
            self.assertTrue(row["sources"])
            self.assertTrue(all(s["url"].startswith("https://") for s in row["sources"]))

if __name__ == "__main__":
    unittest.main()
