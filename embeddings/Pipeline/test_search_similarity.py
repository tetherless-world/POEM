#!/usr/bin/env python3
"""Exhaustive test suite for the POEM embeddings search pipeline.

Two test classes:

  TestSimilarityMetrics  — pure unit tests for the math functions.
                           No embedding endpoint or .npy files required.

  TestSearchQueries      — integration tests that run real queries against
                           the stored .npy embeddings using every metric.
                           Auto-skipped if generate_embeddings.py has not
                           been run yet (instruments/ folder absent/empty).

Run all tests:
    python -m pytest embeddings/test_search_similarity.py -v

Run only unit tests (no endpoint needed):
    python -m pytest embeddings/test_search_similarity.py -v -k "TestSimilarityMetrics"

Run only integration tests:
    python -m pytest embeddings/test_search_similarity.py -v -k "TestSearchQueries"
"""

import os
import glob
import sys
import subprocess

import numpy as np
import pytest

# Make the embeddings package importable when running from the project root
_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, _HERE)

from search_similarity import (
    cosine_similarity,
    dot_product,
    euclidean_distance,
    manhattan_distance,
    load_embeddings,
    embed_query,
    METRICS,
)

# ---------------------------------------------------------------------------
# Helper: check whether .npy files have been generated
# ---------------------------------------------------------------------------

def _embeddings_available() -> bool:
    inst_dir = os.path.join(_HERE, "instruments")
    if not os.path.isdir(inst_dir):
        return False
    return len(glob.glob(os.path.join(inst_dir, "paragraph_*.npy"))) > 0


EMBEDDINGS_AVAILABLE = _embeddings_available()
SKIP_IF_NO_EMBEDDINGS = pytest.mark.skipif(
    not EMBEDDINGS_AVAILABLE,
    reason="Embeddings not generated yet. Run generate_embeddings.py first."
)


# ===========================================================================
# TestSimilarityMetrics — pure math, no endpoint required
# ===========================================================================

