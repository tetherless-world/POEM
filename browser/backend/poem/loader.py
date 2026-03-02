from rdflib import Graph, Namespace, Literal, RDF, Dataset, URIRef
from rdflib.namespace import RDFS
from pathlib import Path
def load_dataset(data_dir: Path)-> Dataset:
    ds: Dataset = Dataset()
    for graph in data_dir.glob("*.ttl"):
        ds.graph(URIRef(f"urn:poem:file:{graph.name}")).parse(graph, format = "turtle")
    return ds

