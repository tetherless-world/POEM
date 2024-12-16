from rdflib import Graph, Literal, Namespace, BNode
from rdflib.namespace import RDF, XSD
import os

def main():
    g = Graph()

    files = os.listdir('./individuals')#('./swj_submission/individuals')
    for file in files:
        if file.endswith('.ttl') and file != 'individuals_full.ttl':
            print("parsing {}".format(file))
            g.parse(f"./individuals/{file}")
            print("parsed {}".format(file))

    ont = BNode()
    OWL = Namespace("http://www.w3.org/2002/07/owl#")
    g.bind("owl", OWL)
    g.add((ont, RDF.type, OWL.Ontology))
    g.add((ont, OWL.imports, Literal("https://raw.githubusercontent.com/tetherless-world/POEM/evidence-modeling/POEM.rdf", datatype=XSD.string)))

    g.serialize(destination='./individuals_full.ttl')

if __name__ == "__main__":
    main()