class TestSimilarityMetrics:
    """Verify correctness of all four similarity/distance functions."""

    # --- fixtures -----------------------------------------------------------

    @pytest.fixture
    def unit_x(self):
        """Unit vector along x-axis."""
        return np.array([1.0, 0.0, 0.0], dtype=np.float32)

    @pytest.fixture
    def unit_y(self):
        """Unit vector along y-axis (orthogonal to unit_x)."""
        return np.array([0.0, 1.0, 0.0], dtype=np.float32)

    @pytest.fixture
    def neg_x(self):
        """Unit vector opposite to unit_x."""
        return np.array([-1.0, 0.0, 0.0], dtype=np.float32)

    @pytest.fixture
    def matrix_3x3(self):
        """Small matrix with three rows: near, mid, far from [1,0,0]."""
        return np.array([
            [1.0, 0.0, 0.0],   # row 0 — identical to query
            [0.0, 1.0, 0.0],   # row 1 — orthogonal
            [-1.0, 0.0, 0.0],  # row 2 — opposite
        ], dtype=np.float32)

    # --- cosine similarity --------------------------------------------------

    def test_cosine_identical_vectors(self, unit_x):
        matrix = unit_x.reshape(1, -1)
        scores = cosine_similarity(unit_x, matrix)
        assert pytest.approx(scores[0], abs=1e-5) == 1.0

    def test_cosine_orthogonal_vectors(self, unit_x, unit_y):
        matrix = unit_y.reshape(1, -1)
        scores = cosine_similarity(unit_x, matrix)
        assert pytest.approx(scores[0], abs=1e-5) == 0.0

    def test_cosine_opposite_vectors(self, unit_x, neg_x):
        matrix = neg_x.reshape(1, -1)
        scores = cosine_similarity(unit_x, matrix)
        assert pytest.approx(scores[0], abs=1e-5) == -1.0

    def test_cosine_ranking_order(self, unit_x, matrix_3x3):
        scores = cosine_similarity(unit_x, matrix_3x3)
        ranked = np.argsort(scores)[::-1]
        assert ranked[0] == 0  # identical is top
        assert ranked[1] == 1  # orthogonal is second
        assert ranked[2] == 2  # opposite is last

    def test_cosine_output_shape(self, unit_x, matrix_3x3):
        scores = cosine_similarity(unit_x, matrix_3x3)
        assert scores.shape == (3,)

    def test_cosine_range(self):
        rng = np.random.default_rng(42)
        query = rng.random(128).astype(np.float32)
        matrix = rng.random((200, 128)).astype(np.float32)
        scores = cosine_similarity(query, matrix)
        assert scores.min() >= -1.0 - 1e-5
        assert scores.max() <=  1.0 + 1e-5

    # --- dot product --------------------------------------------------------

    def test_dot_product_value(self, unit_x):
        vec = np.array([[3.0, 0.0, 0.0]], dtype=np.float32)
        scores = dot_product(unit_x, vec)
        assert pytest.approx(scores[0], abs=1e-5) == 3.0

    def test_dot_product_zero(self, unit_x, unit_y):
        scores = dot_product(unit_x, unit_y.reshape(1, -1))
        assert pytest.approx(scores[0], abs=1e-5) == 0.0

    def test_dot_product_ranking(self, unit_x, matrix_3x3):
        scores = dot_product(unit_x, matrix_3x3)
        ranked = np.argsort(scores)[::-1]
        assert ranked[0] == 0  # identical highest dot product

    def test_dot_product_output_shape(self, unit_x, matrix_3x3):
        scores = dot_product(unit_x, matrix_3x3)
        assert scores.shape == (3,)

    # --- euclidean distance -------------------------------------------------

    def test_euclidean_identical_is_zero(self, unit_x):
        scores = euclidean_distance(unit_x, unit_x.reshape(1, -1))
        assert pytest.approx(scores[0], abs=1e-5) == 0.0  # -0 == 0

    def test_euclidean_returns_nonpositive(self, unit_x, matrix_3x3):
        scores = euclidean_distance(unit_x, matrix_3x3)
        assert (scores <= 0.0 + 1e-5).all()

    def test_euclidean_ranking_order(self, unit_x, matrix_3x3):
        scores = euclidean_distance(unit_x, matrix_3x3)
        ranked = np.argsort(scores)[::-1]
        assert ranked[0] == 0  # identical → distance 0 → highest negated score

    def test_euclidean_output_shape(self, unit_x, matrix_3x3):
        scores = euclidean_distance(unit_x, matrix_3x3)
        assert scores.shape == (3,)

    # --- manhattan distance -------------------------------------------------

    def test_manhattan_identical_is_zero(self, unit_x):
        scores = manhattan_distance(unit_x, unit_x.reshape(1, -1))
        assert pytest.approx(scores[0], abs=1e-5) == 0.0

    def test_manhattan_returns_nonpositive(self, unit_x, matrix_3x3):
        scores = manhattan_distance(unit_x, matrix_3x3)
        assert (scores <= 0.0 + 1e-5).all()

    def test_manhattan_ranking_order(self, unit_x, matrix_3x3):
        scores = manhattan_distance(unit_x, matrix_3x3)
        ranked = np.argsort(scores)[::-1]
        assert ranked[0] == 0  # identical → distance 0 → highest negated score

    def test_manhattan_output_shape(self, unit_x, matrix_3x3):
        scores = manhattan_distance(unit_x, matrix_3x3)
        assert scores.shape == (3,)

    # --- ranking consistency across all metrics ----------------------------

    def test_all_metrics_agree_on_top1(self, unit_x, matrix_3x3):
        """All four metrics should rank the identical vector first."""
        for name, fn in METRICS.items():
            scores = fn(unit_x, matrix_3x3)
            top1 = int(np.argmax(scores))
            assert top1 == 0, f"{name} did not rank identical vector first"


# ===========================================================================
# TestSearchQueries — integration tests against real stored embeddings
# ===========================================================================

