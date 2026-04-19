from pathlib import Path
from rdflib import Dataset
from typing import Dict, List, Tuple, TypedDict
from rapidfuzz import process, fuzz
import copy

class Item(TypedDict):
    name: str
    path: str
    type: str

INSTRUMENTS = {
    "rcads": {
        "keywords": [
            "rcads", "anxiety", "depression", "stress",
            "child", "youth", "adolescent", "internalizing"
        ],
        "items": [],
        "path_prefix": "instruments/rcads"
    },
    "phq": {
        "keywords": [
            "phq", "depression", "sad", "low mood",
            "hopeless", "fatigue", "adult"
        ],
        "items": [],
        "path_prefix": "instruments/phq"
    },
    "gad": {
        "keywords": [
            "gad", "anxiety", "worry", "panic",
            "nervous", "overthinking", "adult"
        ],
        "items":[],

        "path_prefix": "instruments/gad"
    },
    "mtt": {
        "keywords": [
            "treatment", "therapy", "monitor",
            "progress", "intervention", "mtt"
        ],
        "items": [],
        "path_prefix": "instruments/mtt"
    }
}
def generate_txt(POEM: Dataset):
    from poem.query import get_all_instruments

    instrumentPath: str ="instruments/"
    listPath: str = "list/"
    individual: str = "individual/"
    instruments = ["mtt", "rcads", "phq","gad"]
    #plan:
    """
    get all the instrument names from a list
    then create paths for each of them
    then place them into buckets based on scales.
    """
    all  = get_all_instruments(POEM)
    res_labels = {}
    for label in all:
        res_labels[label] = instrumentPath + individual + label
    res_else = {}
    for i in instruments:
        res_else[i] = instrumentPath + i, instrumentPath + listPath + i
    res = [res_labels, res_else]
    return res
def place_into_buckets(all_data) -> Dict:
    import copy
    instrument_buckets = copy.deepcopy(INSTRUMENTS)

    labels, e = all_data

    # individual instruments
    for label, path in labels.items():
        name: str = label.lower()

        for bucket_name, bucket_data in instrument_buckets.items():
            if any(keyword in name for keyword in bucket_data["keywords"]):
                bucket_data["items"].append({
                    "name": name,
                    "path": path,
                    "type": "variant"

                })

    # list + base routes
    for key, paths in e.items():
        name: str = key.lower()

        if name in instrument_buckets:
            bucket = instrument_buckets[name]

            bucket["items"].append({
            "name": name,
            "path": paths[0],
            "type": "base"
        })

            bucket["items"].append({
            "name": f"{name}-list",
            "path": paths[1],
            "type": "list"
        })

    return instrument_buckets

def score_buckets(query: str, instruments: Dict) -> Dict[str, int]:
    query = query.lower()
    scores: Dict[str, int] = {}

    for name, data in instruments.items():
        score: int = 0
        for keyword in data["keywords"]:
            if keyword in query:
                score += 1
        scores[name] = score

    return scores
def fuzzy_search_items(query: str, items: List[Item]) -> List[Tuple[Item, float]]:
    if not items:
        return []

    choices: List[str] = [item["name"] for item in items]

    results = process.extract(
        query,
        choices,
        scorer=fuzz.partial_ratio,
        limit = None
    )

    output: List[Tuple[Item, float]] = []

    for name, score, idx in results:
        if score > 60:
            output.append((items[idx], score))

    return output

def type_boost(item_type: str) -> int:
    if item_type == "base":
        return 50
    if item_type == "list":
        return 30
    return 0  # variants
def search(query: str, buckets: Dict, limit: int) -> List[Dict]:
    bucket_scores = score_buckets(query, buckets)

    sorted_buckets = sorted(
        bucket_scores.items(),
        key=lambda x: x[1],
        reverse=True
    )

    results: List[Dict] = []

    for bucket_name, bucket_score in sorted_buckets:
        if bucket_score == 0:
            continue

        items = buckets[bucket_name]["items"]

        fuzzy_results = fuzzy_search_items(query, items)
        if bucket_score > 0 and not fuzzy_results:
            for item in items:
                results.append({
            "name": item["name"],
            "path": item["path"],
            "score": bucket_score * 10 + type_boost(item["type"])
        })
        for item, score in fuzzy_results:
            boosted_score = score + (bucket_score * 10)
            boosted_score += type_boost(item["type"]) 
            
            results.append({
                "name": item["name"],
                "path": item["path"],
                "score": boosted_score
            })

    # fallback if nothing found
    if not results:
        all_items = []
        for data in buckets.values():
            all_items.extend(data["items"])

        fallback = fuzzy_search_items(query, all_items)
        if bucket_score > 0 and not fuzzy_results:
            for item in items:
                results.append({
            "name": item["name"],
            "path": item["path"],
            "score": bucket_score * 10 + type_boost(item["type"])
        })
        for item, score in fallback:
            results.append({
                "name": item["name"],
                "path": item["path"],
                "score": score
            })

    return sorted(results, key=lambda x: x["score"], reverse=True)[:limit]
def load_buckets(POEM):
    return place_into_buckets(generate_txt(POEM))