@SKIP_IF_NO_EMBEDDINGS
class TestSearchQueries:
    """Run real queries against the stored .npy embeddings.

    Each test embeds a query, runs all four metrics, and asserts that the
    top cosine result falls in the expected section (instruments / scales /
    collections).  The queries use exact phrases, names, and item text that
    appear in templates_official.txt.
    """

    @pytest.fixture(scope="class")
    def corpus(self):
        """Load all embeddings once for the whole class."""
        embeddings, texts, sections = load_embeddings()
        return embeddings, texts, sections

    def _top_section(self, query: str, corpus) -> str:
        """Return the section name of the top cosine-similarity result."""
        embeddings, texts, sections = corpus
        query_vec = embed_query(query)
        scores = cosine_similarity(query_vec, embeddings)
        top_idx = int(np.argmax(scores))
        return str(sections[top_idx])

    def _top_sections(self, query: str, corpus, k: int = 3) -> list:
        """Return section names of the top-k cosine results."""
        embeddings, texts, sections = corpus
        query_vec = embed_query(query)
        scores = cosine_similarity(query_vec, embeddings)
        top_indices = np.argsort(scores)[::-1][:k]
        return [str(sections[i]) for i in top_indices]

    # -----------------------------------------------------------------------
    # Instruments — by code / name
    # -----------------------------------------------------------------------

    def test_gad7_returns_instrument(self, corpus):
        top = self._top_section("GAD-7 generalized anxiety questionnaire", corpus)
        assert top == "instruments"

    def test_rcads25_youth_returns_instrument(self, corpus):
        top = self._top_section("RCADS-25-Y-EN youth anxiety depression scale", corpus)
        assert top == "instruments"

    def test_phq9_returns_instrument(self, corpus):
        top = self._top_section("PHQ-9 patient health questionnaire depression adult", corpus)
        assert top == "instruments"

    def test_mtt_caregiver_returns_instrument(self, corpus):
        top = self._top_section("MTT-35-CG caregiver therapist relationship questionnaire", corpus)
        assert top == "instruments"

    def test_rcads47_returns_instrument(self, corpus):
        top = self._top_section("RCADS-47 child anxiety caregiver report full scale", corpus)
        assert top == "instruments"

    # -----------------------------------------------------------------------
    # Instruments — by informant or language
    # -----------------------------------------------------------------------

    def test_youth_self_report_returns_instrument(self, corpus):
        top = self._top_section("youth self-report anxiety measure", corpus)
        assert top == "instruments"

    def test_caregiver_version_returns_instrument(self, corpus):
        top = self._top_section("caregiver version psychometric questionnaire", corpus)
        assert top == "instruments"

    def test_spanish_language_returns_instrument(self, corpus):
        top = self._top_section("Spanish language anxiety instrument ES", corpus)
        assert top == "instruments"

    def test_norwegian_language_returns_instrument(self, corpus):
        top = self._top_section("Norwegian psychometric questionnaire NO", corpus)
        assert top == "instruments"

    # -----------------------------------------------------------------------
    # Scales
    # -----------------------------------------------------------------------

    def test_social_phobia_returns_scale(self, corpus):
        top = self._top_section("Social Phobia scale score questionnaire", corpus)
        assert top == "scales"

    def test_ocd_returns_scale(self, corpus):
        top = self._top_section("Obsessive Compulsive Disorder symptoms scale", corpus)
        assert top == "scales"

    def test_separation_anxiety_returns_scale(self, corpus):
        top = self._top_section("Separation Anxiety Disorder in children scale", corpus)
        assert top == "scales"

    def test_major_depressive_disorder_returns_scale(self, corpus):
        top = self._top_section("Major Depressive Disorder assessment scale", corpus)
        assert top == "scales"

    def test_total_anxiety_returns_scale(self, corpus):
        top = self._top_section("total anxiety score across subscales", corpus)
        assert top == "scales"

    def test_panic_disorder_returns_scale(self, corpus):
        top = self._top_section("Panic Disorder questionnaire subscale", corpus)
        assert top == "scales"

    def test_homework_scale_returns_scale(self, corpus):
        top = self._top_section("therapeutic homework completion scale MTT", corpus)
        assert top == "scales"

    def test_attendance_scale_returns_scale(self, corpus):
        top = self._top_section("caregiver attendance at therapy appointments scale", corpus)
        assert top == "scales"

    def test_generalized_anxiety_disorder_scale(self, corpus):
        top = self._top_section("Generalized Anxiety Disorder scale GAD", corpus)
        assert top == "scales"

    def test_relationship_scale_returns_scale(self, corpus):
        top = self._top_section("therapist relationship alliance scale", corpus)
        assert top == "scales"

    def test_depression_total_returns_scale(self, corpus):
        top = self._top_section("total depression score subscale", corpus)
        assert top == "scales"

    def test_expectancy_scale_returns_scale(self, corpus):
        top = self._top_section("treatment expectancy belief therapy will work scale", corpus)
        assert top == "scales"

    # -----------------------------------------------------------------------
    # Collections
    # -----------------------------------------------------------------------

    def test_rcads_collection(self, corpus):
        top = self._top_section("RCADS instrument collection set", corpus)
        assert top == "collections"

    def test_mtt_collection(self, corpus):
        top = self._top_section("MTT questionnaire collection set", corpus)
        assert top == "collections"

    def test_phq_collection(self, corpus):
        top = self._top_section("PHQ depression measures collection group", corpus)
        assert top == "collections"

    def test_gad_collection(self, corpus):
        top = self._top_section("GAD anxiety instrument collection group", corpus)
        assert top == "collections"

    # -----------------------------------------------------------------------
    # Verbatim item text (exact sentences from templates_official.txt)
    # -----------------------------------------------------------------------

    def test_item_afraid_crowded_places(self, corpus):
        top = self._top_section("Afraid of being in crowded places", corpus)
        assert top == "instruments"

    def test_item_cannot_concentrate(self, corpus):
        top = self._top_section("Cannot concentrate or think clearly", corpus)
        assert top == "instruments"

    def test_item_actively_participate(self, corpus):
        top = self._top_section(
            "I actively participate during appointments with my child's therapist", corpus)
        assert top == "instruments"

    def test_item_therapy_necessary(self, corpus):
        top = self._top_section(
            "I believe therapy is necessary to solve my child's problems", corpus)
        assert top == "instruments"

    def test_item_afraid_talk_in_class(self, corpus):
        top = self._top_section("Afraid to talk in front of class", corpus)
        assert top == "instruments"

    def test_item_worries_mistakes(self, corpus):
        top = self._top_section("Worries about making mistakes", corpus)
        assert top == "instruments"

    def test_item_scared_test(self, corpus):
        top = self._top_section("Scared to take a test", corpus)
        assert top == "instruments"

    def test_item_part_of_team(self, corpus):
        top = self._top_section(
            "I feel like I am part of a team with my child's therapist", corpus)
        assert top == "instruments"

    def test_item_follow_recommendations(self, corpus):
        top = self._top_section(
            "I follow my child's therapist's recommendations", corpus)
        assert top == "instruments"

    def test_item_comfortable_asking_questions(self, corpus):
        top = self._top_section(
            "I feel comfortable asking my child's therapist questions or raising concerns", corpus)
        assert top == "instruments"

    def test_item_worries_what_others_think(self, corpus):
        top = self._top_section("Worries what others think", corpus)
        assert top == "instruments"

    def test_item_help_choose_goals(self, corpus):
        top = self._top_section("I help choose my child's treatment goals", corpus)
        assert top == "instruments"

    # -----------------------------------------------------------------------
    # Semantic / cross-cutting queries
    # -----------------------------------------------------------------------

    def test_anxiety_and_depression_combined(self, corpus):
        tops = self._top_sections("anxiety and depression combined measure", corpus, k=5)
        assert "instruments" in tops or "scales" in tops

    def test_child_mental_health_self_report(self, corpus):
        top = self._top_section("child mental health self-report questionnaire", corpus)
        assert top == "instruments"

    def test_caregiver_therapeutic_alliance(self, corpus):
        tops = self._top_sections("caregiver therapeutic alliance and expectancy", corpus, k=5)
        assert "instruments" in tops or "scales" in tops

    def test_multilingual_instruments(self, corpus):
        top = self._top_section("instruments available in multiple languages", corpus)
        assert top == "instruments"

    def test_psychometric_questionnaire_generic(self, corpus):
        top = self._top_section("psychometric questionnaire", corpus)
        assert top in ("instruments", "scales", "collections")

    def test_therapist_child_family(self, corpus):
        top = self._top_section("therapist child family therapy sessions", corpus)
        assert top == "instruments"

    def test_depression_self_report_child(self, corpus):
        top = self._top_section("child depression self-report scale", corpus)
        assert top in ("instruments", "scales")

    def test_panic_separation_anxiety(self, corpus):
        tops = self._top_sections("panic and separation anxiety combined subscale", corpus, k=5)
        assert "scales" in tops or "instruments" in tops

    # -----------------------------------------------------------------------
    # Metric consistency — top results across all 4 metrics for same query
    # -----------------------------------------------------------------------

    def test_all_metrics_return_results(self, corpus):
        """All four metrics should return non-empty ranked results."""
        embeddings, texts, sections = corpus
        query_vec = embed_query("RCADS anxiety depression caregiver youth")
        for name, fn in METRICS.items():
            scores = fn(query_vec, embeddings)
            assert scores.shape[0] == len(texts), f"{name} score array length mismatch"
            assert np.isfinite(scores).all(), f"{name} produced non-finite scores"

    def test_cosine_scores_in_valid_range(self, corpus):
        embeddings, texts, sections = corpus
        query_vec = embed_query("Social Phobia questionnaire scale")
        scores = cosine_similarity(query_vec, embeddings)
        assert scores.min() >= -1.0 - 1e-4
        assert scores.max() <=  1.0 + 1e-4

    def test_euclidean_scores_nonpositive(self, corpus):
        embeddings, texts, sections = corpus
        query_vec = embed_query("Obsessive Compulsive Disorder")
        scores = euclidean_distance(query_vec, embeddings)
        assert (scores <= 0.0 + 1e-4).all()

    def test_manhattan_scores_nonpositive(self, corpus):
        embeddings, texts, sections = corpus
        query_vec = embed_query("caregiver attendance therapy")
        scores = manhattan_distance(query_vec, embeddings)
        assert (scores <= 0.0 + 1e-4).all()


# ===========================================================================
# TestCLISearch — subprocess tests that call search_similarity.py as a CLI
# ===========================================================================

@SKIP_IF_NO_EMBEDDINGS
class TestCLISearch:
    """Call search_similarity.py as a real subprocess, exactly as a user would.

    Each test runs the script with a query, prints the full output to the
    console, and asserts on return code and expected content.
    Two tests additionally write their output to embeddings/cli_query_results/.

    Run only this class:
        python -m pytest embeddings/test_search_similarity.py -v -k "TestCLISearch"
    """

    _SCRIPT = os.path.join(_HERE, "search_similarity.py")

    def _run_cli(self, *args) -> subprocess.CompletedProcess:
        """Run search_similarity.py as a subprocess with the given arguments."""
        cmd = [sys.executable, self._SCRIPT] + list(args)
        return subprocess.run(cmd, capture_output=True, text=True)

    # -----------------------------------------------------------------------
    # Instrument queries
    # -----------------------------------------------------------------------

    def test_cli_anxiety_in_children(self):
        result = self._run_cli("instruments that measure anxiety in children")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "instruments" in result.stdout

    def test_cli_rcads25_youth(self):
        result = self._run_cli("RCADS-25-Y-EN youth anxiety depression scale")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "RCADS" in result.stdout

    def test_cli_phq9_depression(self):
        result = self._run_cli("PHQ-9 patient health questionnaire depression")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "PHQ" in result.stdout

    def test_cli_mtt_caregiver(self):
        result = self._run_cli("MTT-35-CG caregiver therapist relationship")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "MTT" in result.stdout

    def test_cli_gad7(self):
        result = self._run_cli("GAD-7 generalized anxiety questionnaire")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "GAD" in result.stdout

    # -----------------------------------------------------------------------
    # --top-k flag
    # -----------------------------------------------------------------------

    def test_cli_top_k_10(self):
        result = self._run_cli("caregiver therapy attendance", "--top-k", "10")
        print(result.stdout)
        assert result.returncode == 0
        assert "Top 10" in result.stdout

    def test_cli_top_k_1(self):
        result = self._run_cli("RCADS anxiety scale", "--top-k", "1")
        print(result.stdout)
        assert result.returncode == 0
        assert "Top 1" in result.stdout

    def test_cli_top_k_20(self):
        result = self._run_cli("Obsessive Compulsive Disorder", "--top-k", "20")
        print(result.stdout)
        assert result.returncode == 0
        assert "Top 20" in result.stdout

    # -----------------------------------------------------------------------
    # Scale queries
    # -----------------------------------------------------------------------

    def test_cli_social_phobia_scale(self):
        result = self._run_cli("Social Phobia subscale score")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "scales" in result.stdout

    def test_cli_major_depressive_disorder(self):
        result = self._run_cli("Major Depressive Disorder assessment scale")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "scales" in result.stdout

    def test_cli_total_anxiety_depression(self):
        result = self._run_cli("total anxiety and depression combined score")
        print(result.stdout)
        assert result.returncode == 0
        assert len(result.stdout) > 0

    # -----------------------------------------------------------------------
    # Collection queries
    # -----------------------------------------------------------------------

    def test_cli_rcads_collection(self):
        result = self._run_cli("RCADS instrument collection set")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "collections" in result.stdout

    def test_cli_mtt_collection(self):
        result = self._run_cli("MTT questionnaire collection group")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "collections" in result.stdout

    # -----------------------------------------------------------------------
    # Verbatim item text
    # -----------------------------------------------------------------------

    def test_cli_item_afraid_crowded_places(self):
        result = self._run_cli("I feel afraid of being in crowded places")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_item_afraid_talk_in_class(self):
        result = self._run_cli("Afraid to talk in front of class")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_item_actively_participate(self):
        result = self._run_cli(
            "I actively participate during appointments with my child's therapist"
        )
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    # -----------------------------------------------------------------------
    # Edge cases
    # -----------------------------------------------------------------------

    def test_cli_single_word(self):
        """Single-word query — should still return results without crashing."""
        result = self._run_cli("anxiety")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert len(result.stdout) > 0

    def test_cli_generic_term(self):
        """Very generic term — all sections plausible, just check it runs."""
        result = self._run_cli("questionnaire")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_question_phrasing(self):
        """Query phrased as a natural-language question."""
        result = self._run_cli("Which instruments measure panic disorder in children?")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_multi_concept_query(self):
        """Multiple clinical concepts in one query string."""
        result = self._run_cli("anxiety depression panic separation caregiver youth")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    # -----------------------------------------------------------------------
    # Write-to-file tests — output saved to cli_query_results/
    # -----------------------------------------------------------------------

    def test_cli_write_instruments_anxiety(self):
        """Run an instrument query and save the full output to a file."""
        result = self._run_cli("instruments measuring anxiety in children")
        print(result.stdout)
        assert result.returncode == 0

        out_dir = os.path.join(_HERE, "cli_query_results")
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "instruments_anxiety.txt")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(result.stdout)
        print(f"Results saved to {out_path}")
        assert os.path.exists(out_path)

    def test_cli_write_scales_ocd_sp(self):
        """Run a scale query and save the full output to a file."""
        result = self._run_cli("Social Phobia Obsessive Compulsive Disorder scale")
        print(result.stdout)
        assert result.returncode == 0

        out_dir = os.path.join(_HERE, "cli_query_results")
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "scales_ocd_sp.txt")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(result.stdout)
        print(f"Results saved to {out_path}")
        assert os.path.exists(out_path)

    # -----------------------------------------------------------------------
    # SNOMED symptom-grounded queries (from constructs.ttl)
    # -----------------------------------------------------------------------

    def test_cli_fear_of_public_speaking(self):
        """Symptom: fear of public speaking — from snomed:247835002 in constructs.ttl."""
        result = self._run_cli("fear of public speaking anxiety disorder")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_intrusive_thoughts_ocd(self):
        """Symptom: intrusive thoughts — from snomed:225445003 in constructs.ttl."""
        result = self._run_cli("intrusive thoughts obsessive compulsive checking behavior")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_persistent_sadness_anhedonia(self):
        """Symptoms: persistent sadness + anhedonia — both in constructs.ttl."""
        result = self._run_cli("persistent sadness anhedonia nothing is fun depressive")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_fear_left_alone_separation(self):
        """Symptom: fear of being left alone — snomed:225629005 in constructs.ttl."""
        result = self._run_cli("fear of being left alone separation anxiety children")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "scales" in result.stdout or "instruments" in result.stdout

    def test_cli_sleep_disturbance_fatigue_depression(self):
        """Symptoms: sleep disturbance + fatigue — both listed in constructs.ttl."""
        result = self._run_cli("sleep disturbance fatigue depression PHQ questionnaire")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_panic_sudden_fear_heart_racing(self):
        """Symptom: panic — snomed:79015004 + heart racing item text in itemStems.ttl."""
        result = self._run_cli("sudden panic no reason heart beats fast out of nowhere")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "instruments" in result.stdout

    def test_cli_fear_appearing_ridiculous(self):
        """Symptom: fear of appearing ridiculous — snomed:247826009 in constructs.ttl."""
        result = self._run_cli("worried about looking foolish ridiculous in front of others")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "instruments" in result.stdout

    # -----------------------------------------------------------------------
    # New informant types (from informants.ttl: Teacher, Therapist, Adult)
    # -----------------------------------------------------------------------

    def test_cli_teacher_informant(self):
        """Informant: Teacher — from informants.ttl."""
        result = self._run_cli("teacher informant school anxiety questionnaire")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_therapist_informant(self):
        """Informant: Therapist — from informants.ttl."""
        result = self._run_cli("therapist rated assessment engagement questionnaire")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "instruments" in result.stdout

    def test_cli_adult_informant(self):
        """Informant: Adult — GAD-7 targets adults per templates_official.txt."""
        result = self._run_cli("adult self-report anxiety worry questionnaire")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "instruments" in result.stdout

    # -----------------------------------------------------------------------
    # Scale notation queries (from scales.ttl: SP, SAD, MDD, AnxDep, OCD)
    # -----------------------------------------------------------------------

    def test_cli_scale_notation_sp(self):
        """Scale: Social Phobia — notation SP in scales.ttl."""
        result = self._run_cli("SP Social Phobia subscale notation score")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "scales" in result.stdout

    def test_cli_scale_notation_sad(self):
        """Scale: Separation Anxiety Disorder — notation SAD in scales.ttl."""
        result = self._run_cli("SAD Separation Anxiety Disorder subscale score")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "scales" in result.stdout

    def test_cli_scale_notation_mdd(self):
        """Scale: Major Depressive Disorder — notation MDD in scales.ttl."""
        result = self._run_cli("MDD Major Depressive Disorder scale score")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "scales" in result.stdout

    def test_cli_scale_clarity(self):
        """Scale: Clarity (7.1) — MTT scale from scales.ttl."""
        result = self._run_cli("Clarity scale therapy understanding treatment goals")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_scale_relationship(self):
        """Scale: Relationship (7.1) — MTT scale from scales.ttl."""
        result = self._run_cli("Relationship scale therapist alliance bond")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "scales" in result.stdout

    # -----------------------------------------------------------------------
    # Clinical use-case queries
    # -----------------------------------------------------------------------

    def test_cli_screening_childhood_anxiety(self):
        """Clinical use case: screening for anxiety in children."""
        result = self._run_cli("screening tool childhood anxiety primary care")
        print(result.stdout)
        assert result.returncode == 0
        assert "instruments" in result.stdout

    def test_cli_parent_completed_child_anxiety(self):
        """Clinical use case: parent (caregiver) completing questionnaire about child."""
        result = self._run_cli("parent completed measure of child anxiety symptoms")
        print(result.stdout)
        assert result.returncode == 0
        assert "instruments" in result.stdout

    def test_cli_depressive_symptoms_adults(self):
        """Clinical use case: depression self-report in adults."""
        result = self._run_cli("self-report measure for depressive symptoms in adults")
        print(result.stdout)
        assert result.returncode == 0
        assert "instruments" in result.stdout

    def test_cli_therapeutic_alliance_engagement(self):
        """Clinical use case: measuring therapeutic relationship quality."""
        result = self._run_cli("therapeutic relationship quality engagement alliance scale")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    def test_cli_school_avoidance_worry(self):
        """Item-based clinical query: school avoidance due to worry."""
        result = self._run_cli("hard to go to school worried scared bad things will happen")
        print(result.stdout)
        assert result.returncode == 0
        assert "instruments" in result.stdout

    def test_cli_compulsive_checking_behavior(self):
        """Clinical query: compulsive checking — symptom in constructs.ttl."""
        result = self._run_cli("must check things over and over compulsive checking behavior")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout

    # -----------------------------------------------------------------------
    # Multilingual queries (inspired by itemStems.ttl)
    # -----------------------------------------------------------------------

    def test_cli_german_anxiety_item(self):
        """German item text from itemStems.ttl — Ich habe Angst."""
        result = self._run_cli("Ich habe Angst wenn ich eine Arbeit schreiben muss")
        print(result.stdout)
        assert result.returncode == 0
        assert "Cosine Similarity" in result.stdout
        assert "instruments" in result.stdout

    def test_cli_multilingual_translations(self):
        """Semantic query about multilingual instrument versions."""
        result = self._run_cli("multilingual translated versions child anxiety questionnaire")
        print(result.stdout)
        assert result.returncode == 0
        assert "instruments" in result.stdout

    # -----------------------------------------------------------------------
    # Additional write-to-file tests
    # -----------------------------------------------------------------------

    def test_cli_write_fear_public_speaking(self):
        """Symptom query saved to file."""
        result = self._run_cli("fear of public speaking social phobia subscale")
        print(result.stdout)
        assert result.returncode == 0

        out_dir = os.path.join(_HERE, "cli_query_results")
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "symptom_fear_speaking.txt")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(result.stdout)
        print(f"Results saved to {out_path}")
        assert os.path.exists(out_path)

    def test_cli_write_teacher_informant(self):
        """Teacher informant query saved to file."""
        result = self._run_cli("teacher informant school anxiety questionnaire", "--top-k", "10")
        print(result.stdout)
        assert result.returncode == 0

        out_dir = os.path.join(_HERE, "cli_query_results")
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "teacher_informant.txt")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(result.stdout)
        print(f"Results saved to {out_path}")
        assert os.path.exists(out_path)

    def test_cli_write_rcads47_full(self):
        """RCADS-47 full-scale query saved to file."""
        result = self._run_cli(
            "RCADS-47 full anxiety depression scale youth caregiver", "--top-k", "10"
        )
        print(result.stdout)
        assert result.returncode == 0

        out_dir = os.path.join(_HERE, "cli_query_results")
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "rcads47_full.txt")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(result.stdout)
        print(f"Results saved to {out_path}")
        assert os.path.exists(out_path)


# ---------------------------------------------------------------------------
# Allow running directly with: python test_search_similarity.py
# Output is printed to the console AND saved to test_results.txt
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import subprocess

    output_path = os.path.join(_HERE, "test_results.txt")

    result = subprocess.run(
        [sys.executable, "-m", "pytest", __file__, "-v", "--tb=short"],
        capture_output=True,
        text=True,
    )

    combined = result.stdout + result.stderr
    print(combined)

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(combined)

    print(f"Results saved to {output_path}")
    raise SystemExit(result.returncode)
